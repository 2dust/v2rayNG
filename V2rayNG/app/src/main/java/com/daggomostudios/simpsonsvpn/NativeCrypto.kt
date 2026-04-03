package com.daggomostudios.simpsonsvpn

object NativeCrypto {
    init {
        System.loadLibrary("vpn_security_lib")
    }

    external fun getDecryptionKey(): String
}
