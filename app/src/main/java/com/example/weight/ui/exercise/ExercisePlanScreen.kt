package com.example.weight.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.data.exercise.ExerciseDifficultyAdjuster
import com.example.weight.ui.common.MyTopBar
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePlanScreen(
    goBack: () -> Unit,
    viewModel: ExercisePlanViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showPreferencesSheet by remember { mutableStateOf(false) }

    val blacklist by LocalStorageData.exerciseBlacklist.collectAsStateWithLifecycle()
    val whitelist by LocalStorageData.exerciseWhitelist.collectAsStateWithLifecycle()
    val scene by LocalStorageData.exerciseScene.collectAsStateWithLifecycle()
    val hasNoPreferences = blacklist.isEmpty() && whitelist.isEmpty() && scene.isEmpty()

    Scaffold(
        topBar = {
            MyTopBar(
                title = "今日运动计划",
                goBack = goBack,
                actions = {
                    IconButton(onClick = { showPreferencesSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "偏好设置",
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading -> {
                    LoadingContent()
                }

                uiState.isGenerating -> {
                    GeneratingContent()
                }

                uiState.todayPlan == null && uiState.error == null -> {
                    EmptyPlanContent(
                        onGenerate = { viewModel.generatePlan() },
                    )
                }

                uiState.todayPlan != null -> {
                    PlanContent(
                        uiState = uiState,
                        onToggle = { viewModel.toggleExercise(it) },
                        onReplace = { viewModel.replaceExercise(it) },
                        onSkip = { id, reason -> viewModel.skipExercise(id, reason) },
                        onRegenerate = { showRegenerateDialog = true },
                        onUseFallback = { viewModel.forceRegenerate() },
                    )
                }

                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error,
                        onRetry = { viewModel.generatePlan() },
                        onUseFallback = { viewModel.forceRegenerate() },
                    )
                }
            }

            // Preferences hint card (when no preferences set and plan exists)
            if (hasNoPreferences && uiState.todayPlan != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PreferencesHintCard(onClick = { showPreferencesSheet = true })
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Preferences bottom sheet
    if (showPreferencesSheet) {
        ExercisePreferencesSheet(onDismiss = { showPreferencesSheet = false })
    }

    // Regenerate confirmation dialog
    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("重新生成计划") },
            text = { Text("确定覆盖今天的运动计划吗？当前的完成记录将丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateDialog = false
                    viewModel.forceRegenerate()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("加载中...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GeneratingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("正在为你量身定制运动计划...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyPlanContent(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有今天的运动计划",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "让AI为你制定一个轻松可达的运动计划吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGenerate) {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("生成今日运动计划")
        }
    }
}

@Composable
private fun ErrorContent(
    error: String?,
    onRetry: () -> Unit,
    onUseFallback: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "生成失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error ?: "请检查网络连接后重试",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry) {
                Text("重试")
            }
            Button(onClick = onUseFallback) {
                Text("使用推荐计划")
            }
        }
    }
}

@Composable
private fun PlanContent(
    uiState: ExercisePlanUiState,
    onToggle: (String) -> Unit,
    onReplace: (String) -> Unit,
    onSkip: (String, String) -> Unit,
    onRegenerate: () -> Unit,
    onUseFallback: () -> Unit,
) {
    val plan = uiState.todayPlan!!

    // Celebration card when all completed
    if (uiState.allCompleted) {
        CelebrationCard(streakDays = uiState.streakDays)
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Plan status card
    PlanStatusCard(
        date = plan.planDate,
        difficultyLevel = plan.difficultyLevel,
        encouragement = plan.aiAdvice,
        streakDays = uiState.streakDays,
        isAiGenerated = plan.isAiGenerated,
        totalCalories = plan.totalCalories,
        totalDuration = plan.totalDuration,
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Exercise cards
    uiState.exercises.forEach { exercise ->
        val completion = uiState.completions.find { it.exerciseId == exercise.id }
        ExerciseCard(
            exercise = exercise,
            completion = completion,
            isGenerating = uiState.isGenerating,
            onToggle = { onToggle(exercise.id) },
            onReplace = { onReplace(exercise.id) },
            onSkip = { reason -> onSkip(exercise.id, reason) },
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Daily tip card
    if (plan.dailyTip.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        DailyTipCard(tip = plan.dailyTip)
    }

    // Action buttons
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onRegenerate,
            enabled = !uiState.isGenerating,
            modifier = Modifier.weight(1f),
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("重新生成")
        }
        if (!plan.isAiGenerated) {
            OutlinedButton(
                onClick = onUseFallback,
                enabled = !uiState.isGenerating,
                modifier = Modifier.weight(1f),
            ) {
                Text("使用推荐计划")
            }
        }
    }
}

@Composable
private fun PlanStatusCard(
    date: String,
    difficultyLevel: Int,
    encouragement: String,
    streakDays: Int,
    isAiGenerated: Boolean,
    totalCalories: Int,
    totalDuration: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                    ) {
                        Text(
                            text = ExerciseDifficultyAdjuster.getDifficultyLabel(difficultyLevel),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    if (!isAiGenerated) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                        ) {
                            Text(
                                text = "离线推荐",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = encouragement,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${totalCalories}千卡 | ${totalDuration}分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (streakDays > 0) {
                    Text(
                        text = "连续打卡 ${streakDays} 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTipCard(tip: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun CelebrationCard(streakDays: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Celebration,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "太棒了，今天的运动全部完成！",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            if (streakDays > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已连续打卡 $streakDays 天，继续加油！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PreferencesHintCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "告诉AI你的运动偏好和身体限制，获得更适合你的计划",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
