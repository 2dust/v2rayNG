package com.v2ray.ang.shizuku;

import android.os.ParcelFileDescriptor;

/** Exposes the running engine configuration and keeps the protected test network alive. */
interface ICoreTetheringLease {
    ParcelFileDescriptor openEngineConfig();
    void holdTestNetwork(in ParcelFileDescriptor tun);
    void releaseTestNetwork();
}
