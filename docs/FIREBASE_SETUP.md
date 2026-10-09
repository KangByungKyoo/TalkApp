# EnglishTalk 2단계 설정과 테스트

대상 프로젝트: `englishtalk-ceccf` / Android 패키지: `com.minipapa.englishtalk`
함수 이름: `createRealtimeSession` / 리전: `asia-northeast3` / 런타임: Node.js 22

현재 프로젝트에는 Android 앱 등록과 서버 배포가 완료되었으며 앱의 인증 성공을 사용자가 확인했습니다.
로컬 `google-services.json` 및 서버 환경 설정은 Git에서 제외됩니다.
실제 OpenAI API 키는 Secret Manager에 보관하며 코드/저장소에는 포함하지 않습니다.
아래는 다른 개발 환경에서 설정을 재현할 때 사용하는 절차입니다.

## 인증 방식 선택

| 방식 | 서버 동작 | Android 관점 |
|---|---|---|
| 서버 프록시(unified interface) | 앱의 SDP를 받아 `/v1/realtime/calls`에 장기 키로 요청하고 SDP 응답 반환 | 임시 bearer 토큰을 앱에 배포하지 않음. SDP 생성이 필요하고 서버가 연결 초기화 경로에 포함됨. 공식 문서에서 더 간단하고 빠른 설정으로 권장함. |
| 임시 클라이언트 인증 | 서버가 `/v1/realtime/client_secrets`에서 임시 토큰 발급 | 이번 단계에서 WebRTC 구현 없이 인증만 테스트 가능. 다음 단계에서 앱이 토큰을 이용해 WebRTC 연결. 장기 키는 서버에만 보관. |

이번 단계는 임시 인증 정보를 반환하는 요구에 맞춰 두 번째 방식을 선택했습니다.
첫 번째 방식도 Android에 사용할 수 있고 보안상 장점이 있습니다. 실제 음성 단계에서
엄격한 연결 수·세션 정책 통제가 필요하면 서버 프록시 및 서버 측 세션 제어를 검토해야 합니다.

현재 발급 요청은 GA `POST https://api.openai.com/v1/realtime/client_secrets`이며,
`expires_after: { anchor: "created_at", seconds: 60 }`와
`session: { type: "realtime", model: "gpt-realtime", audio: { output: { voice: "marin" } } }`를 보냅니다.
구형 `/realtime/sessions` 또는 beta 헤더를 사용하지 않습니다.
모델은 서버 설정 `OPENAI_REALTIME_MODEL`로 변경할 수 있습니다. 실제 계정의 모델 접근 권한을 확인하세요.

임시 토큰은 만료 전 **여러 세션 생성에 사용 가능**하며 만료가 진행 중인 음성 세션을 종료하지는 않습니다.
토큰 TTL과 발급 횟수 제한은 총 사용 비용의 완전한 상한이 아닙니다.
클라이언트는 세션 구성을 바꿀 수도 있으므로 모델/프롬프트 기본값은 강제 보안 경계가 아닙니다.
프로덕션에서는 계정 단위 이용 정책, OpenAI 프로젝트 사용량 모니터링과 서버 측 세션 종료 정책을 추가해야 합니다.

## 사용자가 준비할 Firebase 콘솔 설정

1. 프로젝트 `englishtalk-ceccf`의 프로젝트 설정에서 Android 앱을 등록합니다.
   패키지 이름은 정확히 `com.minipapa.englishtalk`로 입력합니다.
2. 내려받은 `google-services.json`을 `app/google-services.json`에 놓습니다.
   이 파일은 장기 OpenAI 키가 아니며 Git에서 제외합니다. 파일이 없으면 앱은 빌드되지만 테스트 버튼은 설정 오류를 표시합니다.
3. Authentication → Sign-in method에서 **Anonymous(익명 로그인)**를 활성화합니다.
   이번 단계는 익명 UID를 쓰며, 출시 전 영구 계정 연결을 권장합니다.
4. Cloud Firestore 기본 데이터베이스 `(default)`를 생성합니다. 잠긴 규칙으로 시작합니다.
   서버 발급 한도는 Firestore 트랜잭션에 저장되며 클라이언트 접근은 차단합니다.
   새 프로젝트용 `firestore.rules`는 모든 클라이언트 읽기·쓰기를 거부합니다.
   다른 앱의 기존 DB에 배포할 때에는 기존 규칙을 검토하고 서버 전용 컬렉션 규칙을 합쳐야 합니다.
5. App Check에서 Android 앱에 **Play Integrity** 공급자를 등록합니다. 출시 서명 SHA-256과
   Play Console 연결/인증 요구사항을 확인합니다.
