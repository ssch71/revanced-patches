/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.core;

import java.util.List;

public final class Settings {
    /** RemotePreferences 그룹 이름. 설정 앱과 후크 프로세스가 같은 이름을 써야 한다. */
    public static final String PREFS_GROUP = "settings";

    public static final String GROUP_DC = "디시인사이드";
    public static final String GROUP_KAKAO = "카카오톡";

    public static final Setting DC_BLOCK_ADS = new Setting(
            GROUP_DC, "dc_block_ads", "광고 차단",
            "메인/이미지/댓글 광고와 광고 컨트롤러를 비활성화합니다.", true);
    public static final Setting DC_DISABLE_UPDATE_CHECK = new Setting(
            GROUP_DC, "dc_disable_update_check", "업데이트 확인 끄기",
            "앱 실행 시 최신 버전 확인을 건너뜁니다.", true);
    public static final Setting DC_HIDE_OFFICIAL_NOTICES = new Setting(
            GROUP_DC, "dc_hide_official_notices", "운영자 공지 숨기기",
            "갤러리 공지 목록에서 '운영자'가 쓴 공지를 숨깁니다.", false);

    public static final Setting KAKAO_BLOCK_ADS = new Setting(
            GROUP_KAKAO, "kakao_block_ads", "광고 차단",
            "채팅 목록/친구 탭/오픈채팅/Pay/숏폼/배너 광고를 비활성화합니다.", true);
    public static final Setting KAKAO_BLOCK_TRACKERS = new Setting(
            GROUP_KAKAO, "kakao_block_trackers", "트래커 차단",
            "S2Event, AdFit SDK 트래커, Sentry를 비활성화합니다.", true);

    public static final List<Setting> ALL = List.of(
            DC_BLOCK_ADS, DC_DISABLE_UPDATE_CHECK, DC_HIDE_OFFICIAL_NOTICES,
            KAKAO_BLOCK_ADS, KAKAO_BLOCK_TRACKERS);

    private Settings() {
    }
}
