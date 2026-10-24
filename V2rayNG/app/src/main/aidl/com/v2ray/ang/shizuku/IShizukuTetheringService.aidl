package com.v2ray.ang.shizuku;

import com.v2ray.ang.shizuku.ICoreTetheringLease;
import com.v2ray.ang.shizuku.ITetheringStatusListener;
import com.v2ray.ang.shizuku.TetheringStatusSnapshot;

interface IShizukuTetheringService {
    int setWifiHotspotEnabled(boolean enabled) = 2;
    int startRouting(boolean useHev, String profileName, in String[] dnsServers, boolean ipv6Enabled, String xudpKey, String syncToken, String launchId, ICoreTetheringLease coreLease) = 6;
    int stopRouting() = 7;
    oneway void notifyCoreStopping(String syncToken) = 9;
    oneway void synchronizeRouting(String syncToken, boolean useHev, String profileName, in String[] dnsServers, boolean ipv6Enabled, String xudpKey, String launchId, ICoreTetheringLease coreLease) = 10;
    oneway void notifyCoreStartFailed(String syncToken, String detail) = 11;
    TetheringStatusSnapshot getStatus(boolean includeIpv6) = 14;
    oneway void setStatusListener(ITetheringStatusListener listener) = 15;
    void destroy() = 16777114;
}
