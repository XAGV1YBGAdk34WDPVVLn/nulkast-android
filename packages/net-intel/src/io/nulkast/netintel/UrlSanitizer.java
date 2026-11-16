package io.nulkast.netintel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java Zero-Android-Dependency Tracking URL Parameter Sanitizer (`packages/net-intel`).
 * Strips 35+ cross-site ad, social, and analytics click-attribution tokens (`utm_*`, `hsa_*`,
 * `fbclid`, `gclid`, `dclid`, `gbraid`, `wbraid`, `msclkid`, `ttclid`, `twclid`, `igshid`,
 * `igsh`, `si`, `_gl`, `gad_source`, `mc_cid`, `mc_eid`, `mkt_tok`, `yclid`, etc.) while
 * preserving functional query parameters and `#fragment` anchors.
 */
public final class UrlSanitizer {

    public static final Set<String> TRACKING_PARAMS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "ttclid", "twclid",
        "igshid", "igsh", "si", "mc_cid", "mc_eid", "oly_enc_id", "oly_anon_id",
        "_hsenc", "_hsmi", "hsctatracking", "vero_id", "mkt_tok", "rb_clickid",
        "s_kwcid", "yclid", "spm", "scm", "ref_src", "ref_url", "feature",
        "embeds_referring_euri", "_gl", "gad_source", "srsltid", "tw_source", "tw_campaign"
    )));

    public static final class SanitizeResult {
        public final String cleanUrl;
        public final List<String> strippedParams;

        public SanitizeResult(String cleanUrl, List<String> strippedParams) {
            this.cleanUrl = cleanUrl;
            this.strippedParams = strippedParams;
        }
    }

    private UrlSanitizer() {}

    public static boolean isTrackingParamKey(String key) {
        if (key == null || key.isEmpty()) return false;
        String lower = key.trim().toLowerCase(Locale.US);
        return lower.startsWith("utm_")
            || lower.startsWith("hsa_")
            || lower.startsWith("pk_")
            || TRACKING_PARAMS.contains(lower);
    }

    public static SanitizeResult sanitize(String rawUrl) {
        List<String> stripped = new ArrayList<String>();
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return new SanitizeResult("", stripped);
        }
        String raw = rawUrl.trim();

        // Separate #fragment first even if it appears without or after '?'
        String fragment = "";
        int hashIdx = raw.indexOf('#');
        if (hashIdx >= 0) {
            fragment = raw.substring(hashIdx);
            raw = raw.substring(0, hashIdx);
        }

        int qIdx = raw.indexOf('?');
        if (qIdx < 0) {
            return new SanitizeResult(raw + fragment, stripped);
        }

        String base = raw.substring(0, qIdx);
        String query = raw.substring(qIdx + 1);
        String[] pairs = query.split("&");
        StringBuilder keptQuery = new StringBuilder();

        for (String pair : pairs) {
            if (pair == null || pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String rawKey = (eq >= 0 ? pair.substring(0, eq) : pair);
            if (isTrackingParamKey(rawKey)) {
                stripped.add(rawKey.toLowerCase(Locale.US));
            } else {
                if (keptQuery.length() > 0) keptQuery.append('&');
                keptQuery.append(pair);
            }
        }

        String out = base + (keptQuery.length() > 0 ? "?" + keptQuery.toString() : "") + fragment;
        return new SanitizeResult(out, stripped);
    }
}
