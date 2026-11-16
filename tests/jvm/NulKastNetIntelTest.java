import io.nulkast.netintel.AppIntelligence;
import io.nulkast.netintel.BatchMediaScrubber;
import io.nulkast.netintel.JpegExifScrubber;
import io.nulkast.netintel.Mp4AtomScrubber;
import io.nulkast.netintel.PacketEngine;
import io.nulkast.netintel.UrlSanitizer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-JVM Deterministic Unit & Adversarial Binary Vector Test Suite (`tests/jvm/NulKastNetIntelTest.java`).
 * Executes on standard OpenJDK (`java -ea`) in <400ms inside Docker without an Android emulator.
 */
public final class NulKastNetIntelTest {

    private static int passed = 0;

    private static void check(boolean cond, String name) {
        if (!cond) {
            throw new AssertionError("FAILED ASSERTION: " + name);
        }
        passed++;
    }

    public static void main(String[] args) {
        testOnesComplementChecksumAndUdpRule();
        testIpv4TcpResetSynthesisAndLoopGuard();
        testDnsQueryParserAndAdversarialVectors();
        testDnsCnameCloakingAndPointerCycles();
        testTrackerDomainAndCompanyAttribution();
        testJpegExifScrubberAndBinaryEdgeCases();
        testMp4AtomScrubberAndBatchVaultScrubber();
        testUrlSanitizerVectors();
        testAppIntelligenceAndRulepackParser();

        System.out.println("PASS: NulKastNetIntelTest completed " + passed + " deterministic JVM assertions.");
    }

    private static void testOnesComplementChecksumAndUdpRule() {
        // 1. High-bit (0xFF / >= 0x80) sign-extension safety check
        byte[] hdr = new byte[] {
            (byte) 0x45, 0x00, 0x00, 0x28,
            (byte) 0xFE, (byte) 0xDC, 0x40, 0x00,
            0x40, 0x06, 0x00, 0x00,
            (byte) 192, (byte) 168, (byte) 200, (byte) 254,
            (byte) 172, (byte) 217, (byte) 164, (byte) 110
        };
        int csum = PacketEngine.computeOnesComplementChecksum(hdr, 0, 20, 0L);
        hdr[10] = (byte) ((csum >>> 8) & 0xFF);
        hdr[11] = (byte) (csum & 0xFF);
        int verify = PacketEngine.computeOnesComplementChecksum(hdr, 0, 20, 0L);
        check(verify == 0, "RFC 1071 IPv4 header checksum verifies to 0x0000 with >=0x80 bytes");

        // 2. Odd-length buffer padding check
        byte[] odd = new byte[] { (byte) 0xFF, (byte) 0x00, (byte) 0x80 };
        int oddCsum = PacketEngine.computeOnesComplementChecksum(odd, 0, odd.length, 0L);
        check(oddCsum >= 0 && oddCsum <= 0xFFFF, "Odd-length checksum remains within 16-bit unsigned range");
    }

    private static void testIpv4TcpResetSynthesisAndLoopGuard() {
        // Construct a 40-byte IPv4 + TCP SYN packet (src 10.215.173.1:54321 -> dst 157.240.22.35:443, seq=0x80000001)
        byte[] syn = new byte[40];
        syn[0] = 0x45;
        syn[2] = 0x00; syn[3] = 40;
        syn[8] = 64;
        syn[9] = 6; // TCP
        syn[12] = 10; syn[13] = (byte) 215; syn[14] = (byte) 173; syn[15] = 1;
        syn[16] = (byte) 157; syn[17] = (byte) 240; syn[18] = 22; syn[19] = 35;
        // TCP srcPort=54321 (0xD431), dstPort=443 (0x01BB)
        syn[20] = (byte) 0xD4; syn[21] = (byte) 0x31;
        syn[22] = (byte) 0x01; syn[23] = (byte) 0xBB;
        // Seq = 0x80000001 (tests negative 32-bit signed int sign-extension)
        syn[24] = (byte) 0x80; syn[25] = 0x00; syn[26] = 0x00; syn[27] = 0x01;
        syn[32] = 0x50; // Data offset = 5 (20 bytes)
        syn[33] = 0x02; // SYN flag

        byte[] rst = PacketEngine.buildIpv4TcpReset(syn, syn.length);
        check(rst != null && rst.length == 40, "buildIpv4TcpReset returns 40-byte IPv4 RST+ACK packet");
        check((rst[33] & 0xFF) == 0x14, "Synthesized TCP flags equal RST+ACK (0x14)");
        // Verify swapped ports
        check(((rst[20] & 0xFF) << 8 | (rst[21] & 0xFF)) == 443, "TCP RST swaps source port to 443");
        check(((rst[22] & 0xFF) << 8 | (rst[23] & 0xFF)) == 54321, "TCP RST swaps destination port to 54321");
        // Verify ACK number == incoming SEQ + 1 (0x80000002)
        long ackNum = ((rst[28] & 0xFFL) << 24) | ((rst[29] & 0xFFL) << 16) | ((rst[30] & 0xFFL) << 8) | (rst[31] & 0xFFL);
        check(ackNum == 0x80000002L, "TCP RST increments high-bit sequence number to 0x80000002");

        // Verify RST loop prevention: if incoming packet already has RST (0x04), must return null
        syn[33] = 0x04;
        check(PacketEngine.buildIpv4TcpReset(syn, syn.length) == null, "Incoming TCP RST never triggers another RST");
    }

