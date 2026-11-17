package io.nulkast.netintel;

import java.util.Locale;

/**
 * Pure-Java System Diagnostics, `/proc/meminfo` & ChromeOS Host Bridge Parser (`packages/net-intel`).
 * Zero Android UI dependencies so it can be unit-tested in `<900ms` on standard OpenJDK.
 */
public final class SystemDiagnosticsParser {

    public static final class MemInfoSnapshot {
        public final long memTotalMb;
        public final long memAvailableMb;
        public final long memUsedMb;
        public final long cachedMb;
        public final long swapTotalMb;
        public final long swapUsedMb;
        public final int usedPercent;

        public MemInfoSnapshot(
            long memTotalMb,
            long memAvailableMb,
            long memUsedMb,
            long cachedMb,
            long swapTotalMb,
            long swapUsedMb,
            int usedPercent
        ) {
            this.memTotalMb = memTotalMb;
            this.memAvailableMb = memAvailableMb;
            this.memUsedMb = memUsedMb;
            this.cachedMb = cachedMb;
            this.swapTotalMb = swapTotalMb;
            this.swapUsedMb = swapUsedMb;
            this.usedPercent = usedPercent;
        }
    }

    public static final class ChromeOsHostTelemetry {
        public final boolean available;
        public final String osName;
        public final String board;
        public final String cpuModel;
        public final int tempC;
        public final int freqMhz;
        public final double watts;
        public final String thermalTier;
        public final double load1m;
        public final long memUsedMb;
        public final long memTotalMb;
        public final String tcpCc;
        public final int swappiness;
        public final int debloatedDaemons;
        public final int debloatedArcvmPkgs;
        public final int sinkholedDomains;

        public ChromeOsHostTelemetry(
            boolean available,
            String osName,
            String board,
            String cpuModel,
            int tempC,
            int freqMhz,
            double watts,
            String thermalTier,
            double load1m,
            long memUsedMb,
            long memTotalMb,
            String tcpCc,
            int swappiness,
            int debloatedDaemons,
            int debloatedArcvmPkgs,
            int sinkholedDomains
        ) {
            this.available = available;
            this.osName = osName;
            this.board = board;
            this.cpuModel = cpuModel;
            this.tempC = tempC;
            this.freqMhz = freqMhz;
            this.watts = watts;
            this.thermalTier = thermalTier;
            this.load1m = load1m;
            this.memUsedMb = memUsedMb;
            this.memTotalMb = memTotalMb;
            this.tcpCc = tcpCc;
            this.swappiness = swappiness;
            this.debloatedDaemons = debloatedDaemons;
            this.debloatedArcvmPkgs = debloatedArcvmPkgs;
            this.sinkholedDomains = sinkholedDomains;
        }

        public static ChromeOsHostTelemetry empty() {
            return new ChromeOsHostTelemetry(
                false, "", "", "", 0, 0, 0.0, "N/A", 0.0, 0L, 0L, "", 0, 0, 0, 0
            );
        }
    }

    private SystemDiagnosticsParser() {}

    /**
     * Parses Linux `/proc/meminfo` text into a structured `MemInfoSnapshot`.
     */
    public static MemInfoSnapshot parseProcMeminfo(String rawMeminfo) {
        if (rawMeminfo == null || rawMeminfo.isEmpty()) {
            return new MemInfoSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0);
        }
        long memTotalKb = 0L;
        long memFreeKb = 0L;
        long memAvailKb = -1L;
        long buffersKb = 0L;
        long cachedKb = 0L;
        long swapTotalKb = 0L;
        long swapFreeKb = 0L;

