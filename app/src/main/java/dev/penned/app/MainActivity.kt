package dev.penned.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.penned.app.ui.LocalApp
import dev.penned.app.ui.lock.LockScreen
import dev.penned.app.ui.nav.PennedApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as PennedApplication
            // 主题外观（M3-12）：品牌色相 + 深浅模式从 DataStore 读（读法抽在 PennedThemeFromSettings，
            // 与流程测试共用一份；首值未到前先用默认青绿 + 跟随系统）
            dev.penned.app.ui.theme.PennedThemeFromSettings(app) {
                CompositionLocalProvider(LocalApp provides app) {
                    LockGate(app) {
                        PennedApp(app)
                    }
                }
            }
        }
    }
}

/**
 * 密码锁门禁（specs/M1.md 第二批）：
 * - 设置了密码则冷启动锁定；
 * - 退到后台（ON_STOP）后回到应用重新锁定；
 * - 哈希存 DataStore（D023），锁屏在主 UI 之前拦截。
 */
@Composable
private fun LockGate(app: PennedApplication, content: @Composable () -> Unit) {
    // UNRESOLVED 哨兵：DataStore 首值未到前不渲染任何内容，避免锁屏前闪现主界面
    var loaded by remember { mutableStateOf(false) }
    val pinHash by produceState<String?>(initialValue = null) {
        app.settings.pinHash.collect {
            value = it
            loaded = true
        }
    }
    var unlocked by remember { mutableStateOf(false) }
    var backgrounded by remember { mutableStateOf(false) }
    // 会话初始化：冷启动时若已有密码则保持锁定；会话中才设置的密码不打断当前使用
    var sessionResolved by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(loaded) {
        if (loaded && !sessionResolved) {
            sessionResolved = true
            unlocked = pinHash == null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgrounded = true
                Lifecycle.Event.ON_RESUME -> {
                    if (backgrounded) {
                        unlocked = false
                        backgrounded = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !loaded -> Unit
        pinHash != null && !unlocked -> LockScreen(storedHash = pinHash!!, onUnlock = { unlocked = true })
        else -> content()
    }
}