    private static void testDnsQueryParserAndAdversarialVectors() {
        byte[] pkt = buildSampleDnsQueryPacket("Graph.Facebook.com", 1);
        PacketEngine.DnsQueryInfo q = PacketEngine.parseIpv4DnsQuery(pkt, pkt.length);
        check(q != null, "Valid IPv4 UDP/53 DNS query parses cleanly");
        check("graph.facebook.com".equals(q.qname), "DNS QNAME normalizes to lowercase graph.facebook.com");
        check(q.qtype == 1 && q.qclass == 1, "DNS QTYPE=A (1) and QCLASS=IN (1) parsed");

        // Synthesize NXDOMAIN sinkhole response
        byte[] nx = PacketEngine.buildIpv4DnsSinkholeResponse(pkt, q);
        check(nx != null && nx.length == pkt.length, "NXDOMAIN sinkhole response matches query packet length");
        check((nx[28 + 3] & 0x0F) == 3, "Sinkhole response sets RCODE=3 (NXDOMAIN)");

        // Adversarial 1: Truncated packet
        check(PacketEngine.parseIpv4DnsQuery(pkt, 25) == null, "Truncated DNS packet (<40 bytes) returns null");

        // Adversarial 2: Reserved RFC 1035 label bits (0x40 = 01000000)
        byte[] badBits = pkt.clone();
        badBits[28 + 12] = 0x40;
        check(PacketEngine.parseIpv4DnsQuery(badBits, badBits.length) == null, "Reserved RFC 1035 0x40 label bits rejected");

        // Adversarial 3: Truncated label length exceeding packet length
        byte[] overLen = pkt.clone();
        overLen[28 + 12] = 60;
        check(PacketEngine.parseIpv4DnsQuery(overLen, overLen.length) == null, "Label length exceeding packet boundary rejected");
    }

    private static void testDnsCnameCloakingAndPointerCycles() {
        // 1. Self-pointer compression loop (0xC0 0x0C at offset 12)
        byte[] selfLoop = new byte[20];
        selfLoop[12] = (byte) 0xC0;
        selfLoop[13] = 0x0C;
        check(PacketEngine.readCompressedDnsName(selfLoop, selfLoop.length, 12, 16) == null, "Self-referencing 0xC00C DNS pointer loop aborts cleanly");

        // 2. Mutual pointer cycle (offset 12 -> 14 -> 12)
        byte[] mutualLoop = new byte[20];
        mutualLoop[12] = (byte) 0xC0; mutualLoop[13] = 0x0E;
        mutualLoop[14] = (byte) 0xC0; mutualLoop[15] = 0x0C;
        check(PacketEngine.readCompressedDnsName(mutualLoop, mutualLoop.length, 12, 16) == null, "Mutual 0xC0 pointer cycle aborts cleanly");

        // 3. Out-of-bounds pointer (0xC0 0xFF)
        byte[] oobPtr = new byte[20];
        oobPtr[12] = (byte) 0xC0; oobPtr[13] = (byte) 0xFF;
        check(PacketEngine.readCompressedDnsName(oobPtr, oobPtr.length, 12, 16) == null, "Out-of-bounds 0xC0FF compression pointer rejected");

        // 4. Valid CNAME cloaking response: metrics.example.com -> CNAME -> tracker.omtrdc.net
        ByteArrayOutputStream dns = new ByteArrayOutputStream();
        // DNS Header: ID=0x1234, Flags=0x8180, QDCOUNT=1, ANCOUNT=1
        dns.write(new byte[] { 0x12, 0x34, (byte) 0x81, (byte) 0x80, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00 }, 0, 12);
        // Question: metrics.example.com A IN
        writeDnsLabels(dns, "metrics.example.com");
        dns.write(new byte[] { 0x00, 0x01, 0x00, 0x01 }, 0, 4);
        // Answer 1: pointer to 0x0C (metrics.example.com), TYPE=5 (CNAME), CLASS=1, TTL=60
        dns.write(new byte[] { (byte) 0xC0, 0x0C, 0x00, 0x05, 0x00, 0x01, 0x00, 0x00, 0x00, 0x3C }, 0, 10);
        ByteArrayOutputStream rdata = new ByteArrayOutputStream();
        writeDnsLabels(rdata, "cname.omtrdc.net");
        byte[] rdBytes = rdata.toByteArray();
        dns.write((rdBytes.length >>> 8) & 0xFF);
        dns.write(rdBytes.length & 0xFF);
        dns.write(rdBytes, 0, rdBytes.length);

        byte[] respBytes = dns.toByteArray();
        String cloaked = PacketEngine.findCloakedTrackerCname(respBytes, respBytes.length, Collections.<String>emptySet());
        check("cname.omtrdc.net".equals(cloaked), "findCloakedTrackerCname unmasks Adobe Analytics CNAME cloaking (cname.omtrdc.net)");
    }

