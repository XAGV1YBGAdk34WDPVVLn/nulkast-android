package io.nulkast.wall;

import android.content.Context;
import android.content.SharedPreferences;
import io.nulkast.ipcbus.NulKastIpcContract;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Thread-safe state store and persistent configuration for NulKast Wall.
 */
public final class WallStateStore {
    private static final String PREFS_NAME = "nulkast_wall_prefs";
    private static final String KEY_VPN_ENABLED = "vpn_enabled";
    private static final String KEY_STEALTH_LOCKDOWN = "stealth_lockdown";
    private static final String KEY_BLOCKED_PKGS = "blocked_packages";
    private static final String KEY_CUSTOM_DOMAINS = "custom_blocked_domains";
    private static final String KEY_BLOCKED_COUNT = "blocked_trackers_count";
    private static final String KEY_ALLOWED_COUNT = "allowed_queries_count";
    private static final String KEY_UNDO_PKGS = "undo_blocked_packages";
    private static final String KEY_UNDO_DOMAINS = "undo_custom_domains";
    private static final String KEY_HAS_UNDO = "has_undo_snapshot";

    private static volatile boolean vpnActive = false;
    private static volatile boolean stealthLockdown = false;
    private static volatile boolean hasUndo = false;
    private static volatile int blockedTrackersCount = 0;
    private static volatile int allowedQueriesCount = 0;
    private static final Set<String> blockedPackages = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> customBlockedDomains = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> undoPackages = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> undoDomains = Collections.synchronizedSet(new HashSet<String>());
    private static final LinkedList<String> recentEvents = new LinkedList<String>();
    private static volatile boolean initialized = false;

    private WallStateStore() {}

    public static synchronized void ensureInitialized(Context ctx) {
        if (initialized) return;
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        stealthLockdown = sp.getBoolean(KEY_STEALTH_LOCKDOWN, false);
        blockedTrackersCount = sp.getInt(KEY_BLOCKED_COUNT, 0);
        allowedQueriesCount = sp.getInt(KEY_ALLOWED_COUNT, 0);
        Set<String> savedPkgs = sp.getStringSet(KEY_BLOCKED_PKGS, Collections.<String>emptySet());
        if (savedPkgs != null) blockedPackages.addAll(savedPkgs);
        Set<String> savedDoms = sp.getStringSet(KEY_CUSTOM_DOMAINS, Collections.<String>emptySet());
        if (savedDoms != null) customBlockedDomains.addAll(savedDoms);
        hasUndo = sp.getBoolean(KEY_HAS_UNDO, false);
        Set<String> uPkgs = sp.getStringSet(KEY_UNDO_PKGS, Collections.<String>emptySet());
        if (uPkgs != null) undoPackages.addAll(uPkgs);
        Set<String> uDoms = sp.getStringSet(KEY_UNDO_DOMAINS, Collections.<String>emptySet());
        if (uDoms != null) undoDomains.addAll(uDoms);
        initialized = true;
    }

