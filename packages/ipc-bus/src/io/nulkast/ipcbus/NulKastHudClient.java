package io.nulkast.ipcbus;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client helper used by NulKast Home (Launcher) and other NulKast apps to query
 * and control NulKast Wall (Firewall) over the signature-protected IPC ContentProvider.
 */
public final class NulKastHudClient {
    public static final class Snapshot {
        public final boolean vpnActive;
        public final boolean stealthLockdown;
        public final int blockedTrackersCount;
        public final int allowedQueriesCount;
        public final int blockedAppsCount;
        public final List<String> blockedPackages;
        public final List<String> recentEvents;

        public Snapshot(
            boolean vpnActive,
            boolean stealthLockdown,
            int blockedTrackersCount,
            int allowedQueriesCount,
            int blockedAppsCount,
            List<String> blockedPackages,
            List<String> recentEvents
        ) {
            this.vpnActive = vpnActive;
            this.stealthLockdown = stealthLockdown;
            this.blockedTrackersCount = blockedTrackersCount;
            this.allowedQueriesCount = allowedQueriesCount;
            this.blockedAppsCount = blockedAppsCount;
            this.blockedPackages = blockedPackages != null ? blockedPackages : Collections.<String>emptyList();
            this.recentEvents = recentEvents != null ? recentEvents : Collections.<String>emptyList();
        }
    }

    private final Context context;

    public NulKastHudClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public Snapshot fetchSnapshot() {
        try {
            Bundle res = context.getContentResolver().call(
                NulKastIpcContract.CONTENT_URI,
                NulKastIpcContract.METHOD_GET_STATE,
                null,
                null
            );
            if (res == null) {
                return new Snapshot(false, false, 0, 0, 0, Collections.<String>emptyList(), Collections.<String>emptyList());
            }
            ArrayList<String> blockedList = res.getStringArrayList("blocked_packages_list");
            ArrayList<String> events = res.getStringArrayList(NulKastIpcContract.KEY_RECENT_EVENTS);
            return new Snapshot(
                res.getBoolean(NulKastIpcContract.KEY_VPN_ACTIVE, false),
                res.getBoolean(NulKastIpcContract.KEY_LOCKDOWN_ACTIVE, false),
                res.getInt(NulKastIpcContract.KEY_BLOCKED_TRACKERS_COUNT, 0),
                res.getInt(NulKastIpcContract.KEY_ALLOWED_QUERIES_COUNT, 0),
                res.getInt(NulKastIpcContract.KEY_BLOCKED_APPS_COUNT, 0),
                blockedList,
                events
            );
        } catch (Throwable t) {
            return new Snapshot(false, false, 0, 0, 0, Collections.<String>emptyList(), Collections.<String>emptyList());
        }
    }

    public boolean toggleAppBlock(String packageName, boolean block) {
        try {
            Bundle extras = new Bundle();
            extras.putString(NulKastIpcContract.KEY_TARGET_PACKAGE, packageName);
            extras.putBoolean(NulKastIpcContract.KEY_BLOCK_STATE, block);
            Bundle res = context.getContentResolver().call(
                NulKastIpcContract.CONTENT_URI,
                NulKastIpcContract.METHOD_TOGGLE_APP_BLOCK,
                (block ? "block:" : "allow:") + packageName,
                extras
            );
            return res != null && res.getBoolean("is_blocked", block) == block;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean setStealthLockdown(boolean enable) {
        try {
            Bundle extras = new Bundle();
            extras.putBoolean(NulKastIpcContract.KEY_ENABLE_LOCKDOWN, enable);
            Bundle res = context.getContentResolver().call(
                NulKastIpcContract.CONTENT_URI,
                NulKastIpcContract.METHOD_SET_STEALTH_LOCKDOWN,
                null,
                extras
            );
            return res != null && res.getBoolean(NulKastIpcContract.KEY_LOCKDOWN_ACTIVE, enable);
        } catch (Throwable t) {
            return false;
        }
    }

    public ContentObserver registerObserver(final Runnable onChanged) {
        ContentObserver obs = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                if (onChanged != null) onChanged.run();
            }
        };
        try {
            context.getContentResolver().registerContentObserver(
                NulKastIpcContract.CONTENT_URI,
                true,
                obs
            );
        } catch (Throwable ignored) {}
        return obs;
    }
}
