package com.example.weight.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 沉浸式公共顶栏回归：系统栏区域必须由顶栏自身承接，不能露出页面底色。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MyTopBarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `顶栏背景从窗口顶部开始`() {
        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF284F5C),
                    onPrimary = Color.White,
                    background = Color(0xFFF7F3EA),
                ),
            ) {
                MyTopBar(
                    modifier = Modifier,
                    title = "设置",
                    goBack = {},
                    statusBarInsets = WindowInsets(0, 24, 0, 0),
                )
            }
        }

        composeRule.onNodeWithTag(IMMERSIVE_TOP_BAR_TAG)
            .assertTopPositionInRootIsEqualTo(0.dp)
    }
}
