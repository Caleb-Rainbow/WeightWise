package com.example.weight.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 深墨悬浮导航坞（T-4 起为全局层，常驻于所有一级目的地）。
 * 导航与页面表面彻底分层，中心朱砂动作只负责记体重。
 * [currentTab] 为栈顶最近的一级目的地，决定哪个槽位高亮；
 * 栈顶是二级页（设置/身体成分趋势）时沿用其下的一级目的地。
 */
@Composable
internal fun MainBottomToolbar(
    currentTab: Any?,
    onSelectHome: () -> Unit,
    onSelectDiet: () -> Unit,
    onLongPressDiet: () -> Unit,
    onSelectRecord: () -> Unit,
    onSelectReport: () -> Unit,
    onAddWeight: () -> Unit,
    modifier: Modifier = Modifier,
    homeKey: Any,
    dietKey: Any,
    recordKey: Any,
    reportKey: Any,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 18.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainToolbarItem(
                    label = "首页",
                    icon = Icons.Default.Home,
                    onClick = onSelectHome,
                    selected = currentTab == homeKey,
                )
                MainToolbarItem(
                    label = "饮食",
                    icon = Icons.Default.Restaurant,
                    onClick = onSelectDiet,
                    onLongClick = onLongPressDiet,
                    selected = currentTab == dietKey,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FilledIconButton(
                        onClick = onAddWeight,
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "记体重",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                MainToolbarItem(
                    label = "记录",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    onClick = onSelectRecord,
                    selected = currentTab == recordKey,
                )
                MainToolbarItem(
                    label = "报告",
                    icon = Icons.Default.Insights,
                    onClick = onSelectReport,
                    selected = currentTab == reportKey,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.MainToolbarItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                }
            )
            .padding(horizontal = 3.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.inversePrimary
            else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.64f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.inversePrimary
            else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
