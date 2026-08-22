package com.v2ray.ang.shizuku;

import com.v2ray.ang.shizuku.ICoreTetheringLease;
import com.v2ray.ang.shizuku.ITetheringStatusListener;
import com.v2ray.ang.shizuku.TetheringStatusSnapshot;

interface IShizukuTetheringService {
    int setWifiHotspotEnabled(boolean enabled) = 2;
    int startRouting(boolean useHev, String profileName, in String[] dnsServers, boolean ipv6Enabled, String xudpKey, String syncToken, ICoreTetheringLease coreLease) = 6;
    int stopRouting() = 7;
    int notifyCoreStopping(String syncToken) = 9;
    int synchronizeRouting(String syncToken, boolean useHev, String profileName, in String[] dnsServers, boolean ipv6Enabled, ICoreTetheringLease coreLease) = 10;
    int notifyCoreStartFailed(String syncToken, String detail) = 11;
    TetheringStatusSnapshot getStatus(boolean includeIpv6) = 14;
    void setStatusListener(ITetheringStatusListener listener) = 15;
    void destroy() = 16777114;
}
