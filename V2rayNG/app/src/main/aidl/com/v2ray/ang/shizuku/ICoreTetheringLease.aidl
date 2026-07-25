package com.v2ray.ang.shizuku;

import android.os.ParcelFileDescriptor;

/** Keeps the protected test network alive in the normal core process. */
interface ICoreTetheringLease {
    void holdTestNetwork(in ParcelFileDescriptor tun);
    void releaseTestNetwork();
}
