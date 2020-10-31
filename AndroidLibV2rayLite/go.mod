module github.com/2dust/AndroidLibV2rayLite

go 1.14

require (
	golang.org/x/mobile v0.0.0-20200329125638-4c31acba0007
	golang.org/x/sys v0.0.0-20201029080932-201ba4db2418
	v2ray.com/core v4.19.1+incompatible
)

replace v2ray.com/core => github.com/v2fly/v2ray-core v1.24.5-0.20201031030346-e3b96a1b5656
