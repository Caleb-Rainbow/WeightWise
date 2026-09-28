package com.example.weight.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow

/**
 * 手动检查更新的结果反馈（挂在设置页）。
 *
 * 按接口响应分类提示：
 * - 无更新（已是最新版本）→ Snackbar「已是最新版本」
 * - 检查失败（网络/服务异常）→ Snackbar 错误信息
 * - 发现新版本 → 由全局 [UpdateDialog] 弹窗展示（App 级），此处不处理
 * - 下载失败（fromDownload）→ 由更新弹窗内的错误态处理，此处不处理
 *
 * Snackbar 显示后调用 [UpdateManager.resetTransient] 复位瞬时状态，
 * 避免同一提示在页面重组时重复弹出。
 */
@Composable
fun ManualCheckUpdateFeedback(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val showSnackbar = LocalSnackBarShow.current

    LaunchedEffect(state) {
        when (val s = state) {
            is UpdateUiState.NoUpdate -> {
                showSnackbar("已是最新版本")
                manager.resetTransient()
            }
            is UpdateUiState.Error -> {
                if (!s.fromDownload) {
                    showSnackbar(s.message)
                    manager.resetTransient()
                }
            }
            else -> {}
        }
    }
}
