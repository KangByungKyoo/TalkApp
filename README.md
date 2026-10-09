# EnglishTalk — 2단계

Firebase 기반 OpenAI Realtime 임시 인증 발급을 추가했습니다.
실제 음성/WebRTC 연결은 다음 단계입니다.

- Firebase 프로젝트: `englishtalk-ceccf`
- 서버: Functions 2세대 / Node.js 22 / TypeScript
- 함수: `createRealtimeSession`, 리전 `asia-northeast3`
- 앱: 익명 로그인과 App Check를 사용한 `서버 연결 테스트`, 성공/실패 별도 표시
- 보안: Secret Manager 키 바인딩, App Check 재사용 방지, UID별·전체 발급 제한

설정 파일이 없어도 빌드는 가능하지만 서버 테스트는 Firebase 설정 오류를 표시합니다.
**실제 연결에는 Android 앱 등록, google-services.json, 콘솔 설정, Secret 등록 및 배포가 필요합니다.**
이후 사용자와 함께 콘솔 설정·Secret 등록·함수 배포를 완료했고, 앱에서 실제 인증 성공을 확인했습니다.

[Firebase 설정·인증 방식 비교·API 키 저장·배포·테스트 상세](docs/FIREBASE_SETUP.md)

## 2단계 주요 파일

- `EnglishTalkApplication.kt`: Firebase 초기화 및 App Check 설치
- `data/FirebaseSessionRepository.kt`: 익명 로그인, 제한 사용 App Check callable 호출
- `data/SessionRepository.kt`: 인증 계약과 만료 검증, 토큰 로그 출력 방지
- `src/debug/.../AppCheckInstaller.kt`: 개발용 Debug App Check
- `src/release/.../AppCheckInstaller.kt`: 출시용 Play Integrity
- `functions/src/index.ts`: Secret 바인딩, Auth/App Check 강제, callable 엔드포인트
- `functions/src/session.ts`: OpenAI 임시 인증 요청과 안전한 오류 변환
- `functions/src/rateLimit.ts`: Firestore 트랜잭션을 사용하는 발급 한도
- `firebase.json`, `.firebaserc`, `firestore.rules`: 프로젝트·배포·에뮬레이터·DB 접근 설정
- `functions/runtime-config.example`: 비밀 키를 제외한 서버 설정 예시
- UI/상태/ViewModel/Manifest/Gradle: 서버 인증 테스트 UI와 Firebase SDK 추가
- `SessionResponseTest`, `ServerConnectionTest`, `functions/test/`: 인증·만료·보안·동시 제한 검증

Android main 소스 기준 경로는 `app/src/main/java/com/minipapa/englishtalk/`입니다.
`google-services.json`, Secret/환경 변수 파일 및 빌드 결과는 Git에서 제외합니다.
서버 테스트 성공 후 임시 토큰을 UI나 디스크에 보관하지 않습니다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease
npm.cmd --prefix functions ci
npm.cmd --prefix functions test
```

검증 결과와 미검증 항목은 [2단계 검증 기록](docs/STAGE2_VALIDATION.md)에 정리합니다.
이번 구현은 2단계까지입니다.

## 1단계 기록

아래 내용은 1단계 완료 당시의 기록입니다. 현재 Firebase 구현과 설정은 위 내용을 따릅니다.

Kotlin / Jetpack Compose 영어 회화 앱의 기본 프로젝트입니다.
패키지는 `com.minipapa.englishtalk`, minSdk는 26, compileSdk/targetSdk는 36입니다.
AGP 8.10.1, Gradle 8.11.1, Kotlin 2.0.21을 사용합니다.

## 실행

1. Android Studio에서 이 폴더를 열고 Gradle Sync를 완료합니다.
2. SDK Manager에서 Android SDK 36을 설치합니다. Gradle JDK는 17 이상을 선택합니다.
3. API 26 이상 에뮬레이터 또는 Android 기기로 `app`을 실행합니다.
4. `대화 시작`을 누르고 마이크 권한을 허용하면 연결 전 → 연결 중 → 약 1초 후 대화 중으로 바뀝니다.
5. 연결 중 또는 대화 중에 `대화 종료`를 누르면 연결 종료로 바뀝니다. 다시 시작할 수 있습니다.
6. 권한 거부 시 오류 안내를 확인합니다. 반복 거부로 요청창이 나오지 않으면 Android 앱 설정 → 권한 → 마이크에서 허용합니다.
7. 화면 회전 시 상태가 유지되는지, 연결 중 종료 후 대화 중으로 돌아가지 않는지 확인합니다.

다른 PC에서는 `local.properties`의 SDK 경로를 Android Studio가 생성하도록 합니다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 검증 결과

2026-10-09에 `assembleDebug testDebugUnitTest lintDebug`를 실행하여 BUILD SUCCESSFUL을 확인했습니다.
단위 테스트 3개가 모두 통과했습니다. Lint 오류는 없으며 라이브러리 업데이트 알림 4개,
백업 설정 및 앱 아이콘 관련 경고 2개가 남아 있습니다.
연결된 Android 기기가 없어 기기 실행과 권한 대화상자의 실제 조작은 검증하지 않았습니다.

## 생성 파일

- 루트: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `README.md`
- Gradle Wrapper: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- 로컬 SDK 설정: `local.properties` (Git 제외)
- 앱 설정: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- 리소스: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- 소스 (`app/src/main/java/com/minipapa/englishtalk/`): `MainActivity.kt`, `ui/ConversationScreen.kt`, `ui/ConversationUiState.kt`, `ui/ConversationViewModel.kt`
- 테스트: `app/src/test/java/com/minipapa/englishtalk/ui/ConversationViewModelTest.kt`

## 구조

- `MainActivity`: Compose 진입점, Android 마이크 권한 요청 및 결과 처리
- `ui/ConversationScreen`: 상태를 받아 화면을 그리는 UI, 시작/종료 이벤트 전달
- `ui/ConversationUiState`: 선생님 이름, 대화 상태, 오류 메시지
- `ui/ConversationViewModel`: StateFlow로 상태 제공, 데모 연결 작업 및 취소 처리
- `ConversationViewModelTest`: 상태 전이, 연결 중 취소, 종료 후 재시작, 권한 오류 후 복구 검증

현재는 화면 상태만 바뀌며 실제 녹음이나 네트워크 연결은 하지 않습니다.
화면 회전에는 ViewModel이 유지되며 프로세스가 종료되면 연결 전 상태로 시작합니다.
Firebase, WebRTC, OpenAI SDK 및 API 키는 포함하지 않습니다.

## 다음 단계 작업

이후 단계에서는 Firebase 프로젝트/인증 구성과 대화 세션 계층을 추가하고,
ViewModel의 데모 지연을 세션 이벤트로 교체할 수 있습니다.
WebRTC 마이크 스트림, Realtime 연결 및 음성 출력, 연결 오류/해제 처리가 필요합니다.
API 비밀 키는 앱에 넣지 않고 서버에서 관리하는 구조를 준비해야 합니다.
이번 구현은 1단계에서 종료합니다.
