package com.example.weight.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.data.exercise.BlacklistTags
import com.example.weight.data.exercise.SceneTags
import com.example.weight.data.exercise.WhitelistTags

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExercisePreferencesSheet(
    onDismiss: () -> Unit,
) {
    val blacklist by LocalStorageData.exerciseBlacklist.collectAsStateWithLifecycle()
    val whitelist by LocalStorageData.exerciseWhitelist.collectAsStateWithLifecycle()
    val scene by LocalStorageData.exerciseScene.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "运动偏好设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "让AI更懂你的需求",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = onDismiss,
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section 1: Blacklist
            PreferenceCard(
                icon = Icons.Default.Block,
                title = "我不适合的运动",
                subtitle = "AI将绝对避免安排这些类型",
                accentColor = MaterialTheme.colorScheme.error,
                accentContainerColor = MaterialTheme.colorScheme.errorContainer,
            ) {
                TagFlowSection(
                    predefinedTags = BlacklistTags.ALL,
                    selectedTags = blacklist,
                    onTagToggle = { tag ->
                        val current = blacklist
                        LocalStorageData.updateBlacklist(
                            if (tag in current) current - tag else current + tag,
                        )
                    },
                    onCustomTagAdd = { customTag ->
                        LocalStorageData.updateBlacklist(blacklist + customTag)
                    },
                    onCustomTagRemove = { tag ->
                        LocalStorageData.updateBlacklist(blacklist - tag)
                    },
                )
            }

            // Section 2: Whitelist
            PreferenceCard(
                icon = Icons.Default.Favorite,
                title = "我喜欢的运动",
                subtitle = "AI会优先安排这些类型",
                accentColor = MaterialTheme.colorScheme.primary,
                accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                TagFlowSection(
                    predefinedTags = WhitelistTags.ALL,
                    selectedTags = whitelist,
                    onTagToggle = { tag ->
                        val current = whitelist
                        LocalStorageData.updateWhitelist(
                            if (tag in current) current - tag else current + tag,
                        )
                    },
                    onCustomTagAdd = { customTag ->
                        LocalStorageData.updateWhitelist(whitelist + customTag)
                    },
                    onCustomTagRemove = { tag ->
                        LocalStorageData.updateWhitelist(whitelist - tag)
                    },
                )
            }

            // Section 3: Scene
            PreferenceCard(
                icon = Icons.Default.Place,
                title = "运动场景",
                subtitle = "选择你通常运动的场所",
                accentColor = MaterialTheme.colorScheme.tertiary,
                accentContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SceneTags.ALL.forEach { sceneOption ->
                        FilterChip(
                            selected = sceneOption == scene,
                            onClick = {
                                LocalStorageData.exerciseScene.value =
                                    if (sceneOption == scene) "" else sceneOption
                            },
                            label = {
                                Text(sceneOption, style = MaterialTheme.typography.bodySmall)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    accentContainerColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = accentContainerColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlowSection(
    predefinedTags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onCustomTagAdd: (String) -> Unit,
    onCustomTagRemove: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        predefinedTags.forEach { tag ->
            FilterChip(
                selected = tag in selectedTags,
                onClick = { onTagToggle(tag) },
                label = { Text(tag, style = MaterialTheme.typography.bodySmall) },
            )
        }
        val customTags = selectedTags.filter { it !in predefinedTags }
        customTags.forEach { customTag ->
            InputChip(
                selected = true,
                onClick = {  },
                label = { Text(customTag, style = MaterialTheme.typography.bodySmall) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onCustomTagRemove(customTag) },
                    )
                },
            )
        }
    }

    var customInput by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp),
    ) {
        OutlinedTextField(
            value = customInput,
            onValueChange = { customInput = it },
            placeholder = {
                Text(
                    text = "添加自定义标签",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = {
                if (customInput.isNotBlank() && customInput.trim() !in selectedTags) {
                    onCustomTagAdd(customInput.trim())
                    customInput = ""
                }
            },
            enabled = customInput.isNotBlank(),
        ) {
            Text("添加")
        }
    }
}
