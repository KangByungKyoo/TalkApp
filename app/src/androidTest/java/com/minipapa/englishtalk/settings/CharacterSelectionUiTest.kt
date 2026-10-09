package com.minipapa.englishtalk.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minipapa.englishtalk.ui.ConversationScreen
import com.minipapa.englishtalk.ui.ConversationUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class CharacterSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectsAllNineCombinationsAndDisplaysCurrentChoice() {
        val state = mutableStateOf(ConversationUiState())
        compose.setContent {
            MaterialTheme {
                ConversationScreen(state.value, {}, {},
                    onCharacter = { character -> state.value = state.value.copy(settings = state.value.settings.copy(character = character)) },
                    onLength = { length -> state.value = state.value.copy(settings = state.value.settings.copy(responseLength = length)) })
            }
        }
        for (character in ConversationCharacter.entries) for (length in ResponseLength.entries) {
            compose.onNodeWithText("친구 · 답변 길이 설정").performScrollTo().performClick()
            compose.onNodeWithText("${character.config.name} (${character.config.age}세)").performScrollTo().performClick()
            compose.onNodeWithText(length.label).performScrollTo().performClick()
            compose.onNodeWithText("완료").performClick()
            compose.onNodeWithText(ConversationSettings(character, length).summary).performScrollTo().assertIsDisplayed()
        }
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("ui-tests")!!
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "characters-main.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("친구 · 답변 길이 설정").performScrollTo().performClick()
        compose.onNodeWithText("대화 친구와 답변 길이").assertIsDisplayed()
        compose.waitForIdle()
        // Android window entrance animation runs outside Compose's test clock.
        android.os.SystemClock.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "characters-dialog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
