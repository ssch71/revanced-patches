/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.core;

import android.content.pm.ApplicationInfo;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * DexKit 래퍼. ReVanced 패치의 Fingerprint(소스 파일명, 문자열, 시그니처, opcode)로
 * 난독화된 메서드를 런타임에 찾는다.
 *
 * <p>찾은 결과가 정확히 1개가 아니면 null을 돌려준다. 잘못된 메서드를 후킹하는 것보다
 * 후크를 설치하지 않는 쪽이 안전하기 때문이다.
 */
public final class Dex implements AutoCloseable {
    private static boolean nativeLoaded;

    private final DexKitBridge bridge;
    private final ClassLoader classLoader;
    private final HookContext ctx;

    private Dex(DexKitBridge bridge, ClassLoader classLoader, HookContext ctx) {
        this.bridge = bridge;
        this.classLoader = classLoader;
        this.ctx = ctx;
    }

    static synchronized Dex open(HookContext ctx, ApplicationInfo moduleInfo, ClassLoader appClassLoader) {
        if (!nativeLoaded) {
            // 대상 앱의 클래스로더는 모듈의 .so를 모르기 때문에 경로로 직접 로드한다.
            System.load(moduleInfo.nativeLibraryDir + "/libdexkit.so");
            nativeLoaded = true;
        }
        // useMemoryDexFile=true: 메모리에 올라온 dex를 그대로 분석한다.
        return new Dex(DexKitBridge.create(appClassLoader, true), appClassLoader, ctx);
    }

    /** 현재 앱 클래스로더의 dex 전체를 대상으로 하는 새 메서드 매처. */
    public static MethodMatcher method() {
        return MethodMatcher.create();
    }

    public static ClassMatcher clazz() {
        return ClassMatcher.create();
    }

    /** 소스 파일명(예: "SftAdFragment.kt")으로 클래스를 좁히는 매처. */
    public static ClassMatcher source(String sourceFile) {
        return ClassMatcher.create().source(sourceFile);
    }

    public List<MethodData> findAll(MethodMatcher matcher) {
        return new ArrayList<>(bridge.findMethod(FindMethod.create().matcher(matcher)));
    }

    public List<ClassData> findClasses(ClassMatcher matcher) {
        return new ArrayList<>(bridge.findClass(FindClass.create().matcher(matcher)));
    }

    /**
     * 매처와 추가 조건에 맞는 메서드를 정확히 1개일 때만 반환한다.
     *
     * @param label 로그용 이름
     */
    public Executable single(String label, MethodMatcher matcher, Predicate<MethodData> extra) {
        List<MethodData> found = new ArrayList<>();
        for (MethodData data : findAll(matcher)) {
            if (extra == null || extra.test(data)) found.add(data);
        }
        if (found.size() != 1) {
            ctx.warn(label + ": 후보 " + found.size() + "개 (정확히 1개여야 함) — 건너뜀");
            return null;
        }
        try {
            return resolve(found.get(0));
        } catch (Throwable t) {
            ctx.warn(label + ": 리플렉션 변환 실패: " + t);
            return null;
        }
    }

    public Executable single(String label, MethodMatcher matcher) {
        return single(label, matcher, null);
    }

    /**
     * 조건에 맞는 모든 후보를 리플렉션 객체로 돌려준다. DexKit만으로 좁힐 수 없는 조건을
     * 호출자가 리플렉션으로 직접 검사해야 할 때 쓴다.
     */
    public List<Executable> all(String label, MethodMatcher matcher, Predicate<MethodData> extra) {
        List<Executable> out = new ArrayList<>();
        for (MethodData data : findAll(matcher)) {
            if (extra != null && !extra.test(data)) continue;
            try {
                out.add(resolve(data));
            } catch (Throwable t) {
                ctx.warn(label + ": 리플렉션 변환 실패: " + t);
            }
        }
        return out;
    }

    /** DexKit 결과를 리플렉션 Executable로 바꾼다. */
    public Executable resolve(MethodData data) throws Exception {
        return data.isConstructor()
                ? data.getConstructorInstance(classLoader)
                : data.getMethodInstance(classLoader);
    }

    /** 메서드 본문에서 className#methodName 을 호출하는지. */
    public static Predicate<MethodData> calls(String className, String methodName) {
        return data -> {
            for (MethodData invoked : data.getInvokes()) {
                if (invoked.getClassName().equals(className)
                        && invoked.getMethodName().equals(methodName)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** 메서드 본문에서 declaringClass의 어떤 메서드든 호출하는지. */
    public static Predicate<MethodData> callsClass(String className) {
        return data -> {
            for (MethodData invoked : data.getInvokes()) {
                if (invoked.getClassName().equals(className)) return true;
            }
            return false;
        };
    }

    public static Predicate<MethodData> modifiers(int mask) {
        return data -> (data.getModifiers() & mask) == mask;
    }

    @Override
    public void close() {
        bridge.close();
    }
}
