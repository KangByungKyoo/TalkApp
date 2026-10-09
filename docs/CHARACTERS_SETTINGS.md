# 여성 AI 영어 회화 캐릭터와 답변 길이

## 적용 결과

기존 Firebase 인증·WebRTC 음성 대화·서버 테스트·종료 처리를 유지하면서
캐릭터 3명과 답변 길이 3종을 독립적으로 선택하는 기능을 추가했습니다.
기본값은 Emma + SHORT입니다. Preferences DataStore에 캐릭터 ID와 답변 길이 ID를 저장합니다.
이 변경은 기존 `gpt-realtime-2.1-mini` 모델과 서버 발급 함수를 사용합니다.

## 캐릭터 설정

| 캐릭터 | 역할 나이 | 영어 수준 | 성격·말투 | 주제 | voice ID |
|---|---|---|---|---|---|
| Lily | 6세 | 유치원생 수준, 매우 쉬운 단어·단순 문장 | 밝고 귀엽고 호기심 많은 놀이 친구 | 동물·장난감·색·음식·가족·놀이 | shimmer |
| Emma | 9세 | 초등학교 3학년 수준, 짧거나 중간 길이 문장 | 활발하고 친근한 또래 친구 | 학교·친구·취미·음식·운동·여행 | coral |
| Sophia | 13세 | 중학교 1학년 수준, 조금 더 다양한 문장 | 차분하고 친근하며 생각이 깊은 친구 | 학교생활·음악·영화·친구·감정·관심사·꿈 | sage |

모든 캐릭터는 여성으로 설정한 AI 가상 인물입니다. 특정한 실제 사람이나 어린이를 재현하지 않습니다.
위 목소리는 공식 지원 ID 중 서로 다른 음색을 선택한 것이며, 실제 어린이 목소리나 정확한 연령/성별의 음질을 보장하지 않습니다.
`CharacterConfig`의 `voiceId`와 톤 설명을 바꾸면 나중에 청취 비교에 따라 조정할 수 있습니다.

## 답변 길이

| 캐릭터 | 짧게 / SHORT (기본) | 보통 / NORMAL | 자세히 / DETAILED |
|---|---|---|---|
| Lily | 5~15단어 | 15~25단어 | 25~40단어 |
| Emma | 10~20단어 | 20~35단어 | 35~55단어 |
| Sophia | 15~25단어 | 25~45단어 | 45~70단어 |

이는 영어 답변의 권장 목표이며 절대적인 단어 수 제한은 아닙니다.
길어져도 캐릭터별 어휘 수준을 유지하고 자연스럽게 끝맺도록 프롬프트에 명시했습니다.
짧은 질문에는 짧게 답하고, 한 답변에 질문은 최대 1개이며 필요한 경우에만 질문합니다.
영어가 기본이고 요청한 한국어 설명은 간단히 제공합니다. 직접 답변을 우선하고
불필요한 반복·인사·요약을 줄입니다. 안전과 개인정보 보호는 길이 목표보다 우선합니다.
문장이 잘리는 문제를 피하려고 추가적인 출력 토큰 상한을 설정하지 않았습니다.
짧은 답변의 실제 비용 절감률을 고정된 비율로 가정하지 않습니다.

## UI와 저장

메인 화면의 `친구 · 답변 길이 설정`에서 이미지·이름·나이·수준·성격과 길이를 선택합니다.
메인 화면에는 `Emma (9세) · 짧게`처럼 현재 선택을 표시합니다.
캐릭터를 바꿔도 길이는 유지되고 길이를 바꿔도 캐릭터는 유지됩니다.
DataStore의 `conversation_settings`에 `character_id`, `response_length`를 저장합니다.
저장 완료 후 Flow로 UI를 갱신하며, 첫 저장값을 읽기 전에는 대화 시작을 막습니다.
알 수 없는 저장 ID는 Emma/SHORT로 안전하게 복구합니다.

