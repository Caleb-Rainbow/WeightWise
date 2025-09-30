package com.example.weight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.weight.ui.main.MainScreen
import com.example.weight.ui.record.RecordScreen
import com.example.weight.ui.setting.SettingScreen
import com.example.weight.ui.theme.AppTheme
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                ProvideVicoTheme(rememberM3VicoTheme()) {
                    ProvideSnackBarHost {
                        MainNav3()
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(goSetting = {
                navController.navigate("setting")
            }, goRecord = {
                navController.navigate("record")
            })
        }
        composable("setting") {
            SettingScreen {
                navController.navigateUp()
            }
        }
        composable("record") {
            RecordScreen {
                navController.navigateUp()
            }
        }
    }
}


@Serializable
object Main : NavKey
@Serializable
object Setting : NavKey
@Serializable
object Record : NavKey

@Composable
private fun MainNav3() {
    val backStack = rememberNavBackStack(Main)
    NavDisplay(backStack = backStack, entryProvider = entryProvider {
        entry<Main> {
            MainScreen(goSetting = {
                backStack.add(Setting)
            }, goRecord = {
                backStack.add(Record)
            })
        }
        entry<Setting> {
            SettingScreen {
                backStack.removeAt(backStack.lastIndex)
            }
        }
        entry<Record> {
            RecordScreen {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    })
}

val LocalSnackBarShow = compositionLocalOf<(String) -> Unit> {
    error("No LocalSnackBarShow provided")
}
val LocalShowLoadingDialog = compositionLocalOf<() -> Unit> {
    error("No LocalShowLoadingDialog provided")
}
val LocalHideLoadingDialog = compositionLocalOf<() -> Unit> {
    error("No LocalHideLoadingDialog provided")
}
val LocalShowMessageDialog = compositionLocalOf<(String, String, () -> Unit) -> Unit> {
    error("No LocalShowMessageDialog provided")
}

/**
 * 提供一个集中管理 SnackBar 消息、加载对话框、大图对话框、消息对话框和更新对话框的主机。
 *
 * 这个可组合函数使用 [Scaffold] 包裹 `content` 参数提供的内容，并包含一个 [SnackbarHost] 用于显示 snackbar。
 * 它还管理和显示各种类型的对话框，包括：
 * - 加载对话框：一个全屏模态对话框，指示加载状态。
 * - 大图对话框：一个显示大图片的对话框。
 * - 全局消息对话框：一个显示标题和消息的对话框。
 * - 更新对话框：一个提示用户更新应用的对话框。
 *
 * 它使用 [CompositionLocalProvider] 提供访问以下功能的入口，用于显示和隐藏这些对话框，通过以下组合本地：
 * - [LocalSnackBarShow]: 一个显示 SnackBar 消息的函数。
 * - [LocalShowLoadingDialog]: 一个显示加载对话框的函数。
 * - [LocalHideLoadingDialog]: 一个隐藏加载对话框的函数。
 * - [LocalShowMessageDialog]: 一个显示消息对话框的函数。
 *
 * @param content 要显示在 Scaffold 中的内容，会传递内padding的参数。
 * the scaffold, padding values are passed to it.
 *
 */
@Composable
fun ProvideSnackBarHost(
    content: @Composable (PaddingValues) -> Unit
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isShowLoadingDialog by remember {
        mutableStateOf(false)
    }
    var globalMessageDialogData by remember {
        mutableStateOf<GlobalMessageDialogData?>(null)
    }

    val snackBarShow: (String) -> Unit = { message: String ->
        scope.launch {
            snackBarHostState.showSnackbar(message, withDismissAction = true)
        }
    }
    val hideLoadingDialog: () -> Unit = { isShowLoadingDialog = false }
    val showLoadingDialog: () -> Unit = { isShowLoadingDialog = true }

    val showMessageDialog: (String, String, () -> Unit) -> Unit = { title, message, onConfirm ->
        globalMessageDialogData = GlobalMessageDialogData(title, message, onConfirm)
    }

    CompositionLocalProvider(
        LocalSnackBarShow provides snackBarShow,
        LocalShowLoadingDialog provides showLoadingDialog,
        LocalHideLoadingDialog provides hideLoadingDialog,
        LocalShowMessageDialog provides showMessageDialog,
    ) {
        Scaffold(
            modifier = Modifier
                .imePadding()
                .fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackBarHostState) }) { padding ->
            if (isShowLoadingDialog) {
                LoadingDialog {
                    hideLoadingDialog()
                }
            }

            globalMessageDialogData?.let {
                GlobalMessageDialog(title = it.title, message = it.message, onDismissRequest = {
                    it.onConfirm()
                    globalMessageDialogData = null
                })
            }

            content(padding)
        }
    }
}


/**
 * 显示一个带有圆形进度条的简单加载对话框的可组合函数。
 *
 * @param onDismissRequest 当对话框被关闭时调用的回调函数。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingDialog(onDismissRequest: () -> Unit) {
    BasicAlertDialog(modifier = Modifier.size(80.dp), onDismissRequest = onDismissRequest) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Box(modifier = Modifier.fillMaxSize()) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * 用于显示全局消息对话框的数据类。
 *
 * @property title 对话框的标题。
 * @property message 对话框的消息内容。
 */
data class GlobalMessageDialogData(val title: String, val message: String, val onConfirm: () -> Unit)


/**
 * 使用 [AlertDialog] 显示一个全局消息对话框。
 *
 * 此函数创建一个简单的对话框，用于显示带有给定 [title] 的 [message]。
 * 它还包含一个 "确定" 按钮，点击该按钮时会触发 [onDismissRequest] 回调。
 *
 * @param title 对话框的标题。
 * @param message 对话框中显示的消息。
 * @param onDismissRequest 当对话框被关闭或点击确定按钮时调用的回调函数。
 */
@Composable
fun GlobalMessageDialog(title: String, message: String, onDismissRequest: () -> Unit) {
    AlertDialog(onDismissRequest = onDismissRequest, title = { Text(title) }, text = {
        Text(text = message)
    }, confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = "确定")
        }
    })
}

