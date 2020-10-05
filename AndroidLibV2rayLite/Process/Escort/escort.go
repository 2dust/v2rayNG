package Escort

import (
	"os"
	"os/exec"
	"time"

	"log"

	"github.com/2dust/AndroidLibV2rayLite/CoreI"
)

func (v *Escorting) EscortRun(proc string, pt []string, additionalEnv string) {
	log.Println(proc, pt)
	count := 0
	for count <= 42 {
		cmd := exec.Command(proc, pt...)
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr

		if len(additionalEnv) > 0 {
			//additionalEnv := "FOO=bar"
			newEnv := append(os.Environ(), additionalEnv)
			cmd.Env = newEnv
		}

		if err := cmd.Start(); err != nil {
			log.Println("EscortRun cmd.Start err", err)
			goto CMDERROR
		}

		if v.escortProcess == nil {
			log.Println("EscortRun v.escortProcess nil")
			break
		}

		*v.escortProcess = append(*v.escortProcess, cmd.Process)
		log.Println("EscortRun Waiting....")

		if err := cmd.Wait(); err != nil {
			log.Println("EscortRun cmd.Wait err:", err)
		}

	CMDERROR:
		if v.Status.IsRunning {
			log.Println("EscortRun Unexpected Exit, Restart now.")
			count++
		} else {
			log.Println("EscortRun Exit")
			break
		}
	}
}

func (v *Escorting) EscortingUp() {
	if v.escortProcess != nil {
		return
	}
	v.escortProcess = new([](*os.Process))
}

func (v *Escorting) EscortingDown() {
	if v.escortProcess == nil {
		return
	}

	log.Println("EscortingDown() Killing all escorted process ")
	for _, pr := range *v.escortProcess {
		pr.Kill()
		if _, err := pr.Wait(); err != nil {
			log.Println("EscortingDown pr.Wait err:", err)
		}
	}
	v.escortProcess = nil
}

type Escorting struct {
	escortProcess *[](*os.Process)
	Status        *CoreI.Status
}
