package com.kuro.scan;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 鸣潮扫码登录的业务逻辑封装，移植自 KuRo_Scanner(C++) 的 KuRoService。
 */
public class KuRoService {

    /** 游戏类型，与原项目一致。 */
    public enum GameType {
        UNKNOW(-1),
        PunishingGrayRaven(2),
        WutheringWaves(3);

        public final int value;

        GameType(int value) {
            this.value = value;
        }
    }

    /** 扫码结果，与原项目 ScanRet 一致。 */
    public enum ScanRet {
        UNKNOW(-1),
        SUCCESS(0),
        FAILURE_1(1),
        FAILURE_2(2),
        LIVESTOP(3),
        STREAMERROR(4),
        NeedSMSCode(5);

        public final int value;

        ScanRet(int value) {
            this.value = value;
        }
    }

    /** 校验账号 token 是否有效。 */
    public static boolean checkAccountValidity(String uid, String token) {
        JsonNode res = KuRoApi.getUserInfo(uid, token);
        if (res == null) {
            return false;
        }
        JsonNode code = res.get("code");
        return code != null && code.asInt() == 200;
    }

    /** 根据二维码内容判断游戏类型。鸣潮官服二维码以 "G152#KURO" 开头。 */
    public static GameType getQrCodeGameType(String str) {
        if (str != null && str.startsWith("G152#KURO")) {
            return GameType.WutheringWaves;
        }
        return GameType.UNKNOW;
    }

    /** 扫码登录第一步：登记二维码。 */
    public static boolean loginGameByQrCode(String qrCode, String token) {
        JsonNode res = KuRoApi.loginGameByQrCode(qrCode, token);
        if (res == null) {
            return false;
        }
        JsonNode code = res.get("code");
        return code != null && code.asInt() == 200;
    }

    /** 扫码登录第二步：确认登录。返回结果类型。 */
    public static ScanRet confirmLoginGameByQrCode(String qrCode, String token, String sms, boolean autoLogin) {
        JsonNode res = KuRoApi.confirmLoginGameByQrCode(qrCode, token, autoLogin, sms);
        if (res == null) {
            return ScanRet.FAILURE_2;
        }
        JsonNode code = res.get("code");
        if (code == null) {
            return ScanRet.FAILURE_2;
        }
        int c = code.asInt();
        if (c == 200) {
            return ScanRet.SUCCESS;
        } else if (c == 2240) {
            return ScanRet.NeedSMSCode;
        }
        return ScanRet.FAILURE_2;
    }

    /** 发送扫码二次验证短信。 */
    public static boolean getScanLoginSms(String token) {
        JsonNode res = KuRoApi.sendScanSms(token);
        return res != null;
    }

    /** 从 sdkLogin 响应中解析账号信息。 */
    public static final class AccountLoginInfo {
        public final String uid;
        public final String token;
        public final String name;
        public final String mobile;

        public AccountLoginInfo(String uid, String token, String name, String mobile) {
            this.uid = uid;
            this.token = token;
            this.name = name;
            this.mobile = mobile;
        }
    }

    /** 解析手机号登录结果，成功返回 AccountLoginInfo，失败返回 null。 */
    public static AccountLoginInfo parseSdkLogin(JsonNode res) {
        if (res == null) {
            return null;
        }
        JsonNode code = res.get("code");
        if (code == null || code.asInt() != 200) {
            return null;
        }
        JsonNode data = res.get("data");
        if (data == null) {
            return null;
        }
        String uid = textOrNull(data, "userId");
        String token = textOrNull(data, "token");
        if (uid == null || token == null) {
            return null;
        }
        // 通过用户信息接口补全用户名与手机号
        String name = uid;
        String mobile = "";
        JsonNode info = KuRoApi.getUserInfo(uid, token);
        if (info != null && info.get("code") != null && info.get("code").asInt() == 200) {
            JsonNode mine = info.path("data").path("mine");
            if (!mine.isMissingNode()) {
                name = textOrNull(mine, "userName");
                mobile = textOrNull(mine, "mobile");
            }
        }
        return new AccountLoginInfo(uid, token, name, mobile);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
