package cn.tealc.wutheringwavestool.ui.game.manage;

import cn.tealc.wutheringwavestool.base.Config;
import cn.tealc.wutheringwavestool.base.NotificationManager;
import cn.tealc.wutheringwavestool.model.ResponseBody;
import cn.tealc.wutheringwavestool.model.SourceType;
import cn.tealc.teafx.utils.message.MessageInfo;
import cn.tealc.wutheringwavestool.thread.system.CheckGameConfigTask;
import cn.tealc.wutheringwavestool.util.LanguageManager;
import de.saxsys.mvvmfx.SceneLifecycle;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import com.kuro.game.thread.GamePredownloadTask;
import com.kuro.game.thread.GameResourceSyncTask;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @description:
 * @author: Leck
 * @create: 2025-03-08 23:11
 */
public class GameBaseSettingViewModel implements ViewModel, SceneLifecycle {
    private SimpleObjectProperty<SourceType> gameSourceType = new SimpleObjectProperty<>();
    private SimpleStringProperty gameDir=new SimpleStringProperty();
    private SimpleStringProperty gameAppStartPath=new SimpleStringProperty();
    private SimpleBooleanProperty gameAppStartCustom=new SimpleBooleanProperty();
    private SimpleBooleanProperty sourceTypeDisabled01=new SimpleBooleanProperty(true);
    private SimpleBooleanProperty sourceTypeDisabled02=new SimpleBooleanProperty(true);
    private SimpleBooleanProperty sourceTypeDisabled03=new SimpleBooleanProperty(true);
    private SimpleBooleanProperty sourceTypeDisabled04=new SimpleBooleanProperty(true);
    private SimpleStringProperty currentServerName = new SimpleStringProperty("默认");

    /** 资源维护任务进度（-1 表示空闲） */
    private final SimpleDoubleProperty syncProgress = new SimpleDoubleProperty(-1);
    /** 资源维护任务提示信息 */
    private final SimpleStringProperty syncMessage = new SimpleStringProperty("");
    /** 是否有资源维护任务在运行 */
    private final SimpleBooleanProperty syncRunning = new SimpleBooleanProperty(false);
    /** 差异文件状态描述（对应 wutheringwaves-cli-manager 的 checkout 差异文件） */
    private final SimpleStringProperty diffStatus = new SimpleStringProperty("");
    /** 是否存在 .predownload 目录（应用预下载可用） */
    private final SimpleBooleanProperty applyEnabled = new SimpleBooleanProperty(false);

    /**
     * 各服务器在游戏目录中的差异文件（相对游戏根目录）。
     * 参考 timetetng/wutheringwaves-cli-manager 的 SERVER_DIFF_FILES：
     * 官服(cn)/国际服(global)/WeGame 共用 Kuro 登录文件；B服使用 B 站 SDK 文件。
     */
    private static final Map<SourceType, List<String>> SERVER_DIFF_FILES = Map.of(
            SourceType.DEFAULT,  List.of("Client/Binaries/Win64/kuro_login.dll",
                                         "Client/Content/Paks/pakchunk1-Kuro-Win64-Shipping.pak"),
            SourceType.GLOBAL,   List.of("Client/Binaries/Win64/kuro_login.dll",
                                         "Client/Content/Paks/pakchunk1-Kuro-Win64-Shipping.pak"),
            SourceType.WE_GAME,  List.of("Client/Binaries/Win64/kuro_login.dll",
                                         "Client/Content/Paks/pakchunk1-Kuro-Win64-Shipping.pak"),
            SourceType.BILIBILI, List.of("Client/Binaries/Win64/bilibili_sdk.dll",
                                         "Client/Content/Paks/pakchunk1-Bilibili-Win64-Shipping.pak")
    );

    /** 各服务器对应的 appId（写入 launcherDownloadConfig.json） */
    private static final Map<SourceType, String> SERVER_APP_ID = Map.of(
            SourceType.DEFAULT,  "10003",
            SourceType.GLOBAL,   "50004",
            SourceType.WE_GAME,  "10003",
            SourceType.BILIBILI, "10004"
    );


