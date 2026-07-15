package rj.qmce.lite.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.tencent.qphone.base.remote.SimpleAccount
import rj.qmce.lite.R
import rj.qmce.lite.viewmodel.AuthViewModel

private enum class LoginGuideStep {
    Welcome,
    ScreenType,
    Agreement,
    Qr,
}

private enum class ScreenType(val title: String, val detail: String) {
    Round("圆形屏幕", "适用于圆形手表屏幕"),
    Square("方形屏幕", "适用于方形或矩形屏幕（未实装）"),
}

@Composable
fun LoginScreen(
    onLoginSuccess: (String, SimpleAccount) -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    val qrBitmap by vm.qrBitmap.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loginUiState by vm.loginUiState.collectAsState()
    val isBusy by vm.isBusy.collectAsState()
    var step by remember { mutableStateOf(LoginGuideStep.Welcome) }
    var screenType by remember { mutableStateOf(ScreenType.Round) }
    var agreed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.initWtService() }
    LaunchedEffect(Unit) {
        vm.loginResult.collect { (uin, account) -> onLoginSuccess(uin, account) }
    }

    when (step) {
        LoginGuideStep.Welcome -> WelcomeGuide(onContinue = { step = LoginGuideStep.ScreenType })
        LoginGuideStep.ScreenType -> ScreenTypeGuide(
            selected = screenType,
            onSelected = { screenType = it },
            onContinue = { step = LoginGuideStep.Agreement },
            onBack = { step = LoginGuideStep.Welcome },
        )
        LoginGuideStep.Agreement -> AgreementGuide(
            agreed = agreed,
            onAgreedChanged = { agreed = it },
            onContinue = {
                if (agreed) {
                    step = LoginGuideStep.Qr
                    vm.fetchQrCode()
                }
            },
            onBack = { step = LoginGuideStep.ScreenType },
        )
        LoginGuideStep.Qr -> QrLoginGuide(
            qrBitmap = qrBitmap,
            statusText = statusText,
            uiState = loginUiState,
            isBusy = isBusy,
            onRetry = { vm.fetchQrCode() },
            onBack = {
                vm.reset()
                step = LoginGuideStep.Agreement
            },
        )
    }
}

@Composable
private fun WelcomeGuide(onContinue: () -> Unit) {
    GuideScrollColumn {
        item(key = "welcome-content") {
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QQLogo(68.dp)
            Spacer(Modifier.height(18.dp))
            Text("欢迎使用", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "QQ Max",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(30.dp))
            PrimaryGuideButton(text = "开始使用", onClick = onContinue)
        }
        }
    }
}

@Composable
private fun ScreenTypeGuide(
    selected: ScreenType,
    onSelected: (ScreenType) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    GuideScrollColumn {
            item(key = "back") {
                GuideBackButton(onBack)
            }
            item(key = "title") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("选择屏幕适配类型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "这将影响列表的缩放效果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "round") {
                Box(Modifier.padding(vertical = 4.dp)) {
                    ScreenTypeOption(ScreenType.Round, selected == ScreenType.Round, onSelected)
                }
            }
            item(key = "square") {
                Box(Modifier.padding(vertical = 4.dp)) {
                    ScreenTypeOption(ScreenType.Square, selected == ScreenType.Square, onSelected)
                }
            }
            item(key = "continue") {
                PrimaryGuideButton(
                    text = "继续",
                    onClick = onContinue,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                )
        }
    }
}

