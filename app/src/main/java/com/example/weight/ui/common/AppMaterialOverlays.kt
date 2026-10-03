package com.example.weight.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.example.weight.ui.theme.SurfaceEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val parent = LocalOverlayMaterialContext.current
    val state = rememberHazeState()
    val context = remember(state, parent?.effect) { AppMaterialContext(state, parent?.effect ?: SurfaceEffect.BLUR) }
    // A nested menu captures this dialog, while the dialog itself samples its parent window.
    CompositionLocalProvider(LocalOverlayMaterialContext provides context) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier.hazeSource(state).appMaterial(shape, AppMaterialProfile.Readable, containerColor, parent),
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = Color.Transparent,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    val parent = LocalOverlayMaterialContext.current
    val state = rememberHazeState()
    val context = remember(state, parent?.effect) { AppMaterialContext(state, parent?.effect ?: SurfaceEffect.BLUR) }
    CompositionLocalProvider(LocalOverlayMaterialContext provides context) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier.hazeSource(state).appMaterial(shape, AppMaterialProfile.Readable, containerColor, parent),
            sheetState = sheetState,
            sheetGesturesEnabled = sheetGesturesEnabled,
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.appMaterial(
            RoundedCornerShape(20.dp), AppMaterialProfile.Readable,
            MaterialTheme.colorScheme.surfaceContainerHigh, LocalOverlayMaterialContext.current,
        ),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
internal fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.appMaterial(
                MaterialTheme.shapes.large, AppMaterialProfile.Readable,
                MaterialTheme.colorScheme.inverseSurface, LocalOverlayMaterialContext.current,
            ),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
            dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}