캐릭터 이미지는 원본 벡터 리소스 `avatar_lily.xml`, `avatar_emma.xml`, `avatar_sophia.xml`입니다.
같은 drawable 이름으로 PNG/WebP/다른 벡터 리소스를 교체하거나 `characterPortrait()` 매핑을 변경하면 됩니다.

## Realtime API 적용

- 최초 데이터 채널 연결 후 전체 instructions와 선택한 voice를 `session.update`로 보냅니다.
- `session.updated`의 실제 instructions/voice가 현재 요청과 일치하는지 확인합니다.
- 설정 확인 후 마이크를 활성화하고 선택한 캐릭터의 인사를 한 번만 요청합니다.
- 초기 인사의 `response.create`에도 전체 캐릭터/길이 규칙을 넣어 기존 Emma 전용 인사와 충돌하지 않게 했습니다.
- 길이 변경은 같은 연결을 유지합니다. 마이크를 잠시 멈추고 이전 생성/재생/입력 버퍼를 정리한 뒤 전체 instructions를 교체합니다.
- 적용 확인 후 마이크를 재개합니다. 설정 적용 중인 상태를 표시하고 확인이 10초 안에 오지 않으면 오류로 종료합니다.
- 이미 적용/전송한 같은 설정은 다시 보내지 않습니다. 이전 설정의 늦은 확인 응답도 무시합니다.
- 음성이 한 번 출력된 세션에서는 voice를 바꿀 수 없어 캐릭터 변경 시 현재 대화를 종료합니다.
  새 설정을 저장한 뒤 사용자가 대화 시작을 누르면 새 목소리의 세션을 만듭니다.
  자동 재연결로 추가 API 요청을 발생시키지 않습니다. 기존 세션의 문맥은 새 세션에 전달하지 않습니다.
- `response.done.usage`의 입력/출력 토큰을 이번 대화의 누적 사용량으로 확인할 수 있습니다.

서버의 기본 Emma/marin 설정은 첫 응답 전에 클라이언트의 전체 session.update로 대체됩니다.
장기 API 키를 앱에 추가하거나 Firebase 인증·App Check·발급 한도를 완화하지 않았습니다.
20초 발급 간격은 그대로이므로 캐릭터 변경 후 너무 빨리 새 대화를 시작하면 잠시 기다려야 합니다.
대기 중에는 다음 시작까지 남은 시간을 표시하고 시작/인증 테스트 버튼을 비활성화합니다.
서버는 제한 종류와 정확한 재시도 시간을 반환하여 하루 한도와 짧은 간격 제한을 구분합니다.

## 생성·수정 파일

새 main Kotlin 파일 (`app/src/main/java/com/minipapa/englishtalk/` 기준):

- `settings/ConversationSettings.kt`: CharacterConfig, ConversationCharacter, ResponseLength, ConversationSettings
- `settings/ConversationSettingsRepository.kt`: 저장 계약과 Preferences DataStore 구현
- `settings/RealtimeInstructionsBuilder.kt`: 캐릭터·길이·공통 규칙을 조합하는 프롬프트
- `voice/SessionSettingsCoordinator.kt`: 중복 전송 방지와 서버 확인 상태 관리
- `ui/CharacterSelectionScreen.kt`: 캐릭터와 길이 선택 팝업, 교체 가능한 이미지 매핑

새 리소스 및 테스트:

- `app/src/main/res/drawable/avatar_lily.xml`
- `app/src/main/res/drawable/avatar_emma.xml`
- `app/src/main/res/drawable/avatar_sophia.xml`
- `app/src/test/java/com/minipapa/englishtalk/settings/CharacterInstructionsTest.kt`
- `app/src/test/java/com/minipapa/englishtalk/ui/CharacterSettingsViewModelTest.kt`
- `app/src/androidTest/java/com/minipapa/englishtalk/settings/CharacterSettingsTest.kt`
- `app/src/androidTest/java/com/minipapa/englishtalk/settings/CharacterSelectionUiTest.kt`

