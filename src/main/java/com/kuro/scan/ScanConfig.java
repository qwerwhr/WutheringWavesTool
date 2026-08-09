package com.kuro.scan;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * 扫码登录配置（单例），对应 KuRo_Scanner 的 ConfigManager。
 * 包含：启动时自动监视屏幕、扫码成功自动退出、自动二次确认、默认账号 uid。
 */
public class ScanConfig {
    private static final String FILE_NAME = "scan_config.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile ScanConfig instance;

    private boolean autoScreen;
    private boolean autoExit;
    private boolean autoLogin;
    private String defaultAccount = "";

    private ScanConfig() {
        load();
    }

    public static ScanConfig getInstance() {
        if (instance == null) {
            synchronized (ScanConfig.class) {
                if (instance == null) {
                    instance = new ScanConfig();
                }
            }
        }
        return instance;
    }

    private void load() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return;
        }
        try {
            ScanConfig loaded = MAPPER.readValue(file, ScanConfig.class);
            this.autoScreen = loaded.autoScreen;
            this.autoExit = loaded.autoExit;
            this.autoLogin = loaded.autoLogin;
            this.defaultAccount = loaded.defaultAccount == null ? "" : loaded.defaultAccount;
        } catch (Exception ignored) {
        }
    }

    public void save() {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_NAME), this);
        } catch (Exception ignored) {
        }
    }

    public boolean isAutoScreen() {
        return autoScreen;
    }

    public void setAutoScreen(boolean autoScreen) {
        this.autoScreen = autoScreen;
        save();
    }

    public boolean isAutoExit() {
        return autoExit;
    }

    public void setAutoExit(boolean autoExit) {
        this.autoExit = autoExit;
        save();
    }

    public boolean isAutoLogin() {
        return autoLogin;
    }

    public void setAutoLogin(boolean autoLogin) {
        this.autoLogin = autoLogin;
        save();
    }

    public String getDefaultAccount() {
        return defaultAccount;
    }

    public void setDefaultAccount(String defaultAccount) {
        this.defaultAccount = defaultAccount == null ? "" : defaultAccount;
        save();
    }
}
