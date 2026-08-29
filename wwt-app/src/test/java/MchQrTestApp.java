import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import fi.iki.elonen.NanoHTTPD;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Java25 + JavaFX25 鸣潮扫码获取token/did Demo
 */
public class MchQrTestApp extends Application {

    private static final int PORT = 8899;
    private PhoneApiServer apiServer;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 启动局域网http服务
        apiServer = new PhoneApiServer(PORT, (token, did, uid) -> {
            System.out.println("===================收到数据===================");
            System.out.println("token = " + token);
            System.out.println("did   = " + did);
            System.out.println("uid   = " + uid);
            System.out.println("==============================================");
        });

        String lanIp = getLocalLanIp();
        String qrUrl = String.format("http://%s:%d/receive", lanIp, PORT);
        System.out.println("二维码地址：" + qrUrl);

        // 生成二维码
        Image qrImg = genQrCode(qrUrl, 320, 320);
        ImageView iv = new ImageView(qrImg);

        Text tip1 = new Text("手机电脑连接同一个WiFi");
        Text tip2 = new Text("扫码打开网页，粘贴token/did提交");
        Text urlText = new Text(qrUrl);

        VBox root = new VBox(12, iv, tip1, tip2, urlText);
        root.setStyle("-fx-padding:20; -fx-alignment:center;");

        Scene scene = new Scene(root, 450, 520);
        primaryStage.setTitle("鸣潮扫码测试 Java25");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        if(apiServer != null){
            apiServer.stop();
        }
        super.stop();
    }

    // 生成二维码
    public static Image genQrCode(String content, int width, int height) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);
        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }

    // 获取局域网IPv4
    public static String getLocalLanIp() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                continue;
            }
            Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                java.net.InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
        return "127.0.0.1";
    }

    // 本地HTTP服务
    public static class PhoneApiServer extends NanoHTTPD {
        public interface OnTokenReceive {
            void onData(String token, String did, String uid);
        }

        private final OnTokenReceive callback;

        public PhoneApiServer(int port, OnTokenReceive cb) throws IOException {
            super(port);
            this.callback = cb;
            start(SOCKET_READ_TIMEOUT, false);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if ("/receive".equals(session.getUri())) {
                if (Method.POST.equals(session.getMethod())) {
                    // 先创建空Map
                    Map<String, String> params = new HashMap<>();
                    try {
                        // 将map传入，方法内部填充表单数据，没有返回值
                        session.parseBody(params);
                    } catch (Exception e) {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "参数错误");
                    }
                    String token = params.getOrDefault("token", "");
                    String did = params.getOrDefault("did", "");
                    String uid = params.getOrDefault("uid", "");
                    if (callback != null) {
                        Platform.runLater(() -> callback.onData(token, did, uid));
                    }
                    return newFixedLengthResponse(Response.Status.OK, MIME_HTML,
                            "<h2>✅接收成功，可以关闭页面返回电脑</h2>");
                }


                // GET手机表单页面
                String html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>鸣潮参数提交</title>
                        </head>
                        <body style="font-size:18px;padding:12px;">
                        <form method="POST">
                        <div>Token:<br><input name="token" style="width:100%;box-sizing:border-box;height:44px;margin:4px 0 12px;"></div>
                        <div>DID:<br><input name="did" style="width:100%;box-sizing:border-box;height:44px;margin:4px 0 12px;"></div>
                        <div>UID:<br><input name="uid" style="width:100%;box-sizing:border-box;height:44px;margin:4px 0 12px;"></div>
                        <br>
                        <button type="submit" style="width:100%;height:52px;font-size:18px;">提交到PC启动器</button>
                        </form>
                        </body>
                        </html>
                        """;
                return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404");
        }
    }

    public static void main(String[] args) {
        Application.launch(MchQrTestApp.class, args);
    }
}
