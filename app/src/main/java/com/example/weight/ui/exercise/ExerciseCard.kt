package com.example.weight.ui.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weight.data.exercise.ExerciseCompletion
import com.example.weight.data.exercise.ExerciseItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCard(
    exercise: ExerciseItem,
    completion: ExerciseCompletion?,
    isGenerating: Boolean,
    onToggle: () -> Unit,
    onReplace: () -> Unit,
    onSkip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompleted = completion?.isCompleted == true
    val isSkipped = completion?.skipped == true
    var showSkipInput by remember { mutableStateOf(false) }
    var skipReason by remember { mutableStateOf("") }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isDescriptionOverflowing by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isSkipped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = { if (!isGenerating) onToggle() },
                    enabled = !isGenerating,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = exercise.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (result.hasVisualOverflow) {
                                isDescriptionOverflowing = true
                            }
                        },
                        modifier = Modifier.animateContentSize(),
                    )
                    if (isDescriptionOverflowing && !isDescriptionExpanded) {
                        Text(
                            text = "展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .then(
                                    Modifier.clipToBounds().clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { isDescriptionExpanded = true }
                                ),
                        )
                    } else if (isDescriptionExpanded) {
                        Text(
                            text = "收起",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .then(
                                    Modifier.clipToBounds().clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { isDescriptionExpanded = false }
                                ),
                        )
                    }
                }
                // Replace button - only show if not completed
                if (!isCompleted && !isSkipped && !isGenerating) {
                    IconButton(
                        onClick = onReplace,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "换一个",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Skip button
                if (!isCompleted && !isGenerating) {
                    IconButton(
                        onClick = { showSkipInput = !showSkipInput },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "跳过",
                            modifier = Modifier.size(18.dp),
                            tint = if (isSkipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Chips row
            Row(
                modifier = Modifier.padding(start = 44.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip(text = "${exercise.durationMinutes}分钟")
                CategoryChip(text = "约${exercise.estimatedCalories}千卡")
                CategoryChip(text = mapCategoryLabel(exercise.category))
            }

            // Skip reason input
            AnimatedVisibility(visible = showSkipInput && !isCompleted) {
                Column(modifier = Modifier.padding(start = 44.dp, top = 8.dp)) {
                    OutlinedTextField(
                        value = skipReason,
                        onValueChange = { skipReason = it },
                        label = { Text("跳过原因（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isGenerating,
                    )
                    TextButton(
                        onClick = {
                            if (skipReason.isNotBlank()) {
                                onSkip(skipReason)
                                showSkipInput = false
                                skipReason = ""
                            }
                        },
                        enabled = skipReason.isNotBlank() && !isGenerating,
                    ) {
                        Text("确认跳过")
                    }
                }
            }

            // Show skip reason if already skipped
            if (isSkipped && completion.skipReason != null) {
                Text(
                    text = "跳过原因: ${completion.skipReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(text: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun mapCategoryLabel(category: String): String = when (category) {
    "NEAT" -> "日常活动"
    "CARDIO" -> "轻度有氧"
    "STRETCHING" -> "拉伸放松"
    "BODYWEIGHT" -> "徒手训练"
    else -> category
}
