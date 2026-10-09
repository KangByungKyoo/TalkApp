# 3단계 — 실제 WebRTC 음성 대화

2단계 인증 발급을 재사용하여 Android 마이크와 OpenAI Realtime 음성을 연결합니다.
시작/종료 버튼의 데모 지연은 제거했습니다. 서버 키나 Firebase 콘솔 설정 변경은 필요하지 않습니다.

## 동작

1. 대화 시작 시 마이크 권한을 확인하고 Firebase에서 새 임시 인증 정보를 발급받습니다.
2. Android `org.webrtc` PeerConnectionFactory와 JavaAudioDeviceModule로 오디오 연결을 준비합니다.
3. 마이크 오디오 트랙과 `oai-events` 데이터 채널을 추가하고 SDP offer를 생성합니다.
4. `POST https://api.openai.com/v1/realtime/calls`에 SDP와 임시 bearer 토큰을 보냅니다.
5. SDP answer를 설정하고 네이티브 연결 및 데이터 채널 OPEN이 모두 확인된 후 대화 중으로 전환합니다.
6. server VAD를 설정하고 Emma의 짧은 인사를 요청합니다. 발화가 끝나면 AI가 자동 응답합니다.
7. 원격 오디오는 네이티브 WebRTC가 디코딩해 기본 스피커로 재생합니다.
8. 종료, 연결 오류, 오디오 포커스 상실, 앱 백그라운드 전환 시 녹음·재생·연결을 해제합니다.

임시 토큰은 화면/로그/디스크에 저장하지 않습니다. SDP, 음성 데이터, 서버의 원본 오류도 기록하지 않습니다.
서버 시간으로 계산한 인증 유효 시간에 네이티브 준비 시간을 추가 차감한 뒤 SDP 요청을 보냅니다.
만료되었거나 5초 이하로 남은 인증은 거부하며 자동 재발급/재시도는 하지 않습니다.

## 주요 변경 파일

- `voice/VoiceClient.kt`: 연결 계약, 연결/발화 이벤트, 안전한 오류 타입
- `voice/WebRtcVoiceClient.kt`: 네이티브 PeerConnection/오디오/데이터 채널 관리, 비동기 SDP 처리
- `voice/SdpExchange.kt`: HTTPS SDP 교환, 타임아웃, 만료 검사, 요청 취소
- `voice/OpenAiFailure.kt`: OpenAI 429를 잔액·지출·사용 한도와 일시적 요청 제한으로 구분
- `voice/RealtimeEvents.kt`: GA 세션 설정/인사 이벤트 및 발화 상태 해석
- `voice/VoiceAudioRoute.kt`: 통화 오디오 포커스·스피커 라우팅과 기존 설정 복원
- `ConversationViewModel.kt`: 실제 인증과 음성 연결, 상태 전이, 종료/타임아웃/늦은 콜백 차단
- `MainActivity.kt`: 실제 VoiceClient 주입 및 백그라운드 종료
- `ConversationUiState.kt`, `ConversationScreen.kt`: 듣는 중/사용자 발화/AI 준비/AI 발화 표시
- `SessionRepository.kt`: 수신 이후 경과 시간을 고려한 인증 만료 검사
- `AndroidManifest.xml`, `app/build.gradle.kts`: 네트워크·오디오 설정 권한과 WebRTC 의존성

WebRTC 패키지는 Maven Central의 `io.github.webrtc-sdk:android:144.7559.15`로 고정했습니다.
비디오 화면/카메라 권한은 필요하지 않습니다. 네이티브 라이브러리가 포함되어 APK 크기는 증가합니다.

## 기기 테스트

1. Android Studio에서 새 debug 앱을 설치/실행합니다. 기존 Firebase 설정과 Debug App Check 등록을 사용합니다.
2. 인증 테스트를 방금 실행했다면 **20초 이상 기다립니다**. 서버 발급 한도는 실패한 요청에도 적용됩니다.
3. `대화 시작`을 누르고 마이크 권한을 허용합니다.
4. 연결 중 → 대화 중을 확인하고 Emma의 짧은 인사를 듣습니다.
5. 영어로 답하고 말끝에 잠시 쉬면 Emma의 음성 응답이 나오는지 확인합니다.
6. 듣는 중/사용자 발화/AI 답변 준비/AI 발화 표시를 확인합니다.
7. `대화 종료`를 눌러 마이크 표시와 음성 재생이 종료되는지 확인합니다.
8. 연결 중 종료, 네트워크 끊기, 화면 회전, 홈 버튼 이동도 확인합니다.
   화면 회전은 같은 ViewModel 연결을 유지하고, 앱을 벗어나거나 화면을 잠그면 종료합니다.
