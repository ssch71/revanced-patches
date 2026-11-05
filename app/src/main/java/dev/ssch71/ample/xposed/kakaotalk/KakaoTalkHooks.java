/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.kakaotalk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import dev.ssch71.ample.xposed.core.AppHooks;
import dev.ssch71.ample.xposed.core.Dex;
import dev.ssch71.ample.xposed.core.HookContext;
import dev.ssch71.ample.xposed.core.Setting;
import dev.ssch71.ample.xposed.core.Settings;

/**
 * 카카오톡(26.9.1 기준) 패치의 Xposed 버전.
 *
 * <p>대응하는 ReVanced 패치: Disable ChatRoomAdController, Disable chat room list ad,
 * Disable Friend Lists ad, Disable OpenChat feed ad, Disable Pay banner ad,
 * Disable ShortForm ad, Remove BizBoard / Feed ad / Focus ad / Native ad / Tab banner ad,
 * Disable S2Event, Disable SDK Tracker, Disable Sentry.
 *
 * <p>대부분의 카카오톡 클래스는 난독화돼 있지만 Kotlin 소스 파일명(SourceFile 속성)은
 * 남아 있어서, ReVanced Fingerprint와 같은 방식으로 소스 파일명 + 시그니처로 찾는다.
 */
public final class KakaoTalkHooks implements AppHooks {
    private static final String PKG = "com.kakao.talk";
    private static final int PUBLIC_FINAL = Modifier.PUBLIC | Modifier.FINAL;
    private static final String CONTINUATION = "kotlin.coroutines.Continuation";
    private static final String COROUTINE_RESUME_MSG =
            "call to 'resume' before 'invoke' with coroutine";

    @Override
    public String packageName() {
        return PKG;
    }

    @Override
    public void install(HookContext ctx) {
        // 광고
        ctx.install("kakao/chatroom-ad-controller", () -> chatRoomAdController(ctx));
        ctx.install("kakao/chatlist-ad", () -> chatListAd(ctx));
        ctx.install("kakao/friend-ad", () -> friendListAd(ctx));
        ctx.install("kakao/openchat-feed-ad", () -> openChatFeedAd(ctx));
        ctx.install("kakao/pay-banner-ad", () -> payBannerAd(ctx));
        ctx.install("kakao/shortform-ad", () -> shortFormAd(ctx));
        ctx.install("kakao/bizboard", () -> bizBoard(ctx));
        ctx.install("kakao/feed-ad-layout", () -> feedAdLayout(ctx));
        ctx.install("kakao/focus-ad", () -> focusAd(ctx));
        ctx.install("kakao/native-ad", () -> nativeAd(ctx));
        ctx.install("kakao/tab-banner-ad", () -> tabBannerAd(ctx));
        // 트래커
        ctx.install("kakao/s2event", () -> s2Event(ctx));
        ctx.install("kakao/sdk-tracker", () -> sdkTracker(ctx));
        ctx.install("kakao/sentry", () -> sentry(ctx));
    }

    private static MethodMatcher inSource(String sourceFile) {
        return Dex.method().declaredClass(Dex.source(sourceFile));
    }

    private static final Setting ADS = Settings.KAKAO_BLOCK_ADS;
    private static final Setting TRACKERS = Settings.KAKAO_BLOCK_TRACKERS;

    // ================================================================ 광고

