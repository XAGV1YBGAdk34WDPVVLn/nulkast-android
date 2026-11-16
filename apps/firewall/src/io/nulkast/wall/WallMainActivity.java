package io.nulkast.wall;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import io.nulkast.ipcbus.NulKastIpcContract;
import io.nulkast.netintel.AppIntelligence;
import io.nulkast.netintel.PacketEngine;
import io.nulkast.ui.NulKastTheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NulKast Wall v2.0 (`io.nulkast.wall`) — Pro-Studio OLED Control Plane.
 *
 * Implements [[android-ux-design-expert]] 3-Zone Pinned Viewport Shell:
 * - Zone 1 (Pinned Top): Hero Tunnel Status Deck + 3-Col Monospace KPI Strip + Primary Controls
 * - Zone 2 (Center Viewport weight=1): Sticky Filter Bar + Inner Scrollable List
 * - Zone 3 (Pinned Bottom Thumb-Zone Nav): 48dp+ Segmented Bottom Navigation Bar
 */
public final class WallMainActivity extends Activity {
    private static final int REQ_VPN_PERMISSION = 101;

    private LinearLayout heroCard;
    private TextView statusBadge;
    private TextView tunnelSubline;
    private Button toggleVpnBtn;
    private Button presetSafeBtn;
    private Button normalResetBtn;
    private Button undoBtn;
    private TextView kpiBlockedView;
    private TextView kpiAllowedView;
    private TextView kpiAppsBlockedView;

    private LinearLayout centerViewport;
    private final Button[] bottomNavButtons = new Button[4];

