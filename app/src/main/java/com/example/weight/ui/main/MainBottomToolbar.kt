package com.example.weight.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.example.weight.ui.theme.SurfaceEffect
import com.example.weight.ui.common.AppMaterialContext
import com.example.weight.ui.common.appMaterial

/** Floating glass navigation; secondary pages keep their parent tab selected. */
@Composable
internal fun MainBottomToolbar(
    currentTab: Any?,
    onSelectHome: () -> Unit,
    onSelectDiet: () -> Unit,
    onLongPressDiet: () -> Unit,
    onSelectRecord: () -> Unit,
    onSelectReport: () -> Unit,
    onAddWeight: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    effect: SurfaceEffect = SurfaceEffect.BLUR,
    homeKey: Any,
    dietKey: Any,
    recordKey: Any,
    reportKey: Any,
) {
    val selectedIndex = when (currentTab) {
        dietKey -> 1
        recordKey -> 2
        reportKey -> 3
        else -> 0
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val capsule = RoundedCornerShape(50)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .navigationGlass(hazeState, capsule, effect)
                    .selectableGroup()
                    .padding(6.dp),
            ) {
                val itemWidth = maxWidth / 4
                val selectionOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                    label = "navigationSelection",
                )
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .offset { IntOffset(selectionOffset.roundToPx(), 0) }
                            .width(itemWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), capsule),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MainToolbarItem("首页", Icons.Default.Home, onSelectHome, selected = selectedIndex == 0)
                    MainToolbarItem(
                        "饮食", Icons.Default.Restaurant, onSelectDiet,
                        onLongClick = onLongPressDiet, selected = selectedIndex == 1,
                    )
                    MainToolbarItem(
                        "记录", Icons.AutoMirrored.Filled.ReceiptLong, onSelectRecord,
                        selected = selectedIndex == 2,
                    )
                    MainToolbarItem("报告", Icons.Default.Insights, onSelectReport, selected = selectedIndex == 3)
                }
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .navigationGlass(hazeState, capsule, effect)
                    .clickable(role = Role.Button, onClick = onAddWeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "记体重",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun Modifier.navigationGlass(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    effect: SurfaceEffect,
): Modifier {
    return shadow(
        12.dp, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.16f),
    )
        .appMaterial(shape = shape, context = AppMaterialContext(hazeState, effect))
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
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navigationItemColor",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(50))
            .semantics { this.selected = selected }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Tab,
                        onClickLabel = label,
                        onLongClickLabel = "快速记一餐",
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
                },
            )
            .padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
