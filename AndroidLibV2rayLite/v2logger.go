package libv2ray

// This struct creates our own log writer without datatime stamp
// As Android adds time stamps on each line

import (
	"log"
	"os"

	v2commlog "github.com/xtls/xray-core/common/log"
)

type consoleLogWriter struct {
	logger *log.Logger
}

func (w *consoleLogWriter) Write(s string) error {
	w.logger.Print(s)
	return nil
}

func (w *consoleLogWriter) Close() error {
	return nil
}

// This logger won't print data/time stamps
func createStdoutLogWriter() v2commlog.WriterCreator {
	return func() v2commlog.Writer {
		return &consoleLogWriter{
			logger: log.New(os.Stdout, "", 0)}
	}
}