    private final ObservableList<String> startUpParams;
    public GameBaseSettingViewModel() {
        startUpParams = Config.setting().getStartUpParams();
    }



    public void init() {
        gameSourceType.bindBidirectional(Config.setting().gameRootDirSourceProperty());
        gameDir.bindBidirectional(Config.setting().gameRootDirProperty());
        gameAppStartPath.bindBidirectional(Config.setting().gameStarAppPathProperty());
        gameAppStartCustom.bindBidirectional(Config.setting().gameStartAppCustomProperty());

        checkServerExist();
        updateCurrentServerName();

        gameSourceType.addListener((observable, oldValue, newValue) -> refreshDiffStatus());
        gameDir.addListener((observable, oldValue, newValue) -> {
            refreshDiffStatus();
            checkPredownloadExists();
        });
        refreshDiffStatus();
        checkPredownloadExists();
    }








    /**
     * 删除指定启动参数
     * @param index
     */
    public void deleteParam(int index) {
        startUpParams.remove(index);
    }

    /**
     * 添加启动参数
     * @param param
     */
    public void addParam(String param) {
        startUpParams.add(param);
    }

    public boolean isDx11(){
        return startUpParams.contains("-dx11");
    }
    public boolean isDx12(){
        return startUpParams.contains("-dx12");
    }

    /**
     * 启动参数中添加dx11
     */
    public void addDx11(){
        int index = startUpParams.indexOf("-dx12");
        if (index != -1){
            startUpParams.set(index,"-dx11");
        }else {
            startUpParams.add("-dx11");
        }
    }

    /**
     * 启动参数中添加dx12
     */
    public void addDx12(){
        int index = startUpParams.indexOf("-dx11");
        if (index != -1){
            startUpParams.set(index,"-dx12");
        }else {
            startUpParams.add("-dx12");
        }
    }

    public void replaceParam(String param1, String param2) {

    }












    private void checkServerExist(){
        sourceTypeDisabled01.set(gameSourceType.get() != SourceType.DEFAULT);
        sourceTypeDisabled02.set(gameSourceType.get() != SourceType.BILIBILI);
        sourceTypeDisabled03.set(gameSourceType.get() != SourceType.WE_GAME);
        sourceTypeDisabled04.set(gameSourceType.get() != SourceType.GLOBAL);

        File dir = new File(gameDir.get() + File.separator + "servers");
        File defaultServerFile = new File(dir,"default");
        File bilibiliServerFile = new File(dir,"bilibili");
        File wegameServerFile = new File(dir,"wegame");
        File globalServerFile = new File(dir,"global");
        if (!sourceTypeDisabled01.get() && defaultServerFile.exists()) {
            sourceTypeDisabled01.set(false);
        }
        if (!sourceTypeDisabled02.get() && bilibiliServerFile.exists()) {
            sourceTypeDisabled02.set(false);
        }
        if (!sourceTypeDisabled03.get() && wegameServerFile.exists()) {
            sourceTypeDisabled03.set(false);
        }
        if (!sourceTypeDisabled04.get() && globalServerFile.exists()) {
            sourceTypeDisabled04.set(false);
        }
    }

    @Override
    public void onViewAdded() {

    }

    @Override
    public void onViewRemoved() {
        checkGameLogOpen();
        Config.setting().save();
    }
    /**
     * description: 检测游戏日志是否被关闭
     */
    private void checkGameLogOpen() {
        CheckGameConfigTask task = new CheckGameConfigTask();
        task.setOnSucceeded(workerStateEvent -> {
            Boolean value = task.getValue();
            if (!value) { //游戏日志可能被关闭了
                Platform.runLater(() -> {
                    NotificationManager.message(MessageInfo.success(LanguageManager.getString("ui.main.sync.message.log.close")));
                });
            }
        });
        task.setOnFailed(workerStateEvent -> {
            System.err.println("检测游戏日志状态失败: " + workerStateEvent.getSource().getException());
        });
        Thread.startVirtualThread(task);
    }

    public boolean changeServer(SourceType sourceType) {
        return false;
    }



