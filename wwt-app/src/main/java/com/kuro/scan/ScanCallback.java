package com.kuro.scan;

/**
 * 扫码过程回调，供 UI 层接收识别与登录结果。
 */
public interface ScanCallback {
    /** 二维码已识别并完成第一步登记（loginGameByQrCode 成功）。fromScreen 表示来源（屏幕/直播流）。 */
    void onQrRegistered(String qrCode, boolean fromScreen);

    /** 最终登录结果。 */
    void onLoginResult(KuRoService.ScanRet ret, boolean fromScreen);

    /** 状态 / 日志消息。 */
    void onStatus(String message);
}
