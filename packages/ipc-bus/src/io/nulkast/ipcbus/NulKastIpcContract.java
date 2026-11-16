package io.nulkast.ipcbus;

import android.net.Uri;

/**
 * Shared Signature-Level Inter-App IPC Contract (`packages/ipc-bus`).
 * Guarded by `<permission android:name="io.nulkast.permission.IPC_BUS" android:protectionLevel="signature" />`.
 */
public final class NulKastIpcContract {
    public static final String PERMISSION = "io.nulkast.permission.IPC_BUS";
    public static final String AUTHORITY = "io.nulkast.wall.ipc";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/state");

    public static final String METHOD_GET_STATE = "GET_FIREWALL_STATE";
    public static final String METHOD_SET_STEALTH_LOCKDOWN = "SET_STEALTH_LOCKDOWN";
    public static final String METHOD_TOGGLE_APP_BLOCK = "TOGGLE_APP_BLOCK";
    public static final String METHOD_APPLY_RECOMMENDED_SHIELD = "APPLY_RECOMMENDED_SHIELD";
    public static final String METHOD_RESET_NORMAL_STATE = "RESET_NORMAL_STATE";

    public static final String KEY_VPN_ACTIVE = "vpn_active";
    public static final String KEY_LOCKDOWN_ACTIVE = "stealth_lockdown_active";
    public static final String KEY_BLOCKED_TRACKERS_COUNT = "blocked_trackers_count";
    public static final String KEY_ALLOWED_QUERIES_COUNT = "allowed_queries_count";
    public static final String KEY_BLOCKED_APPS_COUNT = "blocked_apps_count";
    public static final String KEY_RECENT_EVENTS = "recent_events";
    public static final String KEY_ENABLE_LOCKDOWN = "enable_lockdown";
    public static final String KEY_TARGET_PACKAGE = "target_package";
    public static final String KEY_BLOCK_STATE = "block_state";

    private NulKastIpcContract() {}
}
