package com.example.weight.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.theme.AppTheme

/** Health Connect 权限页的「隐私政策」入口所展示的本地说明。 */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                HealthConnectRationale(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun HealthConnectRationale(onBack: () -> Unit) {
    Scaffold(topBar = { MyTopBar(title = "Health Connect 数据说明", goBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("数据用途", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                "WeightWise 使用 Health Connect 在本机同步体重、身体成分与饮食记录，" +
                    "并读取步数、能量消耗和睡眠时长，用于生成个人趋势与报告。",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(18.dp))
            Text("控制方式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "同步默认关闭。你可以随时在 WeightWise 设置页暂停同步，或在 Health Connect 中撤销单项权限。" +
                    "停用同步不会自动删除任一侧已经保存的数据。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
