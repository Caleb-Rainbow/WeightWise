package com.example.weight.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * 用于显示AI分析结果的BottomSheet Composable
 *
 * @param showSheet 是否显示此BottomSheet
 * @param onDismissRequest 请求关闭时的回调
 * @param analysisResult 从ViewModel观察的、持续更新的分析结果字符串
 * @param isLoading 是否处于加载状态（等待API首次返回）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnalysisBottomSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    analysisResult: String,
    isLoading: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    if (showSheet) {
        ModalBottomSheet(
            sheetGesturesEnabled = false,
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            dragHandle = {
                Box(modifier = Modifier.fillMaxWidth()){
                    Text(
                        "智能分析报告",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(modifier = Modifier.align(Alignment.CenterEnd), onClick = onDismissRequest) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                if (isLoading) {
                    // 等待流式数据返回时的加载状态
                    ContainedLoadingIndicator(modifier = Modifier.padding(vertical = 48.dp))
                    Text(
                        "正在为您生成分析报告...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(analysisResult) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    val markdownState = rememberMarkdownState(analysisResult, immediate = true)
                    // 显示流式返回的文本
                    Markdown(
                        markdownState = markdownState,
                        modifier = Modifier.verticalScroll(scrollState),
                        colors = markdownColor(),
                        typography = markdownTypography()
                    )

                    /*               Text(
                                       text = analysisResult,
                                       style = MaterialTheme.typography.bodyLarge,
                                       textAlign = TextAlign.Start,
                                       modifier = Modifier
                                           .fillMaxWidth()
                                           .heightIn(min = 100.dp)
                                           .verticalScroll(rememberScrollState())
                                   )*/
                }
            }
        }
    }
}