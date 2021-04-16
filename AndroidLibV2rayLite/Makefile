pb:
	  go get -u github.com/golang/protobuf/protoc-gen-go
		@echo "pb Start"
asset:
	bash gen_assets.sh download
	mkdir assets
	cp -v data/*.dat assets/
	# cd assets;curl https://raw.githubusercontent.com/2dust/AndroidLibV2rayLite/master/data/geosite.dat > geosite.dat		
	# cd assets;curl https://raw.githubusercontent.com/2dust/AndroidLibV2rayLite/master/data/geoip.dat > geoip.dat

ANDROID_HOME=$(HOME)/android-sdk-linux
export ANDROID_HOME
PATH:=$(PATH):$(GOPATH)/bin
export PATH
downloadGoMobile:
	sudo apt-get install -qq libstdc++6:i386 lib32z1 expect
	cd ~ ;curl -L https://raw.githubusercontent.com/2dust/AndroidLibV2rayLite/master/ubuntu-cli-install-android-sdk.sh | sudo bash - > /dev/null
	ls ~
	ls ~/android-sdk-linux/

BuildMobile:
	gomobile init
	gomobile bind -v -ldflags='-s -w' github.com/2dust/AndroidLibV2rayLite

all: asset pb
	@echo DONE
