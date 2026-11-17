package io.nulkast.home;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.nulkast.ipcbus.NulKastHudClient;
import io.nulkast.ui.NulKastTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * NulKast Home (`io.nulkast.home`): Zero-Internet-Permission Custom OS Launcher.
 * Features:
 * - Live Signature-IPC Privacy HUD connected to NulKast Wall (`io.nulkast.wall`)
 * - S-Pen Stylus Barrel-Button quick launcher (`SM-N976V`)
 * - Automatic Foldable Posture Adaptation (4-col on Note 10+ 5G vs 6-col Dual-Pane on Pixel Fold `felix`)
 */
public final class HomeMainActivity extends Activity {
    private NulKastHudClient hudClient;
    private ContentObserver hudObserver;
    private LauncherApps launcherApps;
    private UserManager userManager;
    private LauncherApps.Callback pkgCallback;

    private TextView hudStatusTv;
    private TextView hudCountersTv;
    private Button lockdownBtn;
    private GridLayout appGrid;
    private String searchFilter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NulKastTheme.applyWindowChrome(this);
        hudClient = new NulKastHudClient(this);
        launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        userManager = (UserManager) getSystemService(USER_SERVICE);

        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerCallbacks();
        refreshHud();
        populateAppGrid();
    }

    @Override
    protected void onPause() {
        unregisterCallbacks();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        populateAppGrid();
    }

    @Override
    protected void onDestroy() {
        unregisterCallbacks();
        super.onDestroy();
    }

    private void unregisterCallbacks() {
        if (hudObserver != null) {
            try { getContentResolver().unregisterContentObserver(hudObserver); } catch (Throwable ignored) {}
            hudObserver = null;
        }
        if (launcherApps != null && pkgCallback != null) {
            try { launcherApps.unregisterCallback(pkgCallback); } catch (Throwable ignored) {}
            pkgCallback = null;
        }
    }

    private void registerCallbacks() {
        if (hudObserver == null) {
            hudObserver = hudClient.registerObserver(new Runnable() {
                @Override
                public void run() {
                    refreshHud();
                }
            });
        }
        if (launcherApps != null && pkgCallback == null) {
            pkgCallback = new LauncherApps.Callback() {
                @Override public void onPackageRemoved(String pkg, UserHandle user) { populateAppGrid(); }
                @Override public void onPackageAdded(String pkg, UserHandle user) { populateAppGrid(); }
                @Override public void onPackageChanged(String pkg, UserHandle user) { populateAppGrid(); }
                @Override public void onPackagesAvailable(String[] pkgs, UserHandle u, boolean r) { populateAppGrid(); }
                @Override public void onPackagesUnavailable(String[] pkgs, UserHandle u, boolean r) { populateAppGrid(); }
            };
            launcherApps.registerCallback(pkgCallback, new Handler(Looper.getMainLooper()));
        }
    }

    private void buildUi() {
        // 3-Zone Pinned Shell: Pinned Top HUD + Search, Scrollable App Grid
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(NulKastTheme.BG_PRIMARY);
        int pad = NulKastTheme.dp(this, 14);
        shell.setPadding(pad, pad, pad, 0);

        // 1. Live NulKast Wall Privacy HUD Card (Pinned Top)
        LinearLayout hudCard = new LinearLayout(this);
        hudCard.setOrientation(LinearLayout.VERTICAL);
        hudCard.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_SECONDARY, NulKastTheme.BORDER_COLOR, 14f));
        hudCard.setPadding(pad, NulKastTheme.dp(this, 12), pad, NulKastTheme.dp(this, 12));

        hudStatusTv = new TextView(this);
        hudStatusTv.setTextColor(NulKastTheme.TEXT_PRIMARY);
        hudStatusTv.setTextSize(15.5f);
        hudStatusTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hudCard.addView(hudStatusTv);

        hudCountersTv = new TextView(this);
        hudCountersTv.setTextColor(NulKastTheme.TEXT_MUTED);
        hudCountersTv.setTypeface(Typeface.MONOSPACE);
        hudCountersTv.setTextSize(11f);
        hudCountersTv.setPadding(0, NulKastTheme.dp(this, 4), 0, NulKastTheme.dp(this, 10));
        hudCard.addView(hudCountersTv);

        LinearLayout hudBtnRow = new LinearLayout(this);
        hudBtnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button openWallBtn = new Button(this);
        openWallBtn.setText("🛡️ Wall");
        openWallBtn.setAllCaps(false);
        openWallBtn.setTextSize(12f);
        openWallBtn.setTypeface(Typeface.DEFAULT_BOLD);
        openWallBtn.setTextColor(NulKastTheme.ACCENT_CYAN);
        openWallBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_CYAN_DIM, NulKastTheme.ACCENT_CYAN, 10f));
        openWallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNulKastWall();
            }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = NulKastTheme.dp(this, 5);
        hudBtnRow.addView(openWallBtn, lp1);

        Button openAuditBtn = new Button(this);
        openAuditBtn.setText("🕵️ Audit");
        openAuditBtn.setAllCaps(false);
        openAuditBtn.setTextSize(12f);
        openAuditBtn.setTypeface(Typeface.DEFAULT_BOLD);
        openAuditBtn.setTextColor(NulKastTheme.ACCENT_GREEN);
        openAuditBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_GREEN_DIM, NulKastTheme.ACCENT_GREEN, 10f));
        openAuditBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNulKastApp("io.nulkast.audit", "io.nulkast.audit.AuditMainActivity");
            }
        });
        LinearLayout.LayoutParams lpAudit = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpAudit.leftMargin = NulKastTheme.dp(this, 4);
        lpAudit.rightMargin = NulKastTheme.dp(this, 4);
        hudBtnRow.addView(openAuditBtn, lpAudit);

        Button openScrubBtn = new Button(this);
        openScrubBtn.setText("🧹 Scrub");
        openScrubBtn.setAllCaps(false);
        openScrubBtn.setTextSize(12f);
        openScrubBtn.setTypeface(Typeface.DEFAULT_BOLD);
        openScrubBtn.setTextColor(NulKastTheme.ACCENT_AMBER);
        openScrubBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_AMBER_DIM, NulKastTheme.ACCENT_AMBER, 10f));
        openScrubBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNulKastApp("io.nulkast.scrub", "io.nulkast.scrub.ScrubMainActivity");
            }
        });
        LinearLayout.LayoutParams lpScrub = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpScrub.leftMargin = NulKastTheme.dp(this, 4);
        lpScrub.rightMargin = NulKastTheme.dp(this, 4);
        hudBtnRow.addView(openScrubBtn, lpScrub);

        lockdownBtn = new Button(this);
        lockdownBtn.setAllCaps(false);
        lockdownBtn.setTextSize(12f);
        lockdownBtn.setTypeface(Typeface.DEFAULT_BOLD);
        lockdownBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NulKastHudClient.Snapshot snap = hudClient.fetchSnapshot();
                hudClient.setStealthLockdown(!snap.stealthLockdown);
                refreshHud();
            }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = NulKastTheme.dp(this, 5);
        hudBtnRow.addView(lockdownBtn, lp2);

        hudCard.addView(hudBtnRow);
        shell.addView(hudCard);

        // 2. Pinned Instant App Search Bar
        EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("🔍 Search apps (Zero-Internet NulKast Home)...");
        searchInput.setHintTextColor(NulKastTheme.TEXT_MUTED);
        searchInput.setTextColor(NulKastTheme.TEXT_PRIMARY);
        searchInput.setTextSize(13f);
        int sp = NulKastTheme.dp(this, 10);
        searchInput.setPadding(sp, sp, sp, sp);
        searchInput.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 10f));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.topMargin = NulKastTheme.dp(this, 10);
        searchLp.bottomMargin = NulKastTheme.dp(this, 8);
        shell.addView(searchInput, searchLp);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchFilter = s != null ? s.toString() : "";
                populateAppGrid();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 3. Scrollable Adaptive App Grid (weight = 1f)
        ScrollView gridScroll = new ScrollView(this);
        gridScroll.setFillViewport(true);
        appGrid = new GridLayout(this);
        gridScroll.addView(appGrid, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        shell.addView(gridScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        setContentView(shell);
        refreshHud();
        populateAppGrid();
    }

    private void refreshHud() {
        NulKastHudClient.Snapshot snap = hudClient.fetchSnapshot();
        String postureTag = NulKastTheme.isUnfoldedDualPane(this) ? "Foldable Dual-Pane" : "Compact OLED";
        hudStatusTv.setText(
            snap.vpnActive
                ? "🟢 NULKAST OS // WALL ACTIVE (" + postureTag + ")"
                : "🟠 NULKAST OS // WALL STANDBY (" + postureTag + ")"
        );
        hudCountersTv.setText(
            "Trackers Sinkholed: " + snap.blockedTrackersCount
                + "  |  Allowed DNS: " + snap.allowedQueriesCount
                + "  |  Blocked Apps: " + snap.blockedAppsCount
        );
        lockdownBtn.setText(snap.stealthLockdown ? "🔒 Lockdown: ON" : "🔓 Lockdown: OFF");
        lockdownBtn.setBackground(NulKastTheme.cardDrawable(
            this,
            snap.stealthLockdown ? NulKastTheme.ACCENT_RED : NulKastTheme.BG_SECONDARY,
            snap.stealthLockdown ? NulKastTheme.ACCENT_RED : NulKastTheme.BORDER_COLOR,
            8f
        ));
    }

    private void populateAppGrid() {
        if (appGrid == null || launcherApps == null || userManager == null) return;
        appGrid.removeAllViews();
        int cols = NulKastTheme.recommendedGridColumns(this);
        appGrid.setColumnCount(cols);

        List<LauncherActivityInfo> all = new ArrayList<LauncherActivityInfo>();
        for (UserHandle profile : userManager.getUserProfiles()) {
            List<LauncherActivityInfo> list = launcherApps.getActivityList(null, profile);
            if (list != null) {
                for (LauncherActivityInfo info : list) {
                    if (info.getComponentName().getPackageName().equals(getPackageName())) continue;
                    String label = String.valueOf(info.getLabel());
                    if (!searchFilter.trim().isEmpty()
                        && !label.toLowerCase(Locale.US).contains(searchFilter.trim().toLowerCase(Locale.US))) {
                        continue;
                    }
                    all.add(info);
                }
            }
        }

        Collections.sort(all, new Comparator<LauncherActivityInfo>() {
            @Override
            public int compare(LauncherActivityInfo a, LauncherActivityInfo b) {
                return String.valueOf(a.getLabel()).compareToIgnoreCase(String.valueOf(b.getLabel()));
            }
        });

        int screenWidth = getResources().getDisplayMetrics().widthPixels - NulKastTheme.dp(this, 36);
        int cellWidth = Math.max(NulKastTheme.dp(this, 72), screenWidth / cols);

        for (final LauncherActivityInfo info : all) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = NulKastTheme.dp(this, 8);
            cell.setPadding(p, p, p, p);

            ImageView iconIv = new ImageView(this);
            iconIv.setImageDrawable(info.getBadgedIcon(DisplayMetrics.DENSITY_XXHIGH));
            int iconSize = NulKastTheme.dp(this, 48);
            cell.addView(iconIv, new LinearLayout.LayoutParams(iconSize, iconSize));

            TextView lblTv = new TextView(this);
            lblTv.setText(info.getLabel());
            lblTv.setTextColor(NulKastTheme.TEXT_PRIMARY);
            lblTv.setTextSize(11.5f);
            lblTv.setGravity(Gravity.CENTER);
            lblTv.setMaxLines(2);
            cell.addView(lblTv);

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Rect bounds = new Rect();
                    v.getGlobalVisibleRect(bounds);
                    launcherApps.startMainActivity(info.getComponentName(), info.getUser(), bounds, null);
                }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Rect bounds = new Rect();
                    v.getGlobalVisibleRect(bounds);
                    launcherApps.startAppDetailsActivity(info.getComponentName(), info.getUser(), bounds, null);
                    return true;
                }
            });

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cellWidth;
            appGrid.addView(cell, glp);
        }
    }

    private void openNulKastWall() {
        openNulKastApp("io.nulkast.wall", "io.nulkast.wall.WallMainActivity");
    }

    private void openNulKastApp(String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(pkg, cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {}
    }
}