수정:

- `.gitignore`, `README.md`, `app/build.gradle.kts`
- `MainActivity.kt`, `ui/ConversationScreen.kt`, `ui/ConversationUiState.kt`, `ui/ConversationViewModel.kt`
- `voice/VoiceClient.kt`, `voice/RealtimeEvents.kt`, `voice/WebRtcVoiceClient.kt`
- 기존 `ConversationViewModelTest.kt`, `VoiceConversationTest.kt`의 확장된 연결 계약
- 이 문서 `docs/CHARACTERS_SETTINGS.md`

기존 ViewModel에 설정 저장과 연결 적용을 함께 연결하여 중복 ViewModel을 추가하지 않았습니다.

## 검증 결과 — 2026-10-09

- debug/release 빌드 성공, Android Lint 오류 없이 통과.
- 단위 테스트 22개 통과: 기존 음성/인증 테스트와 9개 프롬프트 조합, 기본값/복구,
  독립 설정 유지, 동일 설정 무전송, 실제 적용 확인, 캐릭터 변경 시 종료, 저장 실패 처리 등.
- 연결된 SM-S926N에서 기기 검사 8개 통과: 기존 WebRTC 4개와 새 검사 4개.
- Compose UI 검사에서 9가지 조합을 직접 선택하고 메인 화면의 선택값 표시를 확인했습니다.
- 실제 Preferences DataStore를 파일로 저장하고 인스턴스/스코프를 닫은 뒤 다시 열어 저장값 유지를 확인했습니다.
- 9개 초기 session.update/greeting 요청의 어휘 수준·목소리·길이 규칙을 검사하고, 길이 변경 요청에서 voice가 빠지는 것을 확인했습니다.
- 실기기 화면 캡처로 이미지·성격 카드·선택 칩의 레이아웃도 확인했습니다.
- 앱과 테스트 APK를 `adb install -r`로 설치해 기존 Firebase/App Check 데이터를 유지했습니다.
- 자동 테스트는 마이크 녹음/실제 OpenAI 음성 호출을 하지 않았습니다.

### 아직 실제 음성으로 확인하지 않은 항목

9가지 조합의 실제 생성 답변의 어휘 수준·단어 수·음색과 실시간 길이 변경 후 다음 답변입니다.
자동 검증은 설정/프롬프트/저장/상태 전이를 확인했으며 AI의 실제 발화 품질을 보장하지 않습니다.
반복 청취 후 voice 또는 프롬프트를 조정할 수 있습니다.

## 사용자가 확인할 테스트

1. 앱 실행 → 친구/답변 길이 설정 → Emma + 짧게로 시작합니다.
2. 영어로 질문하고 간결한 답변과 최대 한 개의 질문을 확인합니다.
3. 대화 중 보통/자세히로 변경하고 적용 중 → 대화 중을 확인한 뒤 새 질문을 합니다.
4. 캐릭터를 바꾸면 대화가 종료되는지 확인하고, 새 대화에서 선택한 이름과 음색을 확인합니다.
5. 앱을 완전히 종료/재실행하여 캐릭터와 길이가 유지되는지 확인합니다.
6. Lily/Emma/Sophia 각각 SHORT/NORMAL/DETAILED로 같은 질문을 비교합니다.
   연결 발급 사이 20초 이상 기다립니다. 실제 음성 사용에는 OpenAI 비용이 발생합니다.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease assembleDebugAndroidTest
```

실기기 검사는 앱 데이터를 보존하기 위해 APK를 `adb install -r`로 설치하고
`adb shell am instrument -w com.minipapa.englishtalk.test/androidx.test.runner.AndroidJUnitRunner`로 실행합니다.

## 공식 참고

- [Realtime session.update, voice 변경 제약](https://developers.openai.com/api/reference/resources/realtime/client-events)
- [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
