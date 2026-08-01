package rj.qmce.lite.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SplitCheckboxButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tencent.qphone.base.remote.SimpleAccount
import rj.qmce.lite.R
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.ui.settingsVm
import rj.qmce.lite.ui.theme.LocalQmceAdaptive
import rj.qmce.lite.viewmodel.AuthViewModel

private enum class LoginGuideStep {
    Welcome,
    ScreenType,
    Qr,
}

private val LoginGuideStep.officialPageId: String?
    get() = when (this) {
        LoginGuideStep.Welcome -> OfficialReportBridge.PageIds.WELCOME
        LoginGuideStep.ScreenType -> null
        LoginGuideStep.Qr -> OfficialReportBridge.PageIds.LOGIN
    }

private enum class ScreenType(val title: String, val detail: String, val selectable: Boolean) {
    Auto("自动检测", "跟随设备", true),
    Round("圆形屏幕", "标准适配", true),
    Square("方形屏幕", "未设计", false),
}

@Composable
fun LoginScreen(
    onLoginSuccess: (String, SimpleAccount) -> Unit,
    onPageIdChanged: (String?) -> Unit = {},
    vm: AuthViewModel = viewModel(),
) {
    val qrBitmap by vm.qrBitmap.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loginUiState by vm.loginUiState.collectAsState()
    val isBusy by vm.isBusy.collectAsState()
    val logText by vm.logText.collectAsState()
    val scannedAccount by vm.scannedAccount.collectAsState()
    var step by remember { mutableStateOf(LoginGuideStep.Welcome) }
    var screenType by remember { mutableStateOf(ScreenType.Auto) }
    var showErrorDetail by remember { mutableStateOf(false) }
    var userAgreement by remember { mutableStateOf(false) }
    var usageAgreement by remember { mutableStateOf(false) }

    LaunchedEffect(step, loginUiState) {
        val pageId = when {
            loginUiState is AuthViewModel.LoginUiState.AwaitingAgreement ->
                OfficialReportBridge.PageIds.PROTOCOL_CONFIRMATION
            else -> step.officialPageId
        }
        onPageIdChanged(pageId)
    }
    LaunchedEffect(Unit) { vm.initWtService() }
    LaunchedEffect(Unit) {
        vm.loginResult.collect { (uin, account) -> onLoginSuccess(uin, account) }
    }
    LaunchedEffect(loginUiState) {
        if (loginUiState !is AuthViewModel.LoginUiState.Error &&
            loginUiState !is AuthViewModel.LoginUiState.Expired
        ) {
            showErrorDetail = false
        }
        if (loginUiState !is AuthViewModel.LoginUiState.AwaitingAgreement) {
            userAgreement = false
            usageAgreement = false
        }
    }

    when (step) {
        LoginGuideStep.Welcome -> WelcomeGuide(
            onContinue = { step = LoginGuideStep.ScreenType },
        )

        LoginGuideStep.ScreenType -> ScreenTypeGuide(
            selected = screenType,
            onSelected = { type ->
                if (type.selectable) screenType = type
            },
            onContinue = {
                val effective = if (screenType.selectable) screenType else ScreenType.Auto
                when (effective) {
                    ScreenType.Auto -> settingsVm.setAutoScale(true)
                    ScreenType.Round -> {
                        settingsVm.setAutoScale(false)
                        settingsVm.setManualScale(1.50f)
                    }
                    ScreenType.Square -> settingsVm.setAutoScale(true)
                }
                step = LoginGuideStep.Qr
                vm.fetchQrCode()
            },
            onBack = { step = LoginGuideStep.Welcome },
        )

        LoginGuideStep.Qr -> {
            if (showErrorDetail) {
                LoginErrorDetailScreen(
                    message = when (val state = loginUiState) {
                        is AuthViewModel.LoginUiState.Error -> state.message
                        is AuthViewModel.LoginUiState.Expired -> "二维码已过期，请重新获取"
                        else -> statusText
                    },
                    logText = logText,
                    onRelogin = {
                        showErrorDetail = false
                        vm.fetchQrCode()
                    },
                    onBack = { showErrorDetail = false },
                )
            } else {
                QrLoginGuide(
                    qrBitmap = qrBitmap,
                    statusText = statusText,
                    uiState = loginUiState,
                    isBusy = isBusy,
                    scannedAccount = scannedAccount,
                    userAgreement = userAgreement,
                    usageAgreement = usageAgreement,
                    onUserAgreementChanged = { userAgreement = it },
                    onUsageAgreementChanged = { usageAgreement = it },
                    onConfirmLogin = vm::confirmLogin,
                    onShowErrorDetail = { showErrorDetail = true },
                    onRetry = { vm.fetchQrCode() },
                    onBack = {
                        vm.reset()
                        step = LoginGuideStep.ScreenType
                    },
                )
            }
        }
    }
}

