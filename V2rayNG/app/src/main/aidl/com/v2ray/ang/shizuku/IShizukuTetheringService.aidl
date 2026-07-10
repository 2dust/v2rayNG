package com.v2ray.ang.shizuku;

interface IShizukuTetheringService {
    int getWifiHotspotState() = 1;
    int setWifiHotspotEnabled(boolean enabled) = 2;
    int getActiveTetheringTypes() = 3;
    int getRoutingState() = 4;
    String getRoutingDetail() = 5;
    int startRouting(boolean useHev, String profileName, String coreConfig, String hevConfig, String assetPath, String xudpKey, String syncToken) = 6;
    int stopRouting() = 7;
    int stopActiveTethering() = 8;
    int notifyCoreStopping(String syncToken) = 9;
    int synchronizeRouting(String syncToken, boolean useHev, String profileName, String coreConfig, String hevConfig) = 10;
    int notifyCoreStartFailed(String syncToken, String detail) = 11;
    void destroy() = 16777114;
}
