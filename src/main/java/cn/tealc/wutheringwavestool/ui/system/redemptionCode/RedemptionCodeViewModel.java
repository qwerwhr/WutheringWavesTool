package cn.tealc.wutheringwavestool.ui.system.redemptionCode;

import cn.tealc.wutheringwavestool.base.AppInjector;
import cn.tealc.wutheringwavestool.base.NotificationKey;
import cn.tealc.wutheringwavestool.model.RedemptionCodeItem;
import cn.tealc.wutheringwavestool.model.ResponseBody;
import cn.tealc.teafx.utils.message.MessageInfo;
import cn.tealc.teafx.utils.message.MessageType;
import cn.tealc.wutheringwavestool.thread.system.RedemptionCodeGetTask;
import cn.tealc.wutheringwavestool.ui.base.BaseViewModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.saxsys.mvvmfx.MvvmFX;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RedemptionCodeViewModel extends BaseViewModel {
    private static final String MC1001 = "mc1001";
    private static final String MC1002 = "mc1002";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObservableList<RedemptionCodeItem> cnCodeList = FXCollections.observableArrayList();
    private final ObservableList<RedemptionCodeItem> globalCodeList = FXCollections.observableArrayList();
    private final SimpleBooleanProperty loading = new SimpleBooleanProperty(false);

    /**
     * 判断兑换码是否仍在有效期内（含当天 23:59:59）
     */
    private static boolean isCurrentlyValid(RedemptionCodeItem item) {
        if (!item.isValid()) return false;
        String end = item.getEndTime();
        if (end == null || end.isEmpty()) return true; // 无截止时间默认有效
        try {
            LocalDateTime endTime = LocalDateTime.parse(end, FMT);
            return !LocalDateTime.now().isAfter(endTime);
        } catch (Exception e) {
            return true; // 解析失败默认保留
        }
    }

    public void initialize() {
        loadRedemptionCodes();
    }

    public void loadRedemptionCodes() {
        loading.set(true);
        RedemptionCodeGetTask task = new RedemptionCodeGetTask();
        task.setOnSucceeded(workerStateEvent -> {
            ResponseBody<Map<String, List<RedemptionCodeItem>>> value = task.getValue();
            if (value.getCode() == 200 && value.getData() != null) {
                Map<String, List<RedemptionCodeItem>> data = value.getData();
                saveCache(data);
                applyData(data, false);
            } else {
                // 网络/接口异常：回退到本地缓存
                Map<String, List<RedemptionCodeItem>> cached = loadCache();
                if (cached != null) {
                    applyData(cached, true);
                    MvvmFX.getNotificationCenter().publish(NotificationKey.MESSAGE,
                            MessageInfo.warning("兑换码获取失败，已显示本地缓存数据"), false);
                } else {
                    MvvmFX.getNotificationCenter().publish(NotificationKey.MESSAGE,
                            MessageInfo.warning(value == null ? "获取兑换码失败" : value.getMsg()), false);
                }
            }
            loading.set(false);
        });
        task.setOnFailed(workerStateEvent -> {
            loading.set(false);
            Map<String, List<RedemptionCodeItem>> cached = loadCache();
            if (cached != null) {
                applyData(cached, true);
                MvvmFX.getNotificationCenter().publish(NotificationKey.MESSAGE,
                        MessageInfo.warning("兑换码获取失败，已显示本地缓存数据"), false);
            } else {
                MvvmFX.getNotificationCenter().publish(NotificationKey.MESSAGE,
                        MessageInfo.error("获取兑换码失败"), false);
            }
        });
        Thread.startVirtualThread(task);
    }

    /**
     * 应用数据到列表（过滤过期 + 按截止时间升序），fromCache 仅用于日志区分
     */
    private void applyData(Map<String, List<RedemptionCodeItem>> data, boolean fromCache) {
        List<RedemptionCodeItem> cnList = data.get(MC1001);
        List<RedemptionCodeItem> globalList = data.get(MC1002);
        Comparator<RedemptionCodeItem> byEndTime =
                Comparator.comparing(RedemptionCodeItem::getEndTime, Comparator.nullsLast(Comparator.naturalOrder()));
        if (cnList != null) {
            cnCodeList.setAll(cnList.stream()
                    .filter(RedemptionCodeViewModel::isCurrentlyValid)
                    .sorted(byEndTime)
                    .collect(Collectors.toList()));
        }
        if (globalList != null) {
            globalCodeList.setAll(globalList.stream()
                    .filter(RedemptionCodeViewModel::isCurrentlyValid)
                    .sorted(byEndTime)
                    .collect(Collectors.toList()));
        }
    }

    private File cacheFile() {
        return new File("redemption_codes_cache.json");
    }

    private void saveCache(Map<String, List<RedemptionCodeItem>> data) {
        try {
            ObjectMapper mapper = AppInjector.getInstance(ObjectMapper.class);
            mapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile(), data);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
        }
    }

    private Map<String, List<RedemptionCodeItem>> loadCache() {
        File f = cacheFile();
        if (!f.exists()) return null;
        try {
            ObjectMapper mapper = AppInjector.getInstance(ObjectMapper.class);
            return mapper.readValue(f,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, List<RedemptionCodeItem>>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public ObservableList<RedemptionCodeItem> getCnCodeList() {
        return cnCodeList;
    }

    public ObservableList<RedemptionCodeItem> getGlobalCodeList() {
        return globalCodeList;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public SimpleBooleanProperty loadingProperty() {
        return loading;
    }
}
