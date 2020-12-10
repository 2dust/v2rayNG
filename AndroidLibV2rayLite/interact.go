package libv2ray

import (
	"fmt"
	"io"
	"log"
	"os"
	"strings"
	"sync"

	"github.com/2dust/AndroidLibV2rayLite/CoreI"
	"github.com/2dust/AndroidLibV2rayLite/Process/Escort"
	"github.com/2dust/AndroidLibV2rayLite/VPN"
	mobasset "golang.org/x/mobile/asset"

	v2core "v2ray.com/core"
	v2filesystem "v2ray.com/core/common/platform/filesystem"
	v2stats "v2ray.com/core/features/stats"
	v2serial "v2ray.com/core/infra/conf/serial"
	v2internet "v2ray.com/core/transport/internet"

	v2applog "v2ray.com/core/app/log"
	v2commlog "v2ray.com/core/common/log"
)

const (
	v2Assert    = "v2ray.location.asset"
	assetperfix = "/dev/libv2rayfs0/asset"
)

/*V2RayPoint V2Ray Point Server
This is territory of Go, so no getter and setters!
*/
type V2RayPoint struct {
	SupportSet   V2RayVPNServiceSupportsSet
	statsManager v2stats.Manager

	dialer    *VPN.ProtectedDialer
	status    *CoreI.Status
	escorter  *Escort.Escorting
	v2rayOP   *sync.Mutex
	closeChan chan struct{}

	PackageName          string
	PackageCodePath      string
	DomainName           string
	ConfigureFileContent string
	EnableLocalDNS       bool
	ForwardIpv6          bool
	ProxyOnly            bool
}

/*V2RayVPNServiceSupportsSet To support Android VPN mode*/
type V2RayVPNServiceSupportsSet interface {
	Setup(Conf string) int
	Prepare() int
	Shutdown() int
	Protect(int) int
	OnEmitStatus(int, string) int
}

/*RunLoop Run V2Ray main loop
 */
func (v *V2RayPoint) RunLoop() (err error) {
	v.v2rayOP.Lock()
	defer v.v2rayOP.Unlock()
	//Construct Context
	v.status.PackageName = v.PackageName
	v.status.PackageCodePath = v.PackageCodePath

	if !v.status.IsRunning {
		v.closeChan = make(chan struct{})
		v.dialer.PrepareResolveChan()
		go v.dialer.PrepareDomain(v.DomainName, v.closeChan)
		go func() {
			select {
			// wait until resolved
			case <-v.dialer.ResolveChan():
				// shutdown VPNService if server name can not reolved
				if !v.dialer.IsVServerReady() {
					log.Println("vServer cannot resolved, shutdown")
					v.StopLoop()
				}

			// stop waiting if manually closed
			case <-v.closeChan:
			}
		}()

		err = v.pointloop()
	}
	return
}

/*StopLoop Stop V2Ray main loop
 */
func (v *V2RayPoint) StopLoop() (err error) {
	v.v2rayOP.Lock()
	defer v.v2rayOP.Unlock()
	if v.status.IsRunning {
		v.shutdownInit()
	}
	return
}

//Delegate Funcation
func (v *V2RayPoint) GetIsRunning() bool {
	return v.status.IsRunning
}

func (v *V2RayPoint) GetIsTRunning() bool {
	return v.status.IsTRunning
}

//Delegate Funcation
func (v V2RayPoint) QueryStats(tag string, direct string) int64 {
	if v.statsManager == nil {
		return 0
	}
	counter := v.statsManager.GetCounter(fmt.Sprintf("outbound>>>%s>>>traffic>>>%s", tag, direct))
	if counter == nil {
		return 0
	}
	return counter.Set(0)
}

func (v *V2RayPoint) shutdownInit() {
	close(v.closeChan)
	v.statsManager = nil
	v.status.Vpoint.Close()
	v.status.Vpoint = nil
	v.status.IsRunning = false

	v.escorter.EscortingDown()

	v.SupportSet.Shutdown()
	v.SupportSet.OnEmitStatus(0, "Closed")
}

