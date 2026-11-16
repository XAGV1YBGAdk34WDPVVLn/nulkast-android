package io.nulkast.wall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import io.nulkast.netintel.PacketEngine;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Set;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Dual-Engine NulKast Wall VpnService:
 * 1. Global Split `/32` DNS Sinkhole (`10.215.173.2/32`) inspecting all UDP/53 queries in `<0.1ms`
 *    against built-in + custom tracker domains and per-UID app blocks.
 * 2. Instant 40-Byte IPv4 TCP `RST+ACK` (`0x14`) Synthesizer for any routed blocked app sockets.
 */
public final class NulKastVpnService extends VpnService implements Runnable {
    public static final String ACTION_START = "io.nulkast.wall.ACTION_START";
    public static final String ACTION_STOP = "io.nulkast.wall.ACTION_STOP";
    public static final String ACTION_RELOAD = "io.nulkast.wall.ACTION_RELOAD";

    private static final String CHANNEL_ID = "nulkast_wall_vpn";
    private static final int NOTIF_ID = 8245;
    private static final String UPSTREAM_DNS_IP = "94.140.14.14"; // AdGuard Tracker-Blocking DNS

    private volatile boolean running = false;
    private ParcelFileDescriptor tunInterface = null;
    private Thread vpnThread = null;