    public SourceType getGameSourceType() {
        return gameSourceType.get();
    }

    public SimpleObjectProperty<SourceType> gameSourceTypeProperty() {
        return gameSourceType;
    }

    public String getGameDir() {
        return gameDir.get();
    }

    public SimpleStringProperty gameDirProperty() {
        return gameDir;
    }

    public boolean isSourceTypeDisabled01() {
        return sourceTypeDisabled01.get();
    }

    public SimpleBooleanProperty sourceTypeDisabled01Property() {
        return sourceTypeDisabled01;
    }

    public boolean isSourceTypeDisabled02() {
        return sourceTypeDisabled02.get();
    }

    public SimpleBooleanProperty sourceTypeDisabled02Property() {
        return sourceTypeDisabled02;
    }

    public boolean isSourceTypeDisabled03() {
        return sourceTypeDisabled03.get();
    }

    public SimpleBooleanProperty sourceTypeDisabled03Property() {
        return sourceTypeDisabled03;
    }

    public boolean isSourceTypeDisabled04() {
        return sourceTypeDisabled04.get();
    }

    public SimpleBooleanProperty sourceTypeDisabled04Property() {
        return sourceTypeDisabled04;
    }

    public String getGameAppStartPath() {
        return gameAppStartPath.get();
    }

    public SimpleStringProperty gameAppStartPathProperty() {
        return gameAppStartPath;
    }

    public boolean isGameAppStartCustom() {
        return gameAppStartCustom.get();
    }

    public SimpleBooleanProperty gameAppStartCustomProperty() {
        return gameAppStartCustom;
    }

    public ObservableList<String> getStartUpParams() {
        return startUpParams;
    }

    private void updateCurrentServerName(){
        gameSourceType.addListener((observable, oldValue, newValue) -> {
            setCurrentServerName(getServerDisplayName(newValue));
        });
        setCurrentServerName(getServerDisplayName(gameSourceType.get()));
    }

    private String getServerDisplayName(SourceType type){
        if (type == null) return "默认";
        return switch (type) {
            case DEFAULT -> LanguageManager.getString("ui.setting.default.game_dir.radio01");
            case BILIBILI -> LanguageManager.getString("ui.setting.default.game_dir.radio04");
            case WE_GAME -> LanguageManager.getString("ui.setting.default.game_dir.radio02");
            case GLOBAL -> LanguageManager.getString("ui.setting.default.game_dir.radio03");
        };
    }

    /**
     * 官服 ⇄ 国际服 互转（均在 Kuro 渠道，差异文件相同，仅更新 appId）
     */
    public void switchToGlobalServer(){
        SourceType current = gameSourceType.get();
        SourceType target = switch (current) {
            case GLOBAL -> SourceType.DEFAULT;
            case DEFAULT -> SourceType.GLOBAL;
            case BILIBILI -> SourceType.DEFAULT;
            case WE_GAME -> SourceType.DEFAULT;
        };
        checkout(target);
    }

    /**
     * B服 ⇄ 官服 互转（真实交换 Kuro / Bilibili 差异文件）
     */
    public void switchToOfficialServer(){
        SourceType current = gameSourceType.get();
        SourceType target = switch (current) {
            case BILIBILI -> SourceType.DEFAULT;
            case DEFAULT -> SourceType.BILIBILI;
            case WE_GAME -> SourceType.BILIBILI;
            case GLOBAL -> SourceType.BILIBILI;
        };
        checkout(target);
    }