@Composable
private fun WelcomeGuide(
    onContinue: () -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    GuideScrollColumn(
        edgeButton = {
            OfficialReportTargetBox(
                key = "login-guide:welcome",
                modifier = Modifier.fillMaxWidth(),
                elementId = OfficialReportBridge.ElementIds.LOGIN,
            ) { reportTarget ->
                EdgeButton(
                    onClick = {
                        OfficialReportBridge.reportElementClick(
                            target = reportTarget,
                            elementId = OfficialReportBridge.ElementIds.LOGIN,
                        )
                        onContinue()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    buttonSize = EdgeButtonSize.Large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(2.dp, scheme.onPrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "下一步",
                            modifier = Modifier.size(16.dp),
                            tint = scheme.onPrimary,
                        )
                    }
                }
            }
        },
    ) {
        item(key = "welcome-content") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec)
                    .graphicsLayer {
                        with(SurfaceTransformation(transformationSpec)) {
                            applyContainerTransformation()
                            applyContentTransformation()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "欢迎使用",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                )
                Spacer(Modifier.height(16.dp))
                QQLogo(76.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    "QMCE",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "QQ Max Compose Edition",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
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
    BackHandler(onBack = onBack)
    val transformationSpec = rememberTransformationSpec()
    GuideScrollColumn(
        edgeButton = {
            EdgeButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                buttonSize = EdgeButtonSize.Large,
                enabled = selected.selectable,
            ) { Text("下一步") }
        },
    ) {
        item(key = "title") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec)
                    .graphicsLayer {
                        with(SurfaceTransformation(transformationSpec)) {
                            applyContainerTransformation()
                            applyContentTransformation()
                        }
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "请选择屏幕适配类型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        ScreenType.entries.forEach { type ->
            item(key = "type-${type.name}") {
                Box(
                    Modifier
                        .transformedHeight(this, transformationSpec)
                        .padding(vertical = 4.dp),
                ) {
                    ScreenTypeOption(
                        type = type,
                        selected = selected == type,
                        onSelected = onSelected,
                        modifier = Modifier,
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
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
    scannedAccount: String?,
    userAgreement: Boolean,
    usageAgreement: Boolean,
    onUserAgreementChanged: (Boolean) -> Unit,
    onUsageAgreementChanged: (Boolean) -> Unit,
    onConfirmLogin: () -> Unit,
    onShowErrorDetail: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val transformationSpec = rememberTransformationSpec()
    val showConfirm = uiState is AuthViewModel.LoginUiState.AwaitingAgreement
    val showError = uiState is AuthViewModel.LoginUiState.Error ||
            uiState is AuthViewModel.LoginUiState.Expired
    val showLoading = uiState is AuthViewModel.LoginUiState.Preparing ||
            uiState is AuthViewModel.LoginUiState.RequestingQr ||
            uiState is AuthViewModel.LoginUiState.WaitingPhoneConfirm ||
            uiState is AuthViewModel.LoginUiState.ExchangingTicket ||
            uiState is AuthViewModel.LoginUiState.Binding ||
            (qrBitmap == null && !showError && !showConfirm)

    GuideScrollColumn(
        edgeButton = when {
            showConfirm -> {
                {
                    OfficialReportTargetBox(
                        key = "login-guide:confirm",
                        modifier = Modifier.fillMaxWidth(),
                        elementId = OfficialReportBridge.ElementIds.AGREE,
                    ) { reportTarget ->
                        EdgeButton(
                            onClick = {
                                OfficialReportBridge.reportElementClick(
                                    target = reportTarget,
                                    elementId = OfficialReportBridge.ElementIds.AGREE,
                                )
                                onConfirmLogin()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            buttonSize = EdgeButtonSize.Large,
                            enabled = userAgreement && usageAgreement,
                        ) { Text("登陆") }
                    }
                }
            }
            showError -> {
                {
                    EdgeButton(
                        onClick = onShowErrorDetail,
                        modifier = Modifier.fillMaxWidth(),
                        buttonSize = EdgeButtonSize.Large,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("错误信息")
                    }
                }
            }
            else -> null
        },
    ) {
        item(key = "qr-content") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec)
                    .graphicsLayer {
                        with(SurfaceTransformation(transformationSpec)) {
                            applyContainerTransformation()
                            applyContentTransformation()
                        }
                    }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    showError -> LoginErrorWarning()
                    showConfirm -> LoginConfirmContent(
                        account = scannedAccount,
                        statusText = statusText,
                        userAgreement = userAgreement,
                        usageAgreement = usageAgreement,
                        onUserAgreementChanged = onUserAgreementChanged,
                        onUsageAgreementChanged = onUsageAgreementChanged,
                    )
                    showLoading -> LoginLoadingContent(uiState = uiState, statusText = statusText)
                    qrBitmap != null -> QrCodeContent(
                        qrBitmap = qrBitmap,
                        isBusy = isBusy,
                        canRefresh = uiState is AuthViewModel.LoginUiState.QrReady,
                        onRetry = onRetry,
                    )
                    else -> LoginLoadingContent(uiState = uiState, statusText = statusText)
                }
            }
        }
    }
}

@Composable
private fun QrCodeContent(
    qrBitmap: Bitmap,
    isBusy: Boolean,
    canRefresh: Boolean,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Login,
        contentDescription = null,
        modifier = Modifier.size(30.dp),
        tint = scheme.onBackground,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "登陆你的QQ账号",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "请使用新版手机QQ扫码",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            "进行登陆",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(14.dp))
    Box(
        modifier = Modifier
            .size(146.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
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
    if (isBusy) {
        Spacer(Modifier.height(10.dp))
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Spacer(Modifier.height(14.dp))
    }
    Button(
        onClick = onRetry,
        enabled = canRefresh && !isBusy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        ),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        icon = {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
            )
        },
    ) {
        Text("刷新登陆二维码", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LoginGuideHeaderIcon() {
    Icon(
        imageVector = Icons.Default.HowToReg,
        contentDescription = null,
        modifier = Modifier.size(28.dp),
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun LoginLoadingContent(
    uiState: AuthViewModel.LoginUiState,
    statusText: String,
) {
    val scheme = MaterialTheme.colorScheme
    val title = when (uiState) {
        is AuthViewModel.LoginUiState.Preparing -> "正在准备登录"
        is AuthViewModel.LoginUiState.RequestingQr -> "正在获取二维码"
        is AuthViewModel.LoginUiState.WaitingPhoneConfirm -> "已扫码，请在手机确认"
        is AuthViewModel.LoginUiState.ExchangingTicket -> "正在换取登录票据"
        is AuthViewModel.LoginUiState.Binding -> "正在完成登录"
        else -> statusText.ifBlank { "请稍候" }
    }
    QQLogo(56.dp)
    Spacer(Modifier.height(16.dp))
    CircularProgressIndicator(
        modifier = Modifier.size(48.dp),
        colors = ProgressIndicatorDefaults.colors(
            indicatorColor = scheme.primary,
            trackColor = scheme.primaryContainer,
        ),
        strokeWidth = CircularProgressIndicatorDefaults.smallStrokeWidth,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    if (statusText.isNotBlank() && statusText != title) {
        Spacer(Modifier.height(4.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoginErrorWarning() {
    val scheme = MaterialTheme.colorScheme
    LoginGuideHeaderIcon()
    Spacer(Modifier.height(18.dp))
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(scheme.error, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = scheme.onError,
            modifier = Modifier.size(40.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "登录出错 请联系开发者",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        color = scheme.onBackground,
    )
}

@Composable
private fun LoginConfirmContent(
    account: String?,
    statusText: String,
    userAgreement: Boolean,
    usageAgreement: Boolean,
    onUserAgreementChanged: (Boolean) -> Unit,
    onUsageAgreementChanged: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LoginGuideHeaderIcon()
    Spacer(Modifier.height(12.dp))
    QQLogo(72.dp)
    Spacer(Modifier.height(10.dp))
    Text(
        "登陆用户名",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        account?.takeIf { it.isNotBlank() } ?: statusText.ifBlank { "等待手机确认" },
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    SplitCheckboxButton(
        checked = userAgreement,
        onCheckedChange = onUserAgreementChanged,
        toggleContentDescription = "用户协议",
        onContainerClick = { onUserAgreementChanged(!userAgreement) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("已确认接受") },
        secondaryLabel = { Text("用户协议") },
    )
    Spacer(Modifier.height(6.dp))
    SplitCheckboxButton(
        checked = usageAgreement,
        onCheckedChange = onUsageAgreementChanged,
        toggleContentDescription = "使用协议",
        onContainerClick = { onUsageAgreementChanged(!usageAgreement) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("已确认接受") },
        secondaryLabel = { Text("使用协议") },
    )
}

@Composable
private fun LoginErrorDetailScreen(
    message: String,
    logText: String,
    onRelogin: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    val detail = buildString {
        append(message)
        if (logText.isNotBlank()) {
            append("\n\n")
            append(logText)
        }
    }.ifBlank { "暂无详细报错" }
    GuideScrollColumn {
        item(key = "error-body") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec)
                    .graphicsLayer {
                        with(SurfaceTransformation(transformationSpec)) {
                            applyContainerTransformation()
                            applyContentTransformation()
                        }
                    }
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    textAlign = if (detail.length < 40) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "error-relogin") {
            GuideActionButton(
                icon = Icons.Default.Refresh,
                title = "重新登陆",
                subtitle = "返回登陆界面",
                onClick = onRelogin,
                modifier = Modifier
                    .transformedHeight(this, transformationSpec)
                    .padding(vertical = 4.dp),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
        item(key = "error-copy") {
            GuideActionButton(
                icon = Icons.Default.ContentCopy,
                title = "复制报错信息",
                subtitle = "发送给开发者",
                enabled = detail != "暂无详细报错",
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("qmce-login-error", detail))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .transformedHeight(this, transformationSpec)
                    .padding(vertical = 4.dp),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
    }
}

@Composable
private fun GuideActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        icon = { Icon(icon, contentDescription = null) },
        secondaryLabel = {
            Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
    ) {
        Text(title, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ScreenTypeOption(
    type: ScreenType,
    selected: Boolean,
    onSelected: (ScreenType) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val enabled = type.selectable
    RadioButton(
        selected = selected && enabled,
        onSelect = { if (enabled) onSelected(type) },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        icon = { ScreenPreview(type, selected && enabled) },
        secondaryLabel = {
            Text(
                type.detail,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        },
        label = {
            Text(
                type.title,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        },
    )
}

@Composable
private fun ScreenPreview(type: ScreenType, selected: Boolean) {
    val color =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    when (type) {
        ScreenType.Auto -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color,
            )
        }
        ScreenType.Round -> {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color,
            )
        }
        ScreenType.Square -> {
            SquareWatchIcon(color = color)
        }
    }
}

@Composable
private fun SquareWatchIcon(color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.size(width = 22.dp, height = 30.dp),
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(3.dp)
                .background(color, RoundedCornerShape(1.dp)),
        )
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 22.dp)
                .border(2.dp, color, RoundedCornerShape(4.dp)),
        )
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(3.dp)
                .background(color, RoundedCornerShape(1.dp)),
        )
    }
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
private fun GuideScrollColumn(
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.() -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val adaptive = LocalQmceAdaptive.current
    GuideSurface {
        if (edgeButton != null) {
            ScreenScaffold(
                scrollState = listState,
                edgeButtonSpacing = adaptive.edgeButtonSpacing,
                edgeButton = edgeButton,
            ) { contentPadding ->
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    content = content,
                )
            }
        } else {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun QQLogo(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                androidx.compose.ui.res.colorResource(R.color.ic_launcher_qq_background),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_qq_splash),
            contentDescription = "QQ",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
