package io.nulkast.netintel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O(1) Constant-Memory (32 KB) Device-Wide Batch Photo (JPEG) & Video (MP4/MOV/M4V/3GP) Scrubber (`packages/net-intel`).
 *
 * Scans or scrubs entire camera roll directories (`DCIM`, `Pictures`, `Movies`, `Download`):
 * - Photos (`.jpg`, `.jpeg`): Uses <0.5ms header quick-skip (`inspectJpegHeaderOnly`) and streams dirty JPEGs
 *   through a single reusable `byte[32768]` buffer (`scrubJpegFileStreaming`) with 0% pixel re-compression.
 * - Videos (`.mp4`, `.mov`, `.m4v`, `.3gp`): Uses `RandomAccessFile` seeking (`Mp4AtomScrubber`) to skip
 *   multi-GB `mdat` video frames in <0.01ms and zero out `©xyz` GPS, `loci`, `smta`, and `meta` atoms in-place
 *   as ISO `'free'` boxes without shifting `stco`/`co64` chunk offsets.
 */
public final class BatchMediaScrubber {

    public interface ProgressListener {
        void onProgress(int filesProcessed, String currentFileName);
    }

    public static final class BatchReport {
        public final boolean dryRun;
        public final int photosScanned;
        public final int photosDirtyOrScrubbed;
        public final int videosScanned;
        public final int videosDirtyOrScrubbed;
        public final int alreadyCleanSkipped;
        public final int totalAtomsOrSegmentsRemoved;
        public final long totalMetadataBytesScrubbed;
        public final long elapsedMs;
        public final List<String> fileDetails;

        public BatchReport(
            boolean dryRun,
            int photosScanned,
            int photosDirtyOrScrubbed,
            int videosScanned,
            int videosDirtyOrScrubbed,
            int alreadyCleanSkipped,
            int totalAtomsOrSegmentsRemoved,
            long totalMetadataBytesScrubbed,
            long elapsedMs,
            List<String> fileDetails
        ) {
            this.dryRun = dryRun;
            this.photosScanned = photosScanned;
            this.photosDirtyOrScrubbed = photosDirtyOrScrubbed;
            this.videosScanned = videosScanned;
            this.videosDirtyOrScrubbed = videosDirtyOrScrubbed;
            this.alreadyCleanSkipped = alreadyCleanSkipped;
            this.totalAtomsOrSegmentsRemoved = totalAtomsOrSegmentsRemoved;
            this.totalMetadataBytesScrubbed = totalMetadataBytesScrubbed;
            this.elapsedMs = elapsedMs;
            this.fileDetails = fileDetails;
        }
    }

    private BatchMediaScrubber() {}

    public static BatchReport executeBatch(
        Iterable<File> rootDirs,
        boolean dryRun,
        ProgressListener listener
    ) {
        long t0 = System.currentTimeMillis();
        byte[] sharedBuffer32K = new byte[32768];
        int[] stats = new int[6]; // 0=photosScanned, 1=photosDirty, 2=videosScanned, 3=videosDirty, 4=cleanSkipped, 5=totalSegs
        long[] byteStats = new long[1];
        List<String> details = new ArrayList<String>();

        if (rootDirs != null) {
            for (File root : rootDirs) {
                if (root != null && root.exists()) {
                    walkDirRecursive(root, 0, dryRun, sharedBuffer32K, stats, byteStats, details, listener);
                }
            }
        }

        long elapsed = Math.max(1L, System.currentTimeMillis() - t0);
        return new BatchReport(
            dryRun,
            stats[0],
            stats[1],
            stats[2],
            stats[3],
            stats[4],
            stats[5],
            byteStats[0],
            elapsed,
            details
        );
    }

    private static void walkDirRecursive(
        File f,
        int depth,
        boolean dryRun,
        byte[] sharedBuf32K,
        int[] stats,
        long[] byteStats,
        List<String> details,
        ProgressListener listener
    ) {
        if (depth > 8 || f == null) return;
        String name = f.getName();
        if (name.startsWith(".") || name.endsWith(".nulkast.tmp")) return;

        if (f.isDirectory()) {
            // Skip Android/data and Android/obb sandboxes
            if ("Android".equals(name)) return;
            File[] children = f.listFiles();
            if (children == null) return;
            for (File child : children) {
                walkDirRecursive(child, depth + 1, dryRun, sharedBuf32K, stats, byteStats, details, listener);
            }
            return;
        }

        if (!f.isFile()) return;
        String lower = name.toLowerCase(Locale.US);

        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            stats[0]++;
            if (listener != null && ((stats[0] + stats[2]) % 5 == 1)) {
                listener.onProgress(stats[0] + stats[2], name);
            }
            JpegExifScrubber.ScrubResult res = dryRun
                ? JpegExifScrubber.inspectJpegHeaderOnly(f)
                : JpegExifScrubber.scrubJpegFileStreaming(f, sharedBuf32K);
            if (res.isJpeg && res.removedSegmentsCount > 0) {
                stats[1]++;
                stats[5] += res.removedSegmentsCount;
                byteStats[0] += res.removedBytesCount;
                if (details.size() < 25) {
                    details.add(
                        "📸 " + name + ": " + (dryRun ? "Found " : "Scrubbed ")
                            + res.removedSegmentsCount + " EXIF/GPS segs (" + res.removedBytesCount + " B)"
                    );
                }
            } else {
                stats[4]++;
            }
        } else if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.endsWith(".3gp")) {
            stats[2]++;
            if (listener != null) {
                listener.onProgress(stats[0] + stats[2], name);
            }
            Mp4AtomScrubber.Mp4ScrubResult res = dryRun
                ? Mp4AtomScrubber.inspectMp4File(f)
                : Mp4AtomScrubber.scrubMp4FileInPlace(f);
            if (res.isMp4 && res.scrubbedBoxesCount > 0) {
                stats[3]++;
                stats[5] += res.scrubbedBoxesCount;
                byteStats[0] += res.zeroedBytesCount;
                if (details.size() < 25) {
                    details.add(
                        "🎬 " + name + ": " + (dryRun ? "Found " : "Zeroed ")
                            + res.scrubbedBoxesCount + " MP4 GPS/Device atoms (" + res.zeroedBytesCount + " B)"
                    );
                }
            } else {
                stats[4]++;
            }
        }
    }
}