    private static void testTrackerDomainAndCompanyAttribution() {
        Set<String> custom = new HashSet<String>(Arrays.asList("custom-spy.example.org"));
        check(PacketEngine.isTrackerDomain("graph.facebook.com", custom), "Blocks graph.facebook.com");
        check(PacketEngine.isTrackerDomain("sub.custom-spy.example.org", custom), "Blocks subdomain of custom rule");
        check(!PacketEngine.isTrackerDomain("play.fe.googleapis.com", custom), "Preserves Google Play Store play.fe.googleapis.com");
        check(!PacketEngine.isTrackerDomain("gvt1.com", custom), "Preserves Play Store APK CDN gvt1.com");
        check("Meta Graph SDK".equals(PacketEngine.identifyTrackerCompany("graph.facebook.com")), "Resolves Meta company attribution");
        check("Samsung Telemetry".equals(PacketEngine.identifyTrackerCompany("vas.samsungapps.com")), "Resolves Samsung Telemetry attribution");
    }

    private static void testJpegExifScrubberAndBinaryEdgeCases() {
        // 1. Sample JPEG with APP1 EXIF+GPS, APP1 XMP, and COM Comment
        byte[] sample = JpegExifScrubber.createSampleExifJpeg();
        JpegExifScrubber.ScrubResult res = JpegExifScrubber.inspectAndScrubJpeg(sample);
        check(res.isJpeg, "Sample JPEG recognized as valid JPEG");
        check(res.removedSegmentsCount == 3, "Stripped all 3 sensitive metadata segments (EXIF+GPS, XMP, COM)");
        check(res.cleanSize < res.originalSize, "Scrubbed JPEG is smaller than original");

        // Re-running scrub on already scrubbed bytes should find 0 sensitive segments
        JpegExifScrubber.ScrubResult secondPass = JpegExifScrubber.inspectAndScrubJpeg(res.cleanBytes);
        check(secondPass.isJpeg && secondPass.removedSegmentsCount == 0, "Idempotent second pass reports 0 remaining EXIF segments");

        // 2. Non-JPEG input passthrough
        byte[] pngHeader = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A };
        JpegExifScrubber.ScrubResult nonJpeg = JpegExifScrubber.inspectAndScrubJpeg(pngHeader);
        check(!nonJpeg.isJpeg && nonJpeg.removedSegmentsCount == 0, "Non-JPEG buffer handled safely without modification");

        // 3. Adversarial: Malformed segment length < 2 (0xFF 0xE1 0x00 0x01) must not underflow
        byte[] badSegLen = new byte[] {
            (byte) 0xFF, (byte) 0xD8,
            (byte) 0xFF, (byte) 0xE1, 0x00, 0x01,
            (byte) 0xFF, (byte) 0xD9
        };
        JpegExifScrubber.ScrubResult badRes = JpegExifScrubber.inspectAndScrubJpeg(badSegLen);
        check(badRes.isJpeg && badRes.cleanBytes.length > 0, "Malformed JPEG segLen=1 handled without underflow exception");

