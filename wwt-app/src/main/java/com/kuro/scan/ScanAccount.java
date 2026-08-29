package com.kuro.scan;

/**
 * 扫码登录账号信息，对应 KuRo_Scanner 的 account.json 中单条记录。
 */
public class ScanAccount {
    private String name;
    private String uid;
    private String token;
    private String note;
    private String mobile;
    private String server;   // 服务器/区域 (China/Eu/Asia/HMT/SEA)
    private String level;    // 等级

    public ScanAccount() {
    }

    public ScanAccount(String name, String uid, String token, String mobile, String note) {
        this.name = name;
        this.uid = uid;
        this.token = token;
        this.mobile = mobile;
        this.note = note;
    }

    public ScanAccount(String name, String uid, String token, String mobile, String note, String server, String level) {
        this.name = name;
        this.uid = uid;
        this.token = token;
        this.mobile = mobile;
        this.note = note;
        this.server = server;
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}
