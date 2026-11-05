/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.core;

/** 설정 화면과 후크 양쪽에서 같은 키/기본값을 쓰기 위한 정의. */
public final class Setting {
    public final String key;
    public final String title;
    public final String summary;
    public final String group;
    public final boolean defaultValue;

    Setting(String group, String key, String title, String summary, boolean defaultValue) {
        this.group = group;
        this.key = key;
        this.title = title;
        this.summary = summary;
        this.defaultValue = defaultValue;
    }
}
