package de.wuselburg

import android.graphics.Picture
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.wuselburg.game.GameViewModel
import de.wuselburg.ui.GameScreen
import de.wuselburg.ui.MenuScreen
import de.wuselburg.ui.theme.WuselburgTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ComposeScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun vm() = GameViewModel(pictureRecorder = { _, _ -> Picture() })

    @Test fun menuShowsGermanTexts() {
        val vm = vm()
        rule.setContent { WuselburgTheme { MenuScreen(vm) } }
        rule.onNodeWithText("Wuselburg").assertIsDisplayed()
        rule.onNodeWithText("Marktplatz").assertIsDisplayed()
        rule.onNodeWithText("Der Kuchen-Fall").assertExists()
        rule.onNodeWithText("Leicht · Fips ist gut zu sehen").assertExists()
    }

    @Test fun gameScreenShowsIntroCard() {
        val vm = vm()
        rule.setContent { WuselburgTheme { GameScreen(vm) } }
        rule.runOnIdle { vm.startLevel("fips1") }
        rule.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
            vm.card != null
        }
        rule.waitForIdle()
        rule.onNodeWithText("Los geht’s!").assertIsDisplayed()
        rule.onNodeWithText("Marktplatz").assertExists()
        rule.onNodeWithText("Fips, der kleine Fuchs, ist beim Markttrubel verloren gegangen. Findest du ihn?").assertExists()
        rule.onNodeWithText("Los geht’s!").performClick()
        rule.onNodeWithContentDescription("Tipp").assertExists()
    }
}
