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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 预下载 / 应用预下载（对应 wutheringwaves-cli-manager 的 predownload / apply_predownload）。
 * - apply=false：把更新资源下载到游戏目录下的 .predownload 临时目录，并记录版本信息。
 * - apply=true：将 .predownload 中的文件移动到游戏根目录，更新版本号，再执行一次全量校验。
 */
public class GamePredownloadTask extends Task<ResponseBody<Boolean>> {
    private static final Logger LOG = LoggerFactory.getLogger(GamePredownloadTask.class);
    private static final int THREADS = 8;
    private static final String VERSION_FILE = "predownload_version.json";

    private final SourceType sourceType;
    private final String gameDir;
    private final boolean apply;

    public GamePredownloadTask(SourceType sourceType, String gameDir, boolean apply) {
        this.sourceType = sourceType;
        this.gameDir = gameDir;
        this.apply = apply;
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
        return apply ? applyPredownload(gameDirFile) : downloadPredownload(gameDirFile);
    }

    private ResponseBody<Boolean> downloadPredownload(File gameDirFile) {
        LauncherResource launcherResource = runTask(new LauncherResourceTask(GameResourceSyncTask.toLauncherType(sourceType))).getData();
        if (launcherResource == null) {
            return ResponseBody.create(-1, "获取启动器配置失败", false);
        }
        UpdateData pre = launcherResource.getPredownload();
        if (pre == null) {
            return ResponseBody.create(-1, "当前服务器未开放预下载，或未能获取预下载配置", false);
        }
        GameResourceList list = runTask(new GameResourceListGetTask(pre.getResourceJsonUrl())).getData();
        if (list == null || list.getResource() == null) {
            return ResponseBody.create(-1, "获取预下载资源清单失败", false);
        }

        File preDir = new File(gameDirFile, ".predownload");
        preDir.mkdirs();

        // 记录版本与服务器，供 apply 时校验
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode info = mapper.createObjectNode();
            info.put("version", pre.getVersion());
            info.put("server", sourceType.name());
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(preDir, VERSION_FILE), info);
        } catch (Exception e) {
            LOG.error("保存预下载版本信息失败", e);
        }

        String cdn = pre.getCdnList().getFirst().getUrl();
        String base = pre.getResourcesBasePath();
        List<FileInfo> resources = list.getResource();
        List<DownloadTaskInfo> tasks = new ArrayList<>();
        for (FileInfo fi : resources) {
            // 下载到 .predownload 目录：以 preDir 作为“游戏根目录”拼装保存路径
            tasks.add(new DownloadTaskInfo(cdn + base + "/" + fi.getDest(), preDir.getAbsolutePath(), fi));
        }

        boolean ok = downloadAll(tasks, "预下载");
        return ResponseBody.create(ok ? 200 : -1, ok ? "预下载资源下载完成" : "部分预下载资源下载失败", ok);
    }

    private ResponseBody<Boolean> applyPredownload(File gameDirFile) {
        File preDir = new File(gameDirFile, ".predownload");
        File verFile = new File(preDir, VERSION_FILE);
        if (!preDir.exists() || !verFile.exists()) {
            return ResponseBody.create(-1, "未找到有效的预下载内容，请先执行预下载", false);
        }

        String targetVersion;
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode info = (ObjectNode) mapper.readTree(verFile);
            String server = info.has("server") ? info.get("server").asText() : "";
            if (!server.isEmpty() && !server.equals(sourceType.name())) {
                return ResponseBody.create(-1, "预下载的服务器类型与当前不符", false);
            }
            targetVersion = info.has("version") ? info.get("version").asText() : null;
        } catch (Exception e) {
            return ResponseBody.create(-1, "预下载版本信息损坏", false);
        }

        updateMessage("正在合并预下载资源...");
        int count = 0;
        List<File> files = listFilesRecursive(preDir);
        for (File f : files) {
            if (!f.isFile() || VERSION_FILE.equals(f.getName())) {
                continue;
            }
            String rel = preDir.toURI().relativize(f.toURI()).getPath();
            File dest = new File(gameDirFile, rel);
            try {
                dest.getParentFile().mkdirs();
                Files.move(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                count++;
            } catch (Exception e) {
                LOG.error("移动预下载文件失败: " + f, e);
            }
        }
        LOG.info("已合并 {} 个文件", count);

        // 清理预下载目录
        deleteRecursive(preDir);

        // 更新版本号
        File cfg = new File(gameDirFile, "launcherDownloadConfig.json");
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = cfg.exists() ? (ObjectNode) mapper.readTree(cfg) : mapper.createObjectNode();
            if (targetVersion != null) {
                root.put("version", targetVersion);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(cfg, root);
        } catch (Exception e) {
            LOG.error("更新 launcherDownloadConfig.json 版本失败", e);
        }

        // 应用后强制做一次全量校验，确保万无一失
        updateMessage("正在进行最终完整性校验...");
        ResponseBody<Boolean> syncResult = runTask(new GameResourceSyncTask(sourceType, gameDirFile.getAbsolutePath(), false));
        boolean ok = syncResult.getCode() == 200;
        return ResponseBody.create(ok ? 200 : -1, ok ? "预下载资源已应用并更新完成" : "资源已合并，但最终校验部分失败", ok);
    }

    private boolean downloadAll(List<DownloadTaskInfo> tasks, String label) {
        int total = tasks.size();
        if (total == 0) {
            updateMessage(label + "：无需下载");
            return true;
        }
        updateMessage(label + "中 (" + total + " 个文件)...");
        AtomicInteger done = new AtomicInteger(0);
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            for (DownloadTaskInfo ti : tasks) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    GameFileDownloadTask d = new GameFileDownloadTask(client, ti);
                    d.run();
                    int c = done.incrementAndGet();
                    updateProgress(c, total);
                    updateMessage(label + " " + c + "/" + total);
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
            LOG.error(label + "失败", e);
            return false;
        } finally {
            pool.shutdown();
        }
    }

    private List<File> listFilesRecursive(File dir) {
        List<File> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) {
            return result;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                result.addAll(listFilesRecursive(c));
            } else {
                result.add(c);
            }
        }
        return result;
    }

    private void deleteRecursive(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) {
                    deleteRecursive(c);
                } else {
                    c.delete();
                }
            }
        }
        dir.delete();
    }

    private static <T> ResponseBody<T> runTask(Task<ResponseBody<T>> task) {
        task.run();
        ResponseBody<T> value = task.getValue();
        return value != null ? value : ResponseBody.create(-1, "任务执行失败", null);
    }
}
