package io.nulkast.audit;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import io.nulkast.ipcbus.NulKastHudClient;
import io.nulkast.netintel.AppIntelligence;
import io.nulkast.ui.NulKastTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NulKast Audit (`io.nulkast.audit`) — Zero-Internet Static APK Tracker & Permission X-Ray Scanner.
 * Scans every installed app's manifest components (`Activities`, `Services`, `Receivers`, `Providers`)
 * against 16 canonical Exodus Privacy tracker SDK signatures and 8 high-risk runtime permissions,
 * computes a deterministic Privacy Grade (`A`..`F`), and links directly to `NulKast Wall` over the
 * cryptographic signature IPC bus (`io.nulkast.permission.IPC_BUS`) for 1-tap firewall blocking.
 */
public final class AuditMainActivity extends Activity {

    private static final class AuditedApp {
        final String packageName;
        final String label;
        final int uid;
        final boolean isSystem;
        final boolean hasInternet;
        final List<String> trackers;
        final List<String> riskyPerms;
        final String grade;
        final int riskScore;

        AuditedApp(
            String packageName,
            String label,
            int uid,
            boolean isSystem,
            boolean hasInternet,
            List<String> trackers,
            List<String> riskyPerms,
            String grade,
            int riskScore
        ) {
            this.packageName = packageName;
            this.label = label;
            this.uid = uid;
            this.isSystem = isSystem;
            this.hasInternet = hasInternet;
            this.trackers = trackers;
            this.riskyPerms = riskyPerms;
            this.grade = grade;
            this.riskScore = riskScore;
        }
    }

    private NulKastHudClient hudClient;
    private List<AuditedApp> cachedAudit = new ArrayList<AuditedApp>();
    private Set<String> wallBlockedPackages = new HashSet<String>();
    private int activeFilter = 0; // 0=Trackers Found, 1=Risky Perms, 2=User Apps, 3=All Apps
    private String searchQuery = "";

    private TextView heroSummaryTv;
    private TextView heroSubTv;
    private LinearLayout centerViewport;
    private Button[] navButtons = new Button[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NulKastTheme.applyWindowChrome(this);
        hudClient = new NulKastHudClient(this);
        buildShellUi();
        runAuditScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncWallStateAndRefresh();
    }

