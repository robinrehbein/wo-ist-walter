// OWNER: UI
package de.wuselburg

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wuselburg.game.GameViewModel
import de.wuselburg.game.Screen
import de.wuselburg.game.SharedPrefsProgressStore
import de.wuselburg.ui.GameScreen
import de.wuselburg.ui.MenuScreen
import de.wuselburg.ui.theme.WuselburgTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars with dark icons: the app is light-only.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { WuselburgTheme { WuselburgApp() } }
    }
}

@Composable
private fun WuselburgApp() {
    val appContext = LocalContext.current.applicationContext
    val factory = remember {
        viewModelFactory { initializer { GameViewModel(store = SharedPrefsProgressStore(appContext)) } }
    }
    val vm: GameViewModel = viewModel(factory = factory)

    // Pause the clock while the app is in the background.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.onAppStopped()
                Lifecycle.Event.ON_START -> vm.onAppStarted()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // On the menu the system handles back (exit the app).
    BackHandler(enabled = vm.screen == Screen.GAME) { vm.onBack() }

    when (vm.screen) {
        Screen.MENU -> MenuScreen(vm)
        Screen.GAME -> GameScreen(vm)
    }
}