6. 개발용 debug APK는 Debug App Check 공급자를 사용합니다. 첫 실행 후 Android Studio Logcat의
   `DebugAppCheckProvider` 토큰을 App Check → 앱 → 디버그 토큰 관리에 등록합니다.
   이 토큰은 로그·스크린샷·Git에 공유하지 마세요. Release APK에는 Debug 공급자가 포함되지 않습니다.
7. Functions의 App Check 재사용 방지에 필요한 **Firebase App Check Token Verifier** 역할을
   함수 런타임 서비스 계정에 부여해야 합니다. 2세대 기본 계정은 보통
   `<PROJECT_NUMBER>-compute@developer.gserviceaccount.com`입니다. 실제 배포 계정을 확인하세요.
8. Cloud Functions 배포에는 **Blaze 요금제**가 필요합니다. 사용자가 비용을 검토하고 직접 변경해야 합니다.
   Functions, Firestore, Secret Manager 및 OpenAI에 비용이 발생할 수 있습니다.
   예산 알림은 지출을 자동 차단하는 장치가 아닙니다.

App Check 검증은 코드에서 항상 활성화되어 있습니다. 테스트 편의를 위해 운영 함수에서 끄지 않습니다.

## 서버 설정 및 장기 API 키 저장

프로젝트 루트에서 실행합니다. OpenAI 키를 채팅이나 파일에 적지 않고 Secret Manager 프롬프트에만 입력합니다.

```powershell
firebase login
firebase use englishtalk-ceccf
npm.cmd --prefix functions ci
firebase functions:secrets:set OPENAI_API_KEY --project englishtalk-ceccf
```

마지막 명령의 입력창에 실제 OpenAI 프로젝트 API 키를 입력합니다.
CLI 명령 인자, `.env`, Android 코드 또는 `google-services.json`에 OpenAI 키를 넣지 않습니다.
`defineSecret("OPENAI_API_KEY")`와 함수의 `secrets` 바인딩으로 런타임에서만 접근합니다.
키를 회전하면 새 버전을 생성한 뒤 함수를 재배포합니다. 기존 키를 노출했다면 폐기하세요.

`functions/runtime-config.example`을 `functions/.env.englishtalk-ceccf`로 복사합니다.
`ALLOWED_ANDROID_APP_ID`에는 Firebase 콘솔의 Android 앱 ID
(`1:...:android:...`)를 입력합니다. 프로젝트 ID나 Android 패키지명이 아닙니다.
`OPENAI_REALTIME_MODEL`은 사용 가능한 Realtime 모델을 지정합니다.

콘솔 준비가 끝난 뒤 사용자 승인 아래 배포합니다.

```powershell
npm.cmd --prefix functions test
firebase deploy --only functions:createRealtimeSession,firestore:rules --project englishtalk-ceccf
```

호출 URL은 `https://asia-northeast3-englishtalk-ceccf.cloudfunctions.net/createRealtimeSession`입니다.
일반 REST가 아닌 **Firebase callable 프로토콜**을 사용합니다.
Android SDK가 ID 토큰과 제한 사용 App Check 토큰을 첨부합니다.
앱에서 서버 리전을 변경하면 `FirebaseSessionRepository`와 함수 리전을 함께 맞춰야 합니다.

## 서버 보안 및 오류 처리

- Firebase Auth가 검증한 UID 필수, App Check 필수, 등록된 Android 앱 ID만 허용.
- 제한 사용 App Check 토큰을 소비하며 이미 소비된 토큰을 거부.
- 클라이언트의 모델/TTL/API 키/선생님 설정 입력을 받지 않고 빈 데이터만 허용.
- UID별 20초 간격, 분당 3회, UTC 날짜 기준 하루 20회. 전체 분당 20회/하루 200회.
- 제한 응답에는 간격/분당/하루 제한 종류와 `retryAfterSeconds`를 포함합니다.
  Android는 남은 대기 시간을 표시하고 대기 중 대화 시작·인증 테스트 요청을 차단합니다.
  정상 인증 직후에도 20초의 보수적인 발급 대기를 적용하며 발급 한도 자체는 유지합니다.
- Firestore 트랜잭션으로 여러 함수 인스턴스에서 한도를 공유. 외부 요청 전에 예약하고 실패한 시도도 포함.
- Firestore 장애 시 발급하지 않음. 최대 함수 인스턴스 3개, OpenAI 요청 타임아웃 15초.
- UID는 SHA-256으로 해시해 카운터 키와 OpenAI safety identifier로 사용.
- 장기 키·임시 토큰·원본 OpenAI 오류를 로그에 남기거나 UI에 표시하지 않음. 응답에 `Cache-Control: no-store` 설정.
- Android는 기기 시계 대신 서버 시간과 전체 왕복 시간을 이용해 남은 유효 시간을 보수적으로 검사. 5초 이하이면 실패 처리.
- 테스트 성공 후 임시 토큰을 UI 상태나 디스크에 저장하지 않고 버림. 표시된 시간은 발급 당시 값이며 실시간 유효성을 뜻하지 않음.

