package rj.qmce.lite.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import rj.qmce.lite.CrashCatcher
import rj.qmce.lite.CrashReport
import rj.qmce.lite.ui.theme.QmceTheme

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashCatcher.readLatestReport(this, intent)
        runCatching {
            setContent { CrashScreen(report) }
        }.onFailure {
            setContentView(android.widget.TextView(this).apply {
                text = "崩溃日志 ID：${report.id}\n\n${report.error}\n\n${report.stacktrace}"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.rgb(20, 20, 28))
                setPadding(32, 32, 32, 32)
                textSize = 14f
            })
        }
    }

    @Composable
    private fun CrashScreen(report: CrashReport) {
        var showDetails by rememberSaveable { mutableStateOf(false) }
        QmceTheme {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "应用发生错误",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "请将下面的日志 ID 提供给开发者",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "关联日志 ID",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = report.id,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showDetails = !showDetails },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showDetails) "隐藏详细日志" else "查看详细日志")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { copyReport(report, includeDetails = showDetails) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showDetails) "复制详细日志" else "复制日志 ID")
                    }

                    if (showDetails) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "${report.process}  ·  ${report.thread}",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = report.error,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = report.stacktrace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            
                        )
                    }
                }
            }
        }
    }

    private fun copyReport(report: CrashReport, includeDetails: Boolean) {
        val text = if (includeDetails) {
            "${report.id}\n${report.process} ${report.thread}\n${report.error}\n\n${report.stacktrace}"
        } else {
            report.id
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QMCE crash", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }
}
