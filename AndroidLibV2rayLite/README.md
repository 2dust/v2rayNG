# AndroidLibV2rayLite

### Preparation
- latest Ubuntu environment
- At lease 30G free space
- Get Repo [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
###Windows 11
- install golang 
- run install gomobile
```

golang.org/x/mobile/cmd/gomobile

```
- Install android studio to simplify the ndk installation or download ndk 
- add ndk to environmental path both user variable and system variable.
  Note : This still won't automatically let golang detect ndk the way around it
  is to install goland https://www.jetbrains.com/go/download/#section=windows then import
  repo [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
  into goland enviroment accept all the popups .
- then after the sync with no errors run the following commands below 
```
go mod download

```

- For Android Library
```
gomobile bind -v -o androidLibV2rayNG.aar -target=android ./

```

- For ios Library

```
gomobile bind -v -o flutterxray.framework -target=ios ./

```

-For flutter builds use the similar steps above and create a native package by initializing both
kotlin v2rayNG and Either swift or c++ for ios and called the package in your flutter project. Make
sure the action callbacks in both android and ios code in package have similar name inorder to simplify
your action executions in flutter project.

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