func (v *V2RayPoint) pointloop() error {
	log.Println("loading v2ray config")
	config, err := v2serial.LoadJSONConfig(strings.NewReader(v.ConfigureFileContent))
	if err != nil {
		log.Println(err)
		return err
	}

	log.Println("new v2ray core")
	inst, err := v2core.New(config)
	if err != nil {
		log.Println(err)
		return err
	}
	v.status.Vpoint = inst
	v.statsManager = inst.GetFeature(v2stats.ManagerType()).(v2stats.Manager)

	log.Println("start v2ray core")
	v.status.IsRunning = true
	if err := v.status.Vpoint.Start(); err != nil {
		v.status.IsRunning = false
		log.Println(err)
		return err
	}

	v.SupportSet.Prepare()
	v.SupportSet.Setup(v.status.GetVPNSetupArg(v.EnableLocalDNS, v.ForwardIpv6))
	v.SupportSet.OnEmitStatus(0, "Running")

	v.status.IsTRunning = false
	if !v.ProxyOnly {
		if err := v.runTun2socks(); err != nil {
			log.Println(err)
			return err
		}
		v.status.IsTRunning = true

		log.Printf("EnableLocalDNS: %v\nForwardIpv6: %v\nDomainName: %s",
			v.EnableLocalDNS,
			v.ForwardIpv6,
			v.DomainName)
	}

	return nil
}

func initV2Env() {
	if os.Getenv(v2Assert) != "" {
		return
	}
	//Initialize asset API, Since Raymond Will not let notify the asset location inside Process,
	//We need to set location outside V2Ray
	os.Setenv(v2Assert, assetperfix)
	//Now we handle read
	v2filesystem.NewFileReader = func(path string) (io.ReadCloser, error) {
		if strings.HasPrefix(path, assetperfix) {
			p := path[len(assetperfix)+1:]
			//is it overridden?
			//by, ok := overridedAssets[p]
			//if ok {
			//	return os.Open(by)
			//}
			return mobasset.Open(p)
		}
		return os.Open(path)
	}
}

//Delegate Funcation
func TestConfig(ConfigureFileContent string) error {
	initV2Env()
	_, err := v2serial.LoadJSONConfig(strings.NewReader(ConfigureFileContent))
	return err
}

/*NewV2RayPoint new V2RayPoint*/
func NewV2RayPoint(s V2RayVPNServiceSupportsSet) *V2RayPoint {
	initV2Env()

	// inject our own log writer
	v2applog.RegisterHandlerCreator(v2applog.LogType_Console,
		func(lt v2applog.LogType,
			options v2applog.HandlerCreatorOptions) (v2commlog.Handler, error) {
			return v2commlog.NewLogger(createStdoutLogWriter()), nil
		})

	dialer := VPN.NewPreotectedDialer(s)
	v2internet.UseAlternativeSystemDialer(dialer)
	status := &CoreI.Status{}
	return &V2RayPoint{
		SupportSet: s,
		v2rayOP:    new(sync.Mutex),
		status:     status,
		dialer:     dialer,
		escorter:   &Escort.Escorting{Status: status},
	}
}

func (v V2RayPoint) runTun2socks() error {
	v.escorter.EscortingUp()
	go v.escorter.EscortRun(
		v.status.GetApp("libtun2socks.so"),
		v.status.GetTun2socksArgs(v.EnableLocalDNS, v.ForwardIpv6), "")

	return nil
}

/*CheckVersion int
This func will return libv2ray binding version.
*/
func CheckVersion() int {
	return CoreI.CheckVersion()
}

/*CheckVersionX string
This func will return libv2ray binding version and V2Ray version used.
*/
func CheckVersionX() string {
	return fmt.Sprintf("Libv2rayLite V%d, Core V%s", CheckVersion(), v2core.Version())
}
