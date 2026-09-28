package com.example.weight.ui.update

import android.content.Context
import android.util.Log
import com.example.weight.BuildConfig
import com.example.weight.data.update.ApkInstaller
import com.example.weight.data.update.DownloadProgress
import com.example.weight.data.update.ReleaseInfo
import com.example.weight.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 更新 UI 状态。
 */
sealed interface UpdateUiState {
    /** 空闲（无弹窗）。 */
    data object Idle : UpdateUiState

    /** 检查中。 */
    data object Checking : UpdateUiState

    /** 已是最新版本（仅手动检查时出现，用于 Snackbar 反馈）。 */
    data object NoUpdate : UpdateUiState

    /** 发现新版本。 */
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateUiState

    /** 下载中，[progress] 含完整下载进度信息（百分比/速度/剩余时间）。 */
    data class Downloading(val progress: DownloadProgress) : UpdateUiState

    /** 下载完成，正在调起安装。 */
    data object Downloaded : UpdateUiState

    /** 出错。[fromDownload]=true 表示下载失败（弹窗重试），false 表示检查失败（Snackbar 反馈）。 */
    data class Error(val message: String, val fromDownload: Boolean = false) : UpdateUiState
}

/**
 * 更新管理器（应用级单例）。
 *
 * 跨页面共享：宿主 Scaffold 观察全局 [state] 显示更新弹窗；设置页调用 [checkUpdate] 触发手动检查。
 * 自动检查（App 启动）出错时静默忽略；手动检查出错或无更新时设置 [NoUpdate]/[Error] 供 Snackbar 反馈。
 */
class UpdateManager(
    private val context: Context,
    private val repository: UpdateRepository,
    private val appScope: CoroutineScope,
) {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state = _state.asStateFlow()

    /** 当前是否处于强制更新流程。 */
    val isForceUpdate: Boolean get() = forceUpdate

    /** 当前发现的 release（跨状态保留，供下载失败后重试）。 */
    private var currentRelease: ReleaseInfo? = null

    /** 是否强制更新（强制更新时不允许关闭弹窗）。 */
    private var forceUpdate = false

    /**
     * 检查更新。
     * @param manual true=手动检查（无更新/出错时设置状态供 Snackbar 反馈）；false=自动检查（静默）
     */
    fun checkUpdate(manual: Boolean) {
        // 检查/下载进行中不再叠加一轮请求（防设置页连点）
        if (_state.value is UpdateUiState.Checking || _state.value is UpdateUiState.Downloading) return
        appScope.launch {
            _state.value = UpdateUiState.Checking
            val response = repository.checkUpdate(BuildConfig.VERSION_CODE)
            if (response == null) {
                _state.value = if (manual) UpdateUiState.Error("检查更新失败，请稍后重试") else UpdateUiState.Idle
                return@launch
            }
            if (response.has_update && response.release != null) {
                Log.d(TAG, "发现新版本：${response.release.version_name} (${response.release.version_code})")
                currentRelease = response.release
                forceUpdate = response.release.force_update
                _state.value = UpdateUiState.UpdateAvailable(response.release)
            } else {
                _state.value = if (manual) UpdateUiState.NoUpdate else UpdateUiState.Idle
            }
        }
    }

    /** 开始下载 APK 并自动调起安装（下载完成自动校验 MD5 并进入系统安装界面）。 */
    fun startDownload() {
        val release = currentRelease ?: return
        appScope.launch {
            _state.value = UpdateUiState.Downloading(
                DownloadProgress(0, 0, -1, 0, -1),
            )
            val file = repository.downloadApk(release) { progress ->
                _state.value = UpdateUiState.Downloading(progress)
            }
            if (file != null) {
                _state.value = UpdateUiState.Downloaded
                // 短暂展示"正在启动安装程序…"后调起；随后复位为 Idle——用户在系统安装器取消安装
                // 返回 App 时界面回到正常状态，不会卡在下载完成弹窗
                delay(600)
                ApkInstaller.install(context, file)
                _state.value = UpdateUiState.Idle
            } else {
                _state.value = UpdateUiState.Error("下载失败，请稍后重试", fromDownload = true)
            }
        }
    }

    /** 重置为空闲（关闭弹窗）。强制更新/下载中不允许关闭。 */
    fun dismiss() {
        val current = _state.value
        if (forceUpdate || current is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Idle
    }

    /** 重置检查更新的瞬时状态（NoUpdate / 检查失败 Error）为空闲（Snackbar 显示后调用）。 */
    fun resetTransient() {
        val s = _state.value
        if (s is UpdateUiState.NoUpdate || (s is UpdateUiState.Error && !s.fromDownload)) {
            _state.value = UpdateUiState.Idle
        }
    }

    private companion object {
        const val TAG = "WW-Update"
    }
}
