package com.example.weight.ui.main

import androidx.compose.runtime.CompositionLocalProvider
import com.example.weight.ui.common.AppMaterialContext
import com.example.weight.ui.common.LocalAppMaterialContext
import com.example.weight.ui.common.AppMaterialProfile
import com.example.weight.ui.common.appMaterial
import com.example.weight.ui.theme.SurfaceEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.weight.ui.common.AppDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyStatMode
import com.example.weight.data.record.DailyWeight
import com.example.weight.data.record.Record
import com.example.weight.ui.common.WeightChart
import com.example.weight.ui.common.SectionHeader
import com.example.weight.ui.common.StatusPill
import com.example.weight.ui.common.WeightWiseDimens
import com.example.weight.ui.common.movingAverage
import com.example.weight.ui.diet.QuickAddSheet
import com.example.weight.util.GoalProgressCalculator
import com.example.weight.util.StreakInfo
import com.example.weight.util.TimeUtils
import com.example.weight.util.WeightPredictor
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.common.vicoTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.absoluteValue



@Composable
fun SelectedRecordContent(
    record: DailyWeight?,
    modifier: Modifier = Modifier,
    streakInfo: StreakInfo = StreakInfo(0, 0, false),
    onSetting: () -> Unit = {},
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
) {
    val heroState = rememberHazeState()
    val effect = LocalAppMaterialContext.current?.effect ?: SurfaceEffect.BLUR
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        val ringColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .hazeSource(heroState)
                .background(MaterialTheme.colorScheme.primary)
                .windowInsetsPadding(statusBarInsets)
                .testTag(HERO_RING_TEST_TAG),
        ) {
            val diameter = 140.dp.toPx()
            val center = Offset(size.width - diameter / 2, size.height / 2)
            drawCircle(color = ringColor, radius = diameter * 0.42f, center = center)
            drawCircle(
                color = ringColor,
                radius = diameter * 0.29f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        CompositionLocalProvider(LocalAppMaterialContext provides AppMaterialContext(heroState, effect)) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(statusBarInsets)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WEIGHTWISE / 今日",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.64f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "今日体重",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Surface(
                    modifier = Modifier.appMaterial(
                        shape = RoundedCornerShape(16.dp), profile = AppMaterialProfile.Chrome,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    IconButton(onClick = onSetting) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(targetState = record?.value ?: 0.0, transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                    } else {
                        slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                    }.using(
                        SizeTransform(clip = false)
                    )
                }) {
                    Text(
                        text = String.format(Locale.CHINA, "%.1f", it),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = (-2).sp,
                    )
                }
                Text(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 8.dp, start = 4.dp),
                    text = "kg",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.64f),
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(visible = record != null) {
                        record?.timestamp?.let {
                            Text(
                                text = "最近记录 ${TimeUtils.convertMillisToTime(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.66f),
                            )
                        }
                    }
                    // 至少连续 2 天才展示徽章，单天打卡没有激励意义。
                    if (streakInfo.currentStreak >= 2) {
                        Spacer(Modifier.height(4.dp))
                        HeroPill(text = "连续 ${streakInfo.currentStreak} 天")
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun HeroPill(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .appMaterial(
                shape = RoundedCornerShape(10.dp), profile = AppMaterialProfile.Chrome,
                color = MaterialTheme.colorScheme.primary,
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.inversePrimary,
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}
