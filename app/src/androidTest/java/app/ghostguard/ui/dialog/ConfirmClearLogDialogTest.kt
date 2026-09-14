package app.ghostguard.ui.dialog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    private val clearLabel = context.getString(R.string.settings_clear_logs)
    private val cancelLabel = context.getString(R.string.cancel)

    @Test
    fun dialog_showsTitleAndButtons() {
        composeTestRule.setContent {
            BlockadsTheme {
                ConfirmClearLogDialog(onClear = {}, onDismiss = {})
            }
        }
        // Title and confirm button share the same string
        composeTestRule.onAllNodesWithText(clearLabel).assertCountEquals(2)
        composeTestRule.onNodeWithText(cancelLabel).assertExists()
    }

    @Test
    fun dialog_clearCallbackFires() {
        var cleared = 0
        composeTestRule.setContent {
            BlockadsTheme {
                ConfirmClearLogDialog(onClear = { cleared++ }, onDismiss = {})
            }
        }
        composeTestRule.onNode(hasText(clearLabel).and(hasClickAction())).performClick()
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
        composeTestRule.onNode(hasText(cancelLabel).and(hasClickAction())).performClick()
        assertEquals(1, dismissed)
    }
}
