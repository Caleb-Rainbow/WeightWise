package com.example.weight.ui.exercise

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "运动偏好设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "让AI更懂你的需求",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Restrictions (blacklist)
            PreferenceSection(
                title = "我不适合的运动",
                subtitle = "AI将绝对避免安排这些类型",
                predefinedTags = BlacklistTags.ALL,
                selectedTags = blacklist,
                onTagToggle = { tag ->
                    val current = blacklist
                    LocalStorageData.updateBlacklist(
                        if (tag in current) current - tag else current + tag
                    )
                },
                onCustomTagAdd = { customTag ->
                    LocalStorageData.updateBlacklist(blacklist + customTag)
                },
                onCustomTagRemove = { tag ->
                    LocalStorageData.updateBlacklist(blacklist - tag)
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Preferences (whitelist)
            PreferenceSection(
                title = "我喜欢的运动",
                subtitle = "AI会优先安排这些类型",
                predefinedTags = WhitelistTags.ALL,
                selectedTags = whitelist,
                onTagToggle = { tag ->
                    val current = whitelist
                    LocalStorageData.updateWhitelist(
                        if (tag in current) current - tag else current + tag
                    )
                },
                onCustomTagAdd = { customTag ->
                    LocalStorageData.updateWhitelist(whitelist + customTag)
                },
                onCustomTagRemove = { tag ->
                    LocalStorageData.updateWhitelist(whitelist - tag)
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Scene (single-select)
            Text(
                text = "运动场景",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "选择你通常运动的场所",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                        label = { Text(sceneOption, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferenceSection(
    title: String,
    subtitle: String,
    predefinedTags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onCustomTagAdd: (String) -> Unit,
    onCustomTagRemove: (String) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

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
        // Custom tags (those not in predefined list)
        val customTags = selectedTags.filter { it !in predefinedTags }
        customTags.forEach { customTag ->
            InputChip(
                selected = true,
                onClick = { onCustomTagRemove(customTag) },
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

    // Add custom tag input
    var customInput by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
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