    /**
     * 参考 wutheringwaves-cli-manager 的 WGameManager.checkout：
     * 1) 将所有服务器的差异文件重命名为 .bak（禁用）
     * 2) 将目标服务器的 .bak 还原（启用）
     * 3) 更新游戏目录下的 launcherDownloadConfig.json（appId/group）
     * 4) 更新本工具的区服配置
     */
    public void checkout(SourceType target) {
        String dirPath = gameDir.get();
        if (dirPath == null || dirPath.isBlank()) {
            NotificationManager.message(MessageInfo.warning(
                    LanguageManager.getString("ui.game_manager.server_switch.no_dir")));
            return;
        }
        File gameDirFile = new File(dirPath);
        Thread.startVirtualThread(() -> {
            boolean missing = doCheckoutFiles(gameDirFile, target);
            updateLauncherConfig(gameDirFile, target);
            boolean finalMissing = missing;
            Platform.runLater(() -> {
                gameSourceType.set(target);
                refreshDiffStatus();
                if (finalMissing) {
                    NotificationManager.message(MessageInfo.warning(
                            LanguageManager.getString("ui.game_manager.server_switch.missing")));
                    startSync();
                } else {
                    NotificationManager.message(MessageInfo.success(
                            LanguageManager.getString("ui.game_manager.server_switch.success")));
                }
            });
        });
    }

    /** 禁用全部差异文件并将目标服务器的差异文件还原，返回是否存在缺失文件 */
    private boolean doCheckoutFiles(File gameDir, SourceType target) {
        Set<String> allDiff = new LinkedHashSet<>();
        for (List<String> files : SERVER_DIFF_FILES.values()) {
            allDiff.addAll(files);
        }
        // 1. 禁用：原文件 -> .bak
        for (String rel : allDiff) {
            File f = new File(gameDir, rel);
            File bak = new File(gameDir, rel + ".bak");
            if (bak.exists()) {
                f.delete();
            } else if (f.exists()) {
                f.renameTo(bak);
            }
        }
        // 2. 启用目标服务器：.bak -> 原文件
        boolean missing = false;
        for (String rel : SERVER_DIFF_FILES.getOrDefault(target, List.of())) {
            File f = new File(gameDir, rel);
            File bak = new File(gameDir, rel + ".bak");
            if (bak.exists()) {
                if (f.exists()) {
                    f.delete();
                }
                bak.renameTo(f);
            } else if (!f.exists()) {
                missing = true;
            }
        }
        return missing;
    }

    /** 更新游戏目录下的 launcherDownloadConfig.json，写入 appId 与 group，保留已有 version */
    private void updateLauncherConfig(File gameDir, SourceType target) {
        File cfg = new File(gameDir, "launcherDownloadConfig.json");
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root;
            if (cfg.exists()) {
                root = (ObjectNode) mapper.readTree(cfg);
            } else {
                root = mapper.createObjectNode();
            }
            root.put("appId", SERVER_APP_ID.get(target));
            root.put("group", "default");
            if (!root.has("version")) {
                root.put("version", "");
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(cfg, root);
        } catch (Exception e) {
            System.err.println("更新 launcherDownloadConfig.json 失败: " + e.getMessage());
        }
    }

    public String getCurrentServerName() {
        return currentServerName.get();
    }

    public SimpleStringProperty currentServerNameProperty() {
        return currentServerName;
    }

    public void setCurrentServerName(String currentServerName) {
        this.currentServerName.set(currentServerName);
    }

    // ===================== 资源维护（sync / predownload / apply） =====================

    public double getSyncProgress() {
        return syncProgress.get();
    }

    public SimpleDoubleProperty syncProgressProperty() {
        return syncProgress;
    }

    public String getSyncMessage() {
        return syncMessage.get();
    }

    public SimpleStringProperty syncMessageProperty() {
        return syncMessage;
    }

    public boolean isSyncRunning() {
        return syncRunning.get();
    }

    public SimpleBooleanProperty syncRunningProperty() {
        return syncRunning;
    }

    public String getDiffStatus() {
        return diffStatus.get();
    }

    public SimpleStringProperty diffStatusProperty() {
        return diffStatus;
    }

    public boolean isApplyEnabled() {
        return applyEnabled.get();
    }

    public SimpleBooleanProperty applyEnabledProperty() {
        return applyEnabled;
    }

    /**
     * 全量校验/修复（对应 CLI 的 sync）：扫描游戏目录全部文件，下载缺失/损坏项。
     */
    public void startSync() {
        if (syncRunning.get()) {
            return;
        }
        String dir = gameDir.get();
        if (dir == null || dir.isBlank()) {
            NotificationManager.message(MessageInfo.warning(
                    LanguageManager.getString("ui.game_manager.server_switch.no_dir")));
            return;
        }
        Task<ResponseBody<Boolean>> task = new GameResourceSyncTask(gameSourceType.get(), dir, false);
        launchMaintain(task,
                LanguageManager.getString("ui.game_manager.resource.sync.success"),
                LanguageManager.getString("ui.game_manager.resource.sync.fail"));
    }

