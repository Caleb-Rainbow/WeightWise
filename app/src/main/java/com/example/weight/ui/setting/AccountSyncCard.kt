package com.example.weight.ui.setting

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.sync.SyncRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountSyncCard(repository: SyncRepository = koinInject()) {
    val account by repository.account.collectAsStateWithLifecycle()
    val status by repository.status.collectAsStateWithLifecycle()
    val pending by repository.pending.collectAsStateWithLifecycle()
    val busy by repository.busy.collectAsStateWithLifecycle()
    val last by repository.lastSync.collectAsStateWithLifecycle()
    val conflicts by repository.conflicts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var showConflicts by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("账号与数据同步", style = MaterialTheme.typography.titleMedium)
            Text(if (account.userId == 0L) "尚未登录 · 数据保存在本机" else "${account.name} · 已绑定账号")
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (account.userId > 0) Text("待同步 $pending 项" + if (last > 0) " · 最近同步 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(last))}" else "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account.userId == 0L) Button(onClick = { login = true }) { Text("登录 / 注册") }
                else {
                    Button(onClick = repository::syncNow, enabled = !busy) { Text(if (busy) "正在同步" else "立即同步") }
                    TextButton(onClick = { login = true }, enabled = !busy) { Text("重新登录") }
                    TextButton(onClick = { logout = true }, enabled = !busy) { Text("退出") }
                }
            }
            if (conflicts.isNotEmpty()) TextButton(onClick = { showConflicts = true }) { Text("查看 ${conflicts.size} 项冲突副本") }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
    if (login) AccountLoginDialog(repository, onDismiss = { login = false })
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("退出账号") },
        text = { Text("账号数据和待同步修改会保留在本机，重新登录后继续同步。退出后进入独立的本机记录空间。") },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { repository.logout() }.onFailure { error = it.message.orEmpty() }; logout = false } }) { Text("退出账号") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
    if (showConflicts) AlertDialog(onDismissRequest = { showConflicts = false }, title = { Text("同步冲突副本") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("另一台设备已修改这些数据。当前采用云端版本，本机修改保留在下方，可恢复为新记录；设置恢复会重新提交本机设置。")
                conflicts.forEach { conflict ->
                    Text(when (conflict.kind) { "weight" -> "体重记录"; "diet" -> "饮食记录"; else -> "个人设置" })
                    val detail = remember(conflict.payload) { runCatching {
                        val change = Json.parseToJsonElement(conflict.payload).jsonObject
                        val p = change.getValue("payload").jsonObject
                        if (change["deleted"]?.jsonPrimitive?.booleanOrNull == true) "本机操作：删除此记录"
                        else when (conflict.kind) {
                            "weight" -> "${p["weight"]?.jsonPrimitive?.content.orEmpty()} kg · ${p["log"]?.jsonPrimitive?.content.orEmpty()}"
                            "diet" -> "${p["date"]?.jsonPrimitive?.content.orEmpty()} · ${p["estimatedCalories"]?.jsonPrimitive?.content.orEmpty()} kcal"
                            else -> "身高 ${p["height"]?.jsonPrimitive?.content.orEmpty()} cm · 目标体重 ${p["targetWeight"]?.jsonPrimitive?.content.orEmpty()} kg"
                        }
                    }.getOrDefault("已保留本机修改") }
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { scope.launch { runCatching { repository.resolveConflict(conflict, true) }.onFailure { error = it.message.orEmpty() } } }) { Text("恢复本机修改") }
                        TextButton(onClick = { scope.launch { runCatching { repository.resolveConflict(conflict, false) }.onFailure { error = it.message.orEmpty() } } }) { Text("保留云端版本") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showConflicts = false }) { Text("关闭") } })
}

@Composable
private fun AccountLoginDialog(repository: SyncRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var captchaId by remember { mutableStateOf("") }
    var captchaImage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun refreshCaptcha() { scope.launch {
        runCatching { repository.captcha() }.onSuccess { captchaId = it["captchaId"]!!.jsonPrimitive.content; captchaImage = it["image"]!!.jsonPrimitive.content }
            .onFailure { error = it.message.orEmpty() }
    } }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (register) "注册账号" else "登录账号") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("登录后，本机未绑定的记录将归入该账号。日常记录可离线使用，联网后自动同步。")
                OutlinedTextField(username, { username = it }, label = { Text("用户名") }, singleLine = true, enabled = !busy)
                OutlinedTextField(password, { password = it }, label = { Text("密码（6–64 位）") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy)
                if (register) {
                    val bitmap = remember(captchaImage) { runCatching { val bytes = Base64.decode(captchaImage.substringAfter(','), Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
                    bitmap?.let { Image(it, "图形验证码", Modifier.height(64.dp)) }
                    OutlinedTextField(code, { code = it }, label = { Text("验证码") }, singleLine = true)
                    TextButton(onClick = ::refreshCaptcha) { Text("换一张验证码") }
                }
                TextButton(onClick = { register = !register; error = ""; if (register) refreshCaptcha() }, enabled = !busy) { Text(if (register) "已有账号，去登录" else "没有账号，去注册") }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            TextButton(enabled = !busy && username.isNotBlank() && password.isNotEmpty(), onClick = {
                busy = true; error = ""
                scope.launch {
                    runCatching { repository.login(username, password, register, captchaId, code) }
                        .onSuccess { onDismiss() }.onFailure { error = it.message ?: "登录失败"; if (register) refreshCaptcha() }
                    busy = false
                }
            }) { Text(if (busy) "请稍候" else if (register) "注册并登录" else "登录") }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
