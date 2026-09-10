package com.v2ray.ang.shizuku;

import android.os.ParcelFileDescriptor;

/** Exposes private core inputs and keeps the protected test network alive. */
interface ICoreTetheringLease {
    ParcelFileDescriptor openEngineConfig(String launchId);
    boolean isCurrentLaunch(String launchId);
    void holdTestNetwork(in ParcelFileDescriptor tun);
    void releaseTestNetwork();
    String assetFingerprint();
    String[] listAssetFiles();
    ParcelFileDescriptor openAssetFile(String name);
}
