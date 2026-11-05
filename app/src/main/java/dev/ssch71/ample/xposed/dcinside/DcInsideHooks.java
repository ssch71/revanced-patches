/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.dcinside;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.ssch71.ample.xposed.core.AppHooks;
import dev.ssch71.ample.xposed.core.Dex;
import dev.ssch71.ample.xposed.core.HookContext;
import dev.ssch71.ample.xposed.core.Settings;

/**
 * 디시인사이드(5.3.20 기준) 패치의 Xposed 버전.
 *
 * <p>대응하는 ReVanced 패치: Disable ad controller, Hide Main Ad, Disable Image Ad,
 * Disable Comment Ad, Disable update check, Hide DC official notices.
 *
 * <p>서명 검증 우회, 패키지명 변경, 브랜딩, 설정 화면 주입 같은 "재서명/재포장" 전용
 * 패치는 원본 APK를 그대로 쓰는 Xposed에서는 필요가 없어 옮기지 않았다.
 */
public final class DcInsideHooks implements AppHooks {
    private static final String PKG = "com.dcinside.app.android";
    private static final int PRIVATE_FINAL = Modifier.PRIVATE | Modifier.FINAL;

    @Override
    public String packageName() {
        return PKG;
    }

    @Override
    public void install(HookContext ctx) {
        ctx.install("dc/ad-controller", () -> adController(ctx));
        ctx.install("dc/main-ad", () -> mainAd(ctx));
        ctx.install("dc/image-ad", () -> imageAd(ctx));
        ctx.install("dc/comment-ad", () -> commentAd(ctx));
        ctx.install("dc/update-check", () -> updateCheck(ctx));
        ctx.install("dc/official-notices", () -> officialNotices(ctx));
    }

    // ------------------------------------------------------------------ 광고

    /** Disable ad controller: 광고 컨트롤러 메서드를 즉시 반환. */
    private void adController(HookContext ctx) {
        Executable target = ctx.dex().single(
                "dc/ad-controller",
                Dex.method()
                        .declaredClass(ClassMatcher.create()
                                .className("com.dcinside.app.ad.support", StringMatchType.StartsWith))
                        .returnType("void")
                        .paramCount(0)
                        .opNames(List.of("iget-object", "if-nez", "goto", "iget-object", "invoke-virtual")),
                Dex.modifiers(PRIVATE_FINAL));
        ctx.returnEarly("dc/ad-controller", target, Settings.DC_BLOCK_ADS, null);
    }

    /** Hide Main Ad: 메인 광고 ViewHolder 생성 직후 itemView를 숨긴다. */
    private void mainAd(HookContext ctx) {
        Executable target = ctx.dex().single(
                "dc/main-ad",
                Dex.method()
                        .name("<init>")
                        .paramTypes("android.view.View")
                        .usingEqStrings("itemView")
                        .opNames(List.of("const-string", "invoke-static", "invoke-direct",
                                "new-instance", "invoke-direct", "move-result-object")));
        ctx.after("dc/main-ad", target, Settings.DC_BLOCK_ADS, (self, args, result) -> {
            View itemView = (View) args.get(0);
            itemView.setVisibility(View.GONE);
            itemView.setMinimumHeight(0);
        });
    }

    /** Disable Image Ad: 이미지 광고 뷰를 숨기고 갱신을 막는다. */
    private void imageAd(HookContext ctx) throws Exception {
        final String cls = "com.dcinside.app.view.PostReadImageAdView";
        Class<?> adView = ctx.loadClass(cls);

        Constructor<?> ctor = adView.getDeclaredConstructor(Context.class, AttributeSet.class, int.class);
        ctx.after("dc/image-ad/ctor", ctor, Settings.DC_BLOCK_ADS, (self, args, result) -> {
            View view = (View) self;
            try {
                Method setHeight = adView.getDeclaredMethod("setAdViewHeight", int.class);
                setHeight.setAccessible(true);
                setHeight.invoke(self, 0);
            } catch (NoSuchMethodException ignored) {
                // 이름이 바뀐 버전: 숨기기만 한다.
            }
            view.setVisibility(View.GONE);
        });

        Executable refresh = ctx.dex().single(
                "dc/image-ad/refresh",
                Dex.method()
                        .declaredClass(ClassMatcher.create().className(cls))
                        .returnType("void")
                        .usingEqStrings("galleryGrade"),
                Dex.modifiers(Modifier.PUBLIC | Modifier.FINAL));
        ctx.returnEarly("dc/image-ad/refresh", refresh, Settings.DC_BLOCK_ADS, null);
    }

    /** Disable Comment Ad: 댓글 광고 뷰 두 종류를 크기 0으로 만들고 숨긴다. */
    private void commentAd(HookContext ctx) throws Exception {
        for (String cls : new String[]{
                "com.dcinside.app.view.PostReadCommentAdView",
                "com.dcinside.app.view.PostReadCommentTopAdView"}) {
            Class<?> adView = ctx.loadClass(cls);
            Constructor<?> ctor = adView.getDeclaredConstructor(Context.class, AttributeSet.class, int.class);
            ctx.after("dc/comment-ad/" + adView.getSimpleName(), ctor, Settings.DC_BLOCK_ADS,
                    (self, args, result) -> {
                        View view = (View) self;
                        view.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
                        view.setVisibility(View.GONE);

                        // 로딩 상태 AtomicBoolean을 true로 해서 광고 로딩을 건너뛰게 한다.
                        Field loading = null;
                        for (Field f : adView.getDeclaredFields()) {
                            if (f.getType() != AtomicBoolean.class) continue;
                            if (loading != null) return; // 후보가 여러 개면 건드리지 않는다.
                            loading = f;
                        }
                        if (loading != null) {
                            loading.setAccessible(true);
                            ((AtomicBoolean) loading.get(self)).set(true);
                        }
                    });
        }
    }

