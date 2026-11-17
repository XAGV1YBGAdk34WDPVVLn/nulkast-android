package io.nulkast.netintel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Built-In App Safety Advisor, Known-Safe Preset Catalog, Exodus Tracker SDK X-Ray Classifier,
 * and `nulkast-rulepack-v1` Community Rulepack Importer/Exporter (`packages/net-intel`).
 */
public final class AppIntelligence {

    public static final int TIER_PROTECTED_CORE = 0;    // Must NEVER be blocked (Play Store, Download Manager, VoLTE)
    public static final int TIER_RECOMMENDED_BLOCK = 1; // Known telemetry, diagnostics, carrier/OEM spyware
    public static final int TIER_SAFE_OFFLINE = 2;      // Keyboards, Gallery, Clock, Files, Contacts (work 100% offline)
    public static final int TIER_STANDARD_APP = 3;      // Normal internet-enabled user/system app

    public static final class Advice {
        public final int tier;
        public final String badgeText;
        public final String reason;

        public Advice(int tier, String badgeText, String reason) {
            this.tier = tier;
            this.badgeText = badgeText;
            this.reason = reason;
        }
    }

    public static final class PrivacyGradeReport {
        public final String grade;
        public final int riskScore;

        public PrivacyGradeReport(String grade, int riskScore) {
            this.grade = grade;
            this.riskScore = riskScore;
        }
    }

    public static final String[][] TRACKER_SIGNATURES = new String[][] {
        { "com.google.firebase.analytics", "Firebase Analytics" },
        { "com.google.android.gms.measurement", "Firebase Analytics" },
        { "com.google.firebase.crashlytics", "Firebase Crashlytics" },
        { "com.google.android.gms.ads", "Google AdMob" },
        { "com.facebook.appevents", "Facebook Analytics" },
        { "com.facebook.ads", "Facebook Ads" },
        { "com.facebook.FacebookActivity", "Facebook Graph SDK" },
        { "com.appsflyer", "AppsFlyer Attribution" },
        { "com.adjust.sdk", "Adjust Attribution" },
        { "com.braze", "Braze Engagement" },
        { "com.appboy", "Braze Engagement" },
        { "io.branch.referral", "Branch Deep-Link" },
        { "com.onesignal", "OneSignal Push" },
        { "com.mixpanel.android", "Mixpanel Telemetry" },
        { "com.amplitude.api", "Amplitude Analytics" },
        { "com.unity3d.services.ads", "Unity Ads" },
        { "com.applovin", "AppLovin MAX" },
        { "com.ironsource.mediationsdk", "IronSource Ads" },
        { "com.bytedance.sdk.openadsdk", "ByteDance Pangle" },
        { "com.microsoft.appcenter", "Microsoft AppCenter" }
    };

    public static final String[][] SENSITIVE_PERMISSIONS = new String[][] {
        { "android.permission.CAMERA", "📷 Camera" },
        { "android.permission.RECORD_AUDIO", "🎙️ Mic" },
        { "android.permission.ACCESS_FINE_LOCATION", "📍 Fine GPS" },
        { "android.permission.ACCESS_COARSE_LOCATION", "🗺️ Coarse Loc" },
        { "android.permission.ACCESS_BACKGROUND_LOCATION", "🛰️ Bg Location" },
        { "android.permission.READ_CONTACTS", "👥 Contacts" },
        { "android.permission.READ_PHONE_STATE", "📞 Phone/IMEI" },
        { "android.permission.READ_EXTERNAL_STORAGE", "🗂️ Storage" },
        { "android.permission.READ_MEDIA_IMAGES", "🖼️ Photos" }
    };

    private static final Map<String, String> PROTECTED_CORE_PACKAGES = new HashMap<String, String>();
    private static final Map<String, String> SAFE_OFFLINE_PACKAGES = new HashMap<String, String>();
    private static final Map<String, String> RECOMMENDED_BLOCK_PACKAGES = new HashMap<String, String>();

    public static final String[] COMMUNITY_EXTENDED_DOMAINS = new String[] {
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "flurry.com",
        "ads.yahoo.com",
        "analytics.yahoo.com",
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "adsrvr.org",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "smartadserver.com",
        "moatads.com",
        "iasds01.com",
        "chartboost.com",
        "vungle.com",
        "applovin.com",
        "applvn.com",
        "inmobi.com",
        "tapjoy.com",
        "startappservice.com",
        "byteoversea.com",
        "tik-tokapi.com",
        "clarity.ms",
        "hotjar.com",
        "fullstory.com",
        "bugsnag.com",
        "sentry.io",
        "datadoghq.com",
        "newrelic.com",
        "segment.io",
        "segment.com",
        "rudderstack.com",
        "clevertap-prod.com",
        "onesignal.com",
        "leanplum.com",
        "kochava.com",
        "singular.net"
    };