        // 4. Adversarial: Truncated segment length exceeding buffer size
        byte[] truncSeg = new byte[] {
            (byte) 0xFF, (byte) 0xD8,
            (byte) 0xFF, (byte) 0xE1, 0x0F, (byte) 0xFF, 0x45, 0x78
        };
        JpegExifScrubber.ScrubResult truncRes = JpegExifScrubber.inspectAndScrubJpeg(truncSeg);
        check(truncRes.isJpeg && truncRes.cleanBytes.length > 0, "Truncated JPEG segment handled without ArrayIndexOutOfBoundsException");
    }

    private static void testMp4AtomScrubberAndBatchVaultScrubber() {
        try {
            File tmpDir = Files.createTempDirectory("nulkast_batch_test_").toFile();
            File jpgFile = new File(tmpDir, "Camera_GPS_001.jpg");
            FileOutputStream fos1 = new FileOutputStream(jpgFile);
            fos1.write(JpegExifScrubber.createSampleExifJpeg());
            fos1.close();

            File mp4File = new File(tmpDir, "Video_4K_GPS_001.mp4");
            byte[] origMp4 = Mp4AtomScrubber.createSampleGpsMp4Bytes();
            FileOutputStream fos2 = new FileOutputStream(mp4File);
            fos2.write(origMp4);
            fos2.close();

            // 1. Dry-run Batch Scan
            BatchMediaScrubber.BatchReport scanRep = BatchMediaScrubber.executeBatch(
                Collections.singletonList(tmpDir),
                true,
                null
            );
            check(scanRep.photosScanned == 1 && scanRep.photosDirtyOrScrubbed == 1, "Batch dry-run detects 1 dirty JPEG photo");
            check(scanRep.videosScanned == 1 && scanRep.videosDirtyOrScrubbed == 1, "Batch dry-run detects 1 dirty MP4 video");
            check(scanRep.totalAtomsOrSegmentsRemoved == 6, "Batch dry-run detects 3 JPEG segments + 3 MP4 atoms (©xyz, smta, meta)");

            // 2. Execute In-Place & Streaming Batch Scrub
            BatchMediaScrubber.BatchReport scrubRep = BatchMediaScrubber.executeBatch(
                Collections.singletonList(tmpDir),
                false,
                null
            );
            check(scrubRep.photosDirtyOrScrubbed == 1 && scrubRep.videosDirtyOrScrubbed == 1, "Batch scrub sanitizes both JPEG and MP4");
            check(mp4File.length() == origMp4.length, "In-place MP4 'free'-box zeroing preserves exact file byte length (stco/co64 safe)");

            // Verify MP4 GPS coordinates (+00.0000-000.0000) and SM-N976V strings are completely gone from file bytes
            String scrubbedMp4Ascii = new String(Files.readAllBytes(mp4File.toPath()), StandardCharsets.ISO_8859_1);
            check(!scrubbedMp4Ascii.contains("+00.0000") && !scrubbedMp4Ascii.contains("SM-N976V"), "MP4 GPS coordinates and camera model zeroed in-place");
            check(scrubbedMp4Ascii.contains("SIMULATED_HEVC_VIDEO_BITSTREAM_FRAMES"), "MP4 mdat video bitstream preserved 100% untouched");

            // 3. Idempotent Re-Scan: both files must now be quick-skipped as clean
            BatchMediaScrubber.BatchReport afterScan = BatchMediaScrubber.executeBatch(
                Collections.singletonList(tmpDir),
                true,
                null
            );
            check(afterScan.photosDirtyOrScrubbed == 0 && afterScan.videosDirtyOrScrubbed == 0, "Post-scrub scan reports 0 dirty photos or videos");
            check(afterScan.alreadyCleanSkipped == 2, "Post-scrub scan quick-skips both clean files");

            jpgFile.delete();
            mp4File.delete();
            tmpDir.delete();
        } catch (Exception e) {
            throw new AssertionError("testMp4AtomScrubberAndBatchVaultScrubber exception: " + e.getMessage(), e);
        }
    }

    private static void testUrlSanitizerVectors() {
        String dirty = "https://shop.example.com/item?id=9941&utm_source=ig&fbclid=IwAR123&sort=price&gclid=Cj099&_gl=1*abc#specs";
        UrlSanitizer.SanitizeResult r = UrlSanitizer.sanitize(dirty);
        check("https://shop.example.com/item?id=9941&sort=price#specs".equals(r.cleanUrl), "UrlSanitizer keeps id & sort and #specs while stripping trackers");
        check(r.strippedParams.size() == 4, "UrlSanitizer records all 4 stripped tracking parameters");

        // All params are trackers -> '?' should be removed cleanly
        UrlSanitizer.SanitizeResult allDirty = UrlSanitizer.sanitize("https://example.com/post?utm_medium=cpc&igshid=xyz#top");
        check("https://example.com/post#top".equals(allDirty.cleanUrl), "UrlSanitizer removes trailing '?' when all query params are trackers");
    }

    private static void testAppIntelligenceAndRulepackParser() {
        check(AppIntelligence.isProtectedCore("io.nulkast.wall"), "io.nulkast.wall is Tier-0 Protected Core");
        check(AppIntelligence.isProtectedCore("io.nulkast.audit"), "io.nulkast.audit is Tier-0 Protected Core");
        check(AppIntelligence.isProtectedCore("io.nulkast.scrub"), "io.nulkast.scrub is Tier-0 Protected Core");
        check(AppIntelligence.isProtectedCore("com.android.vending"), "Google Play Store is Tier-0 Protected Core");
        check(AppIntelligence.isProtectedCore("com.android.providers.downloads"), "Download Manager is Tier-0 Protected Core");
        check(AppIntelligence.isProtectedCore("com.sec.imsservice"), "Samsung VoLTE IMS is Tier-0 Protected Core");

        // Exodus signature matching & grading
        List<String> trackers = AppIntelligence.matchTrackerComponents(Arrays.asList(
            "com.google.firebase.analytics.FirebaseAnalytics",
            "com.facebook.appevents.AppEventsLogger",
            "com.appsflyer.AppsFlyerLib"
        ));
        check(trackers.size() == 3, "Matches Firebase, Facebook, and AppsFlyer Exodus SDK signatures");
        AppIntelligence.PrivacyGradeReport gradeF = AppIntelligence.computePrivacyGrade(trackers.size(), 4, true);
        check("F".equals(gradeF.grade), "App with 3 tracker SDKs receives Grade F");

        AppIntelligence.PrivacyGradeReport gradeAPlus = AppIntelligence.computePrivacyGrade(0, 0, false);
        check("A+".equals(gradeAPlus.grade), "Zero-internet app with 0 trackers receives Grade A+");

        // Rulepack parser safety: attempts to block com.android.vending or play.fe.googleapis.com must be ignored
        String maliciousHosts = "0.0.0.0 play.fe.googleapis.com\n0.0.0.0 ads.eviltracker.com\n||telemetry.bad.org^\n";
        AppIntelligence.ImportedRulepack pack = AppIntelligence.parseRulepackOrHosts(maliciousHosts);
        check(!pack.domains.contains("play.fe.googleapis.com"), "Rulepack parser refuses to block whitelisted Play Store domain");
        check(pack.domains.contains("ads.eviltracker.com") && pack.domains.contains("telemetry.bad.org"), "Rulepack parser imports hosts & AdGuard syntax");
    }

    private static void writeDnsLabels(ByteArrayOutputStream out, String domain) {
        for (String part : domain.split("\\.")) {
            byte[] b = part.getBytes(StandardCharsets.US_ASCII);
            out.write(b.length);
            out.write(b, 0, b.length);
        }
        out.write(0);
    }

    private static byte[] buildSampleDnsQueryPacket(String domain, int qtype) {
        ByteArrayOutputStream dns = new ByteArrayOutputStream();
        dns.write(new byte[] { 0x1A, 0x2B, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, 0, 12);
        writeDnsLabels(dns, domain);
        dns.write((qtype >>> 8) & 0xFF);
        dns.write(qtype & 0xFF);
        dns.write(0x00);
        dns.write(0x01);
        byte[] dnsPayload = dns.toByteArray();

        int udpLen = 8 + dnsPayload.length;
        int ipLen = 20 + udpLen;
        byte[] pkt = new byte[ipLen];
        pkt[0] = 0x45;
        pkt[2] = (byte) ((ipLen >>> 8) & 0xFF);
        pkt[3] = (byte) (ipLen & 0xFF);
        pkt[8] = 64;
        pkt[9] = 17; // UDP
        pkt[12] = 10; pkt[13] = (byte) 215; pkt[14] = (byte) 173; pkt[15] = 1;
        pkt[16] = 10; pkt[17] = (byte) 215; pkt[18] = (byte) 173; pkt[19] = 2;
        pkt[20] = (byte) 0xC3; pkt[21] = 0x50; // srcPort 50000
        pkt[22] = 0x00; pkt[23] = 53;          // dstPort 53
        pkt[24] = (byte) ((udpLen >>> 8) & 0xFF);
        pkt[25] = (byte) (udpLen & 0xFF);
        System.arraycopy(dnsPayload, 0, pkt, 28, dnsPayload.length);
        return pkt;
    }
}
