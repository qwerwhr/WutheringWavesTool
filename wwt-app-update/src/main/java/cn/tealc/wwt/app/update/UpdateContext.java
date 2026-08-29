package cn.tealc.wwt.app.update;

public interface UpdateContext {
    boolean isDev();

    String currentVersion();

    String skipVersion();
}