    static {
        // Tier 0: Critical Protected Core (Never block — keeps NulKast Suite, Play Store, Downloads, VoLTE & Wi-Fi Calling working)
        PROTECTED_CORE_PACKAGES.put("io.nulkast.wall", "PROTECTED • NulKast Wall VpnService host");
        PROTECTED_CORE_PACKAGES.put("io.nulkast.audit", "PROTECTED • NulKast Audit Zero-Internet APK X-Ray");
        PROTECTED_CORE_PACKAGES.put("io.nulkast.scrub", "PROTECTED • NulKast Scrub Zero-Internet Sanitizer");
        PROTECTED_CORE_PACKAGES.put("io.nulkast.pulse", "PROTECTED • NulKast Pulse Zero-Internet OS Analytics");
        PROTECTED_CORE_PACKAGES.put("io.nulkast.home", "PROTECTED • NulKast Home Zero-Internet Hub");
        PROTECTED_CORE_PACKAGES.put("com.android.vending", "PROTECTED • Google Play Store client");
        PROTECTED_CORE_PACKAGES.put("com.google.android.gms", "PROTECTED • Google Play Services & Push notifications");
        PROTECTED_CORE_PACKAGES.put("com.google.android.gsf", "PROTECTED • Google Services Framework auth");
        PROTECTED_CORE_PACKAGES.put("com.android.providers.downloads", "PROTECTED • Download Manager (required by Play Store)");
        PROTECTED_CORE_PACKAGES.put("com.google.android.webview", "PROTECTED • System WebView engine");
        PROTECTED_CORE_PACKAGES.put("com.sec.imsservice", "PROTECTED • Carrier VoLTE voice calls & SMS");
        PROTECTED_CORE_PACKAGES.put("com.sec.epdg", "PROTECTED • Carrier Wi-Fi Calling handoff");
        PROTECTED_CORE_PACKAGES.put("com.samsung.advp.imssettings", "PROTECTED • Carrier VoLTE IMS profile");
        PROTECTED_CORE_PACKAGES.put("com.android.phone", "PROTECTED • Core Telephony stack");

        // Tier 2: Known-Safe Offline Utilities (Work 100% locally; blocking stops cloud telemetry/upsells)
        SAFE_OFFLINE_PACKAGES.put("com.samsung.android.honeyboard", "SAFE OFFLINE • Samsung Keyboard works 100% locally; blocks keystroke cloud telemetry");
        SAFE_OFFLINE_PACKAGES.put("com.google.android.inputmethod.latin", "SAFE OFFLINE • Gboard works 100% locally; blocks federated learning uploads");
        SAFE_OFFLINE_PACKAGES.put("com.sec.android.app.myfiles", "SAFE OFFLINE • Local file manager; blocks cloud upsell pings");
        SAFE_OFFLINE_PACKAGES.put("com.google.android.apps.nbu.files", "SAFE OFFLINE • Local file manager; needs no internet");
        SAFE_OFFLINE_PACKAGES.put("com.sec.android.gallery3d", "SAFE OFFLINE • Local photo gallery & editor; stops cloud sync");
        SAFE_OFFLINE_PACKAGES.put("com.sec.android.app.clockpackage", "SAFE OFFLINE • Alarms, timers & stopwatch work 100% offline");
        SAFE_OFFLINE_PACKAGES.put("com.google.android.deskclock", "SAFE OFFLINE • Alarms & timers work 100% offline");
        SAFE_OFFLINE_PACKAGES.put("com.samsung.android.calendar", "SAFE OFFLINE • Local calendar schedule works offline");
        SAFE_OFFLINE_PACKAGES.put("com.samsung.android.app.contacts", "SAFE OFFLINE • Local contacts & dialer work offline");
        SAFE_OFFLINE_PACKAGES.put("com.diotek.sec.lookup.dictionary", "SAFE OFFLINE • Offline dictionary pack; stops lookup telemetry");
        SAFE_OFFLINE_PACKAGES.put("com.sec.android.app.popupcalculator", "SAFE OFFLINE • Calculator works 100% offline");
        SAFE_OFFLINE_PACKAGES.put("com.google.android.calculator", "SAFE OFFLINE • Calculator needs no internet");
        SAFE_OFFLINE_PACKAGES.put("com.sec.android.app.camera", "SAFE OFFLINE • Hardware camera needs no internet");
        SAFE_OFFLINE_PACKAGES.put("org.fossify.filemanager", "SAFE OFFLINE • Local file manager");

        // Tier 1: Recommended Block (Diagnostic, Telemetry, Setup & Carrier/OEM Background Agents)
        RECOMMENDED_BLOCK_PACKAGES.put("com.sec.android.diagmonagent", "TELEMETRY • Samsung Diagnostic Monitoring uploader");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.android.rubin.app", "TELEMETRY • Samsung Customization behavioral profiler");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.android.dqagent", "TELEMETRY • Device Quality telemetry agent");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.klmsagent", "TELEMETRY • Samsung Knox License telemetry agent");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.sdm", "TELEMETRY • Verizon/Samsung Device Management polling");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.sdm.sdmviewer", "TELEMETRY • SDM telemetry viewer");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.vzwapiservice", "TELEMETRY • Verizon background API telemetry service");
        RECOMMENDED_BLOCK_PACKAGES.put("com.sec.android.soagent", "TELEMETRY • Samsung IMEI/Serial update polling agent");
        RECOMMENDED_BLOCK_PACKAGES.put("com.sec.android.daemonapp", "TELEMETRY • Weather daemon (safe to block without widget)");
        RECOMMENDED_BLOCK_PACKAGES.put("com.google.android.partnersetup", "TELEMETRY • Google Partner Setup telemetry bridge");
        RECOMMENDED_BLOCK_PACKAGES.put("com.google.android.printservice.recommendation", "TELEMETRY • Network printer beacon scanner");
        RECOMMENDED_BLOCK_PACKAGES.put("com.sec.android.app.chromecustomizations", "BLOAT • Carrier Chrome bookmark injector");
        RECOMMENDED_BLOCK_PACKAGES.put("com.android.providers.partnerbookmarks", "BLOAT • Sponsored browser bookmarks provider");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.storyservice", "TELEMETRY • Gallery background story indexer");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.cmh", "TELEMETRY • Samsung Cloud Media Hub indexer");
        RECOMMENDED_BLOCK_PACKAGES.put("com.samsung.mlp", "TELEMETRY • Samsung Machine Learning location profiler");
        RECOMMENDED_BLOCK_PACKAGES.put("com.osp.app.signin", "TELEMETRY • Samsung Account background auth/sync daemon");
        RECOMMENDED_BLOCK_PACKAGES.put("com.sec.android.app.samsungapps", "BLOAT • Samsung Galaxy Store background updater");
    }

    private AppIntelligence() {}

    public static boolean isProtectedCore(String pkg) {
        return pkg != null && PROTECTED_CORE_PACKAGES.containsKey(pkg);
    }

    public static void matchTrackerComponent(String clsName, Set<String> out) {
        if (clsName == null || out == null) return;
        for (String[] sig : TRACKER_SIGNATURES) {
            if (clsName.startsWith(sig[0])) {
                out.add(sig[1]);
            }
        }
    }

    public static List<String> matchTrackerComponents(Iterable<String> componentNames) {
        Set<String> found = new LinkedHashSet<String>();
        if (componentNames != null) {
            for (String cls : componentNames) {
                matchTrackerComponent(cls, found);
            }
        }
        return new ArrayList<String>(found);
    }

    public static PrivacyGradeReport computePrivacyGrade(int trackerCount, int riskyPermCount, boolean hasInternet) {
        int riskScore = trackerCount * 25 + riskyPermCount * 8 + (hasInternet ? 5 : 0);
        String grade;
        if (trackerCount >= 3) {
            grade = "F";
        } else if (trackerCount == 2) {
            grade = "D";
        } else if (trackerCount == 1 || riskyPermCount >= 4) {
            grade = "C";
        } else if (!hasInternet && riskyPermCount == 0) {
            grade = "A+";
        } else if (riskyPermCount <= 1) {
            grade = "A";
        } else {
            grade = "B";
        }
        return new PrivacyGradeReport(grade, riskScore);
    }

    public static Advice classifyPackage(String pkg) {
        if (pkg == null) {
            return new Advice(TIER_STANDARD_APP, "APP", "Standard application");
        }
        if (PROTECTED_CORE_PACKAGES.containsKey(pkg)) {
            return new Advice(TIER_PROTECTED_CORE, "🛡️ KEEP ALLOWED", PROTECTED_CORE_PACKAGES.get(pkg));
        }
        if (RECOMMENDED_BLOCK_PACKAGES.containsKey(pkg)) {
            return new Advice(TIER_RECOMMENDED_BLOCK, "🎯 REC. BLOCK", RECOMMENDED_BLOCK_PACKAGES.get(pkg));
        }
        if (SAFE_OFFLINE_PACKAGES.containsKey(pkg)) {
            return new Advice(TIER_SAFE_OFFLINE, "📴 SAFE OFFLINE", SAFE_OFFLINE_PACKAGES.get(pkg));
        }
        String lower = pkg.toLowerCase(Locale.US);
        if (lower.contains("analytics") || lower.contains("telemetry") || lower.contains("diagmon")
                || lower.contains("logcollector") || lower.contains("bixby") || lower.contains("verizon")
                || lower.contains("vzw") || lower.contains("facebook")) {
            return new Advice(TIER_RECOMMENDED_BLOCK, "🎯 REC. BLOCK", "TELEMETRY • Background OEM/Carrier service");
        }
        return new Advice(TIER_STANDARD_APP, "🌐 INTERNET APP", "Standard app • Block only if you want it offline");
    }

    public static Set<String> getRecommendedBlockPackageSet(Iterable<String> installedPackages) {
        Set<String> out = new HashSet<String>();
        for (String pkg : installedPackages) {
            if (isProtectedCore(pkg)) continue;
            Advice adv = classifyPackage(pkg);
            if (adv.tier == TIER_RECOMMENDED_BLOCK || adv.tier == TIER_SAFE_OFFLINE) {
                out.add(pkg);
            }
        }
        return out;
    }

    public static String exportRulepackJson(
        String packName,
        Set<String> blockedPkgs,
        Set<String> customDomains
    ) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", "nulkast-rulepack-v1");
            root.put("version", "1.0.0");
            root.put("name", packName != null ? packName : "NulKast Custom Shield");
            root.put("author", "NulKast User");

            JSONArray domArr = new JSONArray();
            if (customDomains != null) {
                for (String d : customDomains) domArr.put(d);
            }
            root.put("blocked_domains", domArr);

            JSONArray pkgArr = new JSONArray();
            if (blockedPkgs != null) {
                for (String p : blockedPkgs) {
                    if (!isProtectedCore(p)) pkgArr.put(p);
                }
            }
            root.put("blocked_packages", pkgArr);

            JSONArray protArr = new JSONArray();
            for (String p : PROTECTED_CORE_PACKAGES.keySet()) protArr.put(p);
            root.put("protected_packages", protArr);

            return root.toString(2);
        } catch (Throwable t) {
            return "{}";
        }
    }

    public static final class ImportedRulepack {
        public final String name;
        public final Set<String> domains = new HashSet<String>();
        public final Set<String> packages = new HashSet<String>();

        public ImportedRulepack(String name) {
            this.name = name;
        }
    }

    /**
     * Parses either a `nulkast-rulepack-v1` JSON string OR a community hosts/AdGuard/domain list.
     */
    public static ImportedRulepack parseRulepackOrHosts(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ImportedRulepack("Empty Rulepack");
        }
        String trimmed = rawText.trim();
        if (trimmed.startsWith("{")) {
            try {
                JSONObject obj = new JSONObject(trimmed);
                ImportedRulepack pack = new ImportedRulepack(obj.optString("name", "Imported JSON Rulepack"));
                JSONArray doms = obj.optJSONArray("blocked_domains");
                if (doms != null) {
                    for (int i = 0; i < doms.length(); i++) {
                        String d = doms.optString(i, "").trim().toLowerCase(Locale.US);
                        if (!d.isEmpty() && !PacketEngine.PLAY_STORE_WHITELIST.contains(d)) {
                            pack.domains.add(d);
                        }
                    }
                }
                JSONArray pkgs = obj.optJSONArray("blocked_packages");
                if (pkgs != null) {
                    for (int i = 0; i < pkgs.length(); i++) {
                        String p = pkgs.optString(i, "").trim();
                        if (!p.isEmpty() && !isProtectedCore(p)) {
                            pack.packages.add(p);
                        }
                    }
                }
                JSONArray recPkgs = obj.optJSONArray("recommended_blocked_packages");
                if (recPkgs != null) {
                    for (int i = 0; i < recPkgs.length(); i++) {
                        JSONObject item = recPkgs.optJSONObject(i);
                        String p = item != null ? item.optString("package", "") : recPkgs.optString(i, "");
                        if (!p.isEmpty() && !isProtectedCore(p)) {
                            pack.packages.add(p);
                        }
                    }
                }
                return pack;
            } catch (Throwable ignored) {}
        }

        // Fallback: Parse standard hosts ("0.0.0.0 domain.com"), AdGuard ("||domain.com^"), or plain domains
        ImportedRulepack hostPack = new ImportedRulepack("Community Domain List");
        String[] lines = trimmed.split("\\r?\\n");
        for (String line : lines) {
            String l = line.trim().toLowerCase(Locale.US);
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("!")) continue;
            if (l.startsWith("0.0.0.0 ") || l.startsWith("127.0.0.1 ")) {
                String[] parts = l.split("\\s+");
                if (parts.length >= 2) l = parts[1].trim();
            } else if (l.startsWith("||") && l.contains("^")) {
                l = l.substring(2, l.indexOf('^')).trim();
            }
            if (l.contains(".") && !l.contains("/") && !l.equals("localhost")
                    && !PacketEngine.PLAY_STORE_WHITELIST.contains(l)) {
                hostPack.domains.add(l);
            }
        }
        return hostPack;
    }
}