    public static synchronized void saveUndoSnapshot(Context ctx) {
        ensureInitialized(ctx);
        undoPackages.clear();
        undoPackages.addAll(blockedPackages);
        undoDomains.clear();
        undoDomains.addAll(customBlockedDomains);
        hasUndo = true;
        ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_UNDO, true)
            .putStringSet(KEY_UNDO_PKGS, new HashSet<String>(undoPackages))
            .putStringSet(KEY_UNDO_DOMAINS, new HashSet<String>(undoDomains))
            .apply();
    }

    public static boolean hasUndoSnapshot(Context ctx) {
        ensureInitialized(ctx);
        return hasUndo;
    }

    public static synchronized boolean undoLastChange(Context ctx) {
        ensureInitialized(ctx);
        if (!hasUndo) return false;
        Set<String> currentPkgs = new HashSet<String>(blockedPackages);
        Set<String> currentDoms = new HashSet<String>(customBlockedDomains);

        blockedPackages.clear();
        blockedPackages.addAll(undoPackages);
        customBlockedDomains.clear();
        customBlockedDomains.addAll(undoDomains);

        undoPackages.clear();
        undoPackages.addAll(currentPkgs);
        undoDomains.clear();
        undoDomains.addAll(currentDoms);

        ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_BLOCKED_PKGS, new HashSet<String>(blockedPackages))
            .putStringSet(KEY_CUSTOM_DOMAINS, new HashSet<String>(customBlockedDomains))
            .putStringSet(KEY_UNDO_PKGS, new HashSet<String>(undoPackages))
            .putStringSet(KEY_UNDO_DOMAINS, new HashSet<String>(undoDomains))
            .apply();
        recordEvent(ctx, "[UNDO] Restored previous firewall snapshot (" + blockedPackages.size() + " apps, " + customBlockedDomains.size() + " rules)", true);
        return true;
    }

    public static synchronized void resetToNormalState(Context ctx) {
        ensureInitialized(ctx);
        saveUndoSnapshot(ctx);
        stealthLockdown = false;
        blockedPackages.clear();
        customBlockedDomains.clear();
        ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STEALTH_LOCKDOWN, false)
            .putStringSet(KEY_BLOCKED_PKGS, Collections.<String>emptySet())
            .putStringSet(KEY_CUSTOM_DOMAINS, Collections.<String>emptySet())
            .apply();
        recordEvent(ctx, "[NORMAL-STATE] Reset to Normal State (0 apps blocked, Undo saved)", true);
    }

    public static synchronized void applyPresetOrRulepack(
        Context ctx,
        String presetName,
        Set<String> newPkgs,
        Set<String> newDoms,
        boolean replaceExisting
    ) {
        ensureInitialized(ctx);
        saveUndoSnapshot(ctx);
        if (replaceExisting) {
            blockedPackages.clear();
            customBlockedDomains.clear();
        }
        if (newPkgs != null) {
            for (String p : newPkgs) {
                if (!io.nulkast.netintel.AppIntelligence.isProtectedCore(p)) {
                    blockedPackages.add(p);
                }
            }
        }
        if (newDoms != null) {
            for (String d : newDoms) {
                if (d != null && !d.trim().isEmpty()) {
                    customBlockedDomains.add(d.trim().toLowerCase(Locale.US));
                }
            }
        }
        ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_BLOCKED_PKGS, new HashSet<String>(blockedPackages))
            .putStringSet(KEY_CUSTOM_DOMAINS, new HashSet<String>(customBlockedDomains))
            .apply();
        recordEvent(
            ctx,
            "[PRESET] Applied '" + presetName + "' (" + blockedPackages.size() + " apps, " + customBlockedDomains.size() + " custom domains)",
            true
        );
    }

    public static boolean isVpnActive() {
        return vpnActive;
    }

    public static void setVpnActive(Context ctx, boolean active) {
        ensureInitialized(ctx);
        vpnActive = active;
        ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VPN_ENABLED, active)
            .apply();
        notifyIpc(ctx);
    }

    public static boolean wasVpnEnabled(Context ctx) {
        ensureInitialized(ctx);
        return ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VPN_ENABLED, false);
    }

    public static boolean isStealthLockdown(Context ctx) {
        ensureInitialized(ctx);
        return stealthLockdown;
    }

    public static void setStealthLockdown(Context ctx, boolean enable) {
        ensureInitialized(ctx);
        stealthLockdown = enable;
        ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STEALTH_LOCKDOWN, enable)
            .apply();
        recordEvent(ctx, enable ? "[LOCKDOWN] Stealth Lockdown ENABLED" : "[LOCKDOWN] Stealth Lockdown DISABLED", false);
        notifyIpc(ctx);
    }

    public static Set<String> getBlockedPackages(Context ctx) {
        ensureInitialized(ctx);
        synchronized (blockedPackages) {
            return new HashSet<String>(blockedPackages);
        }
    }

    public static boolean isPackageBlocked(Context ctx, String pkg) {
        ensureInitialized(ctx);
        return blockedPackages.contains(pkg);
    }

    public static void setPackageBlocked(Context ctx, String pkg, boolean blocked) {
        ensureInitialized(ctx);
        if (blocked && io.nulkast.netintel.AppIntelligence.isProtectedCore(pkg)) {
            return; // Deterministically prevent blocking Protected Core packages
        }
        saveUndoSnapshot(ctx);
        if (blocked) {
            blockedPackages.add(pkg);
        } else {
            blockedPackages.remove(pkg);
        }
        synchronized (blockedPackages) {
            ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_BLOCKED_PKGS, new HashSet<String>(blockedPackages))
                .apply();
        }
        recordEvent(ctx, (blocked ? "[APP-BLOCK] " : "[APP-ALLOW] ") + pkg, false);
        notifyIpc(ctx);
    }

    public static Set<String> getCustomBlockedDomains(Context ctx) {
        ensureInitialized(ctx);
        synchronized (customBlockedDomains) {
            return new HashSet<String>(customBlockedDomains);
        }
    }

    public static void addCustomBlockedDomain(Context ctx, String domain) {
        ensureInitialized(ctx);
        if (domain == null) return;
        String clean = domain.trim().toLowerCase(Locale.US);
        if (clean.isEmpty()) return;
        customBlockedDomains.add(clean);
        synchronized (customBlockedDomains) {
            ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_CUSTOM_DOMAINS, new HashSet<String>(customBlockedDomains))
                .apply();
        }
        recordEvent(ctx, "[RULE-ADDED] Sinkholed " + clean, false);
        notifyIpc(ctx);
    }

    public static void removeCustomBlockedDomain(Context ctx, String domain) {
        ensureInitialized(ctx);
        if (domain == null) return;
        customBlockedDomains.remove(domain.trim().toLowerCase(Locale.US));
        synchronized (customBlockedDomains) {
            ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_CUSTOM_DOMAINS, new HashSet<String>(customBlockedDomains))
                .apply();
        }
        notifyIpc(ctx);
    }

    public static int getBlockedTrackersCount(Context ctx) {
        ensureInitialized(ctx);
        return blockedTrackersCount;
    }

    public static int getAllowedQueriesCount(Context ctx) {
        ensureInitialized(ctx);
        return allowedQueriesCount;
    }

    public static void recordDnsDecision(Context ctx, String appLabel, String domain, boolean blocked) {
        ensureInitialized(ctx);
        if (blocked) {
            blockedTrackersCount++;
        } else {
            allowedQueriesCount++;
        }
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
            .putInt(KEY_BLOCKED_COUNT, blockedTrackersCount)
            .putInt(KEY_ALLOWED_COUNT, allowedQueriesCount)
            .apply();
        String tag = blocked ? "🛑 BLOCKED" : "✅ ALLOWED";
        recordEvent(ctx, tag + " | " + appLabel + " -> " + domain, true);
    }

    public static synchronized void recordEvent(Context ctx, String msg, boolean notify) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        recentEvents.addFirst(ts + " " + msg);
        while (recentEvents.size() > 60) {
            recentEvents.removeLast();
        }
        if (notify && ctx != null) {
            notifyIpc(ctx);
        }
    }

    public static synchronized ArrayList<String> getRecentEvents() {
        return new ArrayList<String>(recentEvents);
    }

    public static synchronized void clearRecentEvents(Context ctx) {
        recentEvents.clear();
        if (ctx != null) {
            notifyIpc(ctx);
        }
    }

    private static void notifyIpc(Context ctx) {
        try {
            ctx.getApplicationContext()
                .getContentResolver()
                .notifyChange(NulKastIpcContract.CONTENT_URI, null);
        } catch (Throwable ignored) {}
    }
}
