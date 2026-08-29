package com.kuro.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;

/**
 * 扫码登录配置（单例），对应 KuRo_Scanner 的 ConfigManager。
 * 包含：启动时自动监视屏幕、扫码成功自动退出、自动二次确认、默认账号 uid。
 *
 * <p>注意：原实现 {@code load()} 里调用 {@code MAPPER.readValue(file, ScanConfig.class)}，
 * 反序列化自身类型时会强制实例化一个新的 {@code ScanConfig}，而构造函数又会调用 {@code load()}，
 * 形成无限递归直到 {@link StackOverflowError}。在错误状态下，第一个被命中的 Jackson 栈帧
 * （{@code ObjectWriter.<clinit>}）会被 JVM 标记为「类初始化失败」，污染后续所有调用。</p>
 *
 * <p>修复方案：构造不再递归加载，{@code load()} 使用 {@link ObjectMapper#readTree(File)}
 * 读取为 {@link JsonNode} 后手动复制字段，避免触发自身的反序列化器。</p>
 */
public class ScanConfig {
    private static final String FILE_NAME = "scan_config.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile ScanConfig instance;

    private boolean autoScreen;
    private boolean autoExit;
    private boolean autoLogin;
    private String defaultAccount = "";

    /**
     * 标记是否正在反序列化加载期间，避免 setter 触发自动写盘造成额外的 IO 与潜在递归。
     */
    private transient boolean loading = false;

    private ScanConfig() {
        // 不在这里调 load()，避免触发 load() → readValue(ScanConfig.class) → 构造 → ... 的自递归
    }

    public static ScanConfig getInstance() {
        ScanConfig local = instance;
        if (local == null) {
            synchronized (ScanConfig.class) {
                local = instance;
                if (local == null) {
                    local = new ScanConfig();
                    local.load();
                    instance = local;
                }
            }
        }
        return local;
    }

    private void load() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return;
        }
        loading = true;
        try {
            JsonNode root = MAPPER.readTree(file);
            if (root == null || !root.isObject()) {
                return;
            }
            JsonNode n;
            if ((n = root.get("autoScreen")) != null && n.isBoolean()) {
                this.autoScreen = n.booleanValue();
            }
            if ((n = root.get("autoExit")) != null && n.isBoolean()) {
                this.autoExit = n.booleanValue();
            }
            if ((n = root.get("autoLogin")) != null && n.isBoolean()) {
                this.autoLogin = n.booleanValue();
            }
            if ((n = root.get("defaultAccount")) != null && n.isTextual()) {
                this.defaultAccount = n.asText("");
            }
        } catch (Exception ignored) {
            // 文件可能损坏或半截写入；忽略，保留默认值
        } finally {
            loading = false;
        }
    }

    public void save() {
        // 加载期间禁止写盘，避免和 Jackson 反射调用产生竞态
        if (loading) {
            return;
        }
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("autoScreen", autoScreen);
            root.put("autoExit", autoExit);
            root.put("autoLogin", autoLogin);
            root.put("defaultAccount", defaultAccount == null ? "" : defaultAccount);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_NAME), root);
        } catch (Exception ignored) {
            // 写盘失败不应阻塞业务调用
        }
    }

    public boolean isAutoScreen() {
        return autoScreen;
    }

    public void setAutoScreen(boolean autoScreen) {
        if (this.autoScreen == autoScreen) {
            return;
        }
        this.autoScreen = autoScreen;
        save();
    }

    public boolean isAutoExit() {
        return autoExit;
    }

    public void setAutoExit(boolean autoExit) {
        if (this.autoExit == autoExit) {
            return;
        }
        this.autoExit = autoExit;
        save();
    }

    public boolean isAutoLogin() {
        return autoLogin;
    }

    public void setAutoLogin(boolean autoLogin) {
        if (this.autoLogin == autoLogin) {
            return;
        }
        this.autoLogin = autoLogin;
        save();
    }

    public String getDefaultAccount() {
        return defaultAccount;
    }

    public void setDefaultAccount(String defaultAccount) {
        String newValue = defaultAccount == null ? "" : defaultAccount;
        if (java.util.Objects.equals(this.defaultAccount, newValue)) {
            return;
        }
        this.defaultAccount = newValue;
        save();
    }
}