익명 계정 재생성은 UID별 한도를 우회할 수 있으므로 전체 한도도 적용했습니다.
전체 한도 소진 공격, 정상 App Check 앱으로 발생하는 남용은 완전히 차단되지 않습니다.
규모가 커지면 단일 global 문서의 경합을 고려해 분산 카운터/추가 계정 정책을 적용해야 합니다.
서버 전용 컬렉션 `_sessionLimits`의 사용자 문서에 `expiresAt`을 넣습니다.
Firestore TTL 정책을 별도로 활성화하면 오래된 사용자 카운터를 정리할 수 있습니다(현재 활성화하지 않음).

| 실패 | 서버 코드 | Android 표시 |
|---|---|---|
| 로그인 또는 App Check 누락 | unauthenticated | 인증 설정 확인 |
| 다른 앱/소비된 토큰 | permission-denied | App Check 확인 |
| 요청 한도/OpenAI 429 | resource-exhausted | 요청 한도 초과 |
| API 키·모델 오류 | failed-precondition | 서버 키·모델 설정 확인 |
| 네트워크/타임아웃/상위 서버 장애 | unavailable | 연결 실패, 재시도 안내 |
| 잘못된/만료된 인증 응답 | internal 또는 앱 검증 오류 | 인증 실패 |

## Android 테스트

1. 설정 파일을 추가하고 Gradle Sync 후 debug 앱을 설치합니다.
2. Debug App Check 토큰을 등록한 뒤 앱을 재실행합니다.
3. `서버 연결 테스트`를 누릅니다. 이 버튼에는 마이크 권한이 필요하지 않습니다.
4. 요청 중 → **인증 성공**과 모델/발급 당시 남은 시간 표시를 확인합니다. 토큰 값은 표시되지 않습니다.
5. 20초 이내 다시 누르면 **인증 실패 · 세션 요청 한도 초과**가 표시됩니다.
6. 네트워크 끊기, 익명 로그인 비활성화, 미등록 App Check 토큰으로 실패 표시를 확인합니다.
   운영 API 키를 깨뜨리기보다는 별도 개발 프로젝트에서 테스트합니다.
7. 설정 파일 없는 앱은 Firebase 설정 오류를 표시해야 합니다.
8. 기존 시작/종료 버튼은 1단계 화면 데모를 유지합니다. 서버 인증 성공은 실제 음성 대화 성공과 구분됩니다.

## 로컬 자동 검증

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease
npm.cmd --prefix functions test
```

Firestore 에뮬레이터와 callable 거부 테스트에는 Java 21 이상 및 Firebase CLI가 필요합니다.
Functions 런타임은 Node 22입니다. 개발 PC에 Node 24만 있다면 Node 22로 맞추는 것이 권장됩니다.
로컬 테스트용 `functions/.secret.local`에는 실제 키가 아닌 `OPENAI_API_KEY=test-only-placeholder`를 넣습니다.
`functions/.env.demo-englishtalk`에는 아래 두 줄을 모두 넣어 에뮬레이터의 설정 입력 대기를 피합니다.

```text
ALLOWED_ANDROID_APP_ID=test-only-app
OPENAI_REALTIME_MODEL=gpt-realtime
```
이 두 파일은 Git에서 제외됩니다. demo 프로젝트에서는 실제 Firebase 리소스를 사용하지 않습니다.

```powershell
firebase emulators:exec --non-interactive --only auth,functions,firestore --project demo-englishtalk "npm.cmd --prefix functions run test:emulator"
```

OpenAI 응답은 서버 단위 테스트에서 주입한 가짜 HTTP 응답으로 검증합니다.
에뮬레이터 테스트는 Firestore의 동시 제한/전체 제한, 클라이언트 규칙 차단과
인증 없는 callable HTTP 요청 거부를 확인합니다. 이 과정은 실제 OpenAI를 호출하지 않습니다.
실제 Android → Firebase → OpenAI 성공 검증은 위 콘솔/Secret/배포 준비 후 수행해야 합니다.

## 공식 문서

- [OpenAI WebRTC: 두 연결 방식](https://developers.openai.com/api/docs/guides/voice-webrtc)
- [OpenAI client secret 생성/만료](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create)
- [Firebase Android 설정](https://firebase.google.com/docs/android/setup)
- [Firebase Callable Functions](https://firebase.google.com/docs/functions/callable)
- [App Check 강제 및 재사용 방지](https://firebase.google.com/docs/app-check/cloud-functions)
- [Android Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Secret Manager 바인딩](https://firebase.google.com/docs/functions/config-env)
- [Functions 배포 및 Blaze](https://firebase.google.com/docs/functions/get-started)
