package cn.tealc.wwt.app.update.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReleaseList {
    private Release preRelease; //预览版
    private Release latestRelease; //正式版

    public ReleaseList() {
    }

    public ReleaseList(Release preRelease, Release latestRelease) {
        this.preRelease = preRelease;
        this.latestRelease = latestRelease;
    }

    public Release getPreRelease() {
        return preRelease;
    }

    public void setPreRelease(Release preRelease) {
        this.preRelease = preRelease;
    }

    public Release getLatestRelease() {
        return latestRelease;
    }

    public void setLatestRelease(Release latestRelease) {
        this.latestRelease = latestRelease;
    }
}