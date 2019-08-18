Platdep=shippedBinary.386 shippedBinary.amd64 shippedBinary.arm64 shippedBinary.arm

shippedBinaryDep:
		go get -u github.com/jteeuwen/go-bindata/...

shippedBinary.%:
	go-bindata -nometadata -nomemcopy -pkg shippedBinarys -o ./binary_$*.go -tags $* ArchIndep/ ArchDep/$*/

shippedBinary:shippedBinaryDep  $(Platdep)
	@echo "Done"

clean:
	-rm binary*
