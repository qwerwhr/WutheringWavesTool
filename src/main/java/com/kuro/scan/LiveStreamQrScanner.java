package com.kuro.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 直播流二维码扫描器（移植自 KuRo_Scanner 的 QRCodeForStream）。
 * 通过 ffmpeg 拉取直播间视频流并按帧解码二维码。
 * B站直播间地址获取无需签名，可直接使用；抖音直播间需要 a_bogus 签名，当前版本暂不支持。
 */
public class LiveStreamQrScanner {
    public enum LiveStreamStatus {
        Normal, Absent, NotLive, Error
    }

    private static final int FRAME_W = 1280;
    private static final int FRAME_H = 720;
    private static final int FPS = 5;
    private static final int BYTES_PER_FRAME = FRAME_W * FRAME_H * 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    private final String token;
    private final String uid;
    private final ScanCallback callback;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Process ffmpegProcess;
    private String lastQr = "";
    private String streamUrl;

    public LiveStreamQrScanner(String uid, String token, ScanCallback callback) {
        this.uid = uid;
        this.token = token;
        this.callback = callback;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 准备直播流地址。
     * @param platformIndex 0=抖音, 1=B站（与原项目下拉框顺序一致）
     * @param roomId 直播间 ID（纯数字）
     * @return 状态
     */
    public LiveStreamStatus prepare(int platformIndex, String roomId) {
        if (roomId == null || roomId.trim().isEmpty() || !roomId.trim().matches("\\d+")) {
            callback.onStatus("直播间 ID 必须是纯数字");
            return LiveStreamStatus.Error;
        }
        if (platformIndex == 1) {
            // B站
            String url = fetchBiliUrl(roomId.trim());
            if (url == null) {
                return LiveStreamStatus.Error;
            }
            streamUrl = url;
            return LiveStreamStatus.Normal;
        } else {
            // 抖音：需要 a_bogus 签名，当前版本暂不支持
            callback.onStatus("抖音直播流需要 a_bogus 签名，当前版本暂不支持，请改用 B站直播间或屏幕监视");
            return LiveStreamStatus.Error;
        }
    }

    private String fetchBiliUrl(String roomId) {
        try {
            JsonNode init = getJson("https://api.live.bilibili.com/room/v1/Room/room_init",
                    List.of("id", roomId));
            if (init == null) {
                callback.onStatus("B站直播间请求失败");
                return null;
            }
            int code = init.path("code").asInt(-1);
            if (code == 60004) {
                callback.onStatus("B站直播间不存在");
                return null;
            }
            if (code != 0) {
                callback.onStatus("B站直播间状态异常：" + code);
                return null;
            }
            JsonNode data = init.path("data");
            int liveStatus = data.path("live_status").asInt(-1);
            if (liveStatus != 1) {
                callback.onStatus("B站直播间未开播");
                return null;
            }
            String realRoomId = data.path("room_id").asText();

            List<String> params = new ArrayList<>();
            params.add("codec"); params.add("0");
            params.add("format"); params.add("0,2");
            params.add("only_audio"); params.add("0");
            params.add("only_video"); params.add("0");
            params.add("protocol"); params.add("0,1");
            params.add("qn"); params.add("10000");
            params.add("room_id"); params.add(realRoomId);
            JsonNode play = getJson("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo", params);
            if (play == null) {
                callback.onStatus("B站获取播放地址失败");
                return null;
            }
            JsonNode stream = play.path("data").path("playurl_info").path("playurl").path("stream")
                    .path(0).path("format").path(0).path("codec").path(0);
            if (stream.isMissingNode()) {
                callback.onStatus("B站播放地址解析失败");
                return null;
            }
            String base = stream.path("base_url").asText();
            JsonNode urlInfo = stream.path("url_info").path(0);
            String host = urlInfo.path("host").asText();
            String extra = urlInfo.path("extra").asText();
            if (base.isEmpty() || host.isEmpty()) {
                callback.onStatus("B站播放地址字段缺失");
                return null;
            }
            return host + base + extra;
        } catch (Exception e) {
            callback.onStatus("B站地址获取异常：" + e.getMessage());
            return null;
        }
    }

    private JsonNode getJson(String url, List<String> params) {
        StringBuilder sb = new StringBuilder(url);
        if (!params.isEmpty()) {
            sb.append('?');
            for (int i = 0; i < params.size(); i += 2) {
                if (i > 0) sb.append('&');
                sb.append(params.get(i)).append('=').append(params.get(i + 1));
            }
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(sb.toString()))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/110.0.0.0 Safari/537.36")
                .header("referer", "https://live.bilibili.com");
        try {
            HttpResponse<String> r = CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                return null;
            }
            return MAPPER.readTree(r.body());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.waitFor();
            return p.exitValue() == 0 || p.getInputStream().available() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void start() {
        if (running.get()) {
            return;
        }
        if (streamUrl == null || streamUrl.isEmpty()) {
            callback.onLoginResult(KuRoService.ScanRet.STREAMERROR, false);
            return;
        }
        if (!ffmpegAvailable()) {
            callback.onStatus("未检测到 ffmpeg，无法拉取直播流，请安装 ffmpeg 并加入 PATH");
            callback.onLoginResult(KuRoService.ScanRet.STREAMERROR, false);
            return;
        }
        running.set(true);
        Thread.startVirtualThread(this::loop);
    }

    public void stop() {
        running.set(false);
        if (ffmpegProcess != null) {
            ffmpegProcess.destroyForcibly();
        }
    }

    private void loop() {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("ffmpeg");
            cmd.add("-hide_banner");
            cmd.add("-loglevel");
            cmd.add("error");
            cmd.add("-i");
            cmd.add(streamUrl);
            cmd.add("-f");
            cmd.add("rawvideo");
            cmd.add("-pix_fmt");
            cmd.add("rgb24");
            cmd.add("-s");
            cmd.add(FRAME_W + "x" + FRAME_H);
            cmd.add("-r");
            cmd.add(String.valueOf(FPS));
            cmd.add("-");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            ffmpegProcess = pb.start();
            InputStream in = new BufferedInputStream(ffmpegProcess.getInputStream());
            QRCodeReader reader = new QRCodeReader();
            byte[] frame = new byte[BYTES_PER_FRAME];
            while (running.get()) {
                int read = readFully(in, frame, BYTES_PER_FRAME);
                if (read < BYTES_PER_FRAME) {
                    // 流结束或读取不完整
                    break;
                }
                BufferedImage image = bytesToImage(frame);
                String qr = decode(reader, image);
                if (qr != null && !qr.equals(lastQr)) {
                    KuRoService.GameType type = KuRoService.getQrCodeGameType(qr);
                    if (type == KuRoService.GameType.UNKNOW) {
                        continue;
                    }
                    if (KuRoService.loginGameByQrCode(qr, token)) {
                        lastQr = qr;
                        running.set(false);
                        callback.onQrRegistered(qr, false);
                        break;
                    } else {
                        running.set(false);
                        callback.onLoginResult(KuRoService.ScanRet.FAILURE_1, false);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                callback.onStatus("直播流读取异常：" + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private int readFully(InputStream in, byte[] buf, int total) throws IOException {
        int off = 0;
        while (off < total) {
            int n = in.read(buf, off, total - off);
            if (n < 0) {
                break;
            }
            off += n;
        }
        return off;
    }

    private BufferedImage bytesToImage(byte[] data) {
        BufferedImage img = new BufferedImage(FRAME_W, FRAME_H, BufferedImage.TYPE_3BYTE_BGR);
        // ffmpeg 输出 rgb24：R,G,B 顺序，需转为 BGR
        int idx = 0;
        for (int y = 0; y < FRAME_H; y++) {
            for (int x = 0; x < FRAME_W; x++) {
                int r = data[idx] & 0xFF;
                int g = data[idx + 1] & 0xFF;
                int b = data[idx + 2] & 0xFF;
                int pixel = (b << 16) | (g << 8) | r;
                img.setRGB(x, y, pixel);
                idx += 3;
            }
        }
        return img;
    }

    private String decode(QRCodeReader reader, BufferedImage image) {
        try {
            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
