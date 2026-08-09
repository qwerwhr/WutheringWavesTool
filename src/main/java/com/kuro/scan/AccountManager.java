package com.kuro.scan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 扫码登录账号管理器（单例）。
 * 移植自 KuRo_Scanner 的 AccountManager，使用 scan_accounts.json 持久化。
 */
public class AccountManager {
    private static final String FILE_NAME = "scan_accounts.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile AccountManager instance;

    private final List<ScanAccount> accounts = new ArrayList<>();

    private AccountManager() {
        load();
    }

    public static AccountManager getInstance() {
        if (instance == null) {
            synchronized (AccountManager.class) {
                if (instance == null) {
                    instance = new AccountManager();
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
            List<ScanAccount> list = MAPPER.readValue(file, new TypeReference<List<ScanAccount>>() {
            });
            if (list != null) {
                accounts.addAll(list);
            }
        } catch (Exception e) {
            // 解析失败时保留空列表，避免崩溃
        }
    }

    private void sync() {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_NAME), accounts);
        } catch (Exception ignored) {
        }
    }

    public void addAccount(String name, String uid, String token, String mobile, String note) {
        ScanAccount account = new ScanAccount(name, uid, token, mobile, note == null ? "" : note);
        accounts.add(account);
        sync();
    }

    public void addAccount(String name, String uid, String token, String mobile, String note, String server, String level) {
        ScanAccount account = new ScanAccount(name, uid, token, mobile, note == null ? "" : note, server, level);
        accounts.add(account);
        sync();
    }

    public void deleteAccount(int index) {
        if (index >= 0 && index < accounts.size()) {
            accounts.remove(index);
            sync();
        }
    }

    public ScanAccount getAccount(int index) {
        if (index >= 0 && index < accounts.size()) {
            return accounts.get(index);
        }
        return null;
    }

    public int size() {
        return accounts.size();
    }

    public List<ScanAccount> getAll() {
        return new ArrayList<>(accounts);
    }

    public void setNote(int index, String note) {
        ScanAccount account = getAccount(index);
        if (account != null) {
            account.setNote(note);
            sync();
        }
    }

    /** 根据字段(如 uid)查找账号下标。 */
    public Optional<Integer> findIndexByUid(String key, String value) {
        for (int i = 0; i < accounts.size(); i++) {
            ScanAccount a = accounts.get(i);
            String field;
            switch (key) {
                case "uid" -> field = a.getUid();
                case "token" -> field = a.getToken();
                case "name" -> field = a.getName();
                default -> field = null;
            }
            if (field != null && field.equals(value)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /** uid 是否已存在。 */
    public boolean containsUid(String uid) {
        return findIndexByUid("uid", uid).isPresent();
    }
}
