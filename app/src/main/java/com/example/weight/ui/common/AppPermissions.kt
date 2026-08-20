package com.example.weight.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** 权限请求结果的三种语义，DENIED 与永久拒绝的处理路径不同 */
enum class PermissionOutcome {
    /** 已授权 */
    GRANTED,
    /** 拒绝但下次仍可弹窗请求 */
    DENIED,
    /** 勾选了"不再询问"，系统弹窗不再出现，只能引导去系统设置 */
    PERMANENTLY_DENIED,
}

object AppPermissions {

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * 永久拒绝判定：未授权且系统不再展示请求理由（shouldShowRequestPermissionRationale=false）。
     * 注意须在收到拒绝回调后调用才有意义；请求前调用无法区分"从未请求过"。
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean =
        !isGranted(activity, permission) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    /** 跳转到本应用的系统设置页，供永久拒绝后的手动授权 */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}

/**
 * 统一的运行时权限请求入口。
 * 调用方只需处理三种结果语义：授权继续业务、软拒绝给轻提示、永久拒绝弹窗引导去系统设置。
 */
@Composable
fun rememberPermissionRequester(
    permission: String,
    onResult: (PermissionOutcome) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onResult(PermissionOutcome.GRANTED)
            return@rememberLauncherForActivityResult
        }
        val activity = context as? Activity
        val outcome = if (activity != null && AppPermissions.isPermanentlyDenied(activity, permission)) {
            PermissionOutcome.PERMANENTLY_DENIED
        } else {
            PermissionOutcome.DENIED
        }
        onResult(outcome)
    }
    return { launcher.launch(permission) }
}
