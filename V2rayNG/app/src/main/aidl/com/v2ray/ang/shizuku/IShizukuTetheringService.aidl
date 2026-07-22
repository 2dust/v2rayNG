package com.v2ray.ang.shizuku;

interface IShizukuTetheringService {
    int setWifiHotspotEnabled(boolean enabled) = 2;
    int getActiveTetheringTypes() = 3;
    int getRoutingState() = 4;
    String getRoutingDetail() = 5;
    int startRouting(boolean useHev, String profileName, String engineConfig, in String[] dnsServers, boolean ipv6Enabled, String assetPath, String xudpKey, String syncToken) = 6;
    int stopRouting() = 7;
    int notifyCoreStopping(String syncToken) = 9;
    int synchronizeRouting(String syncToken, boolean useHev, String profileName, String engineConfig, in String[] dnsServers, boolean ipv6Enabled) = 10;
    int notifyCoreStartFailed(String syncToken, String detail) = 11;
    int consumeWarning() = 12;
    void destroy() = 16777114;
}
