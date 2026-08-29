package com.kuro.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 鸣潮(KuRo)扫码登录相关接口。
 * 移植自 KuRo_Scanner(C++) 的 KuRoApi，使用 Java 内置 HttpClient + Jackson 重写。
 * 接口域名 api.kurobbs.com，与原项目保持一致。
 */
public class KuRoApi {
    private static final String DEV_CODE = "3dee77f8-1cb9-4cf6-a585-8677a867dab6";
    private static final String BASE = "https://api.kurobbs.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 9; 23116PN5BC Build/PQ3A.190605.02201427; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.82 Mobile Safari/537.36 Kuro/2.5.0 KuroGameBox/2.5.0";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static HttpRequest.Builder baseBuilder(String url, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("devCode", DEV_CODE)
                .header("source", "android")
                .header("version", "2.5.0")
                .header("versionCode", "2500")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("origin", "https://web-static.kurobbs.com")
                .header("x-requested-with", "com.kurogame.kjq");
        if (token != null && !token.isEmpty()) {
            builder.header("token", token);
        }
        return builder;
    }

    private static String formUrlEncode(List<String> kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.size(); i += 2) {
            if (i > 0) sb.append('&');
            sb.append(kv.get(i)).append('=').append(kv.get(i + 1));
        }
        return sb.toString();
    }

    private static JsonNode post(String url, String token, List<String> kv) {
        String body = formUrlEncode(kv);
        HttpRequest request = baseBuilder(url, token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取短信验证码（手机号注册）。geeTestData 为极验验证数据 JSON 字符串，无极验时可传 "{}"。 */
    public static JsonNode getSmsCode(String phoneNumber, String geeTestData) {
        List<String> kv = new ArrayList<>();
        kv.add("mobile");
        kv.add(phoneNumber);
        kv.add("geeTestData");
        kv.add(geeTestData == null ? "{}" : geeTestData);
        return post(BASE + "/user/getSmsCode", null, kv);
    }

    /** 通过手机号 + 短信验证码登录，返回 userId / token 等。 */
    public static JsonNode loginByMobileCaptcha(String phoneNumber, String code) {
        List<String> kv = new ArrayList<>();
        kv.add("mobile");
        kv.add(phoneNumber);
        kv.add("code");
        kv.add(code);
        return post(BASE + "/user/sdkLogin", null, kv);
    }

    /** 获取用户信息，校验 token 有效性。 */
    public static JsonNode getUserInfo(String uid, String token) {
        List<String> kv = new ArrayList<>();
        kv.add("otherUserId");
        kv.add(uid);
        return post(BASE + "/user/mineV2", token, kv);
    }

    /** 扫码登录第一步：用二维码内容 + token 登记此次扫码。 */
    public static JsonNode loginGameByQrCode(String qrCode, String token) {
        List<String> kv = new ArrayList<>();
        kv.add("qrCode");
        kv.add(qrCode);
        return post(BASE + "/user/auth/roleInfos", token, kv);
    }

    /** 扫码登录第二步：确认登录。autoLogin 是否记住本次验证，sms 为二次验证短信码(可为空)。 */
    public static JsonNode confirmLoginGameByQrCode(String qrCode, String token, boolean autoLogin, String sms) {
        List<String> kv = new ArrayList<>();
        kv.add("autoLogin");
        kv.add(autoLogin ? "true" : "false");
        kv.add("qrCode");
        kv.add(qrCode);
        kv.add("id");
        kv.add("");
        kv.add("verifyCode");
        kv.add(sms == null ? "" : sms);
        return post(BASE + "/user/auth/scanLogin", token, kv);
    }

    /** 扫码二次验证时发送短信验证码。 */
    public static JsonNode sendScanSms(String token) {
        List<String> kv = new ArrayList<>();
        kv.add("geeTestData");
        kv.add("");
        return post(BASE + "/user/sms/scanSms", token, kv);
    }
}