        String[] lines = rawMeminfo.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            long valKb = parseTrailingKb(line.substring(colon + 1));
            if ("MemTotal".equals(key)) memTotalKb = valKb;
            else if ("MemFree".equals(key)) memFreeKb = valKb;
            else if ("MemAvailable".equals(key)) memAvailKb = valKb;
            else if ("Buffers".equals(key)) buffersKb = valKb;
            else if ("Cached".equals(key)) cachedKb = valKb;
            else if ("SwapTotal".equals(key)) swapTotalKb = valKb;
            else if ("SwapFree".equals(key)) swapFreeKb = valKb;
        }

        if (memAvailKb < 0L) {
            memAvailKb = memFreeKb + buffersKb + cachedKb;
        }
        long totalMb = memTotalKb / 1024L;
        long availMb = Math.min(totalMb, Math.max(0L, memAvailKb / 1024L));
        long usedMb = Math.max(0L, totalMb - availMb);
        long cachedMb = cachedKb / 1024L;
        long swapTotalMb = swapTotalKb / 1024L;
        long swapUsedMb = Math.max(0L, (swapTotalKb - swapFreeKb) / 1024L);
        int usedPct = totalMb > 0L ? (int) Math.min(100L, (usedMb * 100L) / totalMb) : 0;

        return new MemInfoSnapshot(totalMb, availMb, usedMb, cachedMb, swapTotalMb, swapUsedMb, usedPct);
    }

    private static long parseTrailingKb(String rawVal) {
        String s = rawVal.trim().toLowerCase(Locale.US);
        if (s.endsWith("kb")) {
            s = s.substring(0, s.length() - 2).trim();
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Parses the single-line or multi-line JSON written by ChromeOS `/usr/local/bin/nulkast-thermal-monitor.sh`.
     */
    public static ChromeOsHostTelemetry parseChromeOsTelemetryJson(String rawJson) {
        if (rawJson == null || !rawJson.contains("{")) {
            return ChromeOsHostTelemetry.empty();
        }
        String osName = extractJsonString(rawJson, "os_name", "NulKast ChromeOS");
        String board = extractJsonString(rawJson, "board", "nocturne");
        String cpuModel = extractJsonString(rawJson, "cpu_model", "Intel Core i7-8500Y");
        int tempC = (int) extractJsonNumber(rawJson, "temp_c", extractJsonNumber(rawJson, "pkg_temp_c", 0.0));
        int freqMhz = (int) extractJsonNumber(rawJson, "freq_mhz", 0.0);
        double watts = extractJsonNumber(rawJson, "watts", 0.0);
        String tier = extractJsonString(rawJson, "tier", "COOL_TURBO");
        double load1m = extractJsonNumber(rawJson, "load_1m", 0.0);
        long memUsedMb = (long) extractJsonNumber(rawJson, "mem_used_mb", 0.0);
        long memTotalMb = (long) extractJsonNumber(rawJson, "mem_total_mb", 16384.0);
        String tcpCc = extractJsonString(rawJson, "tcp_cc", "bbr");
        int swappiness = (int) extractJsonNumber(rawJson, "swappiness", 15.0);
        int debloatedDaemons = (int) extractJsonNumber(rawJson, "debloated_daemons", 10.0);
        int debloatedArcvm = (int) extractJsonNumber(rawJson, "debloated_arcvm_pkgs", 20.0);
        int sinkholed = (int) extractJsonNumber(rawJson, "sinkholed_domains", 28.0);

        return new ChromeOsHostTelemetry(
            true,
            osName,
            board,
            cpuModel,
            tempC,
            freqMhz,
            watts,
            tier,
            load1m,
            memUsedMb,
            memTotalMb,
            tcpCc,
            swappiness,
            debloatedDaemons,
            debloatedArcvm,
            sinkholed
        );
    }

    private static String extractJsonString(String json, String key, String fallback) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return fallback;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return fallback;
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return fallback;
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return fallback;
        return json.substring(firstQuote + 1, secondQuote).trim();
    }

    private static double extractJsonNumber(String json, String key, double fallback) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return fallback;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return fallback;
        int pos = colon + 1;
        while (pos < json.length() && (json.charAt(pos) == ' ' || json.charAt(pos) == '"')) {
            pos++;
        }
        int end = pos;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                end++;
            } else {
                break;
            }
        }
        if (end <= pos) return fallback;
        try {
            return Double.parseDouble(json.substring(pos, end));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Computes a deterministic 0..100 System Slimness, Thermal & Responsiveness Score.
     */
    public static int computeResponsivenessScore(
        int memUsedPercent,
        int tempC,
        double avgFrameMs,
        boolean vpnShieldActive,
        int bloatwareRemovedCount
    ) {
        int score = 100;
        if (memUsedPercent > 85) score -= 20;
        else if (memUsedPercent > 75) score -= 10;

        if (tempC >= 65) score -= 25;
        else if (tempC >= 55) score -= 10;
        else if (tempC >= 45) score -= 4;

        if (avgFrameMs > 24.0) score -= 20;
        else if (avgFrameMs > 17.5) score -= 8;

        if (!vpnShieldActive) score -= 10;
        if (bloatwareRemovedCount < 10) score -= 10;

        return Math.max(0, Math.min(100, score));
    }

    public static String computePerformanceGrade(int score) {
        if (score >= 94) return "A+ (ULTRA-SLIM TURBO)";
        if (score >= 86) return "A (OPTIMIZED)";
        if (score >= 75) return "B (BALANCED)";
        if (score >= 60) return "C (WARM / LOADED)";
        return "D (THROTTLED)";
    }
}
