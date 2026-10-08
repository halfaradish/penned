package dev.penned.app.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.penned.app.PennedApplication
import dev.penned.app.domain.PinHasher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.penned.app.ui.components.PennedTopBar
import dev.penned.app.ui.theme.PennedSpacing
import dev.penned.app.ui.theme.expenseColor

/** 密码锁设置：开启（两次输入）/ 修改（先验旧）/ 关闭。哈希经 [PinHasher] 存储。 */
class PinViewModel(private val app: PennedApplication) : ViewModel() {

    val storedHash: StateFlow<String?> = app.settings.pinHash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UNRESOLVED)

    fun setPin(pin: String) {
        viewModelScope.launch {
            val salt = app.settings.pinHash.firstOrNull()?.substringBefore(':') ?: PinHasher.newSalt()
            app.settings.setPinHash(PinHasher.encode(pin, salt))
        }
    }

    fun removePin() {
        viewModelScope.launch { app.settings.setPinHash(null) }
    }

    companion object {
        /** DataStore 首值未到时的哨兵，区别于"未设置"。 */
        const val UNRESOLVED = "pending"
    }
}

/**
 * 密码锁设置页：
 * - 未启用：输入新密码 → 再输一次 → 保存
 * - 已启用：先验当前密码 → 可改或删
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinManageScreen(
    viewModel: PinViewModel,
    onBack: () -> Unit,
) {
    val storedHash by viewModel.storedHash.collectAsStateWithLifecycle()

    // 步骤机：VERIFY_OLD → NEW → CONFIRM → 完成；未启用直接 NEW
    var step by remember { mutableStateOf<Step>(Step.WAIT_HASH) }
    var candidate by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(storedHash) {
        if (step == Step.WAIT_HASH && storedHash != PinViewModel.UNRESOLVED) {
            step = if (storedHash == null) Step.NEW else Step.VERIFY_OLD
        }
    }

    val state = rememberPinState { s, pin ->
        when (step) {
            Step.WAIT_HASH -> {}
            Step.VERIFY_OLD -> {
                if (storedHash != null && PinHasher.verify(pin, storedHash!!)) {
                    s.reset()
                    step = Step.NEW
                } else {
                    s.fail("当前密码不正确")
                }
            }
            Step.NEW -> {
                candidate = pin
                s.reset()
                step = Step.CONFIRM
            }
            Step.CONFIRM -> {
                if (pin == candidate) {
                    viewModel.setPin(pin)
                    step = Step.DONE
                } else {
                    s.fail("两次输入不一致")
                    step = Step.NEW
                    candidate = null
                }
            }
            Step.DONE -> {}
        }
    }

    Scaffold(
        topBar = { PennedTopBar(title = "密码锁", onBack = onBack) },
    ) { padding ->
        when (step) {
            Step.WAIT_HASH -> { /* DataStore 首值未到 */ }
            Step.DONE -> {
                PinPad(
                    filledCount = PinHasher.PIN_LENGTH,
                    title = "密码锁已开启",
                    subtitle = "下次启动与回到应用时需要输入密码",
                    onDigit = {}, onBackspace = {},
                    modifier = Modifier.padding(padding),
                )
            }
            Step.VERIFY_OLD -> {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    TextButton(
                        onClick = { viewModel.removePin(); onBack() },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.End).padding(horizontal = PennedSpacing.xxl),
                    ) { Text("关闭密码锁", color = expenseColor()) }
                    PinPad(
                        filledCount = state.pin.length,
                        title = "输入当前密码",
                        subtitle = "共 ${PinHasher.PIN_LENGTH} 位数字",
                        errorText = state.error,
                        onDigit = state::onDigit,
                        onBackspace = state::onBackspace,
                    )
                }
            }
            else -> PinPad(
                filledCount = state.pin.length,
                title = when (step) {
                    Step.NEW -> if (storedHash == null) "设置密码" else "设置新密码"
                    Step.CONFIRM -> "再输入一次"
                    else -> "密码锁"
                },
                subtitle = "共 ${PinHasher.PIN_LENGTH} 位数字",
                errorText = state.error,
                onDigit = state::onDigit,
                onBackspace = state::onBackspace,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private enum class Step { WAIT_HASH, VERIFY_OLD, NEW, CONFIRM, DONE }
