package com.example.weight.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.update.formatBytes
import com.example.weight.data.update.formatRemaining
import com.example.weight.data.update.formatSpeed

/**
 * 全局更新弹窗：观察 [UpdateManager.state] 自动显示/隐藏（挂在宿主 Scaffold 内）。
 *
 * - [UpdateUiState.UpdateAvailable]：版本信息 + 更新日志 + 立即更新/以后再说
 * - [UpdateUiState.Downloading]：进度条 + 速度 + 已下载/总大小 + 剩余时间
 * - [UpdateUiState.Downloaded]：下载完成提示（短暂，随后调起系统安装）
 * - [UpdateUiState.Error]：下载失败（重试/关闭）；检查失败走 Snackbar 反馈，不弹窗
 */
@Composable
fun UpdateDialog(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UpdateUiState.UpdateAvailable -> AvailableDialog(s, manager)
        is UpdateUiState.Downloading -> DownloadingDialog(s)
        is UpdateUiState.Downloaded -> DownloadedDialog()
        is UpdateUiState.Error -> if (s.fromDownload) ErrorDialog(s, manager)
        else -> {} // Idle / Checking / NoUpdate / 检查失败 Error — 不显示弹窗
    }
}

@Composable
private fun AvailableDialog(state: UpdateUiState.UpdateAvailable, manager: UpdateManager) {
    val release = state.release
    AlertDialog(
        onDismissRequest = { manager.dismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("发现新版本", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 版本号徽标
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "v${release.version_name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                // 更新日志
                if (release.changelog.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "更新内容",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                release.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { manager.startDownload() }) { Text("立即更新") }
        },
        dismissButton = if (!release.force_update) {
            { TextButton(onClick = { manager.dismiss() }) { Text("以后再说") } }
        } else null,
    )
}

@Composable
private fun DownloadingDialog(state: UpdateUiState.Downloading) {
    val p = state.progress
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("正在下载", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 百分比大字
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${p.percent}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 进度条
                LinearProgressIndicator(
                    progress = { p.percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )

                // 详细信息：已下载 / 总大小
                if (p.totalBytes > 0) {
                    InfoRow(
                        label = "大小",
                        value = "${formatBytes(p.downloadedBytes)} / ${formatBytes(p.totalBytes)}",
                    )
                } else {
                    InfoRow(label = "已下载", value = formatBytes(p.downloadedBytes))
                }

                // 下载速度
                InfoRow(label = "速度", value = formatSpeed(p.speedBytesPerSec))

                // 剩余时间
                InfoRow(label = "剩余", value = formatRemaining(p.remainingSeconds))
            }
        },
        confirmButton = {},
        dismissButton = null,
    )
}

@Composable
private fun DownloadedDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("下载完成", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text("正在启动安装程序…")
            }
        },
        confirmButton = {},
        dismissButton = null,
    )
}

@Composable
private fun ErrorDialog(state: UpdateUiState.Error, manager: UpdateManager) {
    AlertDialog(
        onDismissRequest = { manager.dismiss() },
        title = { Text("更新失败", fontWeight = FontWeight.Bold) },
        text = { Text(state.message) },
        confirmButton = {
            TextButton(onClick = { manager.startDownload() }) { Text("重试") }
        },
        dismissButton = if (!manager.isForceUpdate) {
            { TextButton(onClick = { manager.dismiss() }) { Text("关闭") } }
        } else null,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