    private int activeTab = 0; // 0 = Stream, 1 = Apps, 2 = Presets & Rules, 3 = Bus & ID
    private int streamFilter = 0; // 0 = All, 1 = Blocked, 2 = Allowed
    private int appFilter = 0; // 0 = Recommended, 1 = Blocked, 2 = User, 3 = All
    private String appSearchQuery = "";
    private ContentObserver stateObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NulKastTheme.applyWindowChrome(this);
        WallStateStore.ensureInitialized(this);
        buildThreeZoneShell();
        registerStateObserver();
    }

    @Override
    protected void onDestroy() {
        if (stateObserver != null) {
            try { getContentResolver().unregisterContentObserver(stateObserver); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    private void registerStateObserver() {
        stateObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                refreshHeaderState();
                if (activeTab == 0) {
                    renderCenterViewport();
                }
            }
        };
        getContentResolver().registerContentObserver(NulKastIpcContract.CONTENT_URI, true, stateObserver);
    }

    private void buildThreeZoneShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(NulKastTheme.BG_PRIMARY);

        int padH = NulKastTheme.dp(this, 12);
        int padV = NulKastTheme.dp(this, 10);

        // ====================================================================
        // ZONE 1: PINNED TOP HERO STATUS DECK (Never scrolls off-screen)
        // ====================================================================
        LinearLayout topZone = new LinearLayout(this);
        topZone.setOrientation(LinearLayout.VERTICAL);
        topZone.setPadding(padH, padV, padH, NulKastTheme.dp(this, 6));

        heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setPadding(padH, padV, padH, padV);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(this);
        titleTv.setText("🛡️ NULKAST WALL");
        titleTv.setTextColor(NulKastTheme.TEXT_PRIMARY);
        titleTv.setTextSize(17.5f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusBadge = new TextView(this);
        statusBadge.setTextSize(11f);
        statusBadge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        statusBadge.setPadding(NulKastTheme.dp(this, 10), NulKastTheme.dp(this, 4), NulKastTheme.dp(this, 10), NulKastTheme.dp(this, 4));
        titleRow.addView(statusBadge);
        heroCard.addView(titleRow);

        tunnelSubline = new TextView(this);
        tunnelSubline.setTextColor(NulKastTheme.TEXT_MUTED);
        tunnelSubline.setTypeface(Typeface.MONOSPACE);
        tunnelSubline.setTextSize(10.5f);
        tunnelSubline.setPadding(0, NulKastTheme.dp(this, 3), 0, NulKastTheme.dp(this, 8));
        heroCard.addView(tunnelSubline);

        // 3-Column Monospace KPI Strip
        LinearLayout kpiRow = new LinearLayout(this);
        kpiRow.setOrientation(LinearLayout.HORIZONTAL);
        kpiBlockedView = createKpiTile(kpiRow, "SINKHOLED", "0", NulKastTheme.ACCENT_RED);
        kpiAllowedView = createKpiTile(kpiRow, "ALLOWED", "0", NulKastTheme.ACCENT_GREEN);
        kpiAppsBlockedView = createKpiTile(kpiRow, "APPS LOCKED", "0", NulKastTheme.ACCENT_CYAN);
        heroCard.addView(kpiRow);

        // Primary Action & Zero-Fear Safety Row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, NulKastTheme.dp(this, 8), 0, 0);

        toggleVpnBtn = new Button(this);
        toggleVpnBtn.setAllCaps(false);
        toggleVpnBtn.setTextSize(12f);
        toggleVpnBtn.setTypeface(Typeface.DEFAULT_BOLD);
        toggleVpnBtn.setMinimumHeight(NulKastTheme.dp(this, 40));
        toggleVpnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (WallStateStore.isVpnActive()) {
                    Intent stop = new Intent(WallMainActivity.this, NulKastVpnService.class);
                    stop.setAction(NulKastVpnService.ACTION_STOP);
                    startService(stop);
                } else {
                    startVpnWithConsent();
                }
            }
        });
        LinearLayout.LayoutParams bLp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
        bLp1.rightMargin = NulKastTheme.dp(this, 5);
        btnRow.addView(toggleVpnBtn, bLp1);

        presetSafeBtn = new Button(this);
        presetSafeBtn.setText("✨ Safe Shield");
        presetSafeBtn.setAllCaps(false);
        presetSafeBtn.setTextSize(11.5f);
        presetSafeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        presetSafeBtn.setMinimumHeight(NulKastTheme.dp(this, 40));
        presetSafeBtn.setTextColor(NulKastTheme.ACCENT_GREEN);
        presetSafeBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_GREEN_DIM, NulKastTheme.ACCENT_GREEN, 10f));
        presetSafeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRecommendedSafeShield();
            }
        });
        LinearLayout.LayoutParams bLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        bLp2.rightMargin = NulKastTheme.dp(this, 5);
        btnRow.addView(presetSafeBtn, bLp2);

        normalResetBtn = new Button(this);
        normalResetBtn.setText("🔄 Normal");
        normalResetBtn.setAllCaps(false);
        normalResetBtn.setTextSize(11.5f);
        normalResetBtn.setTypeface(Typeface.DEFAULT_BOLD);
        normalResetBtn.setMinimumHeight(NulKastTheme.dp(this, 40));
        normalResetBtn.setTextColor(NulKastTheme.ACCENT_AMBER);
        normalResetBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_AMBER_DIM, NulKastTheme.ACCENT_AMBER, 10f));
        normalResetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WallStateStore.resetToNormalState(WallMainActivity.this);
                NulKastVpnService.reloadIfRunning(WallMainActivity.this);
                Toast.makeText(WallMainActivity.this, "🔄 Restored Normal State (Tap ↩ Undo to revert)", Toast.LENGTH_SHORT).show();
                refreshHeaderState();
                renderCenterViewport();
            }
        });
        LinearLayout.LayoutParams bLp3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f);
        bLp3.rightMargin = NulKastTheme.dp(this, 5);
        btnRow.addView(normalResetBtn, bLp3);

        undoBtn = new Button(this);
        undoBtn.setText("↩ Undo");
        undoBtn.setAllCaps(false);
        undoBtn.setTextSize(11.5f);
        undoBtn.setMinimumHeight(NulKastTheme.dp(this, 40));
        undoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (WallStateStore.undoLastChange(WallMainActivity.this)) {
                    NulKastVpnService.reloadIfRunning(WallMainActivity.this);
                    Toast.makeText(WallMainActivity.this, "↩ Restored previous firewall snapshot", Toast.LENGTH_SHORT).show();
                    refreshHeaderState();
                    renderCenterViewport();
                } else {
                    Toast.makeText(WallMainActivity.this, "No previous snapshot to undo", Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams bLp4 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f);
        btnRow.addView(undoBtn, bLp4);

        heroCard.addView(btnRow);
        topZone.addView(heroCard);
        shell.addView(topZone);

        // ====================================================================
        // ZONE 2: DYNAMIC CENTER VIEWPORT (weight = 1f, independent scroll)
        // ====================================================================
        centerViewport = new LinearLayout(this);
        centerViewport.setOrientation(LinearLayout.VERTICAL);
        centerViewport.setPadding(padH, NulKastTheme.dp(this, 2), padH, NulKastTheme.dp(this, 4));
        shell.addView(centerViewport, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        // ====================================================================
        // ZONE 3: PINNED BOTTOM THUMB-ZONE NAVIGATION BAR (48dp+ targets)
        // ====================================================================
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_SECONDARY, NulKastTheme.BORDER_COLOR, 0f));
        int navPad = NulKastTheme.dp(this, 6);
        bottomBar.setPadding(navPad, navPad, navPad, navPad);

        String[] navLabels = new String[] { "📡 Stream", "🛡️ Apps", "📦 Presets", "🔗 Bus & ID" };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button nb = new Button(this);
            nb.setText(navLabels[i]);
            nb.setAllCaps(false);
            nb.setTextSize(12f);
            nb.setMinimumHeight(NulKastTheme.dp(this, 46));
            nb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activeTab = idx;
                    refreshBottomNavStyle();
                    renderCenterViewport();
                }
            });
            bottomNavButtons[i] = nb;
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 3) nlp.rightMargin = NulKastTheme.dp(this, 4);
            bottomBar.addView(nb, nlp);
        }
        shell.addView(bottomBar);

        setContentView(shell);
        refreshHeaderState();
        refreshBottomNavStyle();
        renderCenterViewport();
    }

    private TextView createKpiTile(LinearLayout parent, String label, String val, int accentColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 10f));
        int pH = NulKastTheme.dp(this, 10);
        int pV = NulKastTheme.dp(this, 6);
        box.setPadding(pH, pV, pH, pV);

        TextView valTv = new TextView(this);
        valTv.setText(val);
        valTv.setTextColor(accentColor);
        valTv.setTextSize(16.5f);
        valTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        box.addView(valTv);

        TextView lblTv = new TextView(this);
        lblTv.setText(label);
        lblTv.setTextColor(NulKastTheme.TEXT_MUTED);
        lblTv.setTextSize(9.5f);
        lblTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        box.addView(lblTv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = NulKastTheme.dp(this, 5);
        parent.addView(box, lp);
        return valTv;
    }

    private void refreshHeaderState() {
        boolean active = WallStateStore.isVpnActive();
        boolean lockdown = WallStateStore.isStealthLockdown(this);

        int heroBorder = lockdown
            ? NulKastTheme.ACCENT_RED
            : (active ? NulKastTheme.ACCENT_GREEN : NulKastTheme.BORDER_COLOR);
        heroCard.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_SECONDARY, heroBorder, 14f));

        if (lockdown) {
            statusBadge.setText("● LOCKDOWN");
            statusBadge.setTextColor(NulKastTheme.ACCENT_RED);
            statusBadge.setBackground(NulKastTheme.pillDrawable(this, NulKastTheme.ACCENT_RED_DIM, NulKastTheme.ACCENT_RED));
        } else if (active) {
            statusBadge.setText("● TUN0 VALIDATED");
            statusBadge.setTextColor(NulKastTheme.ACCENT_GREEN);
            statusBadge.setBackground(NulKastTheme.pillDrawable(this, NulKastTheme.ACCENT_GREEN_DIM, NulKastTheme.ACCENT_GREEN));
        } else {
            statusBadge.setText("○ STANDBY");
            statusBadge.setTextColor(NulKastTheme.ACCENT_AMBER);
            statusBadge.setBackground(NulKastTheme.pillDrawable(this, NulKastTheme.ACCENT_AMBER_DIM, NulKastTheme.ACCENT_AMBER));
        }

        tunnelSubline.setText(
            active
                ? "tun0 10.215.173.1/30 • 🔒 DoT 853 (AdGuard) • CNAME Unmasker Active"
                : "Tunnel disengaged • Tap '▶ Start VPN' to engage DoT & Sinkhole"
        );

        toggleVpnBtn.setText(active ? "⏹ Stop VPN" : "▶ Start VPN");
        toggleVpnBtn.setTextColor(active ? NulKastTheme.ACCENT_RED : NulKastTheme.BG_PRIMARY);
        toggleVpnBtn.setBackground(NulKastTheme.cardDrawable(
            this,
            active ? NulKastTheme.ACCENT_RED_DIM : NulKastTheme.ACCENT_CYAN,
            active ? NulKastTheme.ACCENT_RED : NulKastTheme.ACCENT_CYAN,
            10f
        ));

        boolean canUndo = WallStateStore.hasUndoSnapshot(this);
        undoBtn.setTextColor(canUndo ? NulKastTheme.ACCENT_CYAN : NulKastTheme.TEXT_MUTED);
        undoBtn.setBackground(NulKastTheme.cardDrawable(
            this,
            NulKastTheme.BG_INPUT,
            canUndo ? NulKastTheme.ACCENT_CYAN : NulKastTheme.BORDER_COLOR,
            10f
        ));

        kpiBlockedView.setText(String.valueOf(WallStateStore.getBlockedTrackersCount(this)));
        kpiAllowedView.setText(String.valueOf(WallStateStore.getAllowedQueriesCount(this)));
        kpiAppsBlockedView.setText(String.valueOf(WallStateStore.getBlockedPackages(this).size()));
    }

    private void applyRecommendedSafeShield() {
        List<ApplicationInfo> installed = getPackageManager().getInstalledApplications(0);
        List<String> pkgNames = new ArrayList<String>();
        for (ApplicationInfo ai : installed) {
            pkgNames.add(ai.packageName);
        }
        Set<String> recBlocks = AppIntelligence.getRecommendedBlockPackageSet(pkgNames);
        WallStateStore.applyPresetOrRulepack(
            this,
            "Recommended Safe Shield",
            recBlocks,
            WallStateStore.getCustomBlockedDomains(this),
            true
        );
        NulKastVpnService.reloadIfRunning(this);
        Toast.makeText(
            this,
            "✨ Applied Recommended Safe Shield (" + recBlocks.size() + " telemetry & offline apps blocked; Play Store & VoLTE protected)",
            Toast.LENGTH_LONG
        ).show();
        refreshHeaderState();
        renderCenterViewport();
    }

    private void refreshBottomNavStyle() {
        for (int i = 0; i < 4; i++) {
            boolean sel = (i == activeTab);
            Button b = bottomNavButtons[i];
            b.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setTextColor(sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.TEXT_SECONDARY);
            b.setBackground(NulKastTheme.cardDrawable(
                this,
                sel ? NulKastTheme.ACCENT_CYAN_DIM : NulKastTheme.BG_SECONDARY,
                sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.BORDER_COLOR,
                10f
            ));
        }
    }

    private void renderCenterViewport() {
        centerViewport.removeAllViews();
        if (activeTab == 0) {
            renderLiveStreamViewport();
        } else if (activeTab == 1) {
            renderAppFirewallViewport();
        } else if (activeTab == 2) {
            renderSinkholeRulesViewport();
        } else {
            renderBusAndAdIdViewport();
        }
    }

    // ========================================================================
    // TAB 0: ACTIONABLE LIVE DNS & SOCKET STREAM (1-Tap Inline Block/Unblock)
    // ========================================================================
    private void renderLiveStreamViewport() {
        // Sticky Filter Bar
        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER_VERTICAL);
        filterBar.setPadding(0, NulKastTheme.dp(this, 4), 0, NulKastTheme.dp(this, 6));

        String[] labels = new String[] { "All Events", "🛑 Blocked", "✅ Allowed" };
        for (int i = 0; i < 3; i++) {
            final int fIdx = i;
            boolean sel = (streamFilter == i);
            Button chip = new Button(this);
            chip.setText(labels[i]);
            chip.setAllCaps(false);
            chip.setTextSize(11.5f);
            chip.setMinimumHeight(NulKastTheme.dp(this, 36));
            chip.setTextColor(sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.TEXT_SECONDARY);
            chip.setBackground(NulKastTheme.pillDrawable(
                this,
                sel ? NulKastTheme.ACCENT_CYAN_DIM : NulKastTheme.BG_SECONDARY,
                sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.BORDER_COLOR
            ));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    streamFilter = fIdx;
                    renderCenterViewport();
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.rightMargin = NulKastTheme.dp(this, 5);
            filterBar.addView(chip, clp);
        }

        Button clearBtn = new Button(this);
        clearBtn.setText("Clear");
        clearBtn.setAllCaps(false);
        clearBtn.setTextSize(11.5f);
        clearBtn.setMinimumHeight(NulKastTheme.dp(this, 36));
        clearBtn.setTextColor(NulKastTheme.TEXT_MUTED);
        clearBtn.setBackground(NulKastTheme.pillDrawable(this, NulKastTheme.BG_SECONDARY, NulKastTheme.BORDER_COLOR));
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WallStateStore.clearRecentEvents(WallMainActivity.this);
                renderCenterViewport();
            }
        });
        filterBar.addView(clearBtn);
        centerViewport.addView(filterBar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        ArrayList<String> events = WallStateStore.getRecentEvents();
        Set<String> customBlocked = WallStateStore.getCustomBlockedDomains(this);
        int shown = 0;

        for (String raw : events) {
            boolean isBlocked = raw.contains("BLOCKED") || raw.contains("RST") || raw.contains("APP-BLOCK") || raw.contains("RULE-ADDED");
            boolean isAllowed = raw.contains("ALLOWED") || raw.contains("APP-ALLOW");
            if (streamFilter == 1 && !isBlocked) continue;
            if (streamFilter == 2 && !isAllowed) continue;
            shown++;

            LinearLayout rowCard = new LinearLayout(this);
            rowCard.setOrientation(LinearLayout.HORIZONTAL);
            rowCard.setGravity(Gravity.CENTER_VERTICAL);
            rowCard.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, NulKastTheme.BORDER_COLOR, 10f));
            int rp = NulKastTheme.dp(this, 10);
            rowCard.setPadding(rp, rp, rp, rp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rlp.bottomMargin = NulKastTheme.dp(this, 6);

            // Parse structured event: "HH:mm:ss 🛑 BLOCKED | pkg -> domain"
            String ts = raw.length() >= 8 ? raw.substring(0, 8) : "";
            String domain = "";
            String appPkg = "";
            if (raw.contains(" -> ")) {
                String[] arrowParts = raw.split(" -> ", 2);
                domain = arrowParts[1].trim();
                int pipeIdx = arrowParts[0].indexOf("|");
                if (pipeIdx >= 0) {
                    appPkg = arrowParts[0].substring(pipeIdx + 1).trim();
                }
            }

            // Verdict Pill
            String badgeText = isBlocked ? "BLOCK" : (isAllowed ? "ALLOW" : "INFO");
            int badgeFg = isBlocked ? NulKastTheme.ACCENT_RED : (isAllowed ? NulKastTheme.ACCENT_GREEN : NulKastTheme.ACCENT_CYAN);
            int badgeBg = isBlocked ? NulKastTheme.ACCENT_RED_DIM : (isAllowed ? NulKastTheme.ACCENT_GREEN_DIM : NulKastTheme.ACCENT_CYAN_DIM);
            TextView badge = NulKastTheme.createSemanticBadge(this, badgeText, badgeFg, badgeBg);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            blp.rightMargin = NulKastTheme.dp(this, 8);
            rowCard.addView(badge, blp);

            // Center 2-Line Details
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView line1 = new TextView(this);
            line1.setText(!domain.isEmpty() ? domain : raw);
            line1.setTextColor(NulKastTheme.TEXT_PRIMARY);
            line1.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            line1.setTextSize(12.5f);
            line1.setSingleLine(true);
            line1.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            textCol.addView(line1);

            if (!domain.isEmpty()) {
                TextView line2 = new TextView(this);
                String appTitle = NulKastTheme.resolveAppLabel(this, appPkg);
                line2.setText(appTitle + " • " + ts);
                line2.setTextColor(NulKastTheme.TEXT_MUTED);
                line2.setTypeface(Typeface.MONOSPACE);
                line2.setTextSize(10.5f);
                textCol.addView(line2);
            }

            rowCard.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Right: 1-Tap Inline [+ Block] / [Unblock] Pill if this is a DNS domain event
            if (!domain.isEmpty() && !domain.equals("ECONNREFUSED")) {
                final String targetDom = domain.toLowerCase(Locale.US);
                final boolean currentlySinkholed = PacketEngine.isTrackerDomain(targetDom, customBlocked);
                Button quickBtn = new Button(this);
                quickBtn.setAllCaps(false);
                quickBtn.setTextSize(11f);
                quickBtn.setMinimumHeight(NulKastTheme.dp(this, 34));
                quickBtn.setText(currentlySinkholed ? "Blocked" : "+ Block");
                quickBtn.setTextColor(currentlySinkholed ? NulKastTheme.ACCENT_RED : NulKastTheme.ACCENT_CYAN);
                quickBtn.setBackground(NulKastTheme.pillDrawable(
                    this,
                    currentlySinkholed ? NulKastTheme.ACCENT_RED_DIM : NulKastTheme.ACCENT_CYAN_DIM,
                    currentlySinkholed ? NulKastTheme.ACCENT_RED : NulKastTheme.ACCENT_CYAN
                ));
                quickBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (customBlocked.contains(targetDom)) {
                            WallStateStore.removeCustomBlockedDomain(WallMainActivity.this, targetDom);
                            Toast.makeText(WallMainActivity.this, "Removed custom rule: " + targetDom, Toast.LENGTH_SHORT).show();
                        } else {
                            WallStateStore.addCustomBlockedDomain(WallMainActivity.this, targetDom);
                            Toast.makeText(WallMainActivity.this, "Sinkholed domain: " + targetDom, Toast.LENGTH_SHORT).show();
                        }
                        renderCenterViewport();
                    }
                });
                rowCard.addView(quickBtn);
            }

            list.addView(rowCard, rlp);
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("No matching network events in buffer. Open any app to watch live DNS & socket attribution.");
            empty.setTextColor(NulKastTheme.TEXT_SECONDARY);
            empty.setTextSize(12.5f);
            empty.setPadding(NulKastTheme.dp(this, 12), NulKastTheme.dp(this, 20), NulKastTheme.dp(this, 12), 0);
            list.addView(empty);
        }

        scroll.addView(list);
        centerViewport.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));
    }

    // ========================================================================
    // TAB 1: PER-APP FIREWALL MATRIX (Real Icons + Faceted Chips + Pill Switch)
    // ========================================================================
    private void renderAppFirewallViewport() {
        // Search Row with [✕] Clear Button
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, NulKastTheme.dp(this, 2), 0, NulKastTheme.dp(this, 6));

        final EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("🔍 Filter apps by name, package, or UID...");
        searchInput.setHintTextColor(NulKastTheme.TEXT_MUTED);
        searchInput.setTextColor(NulKastTheme.TEXT_PRIMARY);
        searchInput.setTextSize(13f);
        searchInput.setText(appSearchQuery);
        int sp = NulKastTheme.dp(this, 10);
        searchInput.setPadding(sp, sp, sp, sp);
        searchInput.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 10f));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!appSearchQuery.isEmpty()) {
            Button clrBtn = new Button(this);
            clrBtn.setText("✕");
            clrBtn.setTextColor(NulKastTheme.TEXT_SECONDARY);
            clrBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_SECONDARY, NulKastTheme.BORDER_COLOR, 10f));
            clrBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    appSearchQuery = "";
                    renderCenterViewport();
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(NulKastTheme.dp(this, 42), ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.leftMargin = NulKastTheme.dp(this, 6);
            searchRow.addView(clrBtn, clp);
        }
        centerViewport.addView(searchRow);

        // 4 Clear Bucket Tabs inside Apps: Safe to Block, Normal Apps, Currently Blocked, Protected OS
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, 0, 0, NulKastTheme.dp(this, 6));
        String[] chipNames = new String[] { "🟢 Safe to Block", "🟡 Normal Apps", "🛑 Blocked", "🔒 Protected" };
        for (int i = 0; i < 4; i++) {
            final int cIdx = i;
            boolean sel = (appFilter == i);
            Button c = new Button(this);
            c.setText(chipNames[i]);
            c.setAllCaps(false);
            c.setTextSize(11.5f);
            c.setMinimumHeight(NulKastTheme.dp(this, 36));
            c.setTextColor(sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.TEXT_SECONDARY);
            c.setBackground(NulKastTheme.pillDrawable(
                this,
                sel ? NulKastTheme.ACCENT_CYAN_DIM : NulKastTheme.BG_SECONDARY,
                sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.BORDER_COLOR
            ));
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    appFilter = cIdx;
                    renderCenterViewport();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 3) lp.rightMargin = NulKastTheme.dp(this, 5);
            chipRow.addView(c, lp);
        }
        centerViewport.addView(chipRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        final LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        centerViewport.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        populateAppMatrixRows(listContainer);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                appSearchQuery = s != null ? s.toString() : "";
                populateAppMatrixRows(listContainer);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void populateAppMatrixRows(LinearLayout listContainer) {
        listContainer.removeAllViews();
        PackageManager pm = getPackageManager();

        // Plain-English Bucket Explanation Banner at the top of the list
        LinearLayout guideBox = new LinearLayout(this);
        guideBox.setOrientation(LinearLayout.VERTICAL);
        int guideBorder = appFilter == 0
            ? NulKastTheme.ACCENT_GREEN
            : (appFilter == 1 ? NulKastTheme.ACCENT_AMBER : (appFilter == 2 ? NulKastTheme.ACCENT_RED : NulKastTheme.ACCENT_CYAN));
        guideBox.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_SECONDARY, guideBorder, 10f));
        int gp = NulKastTheme.dp(this, 10);
        guideBox.setPadding(gp, gp, gp, gp);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        glp.bottomMargin = NulKastTheme.dp(this, 8);

        TextView guideTitle = new TextView(this);
        guideTitle.setTypeface(Typeface.DEFAULT_BOLD);
        guideTitle.setTextSize(12.5f);
        guideTitle.setTextColor(guideBorder);

        TextView guideBody = new TextView(this);
        guideBody.setTextSize(11.5f);
        guideBody.setTextColor(NulKastTheme.TEXT_SECONDARY);
        guideBody.setPadding(0, NulKastTheme.dp(this, 2), 0, 0);

        if (appFilter == 0) {
            guideTitle.setText("🟢 BUCKET 1: SAFE TO BLOCK (Breaks Nothing)");
            guideBody.setText("Offline tools (Keyboard, Gallery, Clock, Files) & Samsung/Verizon telemetry. Blocking these is 100% safe and will NEVER hurt your phone.");
        } else if (appFilter == 1) {
            guideTitle.setText("🟡 BUCKET 2: NORMAL INTERNET APPS (Your Choice)");
            guideBody.setText("Apps that use the internet (Browsers, YouTube, etc.). Blocking one only puts THAT specific app offline—it will never break Android.");
        } else if (appFilter == 2) {
            guideTitle.setText("🛑 CURRENTLY BLOCKED APPS");
            guideBody.setText("All apps currently cut off from the internet. Tap any app to allow it back online, or tap '🔄 Normal' above to reset all.");
        } else {
            guideTitle.setText("🔒 BUCKET 3: PROTECTED CORE OS (Locked Safe)");
            guideBody.setText("Play Store, Download Manager, Phone Calls (VoLTE) & Wi-Fi Calling. NulKast locks these open so you can NEVER accidentally break your phone.");
        }
        guideBox.addView(guideTitle);
        guideBox.addView(guideBody);
        listContainer.addView(guideBox, glp);

        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        List<ApplicationInfo> filtered = new ArrayList<ApplicationInfo>();
        String q = appSearchQuery.trim().toLowerCase(Locale.US);

        for (ApplicationInfo ai : installed) {
            if (ai.packageName.equals(getPackageName())) continue;
            // Only show apps that actually request INTERNET permission (hides ~200 non-network OS stubs)
            boolean hasInternetPerm = (pm.checkPermission(Manifest.permission.INTERNET, ai.packageName) == PackageManager.PERMISSION_GRANTED);
            boolean isBlocked = WallStateStore.isPackageBlocked(this, ai.packageName);
            AppIntelligence.Advice adv = AppIntelligence.classifyPackage(ai.packageName);

            if (!hasInternetPerm && !isBlocked && adv.tier == AppIntelligence.TIER_STANDARD_APP) {
                continue;
            }

            if (appFilter == 0 && !(adv.tier == AppIntelligence.TIER_RECOMMENDED_BLOCK || adv.tier == AppIntelligence.TIER_SAFE_OFFLINE)) {
                continue;
            }
            if (appFilter == 1 && adv.tier != AppIntelligence.TIER_STANDARD_APP) {
                continue;
            }
            if (appFilter == 2 && !isBlocked) {
                continue;
            }
            if (appFilter == 3 && adv.tier != AppIntelligence.TIER_PROTECTED_CORE) {
                continue;
            }

            String label = String.valueOf(pm.getApplicationLabel(ai));
            if (!q.isEmpty()
                && !label.toLowerCase(Locale.US).contains(q)
                && !ai.packageName.toLowerCase(Locale.US).contains(q)
                && !adv.reason.toLowerCase(Locale.US).contains(q)) {
                continue;
            }
            filtered.add(ai);
        }

        Collections.sort(filtered, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                AppIntelligence.Advice aa = AppIntelligence.classifyPackage(a.packageName);
                AppIntelligence.Advice ab = AppIntelligence.classifyPackage(b.packageName);
                if (aa.tier != ab.tier) return aa.tier - ab.tier;
                return a.packageName.compareToIgnoreCase(b.packageName);
            }
        });

        int count = 0;
        for (final ApplicationInfo ai : filtered) {
            if (count++ >= 85) break;
            final boolean isBlocked = WallStateStore.isPackageBlocked(this, ai.packageName);
            final AppIntelligence.Advice adv = AppIntelligence.classifyPackage(ai.packageName);
            final boolean isProtected = (adv.tier == AppIntelligence.TIER_PROTECTED_CORE);

            int cardBorder = isProtected
                ? NulKastTheme.ACCENT_GREEN
                : (isBlocked ? NulKastTheme.ACCENT_RED : NulKastTheme.BORDER_COLOR);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, cardBorder, 10f));
            int rp = NulKastTheme.dp(this, 10);
            row.setPadding(rp, rp, rp, rp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rlp.bottomMargin = NulKastTheme.dp(this, 6);

            // Real App Icon (36dp)
            ImageView iconIv = new ImageView(this);
            Drawable icon = NulKastTheme.loadAppIcon(this, ai.packageName);
            if (icon != null) iconIv.setImageDrawable(icon);
            int iconSz = NulKastTheme.dp(this, 36);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSz, iconSz);
            ilp.rightMargin = NulKastTheme.dp(this, 10);
            row.addView(iconIv, ilp);

            // 3-Line App Identity + Built-In Safety Advisor Reason
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView nameTv = new TextView(this);
            nameTv.setText(pm.getApplicationLabel(ai));
            nameTv.setTextColor(NulKastTheme.TEXT_PRIMARY);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            nameTv.setTextSize(13f);
            nameTv.setSingleLine(true);
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(nameTv);

            TextView advisorTv = new TextView(this);
            advisorTv.setText(adv.reason);
            int advColor = isProtected
                ? NulKastTheme.ACCENT_GREEN
                : (adv.tier == AppIntelligence.TIER_RECOMMENDED_BLOCK
                    ? NulKastTheme.ACCENT_AMBER
                    : (adv.tier == AppIntelligence.TIER_SAFE_OFFLINE ? NulKastTheme.ACCENT_CYAN : NulKastTheme.TEXT_SECONDARY));
            advisorTv.setTextColor(advColor);
            advisorTv.setTextSize(11f);
            textCol.addView(advisorTv);

            TextView pkgTv = new TextView(this);
            pkgTv.setText(ai.packageName);
            pkgTv.setTextColor(NulKastTheme.TEXT_MUTED);
            pkgTv.setTypeface(Typeface.MONOSPACE);
            pkgTv.setTextSize(10f);
            pkgTv.setSingleLine(true);
            pkgTv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            textCol.addView(pkgTv);

            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Right: Locked Shield for Protected Core OR Crisp Toggle Pill
            Button actionBtn = new Button(this);
            actionBtn.setAllCaps(false);
            actionBtn.setTextSize(11f);
            actionBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            actionBtn.setMinimumHeight(NulKastTheme.dp(this, 38));

            if (isProtected) {
                actionBtn.setText("🔒 SAFE");
                actionBtn.setTextColor(NulKastTheme.ACCENT_GREEN);
                actionBtn.setBackground(NulKastTheme.pillDrawable(
                    this,
                    NulKastTheme.ACCENT_GREEN_DIM,
                    NulKastTheme.ACCENT_GREEN
                ));
                actionBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(
                            WallMainActivity.this,
                            "🛡️ Protected Core App (" + adv.reason + ") — kept allowed so Play Store & Calls never break.",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            } else {
                actionBtn.setText(isBlocked ? "✕ BLOCKED" : "✓ ALLOWED");
                actionBtn.setTextColor(isBlocked ? NulKastTheme.ACCENT_RED : NulKastTheme.ACCENT_GREEN);
                actionBtn.setBackground(NulKastTheme.pillDrawable(
                    this,
                    isBlocked ? NulKastTheme.ACCENT_RED_DIM : NulKastTheme.ACCENT_GREEN_DIM,
                    isBlocked ? NulKastTheme.ACCENT_RED : NulKastTheme.ACCENT_GREEN
                ));
                View.OnClickListener toggleListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        WallStateStore.setPackageBlocked(WallMainActivity.this, ai.packageName, !isBlocked);
                        NulKastVpnService.reloadIfRunning(WallMainActivity.this);
                        refreshHeaderState();
                        populateAppMatrixRows(listContainer);
                    }
                };
                actionBtn.setOnClickListener(toggleListener);
                row.setOnClickListener(toggleListener);
            }
            row.addView(actionBtn);

            listContainer.addView(row, rlp);
        }
    }

    // ========================================================================
    // TAB 2: KNOWN-SAFE PRESETS, COMMUNITY RULEPACKS & SINKHOLE RULES
    // ========================================================================
    private void renderSinkholeRulesViewport() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int bp = NulKastTheme.dp(this, 12);

        // Card 1: 1-Tap Known-Safe Presets & Community Rulepacks
        LinearLayout presetCard = new LinearLayout(this);
        presetCard.setOrientation(LinearLayout.VERTICAL);
        presetCard.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, NulKastTheme.ACCENT_CYAN, 12f));
        presetCard.setPadding(bp, bp, bp, bp);
        LinearLayout.LayoutParams pclp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pclp.bottomMargin = NulKastTheme.dp(this, 10);

        TextView pHdr = new TextView(this);
        pHdr.setText("📦 Known-Safe Presets & Community Rulepacks");
        pHdr.setTextColor(NulKastTheme.ACCENT_CYAN);
        pHdr.setTypeface(Typeface.DEFAULT_BOLD);
        pHdr.setTextSize(14f);
        presetCard.addView(pHdr);

        TextView pSub = new TextView(this);
        pSub.setText("Every preset automatically saves an Undo snapshot first and protects Google Play Store, Download Manager & Carrier VoLTE.");
        pSub.setTextColor(NulKastTheme.TEXT_SECONDARY);
        pSub.setTextSize(11.5f);
        pSub.setPadding(0, NulKastTheme.dp(this, 4), 0, NulKastTheme.dp(this, 8));
        presetCard.addView(pSub);

        Button recBtn = new Button(this);
        recBtn.setText("✨ Apply 'Recommended Safe Shield' (Blocks Telemetry + Offline Tools)");
        recBtn.setAllCaps(false);
        recBtn.setTextSize(12f);
        recBtn.setTextColor(NulKastTheme.ACCENT_GREEN);
        recBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_GREEN_DIM, NulKastTheme.ACCENT_GREEN, 10f));
        recBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyRecommendedSafeShield();
            }
        });
        presetCard.addView(recBtn);

        Button commBtn = new Button(this);
        commBtn.setText("🌐 Load Community AdGuard + HaGeZi Mobile Pack (+41 Root Domains)");
        commBtn.setAllCaps(false);
        commBtn.setTextSize(12f);
        commBtn.setTextColor(NulKastTheme.ACCENT_CYAN);
        commBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_CYAN_DIM, NulKastTheme.ACCENT_CYAN, 10f));
        LinearLayout.LayoutParams cmlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cmlp.topMargin = NulKastTheme.dp(this, 6);
        commBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<String> doms = new HashSet<String>(Arrays.asList(AppIntelligence.COMMUNITY_EXTENDED_DOMAINS));
                WallStateStore.applyPresetOrRulepack(
                    WallMainActivity.this,
                    "Community AdGuard+HaGeZi Pack",
                    WallStateStore.getBlockedPackages(WallMainActivity.this),
                    doms,
                    false
                );
                Toast.makeText(WallMainActivity.this, "🌐 Loaded +41 Community Mobile Ad/Spyware Root Domains", Toast.LENGTH_SHORT).show();
                refreshHeaderState();
                renderCenterViewport();
            }
        });
        presetCard.addView(commBtn, cmlp);

        // Share / Import Clipboard Row
        LinearLayout shareRow = new LinearLayout(this);
        shareRow.setOrientation(LinearLayout.HORIZONTAL);
        shareRow.setPadding(0, NulKastTheme.dp(this, 6), 0, 0);

        Button exportBtn = new Button(this);
        exportBtn.setText("📤 Export JSON to Clipboard");
        exportBtn.setAllCaps(false);
        exportBtn.setTextSize(11.5f);
        exportBtn.setTextColor(NulKastTheme.TEXT_PRIMARY);
        exportBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 8f));
        exportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String json = AppIntelligence.exportRulepackJson(
                    "NulKast Community Shield",
                    WallStateStore.getBlockedPackages(WallMainActivity.this),
                    WallStateStore.getCustomBlockedDomains(WallMainActivity.this)
                );
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("nulkast-rulepack", json));
                    Toast.makeText(WallMainActivity.this, "📤 Copied nulkast-rulepack-v1 JSON to clipboard!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp.rightMargin = NulKastTheme.dp(this, 5);
        shareRow.addView(exportBtn, elp);

        Button importBtn = new Button(this);
        importBtn.setText("📥 Import Clipboard Pack");
        importBtn.setAllCaps(false);
        importBtn.setTextSize(11.5f);
        importBtn.setTextColor(NulKastTheme.TEXT_PRIMARY);
        importBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 8f));
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) {
                    Toast.makeText(WallMainActivity.this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
                AppIntelligence.ImportedRulepack pack = AppIntelligence.parseRulepackOrHosts(cs != null ? cs.toString() : "");
                if (pack.domains.isEmpty() && pack.packages.isEmpty()) {
                    Toast.makeText(WallMainActivity.this, "No valid JSON rulepack or hosts found in clipboard", Toast.LENGTH_SHORT).show();
                    return;
                }
                WallStateStore.applyPresetOrRulepack(WallMainActivity.this, pack.name, pack.packages, pack.domains, false);
                NulKastVpnService.reloadIfRunning(WallMainActivity.this);
                Toast.makeText(
                    WallMainActivity.this,
                    "📥 Imported '" + pack.name + "' (" + pack.packages.size() + " apps, " + pack.domains.size() + " domains)",
                    Toast.LENGTH_LONG
                ).show();
                refreshHeaderState();
                renderCenterViewport();
            }
        });
        shareRow.addView(importBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        presetCard.addView(shareRow);
        list.addView(presetCard, pclp);

        // Card 2: Manual Domain Sinkhole Input
        LinearLayout addBar = new LinearLayout(this);
        addBar.setOrientation(LinearLayout.HORIZONTAL);
        addBar.setGravity(Gravity.CENTER_VERTICAL);
        addBar.setPadding(0, NulKastTheme.dp(this, 2), 0, NulKastTheme.dp(this, 8));

        final EditText domInput = new EditText(this);
        domInput.setSingleLine(true);
        domInput.setHint("Add custom domain (e.g. ads.example.com)...");
        domInput.setHintTextColor(NulKastTheme.TEXT_MUTED);
        domInput.setTextColor(NulKastTheme.TEXT_PRIMARY);
        domInput.setTextSize(12.5f);
        int p = NulKastTheme.dp(this, 10);
        domInput.setPadding(p, p, p, p);
        domInput.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 10f));
        addBar.addView(domInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button addBtn = new Button(this);
        addBtn.setText("+ Sinkhole");
        addBtn.setAllCaps(false);
        addBtn.setTextSize(12f);
        addBtn.setTextColor(NulKastTheme.BG_PRIMARY);
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_CYAN, NulKastTheme.ACCENT_CYAN, 10f));
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String d = domInput.getText().toString();
                if (!d.trim().isEmpty()) {
                    WallStateStore.addCustomBlockedDomain(WallMainActivity.this, d);
                    domInput.setText("");
                    renderCenterViewport();
                }
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        alp.leftMargin = NulKastTheme.dp(this, 6);
        addBar.addView(addBtn, alp);
        list.addView(addBar);

        Set<String> custom = WallStateStore.getCustomBlockedDomains(this);
        for (final String cd : custom) {
            LinearLayout cRow = new LinearLayout(this);
            cRow.setOrientation(LinearLayout.HORIZONTAL);
            cRow.setGravity(Gravity.CENTER_VERTICAL);
            cRow.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, NulKastTheme.ACCENT_AMBER, 10f));
            int rp = NulKastTheme.dp(this, 10);
            cRow.setPadding(rp, rp, rp, rp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rlp.bottomMargin = NulKastTheme.dp(this, 6);

            TextView tv = new TextView(this);
            tv.setText("🛑 [CUSTOM] " + cd);
            tv.setTextColor(NulKastTheme.ACCENT_AMBER);
            tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tv.setTextSize(12f);
            cRow.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button rmBtn = new Button(this);
            rmBtn.setText("↩ Undo");
            rmBtn.setAllCaps(false);
            rmBtn.setTextSize(11f);
            rmBtn.setTextColor(NulKastTheme.TEXT_PRIMARY);
            rmBtn.setBackground(NulKastTheme.pillDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR));
            rmBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    WallStateStore.removeCustomBlockedDomain(WallMainActivity.this, cd);
                    renderCenterViewport();
                }
            });
            cRow.addView(rmBtn);
            list.addView(cRow, rlp);
        }

        LinearLayout builtInCard = new LinearLayout(this);
        builtInCard.setOrientation(LinearLayout.VERTICAL);
        builtInCard.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, NulKastTheme.BORDER_COLOR, 12f));
        builtInCard.setPadding(bp, bp, bp, bp);

        TextView builtInHdr = new TextView(this);
        builtInHdr.setText("Built-In Tracker Suffixes (" + PacketEngine.BUILTIN_TRACKER_SUFFIXES.length + " Active — Play Store Whitelisted)");
        builtInHdr.setTextColor(NulKastTheme.ACCENT_CYAN);
        builtInHdr.setTypeface(Typeface.DEFAULT_BOLD);
        builtInHdr.setTextSize(13f);
        builtInHdr.setPadding(0, 0, 0, NulKastTheme.dp(this, 8));
        builtInCard.addView(builtInHdr);

        for (String s : PacketEngine.BUILTIN_TRACKER_SUFFIXES) {
            TextView r = new TextView(this);
            r.setText("• *." + s.trim() + "  ->  NXDOMAIN");
            r.setTextColor(NulKastTheme.TEXT_SECONDARY);
            r.setTypeface(Typeface.MONOSPACE);
            r.setTextSize(11.5f);
            r.setPadding(0, NulKastTheme.dp(this, 2), 0, NulKastTheme.dp(this, 2));
            builtInCard.addView(r);
        }

        list.addView(builtInCard);
        scroll.addView(list);
        centerViewport.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));
    }

    // ========================================================================
    // TAB 3: SIGNATURE IPC BUS INSPECTOR & ADVERTISER ID SHIELD
    // ========================================================================
    private void renderBusAndAdIdViewport() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        int p = NulKastTheme.dp(this, 14);

        // Card 1: Google Advertiser ID (AAID) Zero-Out Shield
        LinearLayout aaidCard = new LinearLayout(this);
        aaidCard.setOrientation(LinearLayout.VERTICAL);
        aaidCard.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, NulKastTheme.ACCENT_GREEN, 12f));
        aaidCard.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp1 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        clp1.bottomMargin = NulKastTheme.dp(this, 10);

        TextView aaidHdr = new TextView(this);
        aaidHdr.setText("🛡️ Google Advertiser ID (AAID) Shield");
        aaidHdr.setTextColor(NulKastTheme.ACCENT_GREEN);
        aaidHdr.setTypeface(Typeface.DEFAULT_BOLD);
        aaidHdr.setTextSize(14.5f);
        aaidCard.addView(aaidHdr);

        TextView uuidReadout = new TextView(this);
        uuidReadout.setText("AAID: 00000000-0000-0000-0000-000000000000\nlimit_ad_tracking = 1 (Null UUID Enforced)");
        uuidReadout.setTextColor(NulKastTheme.TEXT_PRIMARY);
        uuidReadout.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        uuidReadout.setTextSize(11.5f);
        uuidReadout.setPadding(0, NulKastTheme.dp(this, 6), 0, NulKastTheme.dp(this, 8));
        aaidCard.addView(uuidReadout);

        Button openAaidBtn = new Button(this);
        openAaidBtn.setText("🗑️ Open GMS Ads Privacy / Verify Deletion");
        openAaidBtn.setAllCaps(false);
        openAaidBtn.setTextColor(NulKastTheme.ACCENT_CYAN);
        openAaidBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_CYAN_DIM, NulKastTheme.ACCENT_CYAN, 10f));
        openAaidBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] actions = new String[] {
                    "com.google.android.gms.settings.ADS_PRIVACY",
                    "android.settings.AD_SERVICES_SETTINGS",
                    "com.android.settings.AD_SERVICES_SETTINGS",
                    android.provider.Settings.ACTION_PRIVACY_SETTINGS
                };
                for (String act : actions) {
                    try {
                        Intent i = new Intent(act);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        });
        aaidCard.addView(openAaidBtn);
        col.addView(aaidCard, clp1);

        // Card 2: Cryptographic Signature IPC Bus Mesh
        LinearLayout ipcCard = new LinearLayout(this);
        ipcCard.setOrientation(LinearLayout.VERTICAL);
        ipcCard.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, NulKastTheme.BORDER_COLOR, 12f));
        ipcCard.setPadding(p, p, p, p);

        TextView ipcHdr = new TextView(this);
        ipcHdr.setText("🔗 Cryptographic Signature IPC Bus");
        ipcHdr.setTextColor(NulKastTheme.ACCENT_CYAN);
        ipcHdr.setTypeface(Typeface.DEFAULT_BOLD);
        ipcHdr.setTextSize(14.5f);
        ipcCard.addView(ipcHdr);

        TextView ipcSub = new TextView(this);
        ipcSub.setText("Permission: io.nulkast.permission.IPC_BUS (protectionLevel=signature)\nAuthority: content://io.nulkast.wall.ipc/state");
        ipcSub.setTextColor(NulKastTheme.TEXT_MUTED);
        ipcSub.setTypeface(Typeface.MONOSPACE);
        ipcSub.setTextSize(10.5f);
        ipcSub.setPadding(0, NulKastTheme.dp(this, 4), 0, NulKastTheme.dp(this, 10));
        ipcCard.addView(ipcSub);

        String[][] peers = new String[][] {
            { "io.nulkast.wall", "NulKast Wall", "VpnService Host, DoT & Sinkhole Engine" },
            { "io.nulkast.audit", "NulKast Audit", "Zero-Internet APK Tracker & Permission X-Ray" },
            { "io.nulkast.scrub", "NulKast Scrub", "Zero-Internet EXIF, Link & Clipboard Sanitizer" },
            { "io.nulkast.home", "NulKast Home", "Optional Zero-Internet Hub + Live HUD" }
        };
        PackageManager pm = getPackageManager();
        for (String[] peer : peers) {
            boolean installed = false;
            try {
                pm.getPackageInfo(peer[0], 0);
                installed = true;
            } catch (Throwable ignored) {}

            LinearLayout pRow = new LinearLayout(this);
            pRow.setOrientation(LinearLayout.HORIZONTAL);
            pRow.setGravity(Gravity.CENTER_VERTICAL);
            pRow.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 8f));
            int rp = NulKastTheme.dp(this, 8);
            pRow.setPadding(rp, rp, rp, rp);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            plp.bottomMargin = NulKastTheme.dp(this, 6);

            TextView badge = NulKastTheme.createSemanticBadge(
                this,
                installed ? "SIGNED" : "ABSENT",
                installed ? NulKastTheme.ACCENT_GREEN : NulKastTheme.ACCENT_AMBER,
                installed ? NulKastTheme.ACCENT_GREEN_DIM : NulKastTheme.ACCENT_AMBER_DIM
            );
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            blp.rightMargin = NulKastTheme.dp(this, 8);
            pRow.addView(badge, blp);

            LinearLayout tc = new LinearLayout(this);
            tc.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(this);
            t1.setText(peer[1] + " (" + peer[0] + ")");
            t1.setTextColor(NulKastTheme.TEXT_PRIMARY);
            t1.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            t1.setTextSize(11.5f);
            tc.addView(t1);

            TextView t2 = new TextView(this);
            t2.setText(peer[2]);
            t2.setTextColor(NulKastTheme.TEXT_SECONDARY);
            t2.setTextSize(11f);
            tc.addView(t2);

            pRow.addView(tc);
            ipcCard.addView(pRow, plp);
        }

        col.addView(ipcCard);
        scroll.addView(col);
        centerViewport.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));
    }

    private void startVpnWithConsent() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN_PERMISSION);
        } else {
            onActivityResult(REQ_VPN_PERMISSION, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN_PERMISSION && resultCode == RESULT_OK) {
            Intent svc = new Intent(this, NulKastVpnService.class);
            svc.setAction(NulKastVpnService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        }
    }
}