    // ------------------------------------------------------------------ 기타

    /** Disable update check: 최신 버전 확인 메서드가 false를 돌려주게 한다. */
    private void updateCheck(HookContext ctx) {
        Executable target = ctx.dex().single(
                "dc/update-check",
                Dex.method()
                        .declaredClass(ClassMatcher.create()
                                .className("com.dcinside.app.main", StringMatchType.StartsWith))
                        .paramTypes("java.lang.String")
                        .returnType("boolean")
                        .usingEqStrings("\\.")
                        .opNames(List.of("const/4", "if-eqz", "invoke-static", "move-result-object",
                                "invoke-virtual", "move-result-object", "if-eqz", "invoke-interface",
                                "move-result", "const/4", "if-lez", "goto")),
                Dex.modifiers(PRIVATE_FINAL));
        ctx.returnEarly("dc/update-check", target, Settings.DC_DISABLE_UPDATE_CHECK, Boolean.FALSE);
    }

    // ------------------------------------------------------------------ 운영자 공지

    private static final String NOTICE_TAB_NAME = "NOTICE";
    private static final String OFFICIAL_AUTHOR = "운영자";
    private static final Map<Class<?>, Object> AUTHOR_FIELDS = new ConcurrentHashMap<>();
    private static final Object NO_FIELD = new Object();

    /** Hide DC official notices: 갤러리 공지 목록에서 '운영자' 글을 제거한다. */
    private void officialNotices(HookContext ctx) {
        // (1) 갤러리 공지 목록 응답 핸들러: (응답, int, 탭타입)
        List<Executable> handlers = ctx.dex().all(
                "dc/notice/handler",
                Dex.method()
                        .declaredClass(ClassMatcher.create()
                                .className("com.dcinside.app.post.fragments", StringMatchType.StartsWith))
                        .returnType("void")
                        .paramCount(3)
                        .paramTypes(null, "int", null),
                Dex.modifiers(PRIVATE_FINAL).and(d -> !Modifier.isStatic(d.getModifiers())));

        Executable handler = null;
        for (Executable candidate : handlers) {
            if (findListGetter(candidate.getParameterTypes()[0]) != null) {
                if (handler != null) { // 후보가 둘 이상이면 모호하므로 건너뜀
                    ctx.warn("dc/notice/handler: 후보가 여러 개 — 건너뜀");
                    handler = null;
                    break;
                }
                handler = candidate;
            }
        }
        if (handler != null) {
            ctx.before("dc/notice/handler", handler, Settings.DC_HIDE_OFFICIAL_NOTICES, (self, args) -> {
                Object tab = args.get(2);
                if (!(tab instanceof Enum) || !NOTICE_TAB_NAME.equals(((Enum<?>) tab).name())) return null;
                Method getter = findListGetter(args.get(0).getClass());
                if (getter == null) return null;
                getter.setAccessible(true);
                Object list = getter.invoke(args.get(0));
                if (list instanceof List) removeOfficial((List<?>) list);
                return null;
            });
        }

        // (2) 미니 갤러리 공지 어댑터: (List, long, String)
        Executable adapter = ctx.dex().single(
                "dc/notice/mini-adapter",
                Dex.method()
                        .returnType("void")
                        .paramTypes("java.util.List", "long", "java.lang.String")
                        .usingEqStrings("item", "gallId"),
                Dex.modifiers(Modifier.PUBLIC | Modifier.FINAL));
        ctx.before("dc/notice/mini-adapter", adapter, Settings.DC_HIDE_OFFICIAL_NOTICES, (self, args) -> {
            Object first = args.get(0);
            if (!(first instanceof List)) return null;
            List<?> list = (List<?>) first;
            if (removeOfficial(list)) return null;

            // 수정 불가능한 리스트면 필터링한 복사본으로 교체해서 호출한다.
            List<Object> filtered = new ArrayList<>();
            for (Object item : list) if (!isOfficial(item)) filtered.add(item);
            Object[] newArgs = args.toArray();
            newArgs[0] = filtered;
            return newArgs;
        });
    }

    /** 응답 클래스에서 인자 없이 List를 돌려주는 getter. 정확히 1개일 때만 반환. */
    private static Method findListGetter(Class<?> type) {
        Method found = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
            if (!List.class.isAssignableFrom(m.getReturnType())) continue;
            if (found != null) return null;
            found = m;
        }
        return found;
    }

    /** @return 제자리 삭제에 성공했으면 true (삭제할 게 없어도 true), 수정 불가 리스트면 false */
    private static boolean removeOfficial(List<?> list) {
        try {
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                if (isOfficial(it.next())) it.remove();
            }
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    private static boolean isOfficial(Object post) {
        if (post == null) return false;
        Object cached = AUTHOR_FIELDS.computeIfAbsent(post.getClass(), DcInsideHooks::findAuthorField);
        if (cached == NO_FIELD) return false;
        try {
            return OFFICIAL_AUTHOR.equals(((Field) cached).get(post));
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@code @SerializedName("name")}이 붙은 String 필드를 찾는다. */
    private static Object findAuthorField(Class<?> type) {
        for (Field f : type.getDeclaredFields()) {
            if (f.getType() != String.class) continue;
            for (java.lang.annotation.Annotation a : f.getAnnotations()) {
                if (!a.annotationType().getName().endsWith("SerializedName")) continue;
                try {
                    Object value = a.annotationType().getMethod("value").invoke(a);
                    if ("name".equals(value)) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return NO_FIELD;
    }
}
