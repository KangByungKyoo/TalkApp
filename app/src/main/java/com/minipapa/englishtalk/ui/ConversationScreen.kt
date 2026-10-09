package com.minipapa.englishtalk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.minipapa.englishtalk.settings.*

@Composable
fun ConversationScreen(state: ConversationUiState, onStart: () -> Unit, onEnd: () -> Unit, onTestServer: () -> Unit = {},
    onCharacter: (ConversationCharacter) -> Unit = {}, onLength: (ResponseLength) -> Unit = {}) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) CharacterSelectionScreen(state.settings,
        enabled = state.settingsLoaded && !state.settingsSaving && !state.settingsApplying,
        isActive = state.isActive, onCharacter = onCharacter, onLength = onLength, onDismiss = { showSettings = false })
    Scaffold(
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = state.settingsLoaded && !state.settingsSaving && !state.isActive && state.serverTestStatus != ServerTestStatus.LOADING, modifier = Modifier.fillMaxWidth()) { Text("대화 시작") }
                OutlinedButton(onClick = onEnd, enabled = state.isActive, modifier = Modifier.fillMaxWidth()) { Text("대화 종료") }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("English Talk", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            Image(painterResource(characterPortrait(state.settings.character)), "${state.teacherName} 캐릭터 이미지", Modifier.size(80.dp))
            Spacer(Modifier.height(8.dp))
            Text(state.settings.summary, style = MaterialTheme.typography.titleMedium)
            Text(state.settings.character.config.levelLabel, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showSettings = true }, enabled = state.settingsLoaded) { Text("친구 · 답변 길이 설정") }
            if (!state.settingsLoaded || state.settingsSaving) Text("설정을 불러오거나 저장하고 있어요.", style = MaterialTheme.typography.bodySmall)
            state.settingsNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            Spacer(Modifier.weight(1f))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("음성 대화 상태", style = MaterialTheme.typography.labelLarge)
                    if (state.status == ConversationStatus.CONNECTING) CircularProgressIndicator()
                    Text(state.status.label, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        state.errorMessage ?: when (state.status) {
                            ConversationStatus.DISCONNECTED -> "시작 버튼을 눌러 ${state.teacherName}와 영어 회화를 연습해 보세요."
                            ConversationStatus.CONNECTING -> "인증 및 음성 연결을 준비하고 있어요."
                            ConversationStatus.TALKING -> if (state.settingsApplying) "다음 답변을 위한 설정을 적용하고 있어요." else "${state.teacherName}: ${state.voiceActivity.label}"
                            ConversationStatus.ENDED -> "대화가 종료되었습니다. 다시 시작할 수 있어요."
                            ConversationStatus.ERROR -> "대화를 시작할 수 없습니다."
                        },
                        textAlign = TextAlign.Center,
                        color = if (state.status == ConversationStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("대화 중 마이크 음성이 OpenAI로 전송되고 AI 친구의 음성이 스피커로 재생됩니다. 앱을 벗어나면 대화가 종료됩니다.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (state.inputTokens + state.outputTokens > 0) Text("이번 대화 사용량 · 입력 ${state.inputTokens} / 출력 ${state.outputTokens} 토큰", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("서버 인증 테스트", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(state.serverTestMessage, textAlign = TextAlign.Center,
                color = if (state.serverTestStatus == ServerTestStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            if (state.serverTestStatus == ServerTestStatus.LOADING) CircularProgressIndicator()
            OutlinedButton(onClick = onTestServer, enabled = state.settingsLoaded && !state.settingsSaving && !state.isActive && state.serverTestStatus != ServerTestStatus.LOADING) {
                Text("서버 연결 테스트")
            }
            Spacer(Modifier.weight(1f))
        }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationScreenPreview() {
    MaterialTheme { ConversationScreen(ConversationUiState(), {}, {}) }
}
