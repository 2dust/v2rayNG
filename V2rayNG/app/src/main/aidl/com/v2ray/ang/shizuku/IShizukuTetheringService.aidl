package com.v2ray.ang.shizuku;

import com.v2ray.ang.shizuku.ICoreTetheringLease;

interface IShizukuTetheringService {
    int setWifiHotspotEnabled(boolean enabled) = 2;
    int getActiveTetheringTypes() = 3;
    int getRoutingState() = 4;
    String getRoutingDetail() = 5;
    int startRouting(boolean useHev, String profileName, in String[] dnsServers, boolean ipv6Enabled, String xudpKey, String syncToken, ICoreTetheringLease coreLease) = 6;
    int stopRouting() = 7;
    int notifyCoreStopping(String syncToken) = 9;
    int synchronizeRouting(String syncToken, boolean useHev, String profileName, in String[] dnsServers, boolean ipv6Enabled, ICoreTetheringLease coreLease) = 10;
    int notifyCoreStartFailed(String syncToken, String detail) = 11;
    int consumeWarning() = 12;
    int getIpv6TetheringTypes() = 13;
    void destroy() = 16777114;
}
