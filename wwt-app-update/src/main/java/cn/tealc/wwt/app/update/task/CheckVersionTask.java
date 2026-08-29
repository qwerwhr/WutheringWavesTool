package cn.tealc.wwt.app.update.task;

import cn.tealc.wwt.app.update.UpdateConstants;
import cn.tealc.wwt.app.update.UpdateContext;
import cn.tealc.wwt.app.update.UpdateResult;
import cn.tealc.wwt.app.update.model.Release;
import cn.tealc.wwt.app.update.model.ReleaseList;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CheckVersionTask extends Task<UpdateResult<Release>> {
    private static final Logger LOG = LoggerFactory.getLogger(CheckVersionTask.class);

    private static final String TIP = "发现新版本：%s,可在设置中获取更新详细信息";
    private static final String TIP_ERROR = "检查版本更新失败，请检查网络状况";

    private final HttpClient client;
    private final UpdateContext context;

    public CheckVersionTask(HttpClient client, UpdateContext context) {
        this.client = client;
        this.context = context;
    }

    @Override
    protected UpdateResult<Release> call() throws Exception {
        UpdateResult<Release> releaseData = getNetReleaseData();
        if (releaseData != null) {
            return releaseData;
        }
        return UpdateResult.error(-1, "无法检测更新");
    }

    private UpdateResult<Release> getNetReleaseData() {
        boolean isDev = context.isDev();
        String[] urls = isDev
                ? new String[]{UpdateConstants.URL_APP_UPDATE_DEV, UpdateConstants.URL_APP_UPDATE_DEV_2}
                : new String[]{UpdateConstants.URL_APP_UPDATE, UpdateConstants.URL_APP_UPDATE_2};

        for (int i = 0; i < urls.length; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urls[i])).GET().timeout(Duration.ofSeconds(8)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    ObjectMapper mapper = new ObjectMapper();
                    ReleaseList releaseList = mapper.readValue(response.body(), ReleaseList.class);
                    if (releaseList != null) {
                        Release latestRelease = releaseList.getLatestRelease();
                        String version = latestRelease.getVersion();
                        double net = Double.parseDouble(version.replace(".", ""));
                        double now = Double.parseDouble(context.currentVersion().replace(".", ""));
                        String skipVersion = context.skipVersion();
                        if (skipVersion != null && !skipVersion.isEmpty()) {
                            double skip = Double.parseDouble(skipVersion.replace(".", ""));
                            if (net <= skip) {
                                LOG.info("检测到跳过版本更新");
                                return UpdateResult.noUpdate("无更新");
                            }
                        }

                        if (now < net) {
                            LOG.info("检测到有新版本需要更新");
                            return UpdateResult.success(latestRelease, String.format(TIP, version));
                        } else {
                            LOG.info("检测到无新版本需要更新");
                            return UpdateResult.noUpdate("无更新");
                        }
                    }
                } else if (response.statusCode() == 404) {
                    if (i < urls.length - 1) {
                        LOG.warn("{} 返回404，尝试备用地址", urls[i]);
                        continue;
                    }
                    return UpdateResult.error(404, "找不到更新信息：404");
                } else {
                    LOG.error("{} 返回状态码: {}", urls[i], response.statusCode());
                    if (i < urls.length - 1) {
                        LOG.warn("尝试备用地址");
                        continue;
                    }
                    return UpdateResult.error(-1, "无法检测更新，错误代码：" + response.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                LOG.error("{} 请求失败", urls[i], e);
                if (i < urls.length - 1) {
                    LOG.warn("尝试备用地址");
                    continue;
                }
                return UpdateResult.error(-1, "无法检测更新");
            }
        }
        return UpdateResult.error(-1, "无法检测更新");
    }
}