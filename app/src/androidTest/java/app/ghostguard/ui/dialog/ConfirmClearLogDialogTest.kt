package app.ghostguard.ui.dialog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ghostguard.R
import app.ghostguard.ui.theme.BlockadsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfirmClearLogDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun dialog_showsTitleAndButtons() {
        composeTestRule.setContent {
            BlockadsTheme {
                ConfirmClearLogDialog(onClear = {}, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.settings_clear_logs)).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.cancel)).assertExists()
    }

    @Test
    fun dialog_clearCallbackFires() {
        var cleared = 0
        composeTestRule.setContent {
            BlockadsTheme {
                ConfirmClearLogDialog(onClear = { cleared++ }, onDismiss = {})
            }
        }
        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_clear_logs))
            .performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun dialog_dismissCallbackFires() {
        var dismissed = 0
        composeTestRule.setContent {
            BlockadsTheme {
                ConfirmClearLogDialog(onClear = {}, onDismiss = { dismissed++ })
            }
        }
        composeTestRule
            .onNodeWithText(context.getString(R.string.cancel))
            .performClick()
        assertEquals(1, dismissed)
    }
}
