# Ample Xposed

이 레포의 ReVanced(Morphe) 패치 중 카카오톡과 디시인사이드 패치를 **원본 APK를 건드리지 않고** libxposed 후크로 옮긴 LSPosed 모듈입니다.

- Xposed API: libxposed `minApiVersion=101`, 컴파일은 `102.0.0` (공식 example과 동일)
- 난독화된 메서드 탐색: [DexKit](https://github.com/LuckyPray/DexKit) — 패치의 Fingerprint(소스 파일명, 문자열, 시그니처)를 런타임에 같은 방식으로 적용
- 설정: 모듈 앱의 스위치 → RemotePreferences → 대상 앱에 즉시 반영

## 대상 버전

| 앱 | 패키지 | 기준 버전 |
|---|---|---|
| 카카오톡 | `com.kakao.talk` | 26.9.1 |
| 디시인사이드 | `com.dcinside.app.android` | 5.3.20 |

다른 버전에서는 Fingerprint가 맞지 않아 일부 후크가 설치되지 않을 수 있습니다. 후보가 정확히 1개가 아니면 **잘못된 메서드를 후킹하지 않도록 설치를 건너뜁니다.**

## 옮긴 패치

### 디시인사이드
| 설정 | 원본 패치 |
|---|---|
| 광고 차단 | Disable ad controller, Hide Main Ad, Disable Image Ad, Disable Comment Ad |
| 업데이트 확인 끄기 | Disable update check |
| 운영자 공지 숨기기 | Hide DC official notices |

### 카카오톡
| 설정 | 원본 패치 |
|---|---|
| 광고 차단 | Disable ChatRoomAdController, Disable chat room list ad (아이템 필터 포함), Disable Friend Lists ad, Disable OpenChat feed ad (로드/렌더), Disable Pay banner ad, Disable ShortForm ad, Remove BizBoard, Remove Feed ad, Remove Focus ad, Remove Native ad, Remove Tab banner ad |
| 트래커 차단 | Disable S2Event, Disable SDK Tracker, Disable Sentry |

## 옮기지 않은 것

- **재서명/재포장 전용 패치** (서명 검증 우회, 패키지명 변경, 브랜딩, 설정 탭/리소스 주입, Spoof 계열, Pine/패킷 핸들러 주입): Xposed에서는 필요가 없습니다.
- **메서드 중간 바이트코드를 바꾸는 패치** (99/300+ 미읽음 제한 해제, 페이지 표시 숨기기, 홈 컴포넌트 숨기기, 작성자 식별자 표시, 최근 본 글 갤러리 필터, 갤러리 워치 모드, DCCon 차단 등): 메서드 후크로는 중간 지점을 바꿀 수 없어서 그대로 옮길 수 없습니다. 각각 앱의 호출 흐름에 맞춰 다시 설계해야 하고, 실제 APK로 확인이 필요합니다.
- **카카오톡 확장 기능 패치** (삭제 메시지 보기, 읽음 확인, 메시지 상세, 키워드 로그, 고스트 모드 등): 앱 안에 코드를 주입하는 확장 라이브러리 기반이라 별도 이식이 필요합니다.
- 카카오톡 More 탭 광고, 오픈채팅 목록 광고 필터, Talk Share 로그: 바이트코드 편집 의존도가 커서 보류했습니다.

## 빌드

```
cd xposed
./gradlew :app:assembleRelease
```

릴리스 빌드는 디버그 키로 서명됩니다(공식 example과 동일). 배포하려면 `app/build.gradle.kts`의 `signingConfig`를 바꾸세요.

## 설치와 확인

1. APK 설치 후 LSPosed에서 모듈을 켜고 범위에 카카오톡/디시인사이드가 체크돼 있는지 확인합니다.
2. 대상 앱을 강제 종료하고 다시 실행합니다.
3. logcat에서 태그 `AmpleXposed`를 봅니다.
   - `후크 설치 → 클래스#메서드`: 성공
   - `후보 N개 (정확히 1개여야 함) — 건너뜀`: 그 Fingerprint가 현재 버전과 안 맞음. 해당 후크만 꺼진 상태입니다.

## 구조

```
app/src/main/java/dev/ssch71/ample/xposed/
  ModuleMain.java            XposedModule 진입점, 패키지별 AppHooks 실행
  core/Dex.java              DexKit 래퍼 (정확히 1개일 때만 반환)
  core/HookContext.java      returnEarly / replace / before / after 도우미
  core/Settings.java         설정 정의 (UI와 후크가 공유)
  dcinside/DcInsideHooks.java
  kakaotalk/KakaoTalkHooks.java
  ui/                        LSPosed 서비스 연결 + 설정 화면
```

새 후크를 추가하려면 `Settings`에 항목을 추가하고, 해당 앱의 `*Hooks`에 `ctx.install(...)` 한 줄과 메서드를 추가하면 됩니다.

## 알려진 한계

- 이 작업 환경에서는 Gradle/Maven 저장소에 접근할 수 없어 **컴파일과 실기기 테스트를 하지 못했습니다.** 처음 빌드할 때 의존성 버전(`dexkit 2.3.0`, AGP `9.2.1`)이나 API 오타로 오류가 날 수 있습니다.
- Fingerprint는 ReVanced 패치 소스에서 옮긴 것이라 실제 APK와 대조해 검증하지 않았습니다. opcode 시퀀스는 DexKit의 연속 일치(Contains)로 찾기 때문에, 원본 패치가 비연속으로 매칭하던 곳은 후보 0개로 건너뛸 수 있습니다.
- 앱을 실행할 때마다 DexKit로 탐색하므로 시작 시 수백 ms~수 초가 추가될 수 있습니다. 필요하면 결과를 캐시하는 쪽으로 개선할 수 있습니다.

## 라이선스

레포 루트의 `LICENSE`를 따릅니다. 저작권 표기: ssch71.