    /**
     * 预下载更新资源到 .predownload 目录（对应 CLI 的 predownload）。
     */
    public void startPredownload() {
        if (syncRunning.get()) {
            return;
        }
        String dir = gameDir.get();
        if (dir == null || dir.isBlank()) {
            NotificationManager.message(MessageInfo.warning(
                    LanguageManager.getString("ui.game_manager.server_switch.no_dir")));
            return;
        }
        Task<ResponseBody<Boolean>> task = new GamePredownloadTask(gameSourceType.get(), dir, false);
        launchMaintain(task,
                LanguageManager.getString("ui.game_manager.resource.predownload.done"),
                LanguageManager.getString("ui.game_manager.resource.predownload.fail"));
    }

    /**
     * 应用预下载资源（对应 CLI 的 apply_predownload）：合并资源、更新版本号并强制校验。
     */
    public void applyPredownload() {
        if (syncRunning.get()) {
            return;
        }
        String dir = gameDir.get();
        if (dir == null || dir.isBlank()) {
            NotificationManager.message(MessageInfo.warning(
                    LanguageManager.getString("ui.game_manager.server_switch.no_dir")));
            return;
        }
        Task<ResponseBody<Boolean>> task = new GamePredownloadTask(gameSourceType.get(), dir, true);
        launchMaintain(task,
                LanguageManager.getString("ui.game_manager.resource.apply.done"),
                LanguageManager.getString("ui.game_manager.resource.apply.fail"));
    }

    /** 统一启动一个资源维护 Task，并绑定进度/消息，结束弹通知 */
    private void launchMaintain(Task<ResponseBody<Boolean>> task, String okMsg, String failMsg) {
        syncRunning.set(true);
        syncProgress.bind(task.progressProperty());
        syncMessage.bind(task.messageProperty());
        CompletableFuture<ResponseBody<Boolean>> future = new CompletableFuture<>();
        task.setOnSucceeded(event -> future.complete(task.getValue()));
        task.setOnFailed(event -> future.completeExceptionally(task.getException()));
        Thread.startVirtualThread(task);
        future.whenComplete((res, ex) -> Platform.runLater(() -> {
            syncRunning.set(false);
            syncProgress.unbind();
            syncMessage.unbind();
            syncProgress.set(-1);
            syncMessage.set("");
            checkPredownloadExists();
            if (ex != null) {
                String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                NotificationManager.message(MessageInfo.warning(failMsg + "：" + detail));
            } else if (res != null && res.getCode() == 200) {
                NotificationManager.message(MessageInfo.success(okMsg));
            } else {
                NotificationManager.message(MessageInfo.warning(res != null && res.getMsg() != null ? res.getMsg() : failMsg));
            }
        }));
    }

    /** 计算并显示当前服务器差异文件状态 */
    private void refreshDiffStatus() {
        SourceType st = gameSourceType.get();
        String dir = gameDir.get();
        if (st == null || dir == null || dir.isBlank()) {
            diffStatus.set("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String rel : SERVER_DIFF_FILES.getOrDefault(st, List.of())) {
            File f = new File(dir, rel);
            File bak = new File(dir, rel + ".bak");
            String state = f.exists() ? "启用" : (bak.exists() ? "已备份" : "缺失");
            sb.append(new File(rel).getName()).append("：").append(state).append("    ");
        }
        diffStatus.set(sb.toString().trim());
    }

    /** 是否存在 .predownload 目录（决定“应用预下载”按钮是否可用） */
    private void checkPredownloadExists() {
        String dir = gameDir.get();
        if (dir == null || dir.isBlank()) {
            applyEnabled.set(false);
            return;
        }
        applyEnabled.set(new File(dir, ".predownload").exists());
    }

}