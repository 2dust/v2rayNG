cccd6139fc9c2d2ad88172a8f8dfea44ba5ddf59# AndroidLibV2rayLite

### Preparation
- latest Ubuntu environment
- At lease 30G free space
- Get Repo [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
### Prepare Go
- Go to https://golang.org/doc/install and install latest go
- Make sure `go version` works as expected
### Prepare gomobile
- Go to https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile and install gomobile
- export PATH=$PATH:~/go/bin
- Make sure `gomobile init` works as expected
### Prepare NDK
- Go to https://developer.android.com/ndk/downloads and install latest NDK
- export PATH=$PATH:<wherever you ndk is located>
- Make sure `ndk-build -v` works as expected
### Make
- sudo apt install make
- Read and understand [build script](https://github.com/2dust/AndroidLibV2rayLite/blob/master/Makefile)