9. 종료 후 20초 이상 간격으로 다시 연결해 확인합니다. 인증 테스트와 실제 대화 시작은 동시에 실행하지 않습니다.

Debug 앱 재설치/데이터 초기화 후 인증이 실패하면 Logcat의 새 Debug App Check 토큰을 등록하세요.
`connectedDebugAndroidTest` 실행 과정에서도 테스트 대상 앱이 제거되거나 데이터가 초기화될 수 있습니다.
실제 회화에 사용하는 휴대폰의 인증 데이터를 유지하려면 별도 테스트 기기를 사용하거나,
테스트 APK를 `adb install -r`로 설치하고 `adb shell am instrument`로 실행하는 방식을 사용하세요.
서버 로그에서 Auth는 VALID인데 App Check가 INVALID이면 현재 앱의 가장 최근 디버그 토큰을
콘솔에 추가한 후 앱 프로세스를 종료/재실행하여 확인합니다. OpenAI API 키를 바꾸지 않습니다.
OpenAI 실제 음성 사용료가 발생합니다. Firebase 예산은 OpenAI 사용료를 포함하지 않습니다.

## 검증 명령

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease connectedDebugAndroidTest
```

단위 테스트는 실제 준비 완료 전 대화 중 표시 방지, 인증 실패, 타임아웃, 종료/재시작,
이전 연결 콜백 무시 및 인증 만료를 검증합니다.
기기 instrumentation은 네이티브 라이브러리를 로드하여 오디오·데이터 채널 SDP offer를 생성하고
실제 Android JSON 처리로 VAD/인사 요청·음성 이벤트·오류 비노출을 검증합니다.
이 자동 검사는 마이크를 녹음하거나 OpenAI를 호출하지 않습니다.
실제 발화·음성 출력·라우팅 품질은 위 기기 테스트로 별도 확인해야 합니다.

OpenAI 음성 연결의 429 응답은 인식 가능한 `error.code`만 해석하고 원본 오류 본문은
화면이나 로그에 노출하지 않습니다. `Retry-After`가 숫자이면 일시 제한 안내에 반영합니다.
Logcat `RealtimeSignaling`에는 HTTP 상태와 알려진 오류 코드(그 외에는 unknown)만 기록합니다.
잔액/지출/사용량 오류는 반복 요청으로 해결되지 않으므로 OpenAI API 계정의 결제·한도를 확인합니다.
Firebase Blaze와 OpenAI API 결제는 별도입니다.

### 검증 결과 (2026-10-09)

- debug/release APK 빌드와 Android Lint 통과.
- 단위 테스트 14개 통과.
- 연결된 SM-S926N / Android 16에서 instrumentation 테스트 3개 통과.
- OpenAI 오류 분류 instrumentation 테스트 1개를 추가해 별도로 실행했고 통과했습니다.
- OpenAI API 결제를 재개한 후 실제 기기에서 음성 대화가 정상 동작함을 사용자가 확인했습니다.

## 범위와 제한

- 앱이 전경에 있을 때 사용하는 기본 스피커 음성 회화입니다. 백그라운드 서비스는 추가하지 않았습니다.
- 공용 Google STUN 서버를 사용하고 별도 TURN 중계는 구성하지 않았습니다. UDP 제한 네트워크에서 연결이 실패할 수 있습니다.
- 연결이 끊기면 자원을 해제하고 오류로 표시합니다. 자동 재연결은 하지 않으며 사용자가 다시 시작합니다.
- AI 음성은 WebRTC 원격 트랙으로 재생합니다. 텍스트 대화 기록·학습 피드백 저장은 이후 단계에서 확장할 수 있습니다.
- 서버 발급 한도는 실제 음성 세션 시간이나 총 OpenAI 지출의 완전한 상한이 아닙니다.

## 공식 참고 자료

- [OpenAI WebRTC 및 ephemeral token SDP 교환](https://developers.openai.com/api/docs/guides/voice-webrtc)
- [OpenAI VAD](https://developers.openai.com/api/docs/guides/realtime-vad)
- [WebRTC Android PeerConnection API](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/PeerConnection.java)
- [Android WebRTC 라이브러리 배포 프로젝트](https://github.com/webrtc-sdk/android)
