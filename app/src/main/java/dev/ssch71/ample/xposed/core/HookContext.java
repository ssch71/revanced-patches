/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.core;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Executable;
import java.util.List;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedModule;

/** 앱별 후크 클래스에 넘겨 주는 실행 환경 + 후크 등록 도우미. */
public final class HookContext {
    public static final String TAG = "AmpleXposed";

    public final XposedModule module;
    public final ClassLoader classLoader;
    public final String packageName;
    private final SharedPreferences prefs;
    private Dex dex;

    public HookContext(XposedModule module, ClassLoader classLoader, String packageName) {
        this.module = module;
        this.classLoader = classLoader;
        this.packageName = packageName;
        this.prefs = module.getRemotePreferences(Settings.PREFS_GROUP);
    }

    /** DexKit를 처음 쓸 때 연다. close()는 {@link #finish()}에서 한다. */
    public Dex dex() {
        if (dex == null) {
            dex = Dex.open(this, module.getModuleApplicationInfo(), classLoader);
        }
        return dex;
    }

    public void finish() {
        if (dex != null) {
            try {
                dex.close();
            } catch (Throwable ignored) {
            }
            dex = null;
        }
    }

    // ---- 설정 ----

    public boolean enabled(Setting setting) {
        try {
            return prefs.getBoolean(setting.key, setting.defaultValue);
        } catch (Throwable t) {
            return setting.defaultValue;
        }
    }

    // ---- 로그 ----

    public void info(String msg) {
        module.log(Log.INFO, TAG, msg);
    }

    public void warn(String msg) {
        module.log(Log.WARN, TAG, msg);
    }

    public void error(String msg, Throwable t) {
        module.log(Log.ERROR, TAG, msg, t);
    }

    // ---- 클래스 ----

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, classLoader);
    }

    // ---- 후크 등록 ----

    @FunctionalInterface
    public interface Installer {
        void run() throws Throwable;
    }

    @FunctionalInterface
    public interface After {
        /** @param result 원본 메서드의 반환값 (생성자/void는 null) */
        void run(Object self, List<Object> args, Object result) throws Throwable;
    }

    @FunctionalInterface
    public interface Before {
        /**
         * 원본 호출 전에 실행한다. 새 인자 배열을 돌려주면 그 인자로 원본을 호출하고,
         * null이면 인자를 그대로 둔다.
         */
        Object[] run(Object self, List<Object> args) throws Throwable;
    }

    /** 후크 하나의 설치 실패가 다른 후크에 영향을 주지 않도록 감싼다. */
    public void install(String label, Installer installer) {
        try {
            installer.run();
        } catch (Throwable t) {
            error(label + ": 후크 설치 실패", t);
        }
    }

    /** 설정이 켜져 있으면 원본을 실행하지 않고 value를 돌려준다. (ReVanced의 returnEarly) */
    public void returnEarly(String label, Executable target, Setting setting, Object value) {
        if (target == null) return;
        module.hook(target).intercept(chain -> enabled(setting) ? value : chain.proceed());
        info(label + ": 후크 설치 → " + describe(target));
    }

    @FunctionalInterface
    public interface Replace {
        /** 원본 대신 실행된다. 반환값이 메서드의 반환값이 된다 (void는 null). */
        Object run(Object self, List<Object> args) throws Throwable;
    }

    /** 설정이 켜져 있으면 원본 대신 replacement를 실행한다. */
    public void replace(String label, Executable target, Setting setting, Replace replacement) {
        if (target == null) return;
        module.hook(target).intercept(chain -> {
            if (!enabled(setting)) return chain.proceed();
            return replacement.run(chain.getThisObject(), chain.getArgs());
        });
        info(label + ": 후크 설치 → " + describe(target));
    }

    /** 원본을 실행한 뒤 action을 실행한다. */
    public void after(String label, Executable target, Setting setting, After action) {
        if (target == null) return;
        module.hook(target).intercept(chain -> {
            Object result = chain.proceed();
            if (enabled(setting)) {
                try {
                    action.run(chain.getThisObject(), chain.getArgs(), result);
                } catch (Throwable t) {
                    error(label + ": after 실행 실패", t);
                }
            }
            return result;
        });
        info(label + ": 후크 설치 → " + describe(target));
    }

    /** 원본을 실행하기 전에 action을 실행한다. */
    public void before(String label, Executable target, Setting setting, Before action) {
        if (target == null) return;
        module.hook(target).intercept((Chain chain) -> {
            if (enabled(setting)) {
                try {
                    Object[] newArgs = action.run(chain.getThisObject(), chain.getArgs());
                    if (newArgs != null) return chain.proceed(newArgs);
                } catch (Throwable t) {
                    error(label + ": before 실행 실패", t);
                }
            }
            return chain.proceed();
        });
        info(label + ": 후크 설치 → " + describe(target));
    }

    private static String describe(Executable e) {
        return e.getDeclaringClass().getName() + "#" + e.getName();
    }
}
