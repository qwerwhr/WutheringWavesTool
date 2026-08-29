package cn.tealc.wutheringwavestool.ui.scanlogin;

import cn.tealc.wutheringwavestool.base.AppInjector;
import cn.tealc.wutheringwavestool.service.UserInfoService;
import com.kuro.scan.AccountManager;
import com.kuro.scan.KuRoService;
import com.kuro.scan.LiveStreamQrScanner;
import com.kuro.scan.ScanAccount;
import com.kuro.scan.ScanCallback;
import com.kuro.scan.ScanConfig;
import com.kuro.scan.ScreenQrScanner;
import com.kuro.kujiequ.model.sign.UserInfo;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;

/**
 * 扫码登录页 ViewModel，移植自 KuRo_Scanner 的主窗口逻辑。
 */
public class ScanLoginViewModel implements ViewModel {

    private final ObservableList<ScanAccount> accountList = FXCollections.observableArrayList();
    private final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);
    private final StringProperty selectedName = new SimpleStringProperty("未选中");
    private final BooleanProperty screenRunning = new SimpleBooleanProperty(false);
    private final BooleanProperty streamRunning = new SimpleBooleanProperty(false);
    private final BooleanProperty autoScreen = new SimpleBooleanProperty(false);
    private final BooleanProperty autoExit = new SimpleBooleanProperty(false);
    private final BooleanProperty autoLogin = new SimpleBooleanProperty(false);
    private final StringProperty defaultAccountUid = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private ScreenQrScanner screenScanner;
    private LiveStreamQrScanner streamScanner;

    public void init() {
        refreshAccounts();
        // 首次加载时，如果扫码列表为空但账号管理已有数据，自动同步
        if (accountList.isEmpty()) {
            try {
                UserInfoService userInfoService = AppInjector.getInstance(UserInfoService.class);
                syncFromUserInfoService(userInfoService);
            } catch (Exception ignored) {
                // AppInjector 可能未初始化（如测试环境），忽略
            }
        }
        ScanConfig config = ScanConfig.getInstance();
        autoScreen.set(config.isAutoScreen());
        autoExit.set(config.isAutoExit());
        autoLogin.set(config.isAutoLogin());
        defaultAccountUid.set(config.getDefaultAccount());

        autoScreen.addListener((o, ov, nv) -> config.setAutoScreen(nv));
        autoExit.addListener((o, ov, nv) -> config.setAutoExit(nv));
        autoLogin.addListener((o, ov, nv) -> config.setAutoLogin(nv));
    }

    public void refreshAccounts() {
        accountList.setAll(AccountManager.getInstance().getAll());
        if (selectedIndex.get() >= accountList.size()) {
            selectedIndex.set(accountList.size() - 1);
        }
    }

    /**
     * 从已有的 UserInfoService（账号管理 DB）同步账号到扫码登录列表。
     * 登录成功后由 ScanLoginView 调用，将 DB 中的已登录账号导入 scan_accounts.json。
     */
    public void syncFromUserInfoService(UserInfoService userInfoService) {
        List<UserInfo> users = userInfoService.getAllUsers();
        if (users == null || users.isEmpty()) return;
        for (UserInfo u : users) {
            String uid = u.getRoleId();
            if (uid == null || uid.isEmpty() || AccountManager.getInstance().containsUid(uid)) continue;
            // 查询服务器和等级信息
            String server = resolveServerName(uid);
            String level = queryRoleLevel(u.getToken(), uid);
            AccountManager.getInstance().addAccount(
                    u.getRoleName() != null ? u.getRoleName() : uid,
                    uid, u.getToken(),
                    u.getIsWeb() != null && u.getIsWeb() ? "web" : "mobile",
                    "", server, level);
        }
        refreshAccounts();
    }

    /** 根据 roleId 前缀推断服务器名称。 */
    private static String resolveServerName(String roleId) {
        if (roleId == null || roleId.isEmpty()) return "";
        return switch (roleId.charAt(0)) {
            case '1' -> "官服";
            case '6' -> "欧服";
            case '7' -> "亚服";
            case '8' -> "港澳台";
            case '9' -> "东南亚服";
            default -> "未知";
        };
    }

    /** 通过 KuRo API 查询角色等级（异步，返回默认值 "-" 并后台更新）。 */
    private String queryRoleLevel(String token, String uid) {
        // 先返回占位符，后续可改为异步查询后刷新
        return "-";
    }

    public boolean addManual(String name, String uid, String token) {
        if (uid == null || uid.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            statusMessage.set("UID 与 Token 不能为空");
            return false;
        }
        if (AccountManager.getInstance().containsUid(uid.trim())) {
            statusMessage.set("该账号已添加，无需重复添加");
            return false;
        }
        AccountManager.getInstance().addAccount(
                name == null || name.trim().isEmpty() ? uid.trim() : name.trim(),
                uid.trim(), token.trim(), "", "");
        refreshAccounts();
        statusMessage.set("添加成功");
        return true;
    }

    public String addByPhone(String phone, String code) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$") || code == null || code.trim().isEmpty()) {
            return "手机号或验证码格式不正确";
        }
        KuRoService.AccountLoginInfo info = KuRoService.parseSdkLogin(
                com.kuro.scan.KuRoApi.loginByMobileCaptcha(phone, code.trim()));
        if (info == null) {
            return "登录失败，请检查验证码（部分账号需要极验验证，可改用手动添加）";
        }
        if (AccountManager.getInstance().containsUid(info.uid)) {
            return "该账号已添加，无需重复添加";
        }
        AccountManager.getInstance().addAccount(info.name, info.uid, info.token, info.mobile, "");
        refreshAccounts();
        return null;
    }

    public void deleteSelected() {
        int idx = selectedIndex.get();
        if (idx < 0 || idx >= accountList.size()) {
            statusMessage.set("没有选择任何账号");
            return;
        }
        ScanAccount account = accountList.get(idx);
        if (account.getUid().equals(defaultAccountUid.get())) {
            defaultAccountUid.set("");
            ScanConfig.getInstance().setDefaultAccount("");
        }
        AccountManager.getInstance().deleteAccount(idx);
        refreshAccounts();
        statusMessage.set("已删除账号：" + account.getName());
    }

    public void setDefault() {
        int idx = selectedIndex.get();
        if (idx < 0 || idx >= accountList.size()) {
            statusMessage.set("没有选择任何账号");
            return;
        }
        String uid = accountList.get(idx).getUid();
        defaultAccountUid.set(uid);
        ScanConfig.getInstance().setDefaultAccount(uid);
        statusMessage.set("已设为默认账号，勾选\"启动时自动监视屏幕\"后下次启动将自动登录");
    }

    public boolean startScreen(ScanCallback callback) {
        if (selectedIndex.get() < 0 || selectedIndex.get() >= accountList.size()) {
            statusMessage.set("请先选择一个账号");
            return false;
        }
        ScanAccount account = accountList.get(selectedIndex.get());
        if (!KuRoService.checkAccountValidity(account.getUid(), account.getToken())) {
            statusMessage.set("账号登录状态失效，请重新添加账号");
            return false;
        }
        screenScanner = new ScreenQrScanner(account.getUid(), account.getToken(), callback);
        screenRunning.set(true);
        statusMessage.set("正在监视屏幕，请让鸣潮登录二维码显示在屏幕上…");
        screenScanner.start();
        return true;
    }

    public void stopScreen() {
        if (screenScanner != null) {
            screenScanner.stop();
        }
        screenRunning.set(false);
    }

    public boolean startStream(int platformIndex, String roomId, ScanCallback callback) {
        if (selectedIndex.get() < 0 || selectedIndex.get() >= accountList.size()) {
            statusMessage.set("请先选择一个账号");
            return false;
        }
        ScanAccount account = accountList.get(selectedIndex.get());
        if (!KuRoService.checkAccountValidity(account.getUid(), account.getToken())) {
            statusMessage.set("账号登录状态失效，请重新添加账号");
            return false;
        }
        streamScanner = new LiveStreamQrScanner(account.getUid(), account.getToken(), callback);
        LiveStreamQrScanner.LiveStreamStatus status = streamScanner.prepare(platformIndex, roomId);
        if (status != LiveStreamQrScanner.LiveStreamStatus.Normal) {
            stopStream();
            return false;
        }
        streamRunning.set(true);
        statusMessage.set("正在监视直播间，等待二维码出现…");
        streamScanner.start();
        return true;
    }

    public void stopStream() {
        if (streamScanner != null) {
            streamScanner.stop();
        }
        streamRunning.set(false);
    }

    public void onScanFinished() {
        screenRunning.set(false);
        streamRunning.set(false);
    }

    /** 扫码第二步：确认登录。 */
    public KuRoService.ScanRet confirmLogin(String qrCode, boolean autoLogin, String sms, boolean fromScreen) {
        int idx = selectedIndex.get();
        if (idx < 0 || idx >= accountList.size()) {
            return KuRoService.ScanRet.FAILURE_2;
        }
        ScanAccount account = accountList.get(idx);
        return KuRoService.confirmLoginGameByQrCode(qrCode, account.getToken(), sms, autoLogin);
    }

    public boolean sendScanSms() {
        int idx = selectedIndex.get();
        if (idx < 0 || idx >= accountList.size()) {
            return false;
        }
        return KuRoService.getScanLoginSms(accountList.get(idx).getToken());
    }

    public void selectAccount(int index, String name) {
        selectedIndex.set(index);
        selectedName.set(name == null ? "未选中" : name);
    }

    // ---- 属性访问 ----
    public ObservableList<ScanAccount> getAccountList() {
        return accountList;
    }

    public IntegerProperty selectedIndexProperty() {
        return selectedIndex;
    }

    public StringProperty selectedNameProperty() {
        return selectedName;
    }

    public BooleanProperty screenRunningProperty() {
        return screenRunning;
    }

    public BooleanProperty streamRunningProperty() {
        return streamRunning;
    }

    public BooleanProperty autoScreenProperty() {
        return autoScreen;
    }

    public BooleanProperty autoExitProperty() {
        return autoExit;
    }

    public BooleanProperty autoLoginProperty() {
        return autoLogin;
    }

    public StringProperty defaultAccountUidProperty() {
        return defaultAccountUid;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /** 启动后是否自动开始监视屏幕（需已设置默认账号）。 */
    public boolean shouldAutoStartScreen() {
        if (!autoScreen.get() || defaultAccountUid.get().isEmpty()) {
            return false;
        }
        Optional<Integer> idx = AccountManager.getInstance().findIndexByUid("uid", defaultAccountUid.get());
        idx.ifPresent(selectedIndex::set);
        return idx.isPresent();
    }
}
