package io.nulkast.wall;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import io.nulkast.ipcbus.NulKastIpcContract;
import io.nulkast.netintel.AppIntelligence;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cryptographic Signature + ADB Shell Guarded ContentProvider (`content://io.nulkast.wall.ipc/state`).
 * Allows only apps signed with the NulKast release certificate (`io.nulkast.permission.IPC_BUS`)
 * OR the local ADB Shell debugger (`Process.SHELL_UID = 2000`) to query or mutate firewall state.
 */
public final class NulKastWallIpcProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx != null) {
            WallStateStore.ensureInitialized(ctx);
        }
        return true;
    }

    private void enforceCallerAuthorized(Context ctx) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid() || uid == Process.SHELL_UID || uid == Process.SYSTEM_UID || uid == 0) {
            return;
        }
        if (ctx.checkCallingOrSelfPermission(NulKastIpcContract.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        throw new SecurityException("Unauthorized caller UID " + uid + ": requires " + NulKastIpcContract.PERMISSION);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        Bundle out = new Bundle();
        if (ctx == null) return out;
        enforceCallerAuthorized(ctx);
        WallStateStore.ensureInitialized(ctx);

        if (NulKastIpcContract.METHOD_GET_STATE.equals(method)) {
            out.putBoolean(NulKastIpcContract.KEY_VPN_ACTIVE, WallStateStore.isVpnActive());
            out.putBoolean(NulKastIpcContract.KEY_LOCKDOWN_ACTIVE, WallStateStore.isStealthLockdown(ctx));
            out.putInt(NulKastIpcContract.KEY_BLOCKED_TRACKERS_COUNT, WallStateStore.getBlockedTrackersCount(ctx));
            out.putInt(NulKastIpcContract.KEY_ALLOWED_QUERIES_COUNT, WallStateStore.getAllowedQueriesCount(ctx));
            out.putInt(NulKastIpcContract.KEY_BLOCKED_APPS_COUNT, WallStateStore.getBlockedPackages(ctx).size());
            out.putStringArrayList("blocked_packages_list", new ArrayList<String>(WallStateStore.getBlockedPackages(ctx)));
            out.putStringArrayList(NulKastIpcContract.KEY_RECENT_EVENTS, WallStateStore.getRecentEvents());
        } else if (NulKastIpcContract.METHOD_SET_STEALTH_LOCKDOWN.equals(method)) {
            boolean enable = extras != null ? extras.getBoolean(NulKastIpcContract.KEY_ENABLE_LOCKDOWN, false) : "true".equalsIgnoreCase(arg);
            WallStateStore.setStealthLockdown(ctx, enable);
            NulKastVpnService.reloadIfRunning(ctx);
            out.putBoolean(NulKastIpcContract.KEY_LOCKDOWN_ACTIVE, enable);
        } else if (NulKastIpcContract.METHOD_TOGGLE_APP_BLOCK.equals(method)) {
            String pkg = (extras != null && extras.containsKey(NulKastIpcContract.KEY_TARGET_PACKAGE))
                ? extras.getString(NulKastIpcContract.KEY_TARGET_PACKAGE, "")
                : (arg != null ? arg : "");
            boolean block = (extras != null && extras.containsKey(NulKastIpcContract.KEY_BLOCK_STATE))
                ? extras.getBoolean(NulKastIpcContract.KEY_BLOCK_STATE, true)
                : true;
            if (pkg != null && pkg.startsWith("allow:")) {
                pkg = pkg.substring(6);
                block = false;
            } else if (pkg != null && pkg.startsWith("block:")) {
                pkg = pkg.substring(6);
                block = true;
            }
            if (pkg != null && !pkg.isEmpty()) {
                WallStateStore.setPackageBlocked(ctx, pkg, block);
                NulKastVpnService.reloadIfRunning(ctx);
                out.putString("package", pkg);
                out.putBoolean("is_blocked", WallStateStore.isPackageBlocked(ctx, pkg));
                out.putBoolean("protected_core", AppIntelligence.isProtectedCore(pkg));
            }
        } else if (NulKastIpcContract.METHOD_APPLY_RECOMMENDED_SHIELD.equals(method)) {
            List<ApplicationInfo> installed = ctx.getPackageManager().getInstalledApplications(0);
            List<String> names = new ArrayList<String>();
            for (ApplicationInfo ai : installed) names.add(ai.packageName);
            Set<String> rec = AppIntelligence.getRecommendedBlockPackageSet(names);
            WallStateStore.applyPresetOrRulepack(
                ctx,
                "Recommended Safe Shield",
                rec,
                WallStateStore.getCustomBlockedDomains(ctx),
                true
            );
            NulKastVpnService.reloadIfRunning(ctx);
            out.putInt("blocked_apps_count", WallStateStore.getBlockedPackages(ctx).size());
            out.putStringArrayList("blocked_packages_list", new ArrayList<String>(WallStateStore.getBlockedPackages(ctx)));
        } else if (NulKastIpcContract.METHOD_RESET_NORMAL_STATE.equals(method)) {
            WallStateStore.resetToNormalState(ctx);
            NulKastVpnService.reloadIfRunning(ctx);
            out.putInt("blocked_apps_count", 0);
        }
        return out;
    }

    @Override public Cursor query(Uri uri, String[] proj, String sel, String[] selArgs, String sort) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String sel, String[] selArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String sel, String[] selArgs) { return 0; }
}
