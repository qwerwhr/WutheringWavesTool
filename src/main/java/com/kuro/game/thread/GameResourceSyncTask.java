package com.kuro.game.thread;

import cn.tealc.wutheringwavestool.model.ResponseBody;
import cn.tealc.wutheringwavestool.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kuro.game.model.game.DownloadTaskInfo;
import com.kuro.game.model.game.FileInfo;
import com.kuro.game.model.game.GameResourceList;
import com.kuro.game.model.launcher.LauncherResource;
import com.kuro.game.model.launcher.item.UpdateData;
import javafx.concurrent.Task;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全量校验并修复游戏文件（对应 wutheringwaves-cli-manager 的 sync）。
 * 复用项目已有的 LauncherResourceTask / GameResourceListGetTask / GameFileDownloadTask（自带 MD5 校验）。
 */
public class GameResourceSyncTask extends Task<ResponseBody<Boolean>> {
    private static final Logger LOG = LoggerFactory.getLogger(GameResourceSyncTask.class);
    private static final int THREADS = 8;

    private final SourceType sourceType;
    private final String gameDir;
    private final boolean forceMd5;

    public GameResourceSyncTask(SourceType sourceType, String gameDir, boolean forceMd5) {
        this.sourceType = sourceType;
        this.gameDir = gameDir;
        this.forceMd5 = forceMd5;
    }

    @Override
    protected ResponseBody<Boolean> call() {
        if (gameDir == null || gameDir.isBlank()) {
            return ResponseBody.create(-1, "游戏目录未设置", false);
        }
        File gameDirFile = new File(gameDir);
        if (!gameDirFile.exists()) {
            return ResponseBody.create(-1, "游戏目录不存在", false);
        }

        LauncherResource launcherResource = runTask(new LauncherResourceTask(toLauncherType(sourceType))).getData();
        if (launcherResource == null) {
            return ResponseBody.create(-1, "获取启动器配置失败", false);
        }
        UpdateData updateData = launcherResource.getUpdateData();
        if (updateData == null) {
            return ResponseBody.create(-1, "启动器配置缺失", false);
        }
        GameResourceList list = runTask(new GameResourceListGetTask(updateData.getResourceJsonUrl())).getData();
        if (list == null || list.getResource() == null) {
            return ResponseBody.create(-1, "获取资源清单失败", false);
        }

        String cdn = updateData.getCdnList().getFirst().getUrl();
        String base = updateData.getResourcesBasePath();

        // 1. 校验：收集需要下载的文件
        List<DownloadTaskInfo> toDownload = new ArrayList<>();
        List<FileInfo> resources = list.getResource();
        int total = resources.size();
        updateMessage("正在校验文件...");
        updateProgress(0, total);
        for (int i = 0; i < total; i++) {
            FileInfo fi = resources.get(i);
            File local = new File(gameDir, fi.getDest());
            boolean need;
            if (!local.exists()) {
                need = true;
            } else if (local.length() != (fi.getSize() == null ? -1 : fi.getSize())) {
                need = true;
            } else if (forceMd5) {
                need = !md5Equals(local, fi.getMd5());
            } else {
                need = false;
            }
            if (need) {
                toDownload.add(new DownloadTaskInfo(cdn + base + "/" + fi.getDest(), gameDir, fi));
            }
            updateProgress(i + 1, total);
        }

        if (toDownload.isEmpty()) {
            updateLauncherConfigVersion(gameDirFile, updateData.getVersion());
            updateMessage("所有文件校验通过，无需下载");
            return ResponseBody.create(200, "所有文件校验通过，无需下载", true);
        }

        // 2. 下载缺失/损坏文件
        boolean ok = downloadAll(toDownload);
        if (ok) {
            updateLauncherConfigVersion(gameDirFile, updateData.getVersion());
        }
        return ResponseBody.create(ok ? 200 : -1, ok ? "校验/修复完成" : "部分文件下载失败", ok);
    }

    private boolean downloadAll(List<DownloadTaskInfo> tasks) {
        int total = tasks.size();
        updateMessage("正在下载缺失/损坏文件 (" + total + " 个)...");
        AtomicInteger done = new AtomicInteger(0);
        HttpClient client = HttpClient.newBuilder().build();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            for (DownloadTaskInfo ti : tasks) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    GameFileDownloadTask d = new GameFileDownloadTask(client, ti);
                    d.run();
                    int c = done.incrementAndGet();
                    updateProgress(c, total);
                    updateMessage("已下载 " + c + "/" + total);
                    Boolean v = d.getValue();
                    return v != null && v;
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.MINUTES);
            return futures.stream().allMatch(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            LOG.error("下载失败", e);
            return false;
        } finally {
            pool.shutdown();
        }
    }

    private boolean md5Equals(File f, String expected) {
        if (expected == null) {
            return true;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            return DigestUtils.md5Hex(in).equalsIgnoreCase(expected);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateLauncherConfigVersion(File gameDirFile, String version) {
        File cfg = new File(gameDirFile, "launcherDownloadConfig.json");
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = cfg.exists() ? (ObjectNode) mapper.readTree(cfg) : mapper.createObjectNode();
            if (version != null) {
                root.put("version", version);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(cfg, root);
        } catch (Exception e) {
            LOG.error("更新 launcherDownloadConfig.json 版本失败", e);
        }
    }

    public static LauncherResourceTask.Type toLauncherType(SourceType st) {
        if (st == SourceType.BILIBILI) {
            return LauncherResourceTask.Type.BILIBILI;
        }
        if (st == SourceType.GLOBAL) {
            return LauncherResourceTask.Type.GLOBAL;
        }
        return LauncherResourceTask.Type.CN;
    }

    /** 同步执行一个 Task 并取回其 ResponseBody 结果（复用项目既有 Task 实现） */
    private static <T> ResponseBody<T> runTask(Task<ResponseBody<T>> task) {
        task.run();
        ResponseBody<T> value = task.getValue();
        return value != null ? value : ResponseBody.create(-1, "任务执行失败", null);
    }
}
