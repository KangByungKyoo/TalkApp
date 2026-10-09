package com.minipapa.englishtalk.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.minipapa.englishtalk.R
import com.minipapa.englishtalk.settings.*

@DrawableRes
fun characterPortrait(character: ConversationCharacter): Int = when (character) {
    ConversationCharacter.LILY -> R.drawable.avatar_lily
    ConversationCharacter.EMMA -> R.drawable.avatar_emma
    ConversationCharacter.SOPHIA -> R.drawable.avatar_sophia
}

@Composable
fun CharacterSelectionScreen(
    settings: ConversationSettings,
    enabled: Boolean,
    isActive: Boolean,
    onCharacter: (ConversationCharacter) -> Unit,
    onLength: (ResponseLength) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대화 친구와 답변 길이") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("영어 수준에 맞는 친구를 선택해 주세요.")
                ConversationCharacter.entries.forEach { character ->
                    val config = character.config
                    val selected = character == settings.character
                    Card(colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton,
                            onClick = { onCharacter(character) }).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(characterPortrait(character)), "${config.name} 캐릭터 이미지", Modifier.size(52.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${config.name} (${config.age}세)", style = MaterialTheme.typography.titleSmall)
                                Text(config.levelLabel, style = MaterialTheme.typography.bodySmall)
                                Text(config.personalityLabel, style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected, onClick = null, enabled = enabled)
                        }
                    }
                }
                Text("답변 길이", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResponseLength.entries.forEach { length ->
                        FilterChip(settings.responseLength == length, onClick = { onLength(length) }, enabled = enabled,
                            label = { Text(length.label) }, modifier = Modifier.weight(1f))
                    }
                }
                val target = settings.responseLength.targetWords(settings.character)
                Text("${settings.responseLength.description} · 목표 ${target.first}~${target.last}단어", style = MaterialTheme.typography.bodySmall)
                Text("단어 수는 권장 목표이며 자연스럽게 끝맺도록 합니다.", style = MaterialTheme.typography.bodySmall)
                if (isActive) Text("캐릭터 변경은 현재 대화를 종료합니다. 답변 길이를 변경하면 현재 답변을 중단하고 다음 답변부터 적용합니다.", style = MaterialTheme.typography.bodySmall)
                Text("모두 AI 가상 캐릭터입니다. 실제 어린이의 목소리를 재현하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                if (!enabled) Text("설정을 저장하거나 적용하고 있어요.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("완료") } }
    )
}
