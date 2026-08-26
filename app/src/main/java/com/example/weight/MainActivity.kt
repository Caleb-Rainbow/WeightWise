package com.example.weight

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.weight.data.LocalStorageData
import com.example.weight.data.health.HealthConnectManager
import com.example.weight.ui.common.navPopTransitionSpec
import com.example.weight.ui.common.navTransitionSpec
import com.example.weight.ui.common.prependNavTransitionSpec
import com.example.weight.ui.diet.DietRecordScreen
import com.example.weight.ui.main.MainBottomToolbar
import com.example.weight.ui.main.MainScreen
import com.example.weight.ui.record.RecordScreen
import com.example.weight.ui.report.ReportScreen
import com.example.weight.ui.setting.SettingScreen
import com.example.weight.ui.theme.AppTheme
import com.example.weight.ui.theme.AppearanceMode
import com.example.weight.ui.theme.ThemePreset
import com.example.weight.ui.trend.BodyTrendScreen
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    private val healthConnectManager: HealthConnectManager by inject()

    /** 来自通知/小组件/全局导航坞中央按钮的「直达记体重」请求；消费后由 UI 回调清零 */
    private var openAddDialogRequest by mutableStateOf(false)

    /** 来自周报推送通知的「直达报告页」请求；消费后由 UI 回调清零 */
    private var openReportRequest by mutableStateOf(false)

    /** 全局导航坞「饮食」长按 → 回首页弹快速记一餐（QuickAddSheet 宿主在 MainScreen） */
    private var quickAddRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableWeightWiseEdgeToEdge()
        super.onCreate(savedInstanceState)
        openAddDialogRequest = intent.consumeBooleanExtra(EXTRA_OPEN_ADD_DIALOG)
        openReportRequest = intent.consumeBooleanExtra(EXTRA_OPEN_REPORT)
        setContent {
            val themePreset by LocalStorageData.themeId.collectAsState()
            val appearanceMode by LocalStorageData.appearanceMode.collectAsState()
            AppTheme(
                themePreset = ThemePreset.fromId(themePreset),
                appearanceMode = AppearanceMode.fromId(appearanceMode),
            ) {
                WeightWiseStatusBarEffect(window)
                ProvideVicoTheme(rememberM3VicoTheme()) {
                    // 导航状态上提到宿主层（T-4）：全局导航坞需要读栈顶高亮 tab；
                    // backStack 经 remember 固定实例，navigateToTopLevel 捕获后跨重组有效
                    val backStack = rememberNavBackStack(Main)
                    val topLevelKeys = remember { setOf<NavKey>(Main, DietRecord, Record, Report) }

                    fun navigateToTopLevel(key: NavKey) {
                        // tab 切换 = 可预期重置：清到只剩 Main 再入目标（丢弃二级页），栈不随切换增长
                        backStack.removeAll { it != Main }
                        if (key != Main) backStack.add(key)
                    }

                    // 周报推送深链：直达报告 tab
                    LaunchedEffect(openReportRequest) {
                        if (openReportRequest) {
                            navigateToTopLevel(Report)
                            openReportRequest = false
                        }
                    }

                    val currentTopLevel = backStack.lastOrNull { it in topLevelKeys }

                    ProvideSnackBarHost(
                        bottomBar = {
                            MainBottomToolbar(
                                currentTab = currentTopLevel,
                                homeKey = Main,
                                dietKey = DietRecord,
                                recordKey = Record,
                                reportKey = Report,
                                onSelectHome = { navigateToTopLevel(Main) },
                                onSelectDiet = { navigateToTopLevel(DietRecord) },
                                onLongPressDiet = {
                                    navigateToTopLevel(Main)
                                    quickAddRequest = true
                                },
                                onSelectRecord = { navigateToTopLevel(Record) },
                                onSelectReport = { navigateToTopLevel(Report) },
                                onAddWeight = {
                                    navigateToTopLevel(Main)
                                    openAddDialogRequest = true
                                },
                            )
                        },
                    ) { padding ->
                        MainNav3(
                            backStack = backStack,
                            topLevelKeys = topLevelKeys,
                            navigateToTopLevel = ::navigateToTopLevel,
                            contentBottomPadding = padding,
                            openAddDialogRequest = openAddDialogRequest,
                            onOpenAddDialogConsumed = { openAddDialogRequest = false },
                            quickAddRequest = quickAddRequest,
                            onQuickAddConsumed = { quickAddRequest = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.consumeBooleanExtra(EXTRA_OPEN_ADD_DIALOG)) {
            openAddDialogRequest = true
        }
        if (intent.consumeBooleanExtra(EXTRA_OPEN_REPORT)) {
            openReportRequest = true
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { healthConnectManager.syncIfEnabled() }
    }

    /** 读掉布尔 extra 后立即移除：否则配置变更（旋转）重建时 getIntent 仍带旧 extra，深链会再次触发 */
    private fun Intent.consumeBooleanExtra(name: String): Boolean {
        val value = getBooleanExtra(name, false)
        removeExtra(name)
        return value
    }

    companion object {
        /** 通知/桌面小组件点击时携带的 extra：打开后直接弹记体重对话框 */
        const val EXTRA_OPEN_ADD_DIALOG = "open_add_dialog"

        /** 周报推送通知点击时携带的 extra：打开后直达报告页 */
        const val EXTRA_OPEN_REPORT = "open_report"
    }
}

/**
 * 全应用统一绘制到系统栏后方。状态栏先以透明 + 浅色图标启动，首帧后再根据当前主题页头
 * 的 onPrimary 亮度同步图标；导航栏继续交给 Activity 默认策略。
 */
internal fun ComponentActivity.enableWeightWiseEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
}

@Composable
private fun WeightWiseStatusBarEffect(window: Window) {
    val onPrimaryLuminance = MaterialTheme.colorScheme.onPrimary.luminance()
    SideEffect {
        window.syncStatusBarIconContrast(onPrimaryLuminance)
    }
}

/** onPrimary 偏暗说明页头本身偏亮，需要系统切换为深色状态栏图标。 */
internal fun Window.syncStatusBarIconContrast(onPrimaryLuminance: Float) {
    WindowCompat.getInsetsController(this, decorView).isAppearanceLightStatusBars =
        onPrimaryLuminance < 0.5f
}

@Serializable
object Main : NavKey

@Serializable
object Setting : NavKey

@Serializable
object Record : NavKey

@Serializable
object DietRecord : NavKey

@Serializable
object Report : NavKey

@Serializable
object BodyTrend : NavKey

@Composable
private fun MainNav3(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    topLevelKeys: Set<NavKey>,
    navigateToTopLevel: (NavKey) -> Unit,
    contentBottomPadding: PaddingValues,
    openAddDialogRequest: Boolean,
    onOpenAddDialogConsumed: () -> Unit,
    quickAddRequest: Boolean,
    onQuickAddConsumed: () -> Unit,
) {
    val transitionSpec = remember(topLevelKeys) { navTransitionSpec(topLevelKeys) }
    NavDisplay(
        backStack = backStack,
        // 全局坞在宿主 Scaffold bottomBar,页面内容只垫底部,顶部 insets 由各页自管
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentBottomPadding.calculateBottomPadding()),
        transitionSpec = transitionSpec,
        popTransitionSpec = navPopTransitionSpec,
        predictivePopTransitionSpec = prependNavTransitionSpec, entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    openAddDialogRequest = openAddDialogRequest,
                    onOpenAddDialogConsumed = onOpenAddDialogConsumed,
                    quickAddRequest = quickAddRequest,
                    onQuickAddConsumed = onQuickAddConsumed,
                    goSetting = {
                        if (backStack.lastOrNull() != Setting) backStack.add(Setting)
                    }, goRecord = {
                        navigateToTopLevel(Record)
                    }, goDietRecord = {
                        navigateToTopLevel(DietRecord)
                    }, goReport = {
                        navigateToTopLevel(Report)
                    })
            }
            entry<Setting> {
                SettingScreen {
                    backStack.removeAt(backStack.lastIndex)
                }
            }
            entry<Record> {
                RecordScreen(
                    goBack = { backStack.removeAt(backStack.lastIndex) },
                    goBodyTrend = {
                        // 已在趋势页时不再叠加一层（与设置页防抖同源）
                        if (backStack.lastOrNull() != BodyTrend) backStack.add(BodyTrend)
                    },
                )
            }
            entry<DietRecord> {
                DietRecordScreen(goBack = {
                    backStack.removeAt(backStack.lastIndex)
                }, goSetting = {
                    if (backStack.lastOrNull() != Setting) backStack.add(Setting)
                })
            }
            entry<Report> {
                ReportScreen(goBack = {
                    backStack.removeAt(backStack.lastIndex)
                })
            }
            entry<BodyTrend> {
                BodyTrendScreen(goBack = {
                    backStack.removeAt(backStack.lastIndex)
                })
            }
        })
}

