package com.minipapa.englishtalk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ConversationScreen(state: ConversationUiState, onStart: () -> Unit, onEnd: () -> Unit, onTestServer: () -> Unit = {}) {
    Scaffold(
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = !state.isActive, modifier = Modifier.fillMaxWidth()) { Text("대화 시작") }
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
            Text("AI 영어 선생님 · ${state.teacherName}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("음성 대화 상태", style = MaterialTheme.typography.labelLarge)
                    if (state.status == ConversationStatus.CONNECTING) CircularProgressIndicator()
                    Text(state.status.label, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        state.errorMessage ?: when (state.status) {
                            ConversationStatus.DISCONNECTED -> "시작 버튼을 눌러 Emma와 영어 회화를 연습해 보세요."
                            ConversationStatus.CONNECTING -> "대화를 준비하고 있어요."
                            ConversationStatus.TALKING -> "Emma와의 대화 화면입니다."
                            ConversationStatus.ENDED -> "대화가 종료되었습니다. 다시 시작할 수 있어요."
                            ConversationStatus.ERROR -> "대화를 시작할 수 없습니다."
                        },
                        textAlign = TextAlign.Center,
                        color = if (state.status == ConversationStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("대화 시작·종료는 화면 데모입니다. 실제 음성 연결은 다음 단계에서 제공됩니다.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("서버 인증 테스트", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(state.serverTestMessage, textAlign = TextAlign.Center,
                color = if (state.serverTestStatus == ServerTestStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            if (state.serverTestStatus == ServerTestStatus.LOADING) CircularProgressIndicator()
            OutlinedButton(onClick = onTestServer, enabled = state.serverTestStatus != ServerTestStatus.LOADING) {
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