    /** Disable ChatRoomAdController: 오픈링크 채팅방 BizBoard 광고 컨트롤러. */
    private void chatRoomAdController(HookContext ctx) {
        Executable m = ctx.dex().single(
                "kakao/chatroom-ad-controller",
                inSource("OpenLinkChatAdController.kt")
                        .returnType("void")
                        .paramCount(0)
                        .opNames(List.of("iget-boolean", "if-eqz", "invoke-virtual", "move-result", "if-eqz")),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.calls("com.kakao.adfit.ads.bizboard.BizBoardAdView", "loadAd")));
        ctx.returnEarly("kakao/chatroom-ad-controller", m, ADS, null);
    }

    /** Disable chat room list ad: 채팅 목록 광고 (BizBoard, 글로벌 광고, 광고 아이템). */
    private void chatListAd(HookContext ctx) {
        Dex dex = ctx.dex();

        // 채팅 목록 BizBoard 사용 여부 → false
        Executable bizBoardEnabled = dex.single(
                "kakao/chatlist-ad/bizboard",
                inSource("ChatListAdHelper.kt")
                        .paramCount(2)
                        .paramTypes(null, "boolean")
                        .returnType("boolean"),
                Dex.modifiers(PUBLIC_FINAL).and(callsNoArgBoolean()));
        ctx.returnEarly("kakao/chatlist-ad/bizboard", bizBoardEnabled, ADS, Boolean.FALSE);

        // 글로벌 광고 사용 여부 (suspend 함수라 Object 반환) → false
        Executable globalEnabled = dex.single(
                "kakao/chatlist-ad/global",
                inSource("ChatListAdHelper.kt")
                        .paramTypes("android.content.Context", "boolean", CONTINUATION)
                        .returnType("java.lang.Object"),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.calls("android.content.res.Resources", "getConfiguration")));
        ctx.returnEarly("kakao/chatlist-ad/global", globalEnabled, ADS, Boolean.FALSE);

        // 통화 폴더 BizBoard 사용 여부 → false
        Executable cecall = dex.single(
                "kakao/chatlist-ad/cecall",
                inSource("CecallFolderHistoryFragment.kt")
                        .paramTypes(CONTINUATION)
                        .returnType("java.lang.Object"),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.callsClass("com.kakao.talk.activity.main.chatroom.ad.ChatListAdResponse")));
        ctx.returnEarly("kakao/chatlist-ad/cecall", cecall, ADS, Boolean.FALSE);

        // 목록에 이미 들어온 광고 아이템 제거
        Set<Class<?>> adItems = classesBySource(ctx,
                "ChatListAdChatItem.kt", "AdChatItem.kt", "GlobalAdChatItem.kt");
        Executable submit = dex.single(
                "kakao/chatlist-ad/submit",
                inSource("CommonChatRoomListAdapter.kt")
                        .paramTypes("java.util.List", "kotlin.jvm.functions.Function0")
                        .returnType("void"));
        if (!adItems.isEmpty()) {
            ctx.before("kakao/chatlist-ad/submit", submit, ADS, (self, args) -> {
                Object first = args.get(0);
                if (!(first instanceof List)) return null;
                List<Object> filtered = new ArrayList<>();
                boolean removed = false;
                for (Object item : (List<?>) first) {
                    if (item != null && adItems.contains(item.getClass())) {
                        removed = true;
                    } else {
                        filtered.add(item);
                    }
                }
                if (!removed) return null;
                Object[] newArgs = args.toArray();
                newArgs[0] = filtered;
                return newArgs;
            });
        }
    }

    /** Disable Friend Lists ad: 친구 탭 BizBoard/글로벌 광고. */
    private void friendListAd(HookContext ctx) {
        Dex dex = ctx.dex();

        Executable chip = dex.single(
                "kakao/friend-ad/chip",
                inSource("FriendListChipBizBoardAdViewHolder.kt")
                        .returnType("void")
                        .paramCount(2),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.calls("com.kakao.adfit.ads.talk.TalkNativeAdBinder", "setPrivateAdEventListener")));
        ctx.returnEarly("kakao/friend-ad/chip", chip, ADS, null);

        Executable birthday = dex.single(
                "kakao/friend-ad/birthday",
                inSource("FriendTabBirthdayFriendsBizBoardAdViewModel.kt")
                        .paramTypes("com.kakao.adfit.ads.media.NativeAdBinder")
                        .returnType("void"),
                Dex.modifiers(PUBLIC_FINAL));
        ctx.returnEarly("kakao/friend-ad/birthday", birthday, ADS, null);

        Executable global = dex.single(
                "kakao/friend-ad/global",
                inSource("FriendTabGlobalAdViewModel.kt")
                        .paramTypes("java.lang.Object")
                        .returnType("java.lang.Object")
                        .opNames(List.of("new-instance")),
                Dex.modifiers(PUBLIC_FINAL));
        ctx.returnEarly("kakao/friend-ad/global", global, ADS, null);
    }

    /** Disable OpenChat feed ad: 오픈채팅 탭 피드 광고 로드/렌더. */
    private void openChatFeedAd(HookContext ctx) {
        Dex dex = ctx.dex();

        Executable load = dex.single(
                "kakao/openchat-feed-ad/load",
                inSource("OpenChatTabFeedAdViewHolder.kt")
                        .returnType("void")
                        .paramCount(2),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.calls("com.kakao.adfit.ads.feed.FeedAdLoader", "loadAd")));
        ctx.returnEarly("kakao/openchat-feed-ad/load", load, ADS, null);

        Executable render = dex.single(
                "kakao/openchat-feed-ad/render",
                inSource("OpenChatTabFeedAdViewHolder.kt")
                        .returnType("void")
                        .paramCount(3),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.calls("com.kakao.adfit.ads.feed.FeedAdLayout", "setFeedAd")));
        ctx.returnEarly("kakao/openchat-feed-ad/render", render, ADS, null);
    }

    /** Disable Pay banner ad: 카카오페이 배너 광고. */
    private void payBannerAd(HookContext ctx) {
        Dex dex = ctx.dex();

        Executable load = dex.single(
                "kakao/pay-banner/load",
                inSource("PayAdPlatformBannerState.kt")
                        .returnType("void")
                        .paramCount(1)
                        .opNames(List.of("iget-object", "iget-object", "xor-int/lit8",
                                "invoke-virtual", "return-void")),
                Dex.modifiers(PUBLIC_FINAL));
        ctx.returnEarly("kakao/pay-banner/load", load, ADS, null);

        Executable render = dex.single(
                "kakao/pay-banner/render",
                inSource("PayAdPlatformAdFit.kt")
                        .returnType("void")
                        .paramTypes("java.lang.String", "com.kakao.adfit.ads.media.NativeAdBinder", null,
                                "kotlin.jvm.functions.Function1", "kotlin.jvm.functions.Function0",
                                "kotlin.jvm.functions.Function1", "kotlin.jvm.functions.Function0",
                                null, "int", "int"),
                Dex.modifiers(PUBLIC_FINAL | Modifier.STATIC));
        ctx.returnEarly("kakao/pay-banner/render", render, ADS, null);
    }

    /** Disable ShortForm ad: 숏폼 광고 로드/렌더. */
    private void shortFormAd(HookContext ctx) {
        Dex dex = ctx.dex();

        Executable load = dex.single(
                "kakao/shortform-ad/load",
                inSource("SftAdFragment.kt")
                        .returnType("void")
                        .paramCount(0)
                        .usingEqStrings("feedAdLoader"),
                Dex.modifiers(Modifier.PRIVATE | Modifier.FINAL)
                        .and(Dex.calls("com.kakao.adfit.ads.feed.FeedAdLoader", "isLoading")));
        ctx.returnEarly("kakao/shortform-ad/load", load, ADS, null);

        Executable render = dex.single(
                "kakao/shortform-ad/render",
                inSource("SftAdFragment.kt")
                        .returnType("void")
                        .paramCount(2)
                        .usingEqStrings("feedAdLayout"),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.calls("com.kakao.adfit.ads.feed.FeedAdLayout", "setFeedAd")));
        ctx.returnEarly("kakao/shortform-ad/render", render, ADS, null);
    }

    /** Remove BizBoard: BizBoardFrameLayout을 크기 0으로 측정하고 숨긴다. */
    private void bizBoard(HookContext ctx) throws Exception {
        Class<?> layout = ctx.loadClass("com.kakao.talk.widget.ad.BizBoardFrameLayout");
        Method onMeasure = layout.getDeclaredMethod("onMeasure", int.class, int.class);
        Method setMeasured = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
        setMeasured.setAccessible(true);

        ctx.replace("kakao/bizboard", onMeasure, ADS, (self, args) -> {
            setMeasured.invoke(self, 0, 0);
            ((View) self).setVisibility(View.GONE);
            return null;
        });
    }

    /** Remove Feed ad: FeedAdLayout을 숨기고 setFeedAd를 막는다. */
    private void feedAdLayout(HookContext ctx) throws Exception {
        Class<?> layout = ctx.loadClass("com.kakao.adfit.ads.feed.FeedAdLayout");

        Constructor<?> ctor = layout.getDeclaredConstructor(Context.class, AttributeSet.class, int.class);
        ctx.after("kakao/feed-ad-layout/ctor", ctor, ADS, (self, args, result) -> {
            View view = (View) self;
            view.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.width = 0;
                lp.height = 0;
            }
            view.requestLayout();
        });

        for (Method m : layout.getDeclaredMethods()) {
            if (m.getName().equals("setFeedAd")) {
                ctx.returnEarly("kakao/feed-ad-layout/setFeedAd", m, ADS, null);
            }
        }
    }

    /** Remove Focus ad: 포커스 광고 로드를 성공한 것처럼 true로 반환. */
    private void focusAd(HookContext ctx) {
        Executable m = ctx.dex().single(
                "kakao/focus-ad",
                Dex.method()
                        .paramTypes("com.kakao.adfit.ads.focus.FocusAdLoader$OnAdLoadListener")
                        .returnType("boolean")
                        .usingEqStrings(" owner is destroyed.", " loading is already started.",
                                "Request Focus AD", " loading is started.", "Focus ad is cached. [id = "),
                Dex.modifiers(Modifier.PUBLIC));
        ctx.returnEarly("kakao/focus-ad", m, ADS, Boolean.TRUE);
    }

    /** Remove Native ad: 네이티브 광고 로드를 true로 반환. */
    private void nativeAd(HookContext ctx) {
        Executable m = ctx.dex().single(
                "kakao/native-ad",
                Dex.method()
                        .name("load")
                        .paramTypes("int", "com.kakao.adfit.ads.media.NativeAdLoader$AdLoadListener")
                        .returnType("boolean")
                        .usingEqStrings(" owner is destroyed.", " loading is already started.",
                                "Request Native AD", "Native ad is cached. [id = "),
                Dex.modifiers(PUBLIC_FINAL));
        ctx.returnEarly("kakao/native-ad", m, ADS, Boolean.TRUE);
    }

    /** Remove Tab banner ad: 메인 탭 하단 배너. */
    private void tabBannerAd(HookContext ctx) throws Exception {
        Executable render = ctx.dex().single(
                "kakao/tab-banner/render",
                inSource("MainTailBannerOverlay.kt")
                        .returnType("void")
                        .usingStrings(".MainTailBannerOverlay ("),
                Dex.modifiers(PUBLIC_FINAL | Modifier.STATIC));
        ctx.returnEarly("kakao/tab-banner/render", render, ADS, null);

        Class<?> banner = ctx.loadClass("com.kakao.adfit.ads.talk.TalkBannerAdView");
        ctx.returnEarly("kakao/tab-banner/load", banner.getDeclaredMethod("loadAd"), ADS, null);
    }

    // ================================================================ 트래커

    /** Disable S2Event: S2 이벤트 저장/전송을 막는다. */
    private void s2Event(HookContext ctx) throws Exception {
        Dex dex = ctx.dex();

        Executable save = dex.single(
                "kakao/s2event/save",
                inSource("Tracker.kt")
                        .returnType("java.lang.Object")
                        .usingEqStrings(COROUTINE_RESUME_MSG, "AllDone")
                        .opNames(List.of("instance-of", "if-eqz", "move-object", "check-cast", "iget",
                                "const/high16", "and-int", "if-eqz", "sub-int/2addr", "iput", "goto",
                                "new-instance")),
                Dex.modifiers(PUBLIC_FINAL));
        ctx.returnEarly("kakao/s2event/save", save, TRACKERS, Boolean.FALSE);

        Executable send = dex.single(
                "kakao/s2event/send",
                inSource("S2EventRepository.kt")
                        .returnType("java.lang.Object")
                        .usingEqStrings(COROUTINE_RESUME_MSG),
                Dex.modifiers(PUBLIC_FINAL)
                        .and(Dex.callsClass("com.kakao.talk.core.tracker.data.model.S2EventResponse")));
        Object unit = ctx.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
        ctx.returnEarly("kakao/s2event/send", send, TRACKERS, unit);
    }

    /** Disable SDK Tracker: AdFit SDK 트래커 URL을 빈 문자열로. */
    private void sdkTracker(HookContext ctx) {
        Executable m = ctx.dex().single(
                "kakao/sdk-tracker",
                Dex.method()
                        .declaredClass(ClassMatcher.create()
                                .className("com.kakao.adfit", StringMatchType.StartsWith))
                        .paramCount(0)
                        .returnType("java.lang.String")
                        .usingEqStrings("id", "sdktype", "sdkver", "cnt", "test", "Y", "ctag", "ukeyword"),
                Dex.modifiers(PUBLIC_FINAL));
        ctx.returnEarly("kakao/sdk-tracker", m, TRACKERS, "");
    }

    /** Disable Sentry: SentryAndroid.init 실패 처리 메서드를 막는다. */
    private void sentry(HookContext ctx) {
        Executable m = ctx.dex().single(
                "kakao/sentry",
                inSource("SentryAndroid.java")
                        .returnType("void")
                        .usingEqStrings("Fatal error during SentryAndroid.init(...)",
                                "Failed to initialize Sentry's SDK"),
                Dex.modifiers(Modifier.PUBLIC | Modifier.STATIC));
        ctx.returnEarly("kakao/sentry", m, TRACKERS, null);
    }

    // ================================================================ 도우미

    /** 본문에서 "인자 없고 boolean을 돌려주는 메서드"를 호출하는지. */
    private static Predicate<MethodData> callsNoArgBoolean() {
        return data -> {
            for (MethodData invoked : data.getInvokes()) {
                if (invoked.getParamTypeNames().isEmpty()
                        && "boolean".equals(invoked.getReturnTypeName())) {
                    return true;
                }
            }
            return false;
        };
    }

    /** 소스 파일명으로 클래스를 찾아 Class 객체 집합으로 돌려준다. 못 찾은 것은 로그만 남긴다. */
    private static Set<Class<?>> classesBySource(HookContext ctx, String... sourceFiles) {
        Set<Class<?>> out = new HashSet<>();
        for (String source : sourceFiles) {
            List<ClassData> found = ctx.dex().findClasses(Dex.source(source));
            if (found.size() != 1) {
                ctx.warn("kakao/class " + source + ": 후보 " + found.size() + "개 — 건너뜀");
                continue;
            }
            try {
                out.add(ctx.loadClass(found.get(0).getName()));
            } catch (ClassNotFoundException e) {
                ctx.warn("kakao/class " + source + ": 클래스 로드 실패 " + e);
            }
        }
        return out;
    }
}
