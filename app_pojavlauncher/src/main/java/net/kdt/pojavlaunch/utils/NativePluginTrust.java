package net.kdt.pojavlaunch.utils;

import java.util.Set;

final class NativePluginTrust {
    private NativePluginTrust() { }
    static String key(String packageName, String signingIdentity) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_.]+")
                || signingIdentity == null || !signingIdentity.matches("[a-f0-9]{64}")) return null;
        return packageName + "|" + signingIdentity;
    }
    static boolean isApproved(Set<String> approvals, String packageName, String signingIdentity) {
        String key = key(packageName, signingIdentity);
        return key != null && approvals.contains(key);
    }
}
