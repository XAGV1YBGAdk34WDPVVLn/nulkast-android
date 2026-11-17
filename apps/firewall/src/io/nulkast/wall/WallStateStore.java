package io.nulkast.wall;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import io.nulkast.ipcbus.NulKastIpcContract;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, battery-optimized state store and persistent configuration for NulKast Wall.
 * Uses lock-free `AtomicInteger` counters, 30-second lazy disk flushing, and screen-interactive
 * 1,000ms debounced Binder IPC notifications (`0` disk writes or Binder wakeups while screen is off).
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

    private static final long DISK_FLUSH_INTERVAL_MS = 30_000L;
    private static final long IPC_NOTIFY_DEBOUNCE_MS = 1_000L;

    private static volatile boolean vpnActive = false;
    private static volatile boolean stealthLockdown = false;
    private static volatile boolean hasUndo = false;
    private static final AtomicInteger blockedTrackersCount = new AtomicInteger(0);
    private static final AtomicInteger allowedQueriesCount = new AtomicInteger(0);
    private static final AtomicLong lastDiskFlushMs = new AtomicLong(0L);
    private static final AtomicLong lastIpcNotifyMs = new AtomicLong(0L);

    private static final Set<String> blockedPackages = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> customBlockedDomains = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> undoPackages = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> undoDomains = Collections.synchronizedSet(new HashSet<String>());
    private static final LinkedList<String> recentEvents = new LinkedList<String>();
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final Date REUSABLE_DATE = new Date();
    private static volatile boolean initialized = false;

    private WallStateStore() {}

    public static void ensureInitialized(Context ctx) {
        if (initialized) return;
        synchronized (WallStateStore.class) {
            if (initialized) return;
            SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            stealthLockdown = sp.getBoolean(KEY_STEALTH_LOCKDOWN, false);
            blockedTrackersCount.set(sp.getInt(KEY_BLOCKED_COUNT, 0));
            allowedQueriesCount.set(sp.getInt(KEY_ALLOWED_COUNT, 0));
            Set<String> savedPkgs = sp.getStringSet(KEY_BLOCKED_PKGS, Collections.<String>emptySet());
            if (savedPkgs != null) blockedPackages.addAll(savedPkgs);
            Set<String> savedDoms = sp.getStringSet(KEY_CUSTOM_DOMAINS, Collections.<String>emptySet());
            if (savedDoms != null) customBlockedDomains.addAll(savedDoms);
            hasUndo = sp.getBoolean(KEY_HAS_UNDO, false);
            Set<String> uPkgs = sp.getStringSet(KEY_UNDO_PKGS, Collections.<String>emptySet());
            if (uPkgs != null) undoPackages.addAll(uPkgs);
            Set<String> uDoms = sp.getStringSet(KEY_UNDO_DOMAINS, Collections.<String>emptySet());
            if (uDoms != null) undoDomains.addAll(uDoms);
            lastDiskFlushMs.set(System.currentTimeMillis());
            initialized = true;
        }
    }

    public static void flushCountersToDisk(Context ctx) {
        if (ctx == null || !initialized) return;
        lastDiskFlushMs.set(System.currentTimeMillis());
        ctx.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BLOCKED_COUNT, blockedTrackersCount.get())
            .putInt(KEY_ALLOWED_COUNT, allowedQueriesCount.get())
            .apply();
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
        recordEvent(ctx, "[UNDO] Restored previous firewall snapshot (" + blockedPackages.size() + " apps, " + customBlockedDomains.size() + " rules)", false);
        notifyIpc(ctx, true);
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
        recordEvent(ctx, "[NORMAL-STATE] Reset to Normal State (0 apps blocked, Undo saved)", false);
        notifyIpc(ctx, true);
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
            false
        );
        notifyIpc(ctx, true);
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
            .putInt(KEY_BLOCKED_COUNT, blockedTrackersCount.get())
            .putInt(KEY_ALLOWED_COUNT, allowedQueriesCount.get())
            .apply();
        notifyIpc(ctx, true);
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
        notifyIpc(ctx, true);
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
            return;
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
        notifyIpc(ctx, true);
    }

    public static Set<String> getCustomBlockedDomains(Context ctx) {
        ensureInitialized(ctx);
        synchronized (customBlockedDomains) {
            return new HashSet<String>(customBlockedDomains);
        }
    }

    /**
     * Zero-allocation read-only access to the live synchronized custom blocked domain set
     * for the hot packet loop in `NulKastVpnService`.
     */
    public static Set<String> getCustomBlockedDomainsLiveRef(Context ctx) {
        ensureInitialized(ctx);
        return customBlockedDomains;
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
        notifyIpc(ctx, true);
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
        notifyIpc(ctx, true);
    }

    public static int getBlockedTrackersCount(Context ctx) {
        ensureInitialized(ctx);
        return blockedTrackersCount.get();
    }

    public static int getAllowedQueriesCount(Context ctx) {
        ensureInitialized(ctx);
        return allowedQueriesCount.get();
    }

    public static void recordDnsDecision(Context ctx, String appLabel, String domain, boolean blocked) {
        ensureInitialized(ctx);
        if (blocked) {
            blockedTrackersCount.incrementAndGet();
        } else {
            allowedQueriesCount.incrementAndGet();
        }
        long now = System.currentTimeMillis();
        long prevFlush = lastDiskFlushMs.get();
        if (now - prevFlush >= DISK_FLUSH_INTERVAL_MS && lastDiskFlushMs.compareAndSet(prevFlush, now)) {
            flushCountersToDisk(ctx);
        }
        String tag = blocked ? "🛑 BLOCKED" : "✅ ALLOWED";
        recordEvent(ctx, tag + " | " + appLabel + " -> " + domain, true);
    }

    public static synchronized void recordEvent(Context ctx, String msg, boolean notify) {
        REUSABLE_DATE.setTime(System.currentTimeMillis());
        String ts = TIME_FMT.format(REUSABLE_DATE);
        recentEvents.addFirst(ts + " " + msg);
        while (recentEvents.size() > 60) {
            recentEvents.removeLast();
        }
        if (notify && ctx != null) {
            notifyIpc(ctx, false);
        }
    }

    public static synchronized ArrayList<String> getRecentEvents() {
        return new ArrayList<String>(recentEvents);
    }

    public static synchronized void clearRecentEvents(Context ctx) {
        recentEvents.clear();
        if (ctx != null) {
            notifyIpc(ctx, true);
        }
    }

    private static void notifyIpc(Context ctx, boolean forceImmediate) {
        if (ctx == null) return;
        try {
            Context appCtx = ctx.getApplicationContext();
            if (!forceImmediate) {
                PowerManager pm = (PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isInteractive()) {
                    // Phone screen is OFF: skip cross-process Binder ContentObserver wakeups completely!
                    return;
                }
                long now = System.currentTimeMillis();
                long prev = lastIpcNotifyMs.get();
                if (now - prev < IPC_NOTIFY_DEBOUNCE_MS || !lastIpcNotifyMs.compareAndSet(prev, now)) {
                    return;
                }
            } else {
                lastIpcNotifyMs.set(System.currentTimeMillis());
            }
            appCtx.getContentResolver().notifyChange(NulKastIpcContract.CONTENT_URI, null);
        } catch (Throwable ignored) {}
    }
}
