package dev.penned.app.ui.intelligence

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 系统分享进来的图（M4-7）：`MainActivity` 收到 `ACTION_SEND` 就放这儿，导航层观察它并跳到截图导入页。
 *
 * 为什么用单例中转：单 Activity + Compose 导航，intent 到不了 composable 里；
 * 而 `EXTRA_STREAM` 是 `content://`（带临时授权），只能在这一层接住。
 */
object ScreenshotShareRequest {

    private val _incoming = MutableStateFlow<Uri?>(null)
    val incoming: StateFlow<Uri?> = _incoming

    /** 从 intent 里取图（没有就什么都不做）。 */
    fun offerFrom(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.let { @Suppress("DEPRECATION") it.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }
        if (uri != null) _incoming.value = uri
    }

    /** 导航层处理完就清掉（避免回来又跳一次）。 */
    fun consume() {
        _incoming.value = null
    }
}
