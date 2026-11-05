/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.core;

/** 앱 하나에 대한 후크 묶음. */
public interface AppHooks {
    /** 대상 패키지명. */
    String packageName();

    /** 후크를 설치한다. 개별 후크 실패는 {@link HookContext#install}로 격리할 것. */
    void install(HookContext ctx);
}
