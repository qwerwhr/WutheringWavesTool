package com.kuro.scan;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 屏幕二维码扫描器（移植自 KuRo_Scanner 的 QRCodeForScreen）。
 * 使用 java.awt.Robot 截取主屏幕，使用 ZXing 解码二维码，
 * 识别到鸣潮登录二维码后调用 KuRoService 完成扫码登录第一步。
 */
public class ScreenQrScanner {
    private static final int DELAY_MS = 200;
    private static final int MAX_WIDTH = 1280;

    private final String token;
    private final String uid;
    private final ScanCallback callback;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Robot robot;
    private Rectangle screenRect;
    private String lastQr = "";

    public ScreenQrScanner(String uid, String token, ScanCallback callback) {
        this.uid = uid;
        this.token = token;
        this.callback = callback;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (running.get()) {
            return;
        }
        try {
            robot = new Robot();
            var ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            var sb = ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            screenRect = new Rectangle(sb.x, sb.y, sb.width, sb.height);
        } catch (Exception e) {
            callback.onStatus("无法初始化屏幕捕获：" + e.getMessage());
            return;
        }
        running.set(true);
        Thread.startVirtualThread(this::loop);
    }

    public void stop() {
        running.set(false);
    }

    private void loop() {
        QRCodeReader reader = new QRCodeReader();
        while (running.get()) {
            try {
                BufferedImage image = robot.createScreenCapture(screenRect);
                String qr = decode(reader, image);
                if (qr != null && !qr.equals(lastQr)) {
                    KuRoService.GameType type = KuRoService.getQrCodeGameType(qr);
                    if (type == KuRoService.GameType.UNKNOW) {
                        continue;
                    }
                    if (KuRoService.loginGameByQrCode(qr, token)) {
                        lastQr = qr;
                        running.set(false);
                        callback.onQrRegistered(qr, true);
                        return;
                    } else {
                        running.set(false);
                        callback.onLoginResult(KuRoService.ScanRet.FAILURE_1, true);
                        return;
                    }
                }
            } catch (Exception ignored) {
                // 单帧解码失败不影响整体循环
            }
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String decode(QRCodeReader reader, BufferedImage image) {
        try {
            BufferedImage scaled = resize(image, MAX_WIDTH);
            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(scaled);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage resize(BufferedImage src, int maxWidth) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxWidth) {
            return src;
        }
        int nw = maxWidth;
        int nh = (int) (h * (maxWidth / (double) w));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getScaledInstance(nw, nh, Image.SCALE_FAST), 0, 0, null);
        g.dispose();
        return out;
    }
}
