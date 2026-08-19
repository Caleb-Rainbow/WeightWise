package com.example.weight.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
 * 用于显示AI分析结果的BottomSheet Composable，内容区三态：加载中 / 结果 / 失败（可重试）
 *
 * @param showSheet 是否显示此BottomSheet
 * @param onDismissRequest 请求关闭时的回调
 * @param analysisResult 从ViewModel观察的、持续更新的分析结果字符串
 * @param isLoading 是否处于加载状态（等待API首次返回）
 * @param errorMessage 失败原因，非 null 时展示错误态
 * @param onRetry 错误态下点击"重新生成"的回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnalysisBottomSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    analysisResult: String,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRetry: () -> Unit = {}
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

                when {
                    isLoading -> {
                        // 等待流式数据返回时的加载状态
                        ContainedLoadingIndicator(modifier = Modifier.padding(vertical = 48.dp))
                        Text(
                            "正在为您生成分析报告...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    errorMessage != null -> {
                        // 请求失败（鉴权失败/网络异常等）的错误状态，提供重试入口
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 32.dp)
                                .size(44.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "分析失败：$errorMessage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(onClick = onRetry) {
                            Text("重新生成")
                        }
                    }

                    else -> {
                        val scrollState = rememberScrollState()
                        LaunchedEffect(analysisResult) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        val markdownState = rememberMarkdownState(analysisResult, retainState = true)
                        // 显示流式返回的文本
                        Markdown(
                            markdownState = markdownState,
                            modifier = Modifier.verticalScroll(scrollState),
                            colors = markdownColor(),
                            typography = markdownTypography()
                        )
                    }
                }
            }
        }
    }
}