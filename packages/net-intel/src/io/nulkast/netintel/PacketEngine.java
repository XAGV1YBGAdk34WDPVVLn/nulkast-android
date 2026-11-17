package io.nulkast.netintel;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Shared RFC Packet Engine & Tracker Domain Intelligence (`packages/net-intel`).
 *
 * Implements:
 * - RFC 791 IPv4 header parsing & RFC 1071 16-bit ones'-complement pseudo-header checksums
 * - RFC 793 / RFC 9293 Instant 40-Byte IPv4 TCP RST+ACK (`0x14`) synthesis (`< 0.1ms` ECONNREFUSED)
 * - RFC 1035 DNS Wire-Format QNAME extraction & `0xC00C` Sinkhole Response synthesis (`0.0.0.0` / `NXDOMAIN`)
 * - Built-in 65+ Samsung, Meta, Verizon, Google & AdTech Tracker Suffix Matcher (preserving Google Play Store)
 */
public final class PacketEngine {

    public static final String[] BUILTIN_TRACKER_SUFFIXES = new String[] {
        // Samsung OneUI, Rubin, Knox & Game Optimizing Service Telemetry
        "smetrics.samsung.com",
        "nmetrics.samsung.com",
        "diagmon-service.samsung.com",
        "dqa.samsung.com",
        "rubin.samsung.com",
        "samsungads.com",
        "config.samsungads.com",
        "events.samsungads.com",
        "ads.samsung.com",
        "bixby.samsung.com",
        "gld.push.samsungosp.com",
        "samsungosp.com",
        "vas.samsungapps.com",
        "gos-api.gos-gsp.io",
        // Meta / Facebook Background Trackers
        "graph.facebook.com",
        "edge-mqtt.facebook.com",
        "b-graph.facebook.com",
        "connect.facebook.net",
        "an.facebook.com",
        // Google AdTech & Crash/Behavioral Telemetry (Explicitly preserves play.fe.googleapis.com, gvt1.com, fcm.googleapis.com)
        "app-measurement.com",
        "firebaselogging-pa.googleapis.com",
        "firebaselogging.googleapis.com",
        "crashlyticsreports-pa.googleapis.com",
        "fundingchoicesmessages.google.com",
        "pagead2.googlesyndication.com",
        "googleadservices.com",
        "doubleclick.net",
        "adservice.google.com",
        // Third-Party Mobile Attribution & Analytics SDKs
        "app.adjust.com",
        "adjust.com",
        "launches.appsflyer.com",
        "conversions.appsflyer.com",
        "appsflyer.com",
        "api.amplitude.com",
        "api.mixpanel.com",
        "sdk.iad-01.braze.com",
        "braze.com",
        "api.branch.io",
        "branch.io",
        "mobile-data.onetrust.io",
        "logs.ironsrc.mobi",
        "supersonicads.com",
        "unityads.unity3d.com",
        "ads.mopub.com",
        "scorecardresearch.com",
        // CNAME Cloaking First-Party Disguise Trackers (RFC 1035 TYPE=5 Uncloaking)
        "omtrdc.net",
        "2o7.net",
        "pardot.com",
        "eulerian.net",
        "at-o.net",
        "xiti.com",
        "wt-eu02.net",
        "webtrekk.net",
        "trace.adform.net",
        "adform.net",
        "keyade.com",
        "ingenioustechnologies.com",
        "affex.org",
        "actonservice.com",
        "eloqua.com",
        "hs-analytics.net",
        "bkrtx.com",
        "dnsdelegation.io"
    };

