package app.ghostguard.ui.home.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun statCard_displaysLabelAndValue() {
        composeTestRule.setContent {
            app.ghostguard.ui.theme.BlockadsTheme {
                StatCard(
                    icon = Icons.Filled.Shield,
                    label = "Blocked",
                    value = "1,234",
                    color = androidx.compose.ui.graphics.Color.Red,
                )
            }
        }
        composeTestRule.onNodeWithText("Blocked").assertExists()
        composeTestRule.onNodeWithText("1,234").assertExists()
    }

    @Test
    fun statCard_onClickFires() {
        var clicks = 0
        composeTestRule.setContent {
            app.ghostguard.ui.theme.BlockadsTheme {
                StatCard(
                    icon = Icons.Filled.Shield,
                    label = "Blocked",
                    value = "10",
                    color = androidx.compose.ui.graphics.Color.Red,
                    onClick = { clicks++ },
                )
            }
        }
        composeTestRule.onAllNodesWithText("Blocked")[0].performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun statCard_rendersMultipleCards() {
        composeTestRule.setContent {
            app.ghostguard.ui.theme.BlockadsTheme {
                androidx.compose.foundation.layout.Column {
                    StatCard(icon = Icons.Filled.Shield, label = "Ads", value = "10", color = androidx.compose.ui.graphics.Color.Red)
                    StatCard(icon = Icons.Filled.Shield, label = "Trackers", value = "20", color = androidx.compose.ui.graphics.Color.Blue)
                }
            }
        }
        composeTestRule.onAllNodesWithText("Ads").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Trackers").assertCountEquals(1)
    }
}
