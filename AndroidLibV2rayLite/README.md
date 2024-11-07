# AndroidLibV2rayLite Setup Instructions

### Prerequisites
- Operating System: Latest Ubuntu
- Disk Space: At least 30 GB free
- Clone the repository:
- For V2ray:
bash
git clone https://github.com/2dust/AndroidLibV2rayLite.git

- For Xray:
bash
git clone https://github.com/2dust/AndroidLibXrayLite.git

### Go Installation
- Visit Go Installation for instructions
- Install the latest version of Go: https://golang.org/doc/install
- Verify installation: bash go version
bash
go version

### gomobile Installation
- Visit gomobile Installation for details.
- Install gomobile: https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile
- Update your PATH: 
bash
export PATH=$PATH:~/go/bin

- Initialize gomobile:
bash
gomobile init

### Android NDK Installation
- Download and install the latest NDK from NDK Downloads. https://developer.android.com/ndk/downloads
- Update your PATH with the NDK location:
bash
export PATH=$PATH:<path_to_your_ndk>

- Verify NDK installation:
bash
ndk-build -v

### Build Environment Setup
- Install Make:
bash
sudo apt install make

- Review the Makefile to understand the build process: https://github.com/2dust/AndroidLibV2rayLite/blob/master/Makefile
- This refactored version organizes the steps into clear sections, uses consistent formatting, and provides command snippets for ease of use.