    private void buildShellUi() {
        int pad = NulKastTheme.dp(this, 12);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(NulKastTheme.BG_PRIMARY);

        // ====================================================================
        // 1. TOP PINNED HERO CARD (Device Tracker & Permission X-Ray Summary)
        // ====================================================================
        LinearLayout topHero = new LinearLayout(this);
        topHero.setOrientation(LinearLayout.VERTICAL);
        topHero.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_SECONDARY, NulKastTheme.BORDER_COLOR, 14f));
        topHero.setPadding(pad, NulKastTheme.dp(this, 10), pad, NulKastTheme.dp(this, 10));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        heroLp.setMargins(pad, NulKastTheme.dp(this, 8), pad, NulKastTheme.dp(this, 6));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        heroSummaryTv = new TextView(this);
        heroSummaryTv.setText("🕵️ NULKAST AUDIT // APK X-RAY");
        heroSummaryTv.setTextColor(NulKastTheme.TEXT_PRIMARY);
        heroSummaryTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        heroSummaryTv.setTextSize(15f);
        titleCol.addView(heroSummaryTv);

        heroSubTv = new TextView(this);
        heroSubTv.setText("Zero-Internet Exodus SDK & Runtime Permission Scanner");
        heroSubTv.setTextColor(NulKastTheme.TEXT_SECONDARY);
        heroSubTv.setTypeface(Typeface.MONOSPACE);
        heroSubTv.setTextSize(11f);
        titleCol.addView(heroSubTv);

        titleRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button rescanBtn = new Button(this);
        rescanBtn.setText("🔄 Rescan");
        rescanBtn.setAllCaps(false);
        rescanBtn.setTextSize(11.5f);
        rescanBtn.setTypeface(Typeface.DEFAULT_BOLD);
        rescanBtn.setTextColor(NulKastTheme.ACCENT_CYAN);
        rescanBtn.setBackground(NulKastTheme.pillDrawable(this, NulKastTheme.ACCENT_CYAN_DIM, NulKastTheme.ACCENT_CYAN));
        rescanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAuditScan();
                Toast.makeText(AuditMainActivity.this, "Completed full static APK manifest X-Ray scan", Toast.LENGTH_SHORT).show();
            }
        });
        titleRow.addView(rescanBtn);
        topHero.addView(titleRow);
        shell.addView(topHero, heroLp);

        // ====================================================================
        // 2. CENTER VIEWPORT (Search + Audited App Cards)
        // ====================================================================
        centerViewport = new LinearLayout(this);
        centerViewport.setOrientation(LinearLayout.VERTICAL);
        centerViewport.setPadding(pad, 0, pad, 0);
        shell.addView(centerViewport, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        // ====================================================================
        // 3. BOTTOM PINNED 4-FILTER NAVIGATION DOCK
        // ====================================================================
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(NulKastTheme.BG_SECONDARY);
        int navPad = NulKastTheme.dp(this, 8);
        bottomNav.setPadding(navPad, navPad, navPad, navPad);

        String[] labels = new String[] { "🛑 Trackers", "⚠️ Perms", "👤 User Apps", "📦 All Apps" };
        for (int i = 0; i < 4; i++) {
            final int tabIdx = i;
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(11.5f);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activeFilter = tabIdx;
                    updateNavHighlight();
                    renderListViewport();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, NulKastTheme.dp(this, 44), 1f);
            if (i < 3) lp.rightMargin = NulKastTheme.dp(this, 5);
            navButtons[i] = b;
            bottomNav.addView(b, lp);
        }
        shell.addView(bottomNav, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(shell);
        updateNavHighlight();
    }

    private void updateNavHighlight() {
        for (int i = 0; i < 4; i++) {
            boolean sel = (i == activeFilter);
            navButtons[i].setTextColor(sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.TEXT_SECONDARY);
            navButtons[i].setBackground(NulKastTheme.cardDrawable(
                this,
                sel ? NulKastTheme.ACCENT_CYAN_DIM : NulKastTheme.BG_CARD,
                sel ? NulKastTheme.ACCENT_CYAN : NulKastTheme.BORDER_COLOR,
                10f
            ));
        }
    }

    private void syncWallStateAndRefresh() {
        NulKastHudClient.Snapshot snap = hudClient.fetchSnapshot();
        wallBlockedPackages.clear();
        if (snap.blockedPackages != null) {
            wallBlockedPackages.addAll(snap.blockedPackages);
        }
        updateHeroReadout();
        renderListViewport();
    }

    private void runAuditScan() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        List<AuditedApp> results = new ArrayList<AuditedApp>();

        int flags = PackageManager.GET_ACTIVITIES
            | PackageManager.GET_SERVICES
            | PackageManager.GET_RECEIVERS
            | PackageManager.GET_PROVIDERS
            | PackageManager.GET_PERMISSIONS;

        for (PackageInfo basic : pkgs) {
            if (basic.applicationInfo == null) continue;
            String pkgName = basic.packageName;
            PackageInfo full = basic;
            try {
                full = pm.getPackageInfo(pkgName, flags);
            } catch (Throwable ignored) {}

            Set<String> foundTrackers = new LinkedHashSet<String>();
            if (full.activities != null) {
                for (ActivityInfo a : full.activities) AppIntelligence.matchTrackerComponent(a.name, foundTrackers);
            }
            if (full.services != null) {
                for (ServiceInfo s : full.services) AppIntelligence.matchTrackerComponent(s.name, foundTrackers);
            }
            if (full.receivers != null) {
                for (ActivityInfo r : full.receivers) AppIntelligence.matchTrackerComponent(r.name, foundTrackers);
            }
            if (full.providers != null) {
                for (ProviderInfo p : full.providers) AppIntelligence.matchTrackerComponent(p.name, foundTrackers);
            }

            boolean hasInternet = false;
            Set<String> foundPerms = new LinkedHashSet<String>();
            if (full.requestedPermissions != null) {
                for (String perm : full.requestedPermissions) {
                    if ("android.permission.INTERNET".equals(perm)) {
                        hasInternet = true;
                    }
                    for (String[] sp : AppIntelligence.SENSITIVE_PERMISSIONS) {
                        if (sp[0].equals(perm)) {
                            foundPerms.add(sp[1]);
                        }
                    }
                }
            }

            boolean isSystem = (basic.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            String label = NulKastTheme.resolveAppLabel(this, pkgName);
            List<String> trackerList = new ArrayList<String>(foundTrackers);
            List<String> permList = new ArrayList<String>(foundPerms);

            AppIntelligence.PrivacyGradeReport gradeReport = AppIntelligence.computePrivacyGrade(
                trackerList.size(),
                permList.size(),
                hasInternet
            );

            results.add(new AuditedApp(
                pkgName,
                label,
                basic.applicationInfo.uid,
                isSystem,
                hasInternet,
                trackerList,
                permList,
                gradeReport.grade,
                gradeReport.riskScore
            ));
        }

        Collections.sort(results, new Comparator<AuditedApp>() {
            @Override
            public int compare(AuditedApp a, AuditedApp b) {
                if (a.riskScore != b.riskScore) return b.riskScore - a.riskScore;
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        cachedAudit = results;
        syncWallStateAndRefresh();
    }

    private void updateHeroReadout() {
        int appsWithTrackers = 0;
        int totalTrackerEmbeds = 0;
        int appsWithRiskyPerms = 0;
        for (AuditedApp a : cachedAudit) {
            if (!a.trackers.isEmpty()) {
                appsWithTrackers++;
                totalTrackerEmbeds += a.trackers.size();
            }
            if (!a.riskyPerms.isEmpty()) {
                appsWithRiskyPerms++;
            }
        }
        heroSummaryTv.setText(
            "🕵️ AUDIT // " + appsWithTrackers + " Apps w/ Trackers (" + totalTrackerEmbeds + " SDKs)"
        );
        heroSubTv.setText(
            "Scanned: " + cachedAudit.size() + " pkgs  •  Risky Perms: " + appsWithRiskyPerms
                + "  •  Wall Blocked: " + wallBlockedPackages.size()
        );
    }

    private void renderListViewport() {
        centerViewport.removeAllViews();

        // Pinned Search Box
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, NulKastTheme.dp(this, 2), 0, NulKastTheme.dp(this, 6));

        final EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("🔍 Filter apps, tracker SDKs, or permissions...");
        searchInput.setHintTextColor(NulKastTheme.TEXT_MUTED);
        searchInput.setTextColor(NulKastTheme.TEXT_PRIMARY);
        searchInput.setTextSize(13f);
        searchInput.setText(searchQuery);
        int sp = NulKastTheme.dp(this, 10);
        searchInput.setPadding(sp, sp, sp, sp);
        searchInput.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_INPUT, NulKastTheme.BORDER_COLOR, 10f));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button goBtn = new Button(this);
        goBtn.setText("Filter");
        goBtn.setAllCaps(false);
        goBtn.setTextSize(11.5f);
        goBtn.setTextColor(NulKastTheme.ACCENT_CYAN);
        goBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_CYAN_DIM, NulKastTheme.ACCENT_CYAN, 10f));
        goBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchQuery = searchInput.getText().toString().trim();
                renderListViewport();
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = NulKastTheme.dp(this, 6);
        searchRow.addView(goBtn, glp);
        centerViewport.addView(searchRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        String q = searchQuery.toLowerCase(Locale.US);
        int shown = 0;

        for (final AuditedApp app : cachedAudit) {
            if (activeFilter == 0 && app.trackers.isEmpty()) continue;
            if (activeFilter == 1 && app.riskyPerms.isEmpty()) continue;
            if (activeFilter == 2 && app.isSystem) continue;

            if (!q.isEmpty()) {
                String joined = (app.label + " " + app.packageName + " " + app.trackers.toString() + " " + app.riskyPerms.toString()).toLowerCase(Locale.US);
                if (!joined.contains(q)) continue;
            }

            if (shown >= 85) break;
            shown++;

            final boolean isBlockedInWall = wallBlockedPackages.contains(app.packageName);
            final boolean isProtectedCore = AppIntelligence.isProtectedCore(app.packageName);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            int borderCol = !app.trackers.isEmpty() ? NulKastTheme.ACCENT_RED : NulKastTheme.BORDER_COLOR;
            if (isBlockedInWall) borderCol = NulKastTheme.ACCENT_GREEN;
            card.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_CARD, borderCol, 12f));
            int cp = NulKastTheme.dp(this, 10);
            card.setPadding(cp, cp, cp, cp);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            clp.bottomMargin = NulKastTheme.dp(this, 7);

            // Top Row: App Icon + Title/Pkg + Privacy Grade Badge
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            ImageView iconIv = new ImageView(this);
            Drawable d = NulKastTheme.loadAppIcon(this, app.packageName);
            if (d != null) iconIv.setImageDrawable(d);
            int iconPx = NulKastTheme.dp(this, 36);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconPx, iconPx);
            ilp.rightMargin = NulKastTheme.dp(this, 10);
            topRow.addView(iconIv, ilp);

            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);

            TextView titleTv = new TextView(this);
            titleTv.setText(app.label);
            titleTv.setTextColor(NulKastTheme.TEXT_PRIMARY);
            titleTv.setTypeface(Typeface.DEFAULT_BOLD);
            titleTv.setTextSize(13.5f);
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            nameCol.addView(titleTv);

            TextView pkgTv = new TextView(this);
            pkgTv.setText(app.packageName + (app.hasInternet ? " • 🌐 INTERNET" : " • 🔒 OFFLINE"));
            pkgTv.setTextColor(NulKastTheme.TEXT_MUTED);
            pkgTv.setTypeface(Typeface.MONOSPACE);
            pkgTv.setTextSize(10f);
            pkgTv.setSingleLine(true);
            pkgTv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            nameCol.addView(pkgTv);

            topRow.addView(nameCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            int gradeFg = NulKastTheme.ACCENT_GREEN;
            int gradeBg = NulKastTheme.ACCENT_GREEN_DIM;
            if ("C".equals(app.grade)) {
                gradeFg = NulKastTheme.ACCENT_AMBER;
                gradeBg = NulKastTheme.ACCENT_AMBER_DIM;
            } else if ("D".equals(app.grade) || "F".equals(app.grade)) {
                gradeFg = NulKastTheme.ACCENT_RED;
                gradeBg = NulKastTheme.ACCENT_RED_DIM;
            }
            TextView gradeBadge = NulKastTheme.createSemanticBadge(this, "GRADE " + app.grade, gradeFg, gradeBg);
            topRow.addView(gradeBadge);
            card.addView(topRow);

            // Middle Row: Found Tracker SDKs + Sensitive Permissions
            TextView findingsTv = new TextView(this);
            StringBuilder sb = new StringBuilder();
            if (!app.trackers.isEmpty()) {
                sb.append("🛑 Trackers (").append(app.trackers.size()).append("): ")
                    .append(TextUtils.join(", ", app.trackers));
            } else {
                sb.append("🟢 0 Embedded Tracker SDKs Detected");
            }
            if (!app.riskyPerms.isEmpty()) {
                sb.append("\n⚠️ Perms: ").append(TextUtils.join("  ", app.riskyPerms));
            }
            findingsTv.setText(sb.toString());
            findingsTv.setTextColor(!app.trackers.isEmpty() ? NulKastTheme.ACCENT_AMBER : NulKastTheme.TEXT_SECONDARY);
            findingsTv.setTypeface(Typeface.MONOSPACE);
            findingsTv.setTextSize(11f);
            findingsTv.setPadding(0, NulKastTheme.dp(this, 6), 0, NulKastTheme.dp(this, 8));
            card.addView(findingsTv);

            // Bottom Action Row: [🛡️ Block in Wall] + [⚙️ OS Permissions]
            LinearLayout actRow = new LinearLayout(this);
            actRow.setOrientation(LinearLayout.HORIZONTAL);

            Button wallBtn = new Button(this);
            wallBtn.setAllCaps(false);
            wallBtn.setTextSize(11.5f);
            wallBtn.setTypeface(Typeface.DEFAULT_BOLD);
            wallBtn.setMinimumHeight(NulKastTheme.dp(this, 36));
            if (isProtectedCore) {
                wallBtn.setText("🔒 Core Protected");
                wallBtn.setTextColor(NulKastTheme.ACCENT_CYAN);
                wallBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_CYAN_DIM, NulKastTheme.ACCENT_CYAN, 8f));
                wallBtn.setEnabled(false);
            } else if (isBlockedInWall) {
                wallBtn.setText("🛡️ Firewalled in Wall (Tap to Unblock)");
                wallBtn.setTextColor(NulKastTheme.ACCENT_GREEN);
                wallBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_GREEN_DIM, NulKastTheme.ACCENT_GREEN, 8f));
            } else {
                wallBtn.setText("🛑 Block in NulKast Wall");
                wallBtn.setTextColor(NulKastTheme.ACCENT_RED);
                wallBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.ACCENT_RED_DIM, NulKastTheme.ACCENT_RED, 8f));
            }
            wallBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean targetBlock = !isBlockedInWall;
                    boolean ok = hudClient.toggleAppBlock(app.packageName, targetBlock);
                    Toast.makeText(
                        AuditMainActivity.this,
                        (targetBlock ? "Firewalled in NulKast Wall: " : "Unblocked in NulKast Wall: ") + app.label,
                        Toast.LENGTH_SHORT
                    ).show();
                    syncWallStateAndRefresh();
                }
            });
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f);
            wlp.rightMargin = NulKastTheme.dp(this, 6);
            actRow.addView(wallBtn, wlp);

            Button permBtn = new Button(this);
            permBtn.setText("⚙️ Permissions");
            permBtn.setAllCaps(false);
            permBtn.setTextSize(11.5f);
            permBtn.setMinimumHeight(NulKastTheme.dp(this, 36));
            permBtn.setTextColor(NulKastTheme.TEXT_PRIMARY);
            permBtn.setBackground(NulKastTheme.cardDrawable(this, NulKastTheme.BG_SECONDARY, NulKastTheme.BORDER_COLOR, 8f));
            permBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.fromParts("package", app.packageName, null));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Throwable ignored) {}
                }
            });
            actRow.addView(permBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));

            card.addView(actRow);
            list.addView(card, clp);
        }

        if (shown == 0) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("🟢 Zero matching apps in this category. Your clean-slate Note 10+ 5G has minimal embedded tracker SDK exposure.");
            emptyTv.setTextColor(NulKastTheme.ACCENT_GREEN);
            emptyTv.setTypeface(Typeface.MONOSPACE);
            emptyTv.setTextSize(12.5f);
            emptyTv.setPadding(NulKastTheme.dp(this, 12), NulKastTheme.dp(this, 24), NulKastTheme.dp(this, 12), 0);
            list.addView(emptyTv);
        }

        scroll.addView(list);
        centerViewport.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));
    }
}