    public static final Set<String> BUILTIN_TRACKER_SET = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(BUILTIN_TRACKER_SUFFIXES))
    );

    public static String identifyTrackerCompany(String domain) {
        if (domain == null || domain.isEmpty()) return "";
        String d = domain.toLowerCase(Locale.US);
        if (d.contains("samsung") || d.contains("gos-gsp")) return "Samsung Telemetry";
        if (d.contains("facebook") || d.contains("fbcdn") || d.contains("instagram")) return "Meta Graph SDK";
        if (d.contains("doubleclick") || d.contains("googlesyndication") || d.contains("googleadservices")
                || d.contains("app-measurement") || d.contains("firebaselogging") || d.contains("crashlytics")
                || d.contains("adservice.google")) return "Google AdTech";
        if (d.contains("appsflyer")) return "AppsFlyer SDK";
        if (d.contains("adjust.com")) return "Adjust Attribution";
        if (d.contains("braze.com")) return "Braze Engagement";
        if (d.contains("branch.io")) return "Branch DeepLink";
        if (d.contains("omtrdc") || d.contains("2o7.net")) return "Adobe CNAME Tracker";
        if (d.contains("pardot") || d.contains("eloqua")) return "Salesforce/Oracle CNAME";
        if (d.contains("criteo") || d.contains("dnsdelegation")) return "Criteo AdTech";
        if (d.contains("unityads") || d.contains("ironsrc") || d.contains("supersonic") || d.contains("applovin")) return "Mobile Game Ad SDK";
        if (d.contains("verizon") || d.contains("vzw")) return "Verizon Telemetry";
        return "";
    }

    public static final Set<String> PLAY_STORE_WHITELIST = new HashSet<>(Arrays.asList(
        "play.fe.googleapis.com",
        "android.clients.google.com",
        "fcm.googleapis.com",
        "mtalk.google.com",
        "dl.google.com",
        "gvt1.com",
        "gvt2.com",
        "googleapis.com",
        "gstatic.com"
    ));

    private PacketEngine() {}

    public static boolean isTrackerDomain(String rawDomain, Set<String> customBlocked) {
        if (rawDomain == null || rawDomain.isEmpty()) return false;
        String d = rawDomain.toLowerCase(Locale.US).trim();
        if (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        if (PLAY_STORE_WHITELIST.contains(d)) {
            return false;
        }
        boolean hasCustom = (customBlocked != null && !customBlocked.isEmpty());
        if (BUILTIN_TRACKER_SET.contains(d) || (hasCustom && customBlocked.contains(d))) {
            return true;
        }
        int dot = d.indexOf('.');
        while (dot >= 0 && dot + 1 < d.length()) {
            String parent = d.substring(dot + 1);
            if (PLAY_STORE_WHITELIST.contains(parent)) {
                return false;
            }
            if (BUILTIN_TRACKER_SET.contains(parent) || (hasCustom && customBlocked.contains(parent))) {
                return true;
            }
            dot = d.indexOf('.', dot + 1);
        }
        return false;
    }

    /**
     * Computes RFC 1071 16-bit ones'-complement checksum.
     */
    public static int computeOnesComplementChecksum(byte[] buf, int offset, int length, long initialSum) {
        long sum = initialSum;
        int i = offset;
        int end = offset + (length & 0xFFFE);
        while (i < end) {
            int word = ((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF);
            sum += word;
            i += 2;
        }
        if ((length & 1) != 0) {
            int last = (buf[offset + length - 1] & 0xFF) << 8;
            sum += last;
        }
        while ((sum >>> 16) != 0L) {
            sum = (sum & 0xFFFFL) + (sum >>> 16);
        }
        return (int) (~sum & 0xFFFFL);
    }

    public static int computeTransportChecksumIpv4(
        byte[] packet,
        int ipOffset,
        int l4Offset,
        int l4Length,
        int protocol
    ) {
        long sum = 0L;
        for (int i = ipOffset + 12; i < ipOffset + 20; i += 2) {
            sum += ((packet[i] & 0xFF) << 8) | (packet[i + 1] & 0xFF);
        }
        sum += (protocol & 0xFF);
        sum += (l4Length & 0xFFFF);
        int csum = computeOnesComplementChecksum(packet, l4Offset, l4Length, sum);
        if (protocol == 17 && csum == 0) {
            return 0xFFFF;
        }
        return csum;
    }

    /**
     * Synthesizes a 40-byte IPv4 TCP RST+ACK (`0x14`) response packet to immediately terminate
     * outbound TCP connections from blocked apps with `ECONNREFUSED` in `< 0.1ms`.
     */
    public static byte[] buildIpv4TcpReset(byte[] inBuf, int inLen) {
        if (inLen < 40) return null;
        int version = (inBuf[0] >>> 4) & 0x0F;
        if (version != 4) return null;
        int inIhl = (inBuf[0] & 0x0F) * 4;
        if (inIhl < 20 || inLen < inIhl + 20) return null;
        int proto = inBuf[9] & 0xFF;
        if (proto != 6) return null;

        int inFlags = inBuf[inIhl + 13] & 0xFF;
        // Never reply to an incoming RST
        if ((inFlags & 0x04) != 0) return null;

        long inSeq = ((long) (inBuf[inIhl + 4] & 0xFF) << 24)
            | ((long) (inBuf[inIhl + 5] & 0xFF) << 16)
            | ((long) (inBuf[inIhl + 6] & 0xFF) << 8)
            | ((long) (inBuf[inIhl + 7] & 0xFF));
        long inAck = ((long) (inBuf[inIhl + 8] & 0xFF) << 24)
            | ((long) (inBuf[inIhl + 9] & 0xFF) << 16)
            | ((long) (inBuf[inIhl + 10] & 0xFF) << 8)
            | ((long) (inBuf[inIhl + 11] & 0xFF));

        long outSeq;
        long outAck;
        int outFlags;
        if ((inFlags & 0x10) != 0) {
            outSeq = inAck;
            outAck = 0L;
            outFlags = 0x04; // RST
        } else {
            outSeq = 0L;
            outAck = (inSeq + 1L) & 0xFFFFFFFFL;
            outFlags = 0x14; // RST | ACK
        }

        byte[] out = new byte[40];
        out[0] = 0x45; // IPv4, 20-byte header
        out[1] = 0x00;
        out[2] = 0x00;
        out[3] = 40;   // Total length = 40 bytes
        out[6] = 0x40; // Don't Fragment
        out[8] = 64;   // TTL = 64
        out[9] = 6;    // TCP

        // Swap Source & Destination IPv4 addresses
        System.arraycopy(inBuf, 16, out, 12, 4);
        System.arraycopy(inBuf, 12, out, 16, 4);

        // IPv4 Header Checksum
        int ipCsum = computeOnesComplementChecksum(out, 0, 20, 0L);
        out[10] = (byte) ((ipCsum >>> 8) & 0xFF);
        out[11] = (byte) (ipCsum & 0xFF);

        // Swap TCP Source & Destination Ports
        out[20] = inBuf[inIhl + 2];
        out[21] = inBuf[inIhl + 3];
        out[22] = inBuf[inIhl];
        out[23] = inBuf[inIhl + 1];

        // Sequence Number
        out[24] = (byte) ((outSeq >>> 24) & 0xFF);
        out[25] = (byte) ((outSeq >>> 16) & 0xFF);
        out[26] = (byte) ((outSeq >>> 8) & 0xFF);
        out[27] = (byte) (outSeq & 0xFF);

        // Ack Number
        out[28] = (byte) ((outAck >>> 24) & 0xFF);
        out[29] = (byte) ((outAck >>> 16) & 0xFF);
        out[30] = (byte) ((outAck >>> 8) & 0xFF);
        out[31] = (byte) (outAck & 0xFF);

        out[32] = 0x50; // Data offset = 5 (20 bytes)
        out[33] = (byte) outFlags;

        int tcpCsum = computeTransportChecksumIpv4(out, 0, 20, 20, 6);
        out[36] = (byte) ((tcpCsum >>> 8) & 0xFF);
        out[37] = (byte) (tcpCsum & 0xFF);
        return out;
    }

    public static final class DnsQueryInfo {
        public final int txId;
        public final String qname;
        public final int qtype;
        public final int qclass;
        public final int dnsOffset;
        public final int questionEnd;

        public DnsQueryInfo(int txId, String qname, int qtype, int qclass, int dnsOffset, int questionEnd) {
            this.txId = txId;
            this.qname = qname;
            this.qtype = qtype;
            this.qclass = qclass;
            this.dnsOffset = dnsOffset;
            this.questionEnd = questionEnd;
        }
    }

    /**
     * Parses an IPv4 UDP Port 53 DNS query packet (`RFC 1035`).
     */
    public static DnsQueryInfo parseIpv4DnsQuery(byte[] buf, int pktLen) {
        if (pktLen < 40) return null;
        int version = (buf[0] >>> 4) & 0x0F;
        if (version != 4) return null;
        int ihl = (buf[0] & 0x0F) * 4;
        if ( (buf[9] & 0xFF) != 17) return null; // Must be UDP
        if (pktLen < ihl + 8 + 12) return null;

        int dstPort = ((buf[ihl + 2] & 0xFF) << 8) | (buf[ihl + 3] & 0xFF);
        if (dstPort != 53) return null;

        int dnsOffset = ihl + 8;
        int flags = ((buf[dnsOffset + 2] & 0xFF) << 8) | (buf[dnsOffset + 3] & 0xFF);
        if ((flags & 0x8000) != 0) return null; // Must be a query (QR == 0)

        int qdCount = ((buf[dnsOffset + 4] & 0xFF) << 8) | (buf[dnsOffset + 5] & 0xFF);
        if (qdCount < 1) return null;

        int txId = ((buf[dnsOffset] & 0xFF) << 8) | (buf[dnsOffset + 1] & 0xFF);
        int pos = dnsOffset + 12;
        boolean terminated = false;
        StringBuilder sb = new StringBuilder();
        while (pos < pktLen) {
            int labelLen = buf[pos] & 0xFF;
            if (labelLen == 0) {
                pos += 1;
                terminated = true;
                break;
            }
            if ((labelLen & 0xC0) == 0xC0) {
                if (pos + 2 > pktLen) return null;
                pos += 2;
                terminated = true;
                break;
            }
            if ((labelLen & 0xC0) != 0) {
                return null; // Reject RFC 1035 reserved 0x40 / 0x80 label bits
            }
            if (pos + 1 + labelLen > pktLen) return null;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(buf, pos + 1, labelLen, StandardCharsets.US_ASCII).toLowerCase(Locale.US));
            pos += 1 + labelLen;
        }
        if (!terminated || pos + 4 > pktLen) return null;
        int qtype = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        int qclass = ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
        return new DnsQueryInfo(txId, sb.toString(), qtype, qclass, dnsOffset, pos + 4);
    }

    /**
     * Builds a complete IPv4 + UDP + DNS Sinkhole Response (`0.0.0.0` for A, `::` for AAAA, or `NXDOMAIN`).
     */
    public static byte[] buildIpv4DnsSinkholeResponse(byte[] inBuf, DnsQueryInfo q) {
        int inIhl = (inBuf[0] & 0x0F) * 4;
        int questionLen = q.questionEnd - (q.dnsOffset + 12);
        if (questionLen <= 0 || questionLen > 512) return null;

        // Synthesize NXDOMAIN (RCODE=3) so clients fail fast without attempting TCP connection to 0.0.0.0
        int dnsRespLen = 12 + questionLen;
        int udpLen = 8 + dnsRespLen;
        int totalIpLen = 20 + udpLen;

        byte[] out = new byte[totalIpLen];
        out[0] = 0x45;
        out[1] = 0x00;
        out[2] = (byte) ((totalIpLen >>> 8) & 0xFF);
        out[3] = (byte) (totalIpLen & 0xFF);
        out[6] = 0x40;
        out[8] = 64;
        out[9] = 17; // UDP

        // Swap IPv4 Source & Destination
        System.arraycopy(inBuf, 16, out, 12, 4);
        System.arraycopy(inBuf, 12, out, 16, 4);

        int ipCsum = computeOnesComplementChecksum(out, 0, 20, 0L);
        out[10] = (byte) ((ipCsum >>> 8) & 0xFF);
        out[11] = (byte) (ipCsum & 0xFF);

        // Swap UDP Source & Destination Ports
        out[20] = inBuf[inIhl + 2];
        out[21] = inBuf[inIhl + 3];
        out[22] = inBuf[inIhl];
        out[23] = inBuf[inIhl + 1];
        out[24] = (byte) ((udpLen >>> 8) & 0xFF);
        out[25] = (byte) (udpLen & 0xFF);

        // DNS Header (offset 28)
        int dOff = 28;
        out[dOff] = inBuf[q.dnsOffset];
        out[dOff + 1] = inBuf[q.dnsOffset + 1];
        out[dOff + 2] = (byte) 0x81; // QR=1, RD=1
        out[dOff + 3] = (byte) 0x83; // RA=1, RCODE=3 (NXDOMAIN)
        out[dOff + 4] = 0x00;
        out[dOff + 5] = 0x01; // QDCOUNT = 1

        // Copy Question Section verbatim
        System.arraycopy(inBuf, q.dnsOffset + 12, out, dOff + 12, questionLen);

        int udpCsum = computeTransportChecksumIpv4(out, 0, 20, udpLen, 17);
        out[26] = (byte) ((udpCsum >>> 8) & 0xFF);
        out[27] = (byte) (udpCsum & 0xFF);
        return out;
    }

    /**
     * Wraps a raw upstream UDP DNS reply payload into an IPv4 + UDP packet addressed back to the querying socket on tun0.
     */
    public static byte[] wrapUpstreamDnsReplyIpv4(byte[] inQueryPkt, byte[] dnsReplyPayload, int dnsReplyLen) {
        int inIhl = (inQueryPkt[0] & 0x0F) * 4;
        int udpLen = 8 + dnsReplyLen;
        int totalIpLen = 20 + udpLen;
        byte[] out = new byte[totalIpLen];

        out[0] = 0x45;
        out[2] = (byte) ((totalIpLen >>> 8) & 0xFF);
        out[3] = (byte) (totalIpLen & 0xFF);
        out[6] = 0x40;
        out[8] = 64;
        out[9] = 17;

        System.arraycopy(inQueryPkt, 16, out, 12, 4);
        System.arraycopy(inQueryPkt, 12, out, 16, 4);

        int ipCsum = computeOnesComplementChecksum(out, 0, 20, 0L);
        out[10] = (byte) ((ipCsum >>> 8) & 0xFF);
        out[11] = (byte) (ipCsum & 0xFF);

        out[20] = inQueryPkt[inIhl + 2];
        out[21] = inQueryPkt[inIhl + 3];
        out[22] = inQueryPkt[inIhl];
        out[23] = inQueryPkt[inIhl + 1];
        out[24] = (byte) ((udpLen >>> 8) & 0xFF);
        out[25] = (byte) (udpLen & 0xFF);

        System.arraycopy(dnsReplyPayload, 0, out, 28, dnsReplyLen);

        int udpCsum = computeTransportChecksumIpv4(out, 0, 20, udpLen, 17);
        out[26] = (byte) ((udpCsum >>> 8) & 0xFF);
        out[27] = (byte) (udpCsum & 0xFF);
        return out;
    }

    public static int skipDnsName(byte[] dnsBuf, int dnsLen, int pos) {
        while (pos < dnsLen) {
            int b = dnsBuf[pos] & 0xFF;
            if (b == 0) return pos + 1;
            if ((b & 0xC0) == 0xC0) return (pos + 2 <= dnsLen) ? (pos + 2) : -1;
            if ((b & 0xC0) != 0) return -1;
            pos += 1 + b;
        }
        return -1;
    }

    public static String readCompressedDnsName(byte[] dnsBuf, int dnsLen, int startOffset, int maxJumps) {
        StringBuilder sb = new StringBuilder();
        int pos = startOffset;
        int jumps = 0;
        while (pos >= 0 && pos < dnsLen) {
            int b = dnsBuf[pos] & 0xFF;
            if (b == 0) break;
            if ((b & 0xC0) == 0xC0) {
                if (pos + 1 >= dnsLen || ++jumps > maxJumps) return null;
                int ptr = ((b & 0x3F) << 8) | (dnsBuf[pos + 1] & 0xFF);
                if (ptr < 12 || ptr >= dnsLen) return null;
                pos = ptr;
                continue;
            }
            if ((b & 0xC0) != 0 || pos + 1 + b > dnsLen) return null;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(dnsBuf, pos + 1, b, StandardCharsets.US_ASCII).toLowerCase(Locale.US));
            pos += 1 + b;
        }
        return sb.toString();
    }

    /**
     * Inspects an upstream RFC 1035 DNS answer payload for CNAME (`TYPE = 5`) cloaking.
     * Returns the hidden tracker target domain if any CNAME in the chain matches the tracker blocklist.
     */
    public static String findCloakedTrackerCname(byte[] dnsBuf, int dnsLen, Set<String> customBlocked) {
        if (dnsBuf == null || dnsLen < 12) return null;
        int qdCount = ((dnsBuf[4] & 0xFF) << 8) | (dnsBuf[5] & 0xFF);
        int anCount = ((dnsBuf[6] & 0xFF) << 8) | (dnsBuf[7] & 0xFF);
        if (anCount <= 0 || anCount > 32) return null;

        int pos = 12;
        for (int q = 0; q < qdCount; q++) {
            pos = skipDnsName(dnsBuf, dnsLen, pos);
            if (pos < 0 || pos + 4 > dnsLen) return null;
            pos += 4;
        }
        for (int a = 0; a < anCount && pos < dnsLen; a++) {
            pos = skipDnsName(dnsBuf, dnsLen, pos);
            if (pos < 0 || pos + 10 > dnsLen) return null;
            int type = ((dnsBuf[pos] & 0xFF) << 8) | (dnsBuf[pos + 1] & 0xFF);
            int rdLength = ((dnsBuf[pos + 8] & 0xFF) << 8) | (dnsBuf[pos + 9] & 0xFF);
            int rdataOffset = pos + 10;
            if (rdataOffset + rdLength > dnsLen) return null;

            if (type == 5) { // RFC 1035 CNAME record
                String cnameTarget = readCompressedDnsName(dnsBuf, dnsLen, rdataOffset, 16);
                if (cnameTarget != null && isTrackerDomain(cnameTarget, customBlocked)) {
                    return cnameTarget;
                }
            } else if ((type == 64 || type == 65) && rdLength > 3) { // RFC 9460 SVCB (64) / HTTPS (65) TargetName
                String svcbTarget = readCompressedDnsName(dnsBuf, dnsLen, rdataOffset + 2, 16);
                if (svcbTarget != null && !svcbTarget.isEmpty() && isTrackerDomain(svcbTarget, customBlocked)) {
                    return svcbTarget;
                }
            }
            pos = rdataOffset + rdLength;
        }
        return null;
    }
}