@Composable
private fun AgreementGuide(
    agreed: Boolean,
    onAgreedChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    GuideScrollColumn {
        item(key = "back") {
            GuideBackButton(onBack)
        }
        item(key = "agreement-content") {
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QQLogo(54.dp)
            Spacer(Modifier.height(15.dp))
            Text("同意许可协议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "继续使用即表示你同意 QQ Max 的用户许可协议与隐私说明。",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                
            )
            Spacer(Modifier.height(20.dp))
            CheckboxButton(
                checked = agreed,
                onCheckedChange = onAgreedChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("我已阅读并同意") },
            )
                Spacer(Modifier.height(16.dp))
                PrimaryGuideButton(text = "同意并继续", enabled = agreed, onClick = onContinue)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun QrLoginGuide(
    qrBitmap: Bitmap?,
    statusText: String,
    uiState: AuthViewModel.LoginUiState,
    isBusy: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    GuideScrollColumn {
        item(key = "back") {
            GuideBackButton(onBack)
        }
        item(key = "qr-content") {
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                uiState is AuthViewModel.LoginUiState.Error -> {
                    LoginErrorContent(uiState.message, onRetry)
                }
                uiState is AuthViewModel.LoginUiState.Expired -> {
                    LoginErrorContent("二维码已过期，请重新获取", onRetry)
                }
                uiState is AuthViewModel.LoginUiState.Preparing ||
                    uiState is AuthViewModel.LoginUiState.RequestingQr ||
                    qrBitmap == null -> {
                    LoginLoadingContent(statusText)
                }
                else -> QrCodeContent(
                    qrBitmap = qrBitmap,
                    statusText = statusText,
                    isBusy = isBusy,
                    canRefresh = uiState is AuthViewModel.LoginUiState.QrReady,
                    onRetry = onRetry,
                )
            }
        }
        }
    }
}

@Composable
private fun QrCodeContent(
    qrBitmap: Bitmap,
    statusText: String,
    isBusy: Boolean,
    canRefresh: Boolean,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text("扫码登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp))
    Text("请使用手机 QQ 扫描二维码", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Box(
        modifier = Modifier
            .size(142.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "登录二维码",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
    Spacer(Modifier.height(15.dp))
    LoginStatus(statusText, isBusy)
    Spacer(Modifier.height(18.dp))
    SecondaryGuideButton(text = "刷新二维码", enabled = canRefresh && !isBusy, onClick = onRetry)
}

@Composable
private fun LoginLoadingContent(statusText: String) {
    Spacer(Modifier.height(40.dp))
    Box(
        modifier = Modifier.size(82.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
    }
    Spacer(Modifier.height(20.dp))
    Text("正在准备登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun LoginErrorContent(statusText: String, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Spacer(Modifier.height(28.dp))
    Box(
        modifier = Modifier.size(72.dp).background(scheme.errorContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = scheme.onErrorContainer)
    }
    Spacer(Modifier.height(18.dp))
    Text("登录出错", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(statusText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    PrimaryGuideButton(text = "重新扫码", onClick = onRetry)
}

@Composable
private fun LoginStatus(statusText: String, isBusy: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScreenTypeOption(type: ScreenType, selected: Boolean, onSelected: (ScreenType) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RadioButton(
        selected = selected,
        onSelect = { onSelected(type) },
        modifier = Modifier.fillMaxWidth(),
        icon = { ScreenPreview(type, selected) },
        secondaryLabel = { Text(type.detail) },
        label = { Text(type.title, fontWeight = FontWeight.Medium) },
    )
}

@Composable
private fun ScreenPreview(type: ScreenType, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(if (type == ScreenType.Round) 34.dp else 32.dp)
            .clip(if (type == ScreenType.Round) CircleShape else RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.22f))
            .border(2.dp, color, if (type == ScreenType.Round) CircleShape else RoundedCornerShape(7.dp)),
    )
}

@Composable
private fun GuideSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        content = { content() },
    )
}

@Composable
private fun GuideScrollColumn(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    GuideSurface {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                viewportVerticalOffsetResolver = { 0 },
            ),
            autoCentering = null,
            content = content,
        )
    }
}

@Composable
private fun QQLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.size(size).background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.qqpro_ic_fg),
            contentDescription = "QQ",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun GuideBackButton(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        TextButton(onClick = onBack) {
            Text("返回", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrimaryGuideButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SecondaryGuideButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun String.isLoginError(): Boolean = contains("失败") || contains("错误") || contains("过期") || contains("不可用")