    public static void reloadIfRunning(Context ctx) {
        if (!WallStateStore.isVpnActive()) return;
        Intent svc = new Intent(ctx, NulKastVpnService.class);
        svc.setAction(ACTION_RELOAD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(svc);
        } else {
            ctx.startService(svc);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNotification();
        startOrReloadVpn();
        return START_STICKY;
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "NulKast Wall Firewall",
                NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(ch);
        }
        Intent openIntent = new Intent(this, WallMainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder nb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        Notification notif = nb
            .setContentTitle("NulKast Wall Active")
            .setContentText("Zero-Telemetry Per-App Firewall & DNS Sinkhole Running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notif);
        }
    }

    private synchronized void startOrReloadVpn() {
        stopVpnInternal();
        try {
            Builder builder = new Builder()
                .setSession("NulKast Wall")
                .setMtu(1500)
                .addAddress("10.215.173.1", 30)
                .addDnsServer("10.215.173.2")
                .addRoute("10.215.173.2", 32);

            // Prevent self-loop and preserve Carrier VoLTE/Wi-Fi Calling IMS daemons
            try { builder.addDisallowedApplication(getPackageName()); } catch (Throwable ignored) {}
            try { builder.addDisallowedApplication("com.sec.imsservice"); } catch (Throwable ignored) {}
            try { builder.addDisallowedApplication("com.sec.epdg"); } catch (Throwable ignored) {}

            Intent cfgIntent = new Intent(this, WallMainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                cfgIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setConfigureIntent(pi);

            tunInterface = builder.establish();
            if (tunInterface != null) {
                running = true;
                WallStateStore.setVpnActive(this, true);
                WallStateStore.recordEvent(this, "[VPN-UP] NulKast Wall tun0 established (10.215.173.2/32)", true);
                vpnThread = new Thread(this, "NulKastWallTunLoop");
                vpnThread.start();
            }
        } catch (Throwable t) {
            WallStateStore.recordEvent(this, "[VPN-ERR] " + t.getMessage(), true);
            WallStateStore.setVpnActive(this, false);
        }
    }

    private synchronized void stopVpnInternal() {
        running = false;
        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }
        if (tunInterface != null) {
            try { tunInterface.close(); } catch (Throwable ignored) {}
            tunInterface = null;
        }
    }

    private void stopVpn() {
        stopVpnInternal();
        WallStateStore.setVpnActive(this, false);
        WallStateStore.recordEvent(this, "[VPN-DOWN] NulKast Wall stopped", true);
        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void run() {
        ParcelFileDescriptor pfd = this.tunInterface;
        if (pfd == null) return;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        byte[] packet = new byte[32767];

        try (
            FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())
        ) {
            while (running && !Thread.currentThread().isInterrupted()) {
                int len = in.read(packet);
                if (len <= 0) continue;

                int version = (packet[0] >>> 4) & 0x0F;
                if (version != 4 || len < 28) continue;

                int ihl = (packet[0] & 0x0F) * 4;
                int proto = packet[9] & 0xFF;

                // Resolve owning app package via ConnectivityManager.getConnectionOwnerUid
                String ownerPkg = resolveOwnerPackage(cm, packet, ihl, proto);
                boolean appBlocked = WallStateStore.isStealthLockdown(this)
                    || (ownerPkg != null && WallStateStore.isPackageBlocked(this, ownerPkg));

                if (proto == 6) {
                    // TCP packet: if routed or blocked, immediately synthesize 40-byte TCP RST+ACK (0x14)
                    byte[] rst = PacketEngine.buildIpv4TcpReset(packet, len);
                    if (rst != null) {
                        out.write(rst);
                        WallStateStore.recordEvent(
                            this,
                            "🛑 TCP-RST | " + (ownerPkg != null ? ownerPkg : "app") + " -> ECONNREFUSED",
                            true
                        );
                    }
                    continue;
                }

                if (proto == 17) {
                    PacketEngine.DnsQueryInfo q = PacketEngine.parseIpv4DnsQuery(packet, len);
                    if (q == null) continue;

                    Set<String> customDoms = WallStateStore.getCustomBlockedDomains(this);
                    boolean isTracker = PacketEngine.isTrackerDomain(q.qname, customDoms);
                    boolean shouldBlock = appBlocked || isTracker;

                    String appTag = ownerPkg != null ? ownerPkg : "system-dns";
                    String company = PacketEngine.identifyTrackerCompany(q.qname);
                    String displayDomain = company.isEmpty() ? q.qname : (q.qname + " [" + company + "]");

                    if (shouldBlock) {
                        byte[] sinkhole = PacketEngine.buildIpv4DnsSinkholeResponse(packet, q);
                        if (sinkhole != null) {
                            out.write(sinkhole);
                        }
                        WallStateStore.recordDnsDecision(this, appTag, displayDomain, true);
                    } else {
                        byte[] rawDnsReply = queryUpstreamEncryptedOrUdp(packet, len, ihl);
                        if (rawDnsReply != null) {
                            // Inspect DNS Answer section for RFC 1035 TYPE=5 CNAME Cloaking!
                            String cloakedTarget = PacketEngine.findCloakedTrackerCname(rawDnsReply, rawDnsReply.length, customDoms);
                            if (cloakedTarget != null) {
                                byte[] sinkhole = PacketEngine.buildIpv4DnsSinkholeResponse(packet, q);
                                if (sinkhole != null) {
                                    out.write(sinkhole);
                                }
                                WallStateStore.recordDnsDecision(
                                    this,
                                    appTag,
                                    q.qname + " [CNAME->" + cloakedTarget + "]",
                                    true
                                );
                            } else {
                                byte[] replyPkt = PacketEngine.wrapUpstreamDnsReplyIpv4(packet, rawDnsReply, rawDnsReply.length);
                                if (replyPkt != null) {
                                    out.write(replyPkt);
                                }
                                WallStateStore.recordDnsDecision(this, appTag, displayDomain, false);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private String resolveOwnerPackage(ConnectivityManager cm, byte[] pkt, int ihl, int proto) {
        if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            int osProto = proto == 6 ? OsConstants.IPPROTO_TCP : OsConstants.IPPROTO_UDP;
            byte[] srcIp = Arrays.copyOfRange(pkt, 12, 16);
            byte[] dstIp = Arrays.copyOfRange(pkt, 16, 20);
            int srcPort = ((pkt[ihl] & 0xFF) << 8) | (pkt[ihl + 1] & 0xFF);
            int dstPort = ((pkt[ihl + 2] & 0xFF) << 8) | (pkt[ihl + 3] & 0xFF);

            InetSocketAddress local = new InetSocketAddress(InetAddress.getByAddress(srcIp), srcPort);
            InetSocketAddress remote = new InetSocketAddress(InetAddress.getByAddress(dstIp), dstPort);
            int uid = cm.getConnectionOwnerUid(osProto, local, remote);
            if (uid <= 0 || uid == 1051) return null;
            String[] pkgs = getPackageManager().getPackagesForUid(uid);
            if (pkgs != null && pkgs.length > 0) {
                return pkgs[0];
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Queries upstream DNS using RFC 7858 DNS-over-TLS (`DoT` port 853, `dns.adguard-dns.com`)
     * on a `protect(Socket)` descriptor, with seamless fallback to protected UDP/53 if port 853 is blocked.
     */
    private byte[] queryUpstreamEncryptedOrUdp(byte[] inPkt, int inLen, int ihl) {
        int dnsOffset = ihl + 8;
        int dnsPayloadLen = inLen - dnsOffset;
        if (dnsPayloadLen <= 0) return null;

        // 1. Primary: Encrypted RFC 7858 DNS-over-TLS (Port 853)
        try (Socket rawSocket = new Socket()) {
            if (protect(rawSocket)) {
                rawSocket.setSoTimeout(2200);
                rawSocket.connect(new InetSocketAddress(InetAddress.getByName(UPSTREAM_DNS_IP), 853), 1800);
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                try (SSLSocket sslSocket = (SSLSocket) factory.createSocket(rawSocket, "dns.adguard-dns.com", 853, true)) {
                    sslSocket.setSoTimeout(2200);
                    SSLParameters params = sslSocket.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    sslSocket.setSSLParameters(params);
                    sslSocket.startHandshake();

                    DataOutputStream out = new DataOutputStream(sslSocket.getOutputStream());
                    DataInputStream in = new DataInputStream(sslSocket.getInputStream());
                    out.writeShort(dnsPayloadLen);
                    out.write(inPkt, dnsOffset, dnsPayloadLen);
                    out.flush();

                    int respLen = in.readUnsignedShort();
                    if (respLen > 0 && respLen <= 1472) {
                        byte[] respBuf = new byte[respLen];
                        in.readFully(respBuf, 0, respLen);
                        return respBuf;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fallback to protected UDP/53 below if local network blocks TCP/853
        }

        // 2. Fallback: Protected UDP/53
        try (DatagramSocket sock = new DatagramSocket()) {
            protect(sock);
            sock.setSoTimeout(2000);
            InetAddress upstreamAddr = InetAddress.getByName(UPSTREAM_DNS_IP);
            DatagramPacket outDp = new DatagramPacket(inPkt, dnsOffset, dnsPayloadLen, upstreamAddr, 53);
            sock.send(outDp);

            byte[] respBuf = new byte[1472];
            DatagramPacket inDp = new DatagramPacket(respBuf, respBuf.length);
            sock.receive(inDp);
            return Arrays.copyOf(respBuf, inDp.getLength());
        } catch (Throwable t) {
            return null;
        }
    }
}