// static：这些入口在 ProvideSnackBarHost 里经 remember 固定为单例，值不再变化，
// 用 static 让读取方免于逐次失效追踪，弹窗开关不再波及各页面
val LocalSnackBarShow = staticCompositionLocalOf<(String) -> Unit> {
    error("No LocalSnackBarShow provided")
}

/** 饮食删除撤销等需要带 action 按钮 SnackBar 的场景直接拿宿主状态自行 showSnackbar（可控制时长/取消重发） */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No LocalSnackbarHostState provided")
}
val LocalShowLoadingDialog = staticCompositionLocalOf<() -> Unit> {
    error("No LocalShowLoadingDialog provided")
}
val LocalHideLoadingDialog = staticCompositionLocalOf<() -> Unit> {
    error("No LocalHideLoadingDialog provided")
}
val LocalShowMessageDialog = staticCompositionLocalOf<(String, String, () -> Unit) -> Unit> {
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
    bottomBar: @Composable () -> Unit = {},
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

    // remember 固定引用：这些 lambda 经 CompositionLocal 下发到各页面，若每次重组都新建，
    // 加载/消息弹窗的开关会让所有读取方整批失效重组
    val snackBarShow: (String) -> Unit = remember(snackBarHostState, scope) {
        { message: String ->
            scope.launch {
                snackBarHostState.showSnackbar(message, withDismissAction = true)
            }
        }
    }
    val hideLoadingDialog: () -> Unit = remember { { isShowLoadingDialog = false } }
    val showLoadingDialog: () -> Unit = remember { { isShowLoadingDialog = true } }

    val showMessageDialog: (String, String, () -> Unit) -> Unit = remember {
        { title, message, onConfirm ->
            globalMessageDialogData = GlobalMessageDialogData(title, message, onConfirm)
        }
    }

    CompositionLocalProvider(
        LocalSnackBarShow provides snackBarShow,
        LocalSnackbarHostState provides snackBarHostState,
        LocalShowLoadingDialog provides showLoadingDialog,
        LocalHideLoadingDialog provides hideLoadingDialog,
        LocalShowMessageDialog provides showMessageDialog,
    ) {
        Scaffold(
            modifier = Modifier
                .imePadding()
                .fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
            bottomBar = bottomBar) { padding ->
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
        Card {
            Box(modifier = Modifier.fillMaxSize()) {
                ContainedLoadingIndicator(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(15.dp)
                        .align(
                            Alignment.Center
                        ), indicatorColor = MaterialTheme.colorScheme.primary
                )
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
data class GlobalMessageDialogData(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)


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

