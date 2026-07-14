package rj.qmce.lite.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.account.wtlogin.QrWtLoginExtObserver
import com.tencent.qqnt.account.wtlogin.api.IWtLoginService
import com.tencent.qphone.base.remote.SimpleAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mqq.app.AppRuntime
import mqq.app.MobileQQ
import oicq.wlogin_sdk.tools.ErrMsg
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuthViewModel : ViewModel() {

    companion object {
        private const val TAG = "QMCE"
        private const val LOGIN_APP_ID = 537243416
        private const val SERVICE_INIT_TIMEOUT_MS = 12_000L
        private const val FETCH_TIMEOUT_MS = 8_000L
        private const val CALLBACK_TIMEOUT_MS = 35_000L
        private const val MAX_TICKET_ATTEMPTS = 3
        private const val TICKET_RETRY_DELAY_MS = 1_500L
    }

    sealed interface LoginUiState {
        data object Idle : LoginUiState
        data object Preparing : LoginUiState
        data object RequestingQr : LoginUiState
        data object QrReady : LoginUiState
        data object Scanned : LoginUiState
        data object ExchangingTicket : LoginUiState
        data object Binding : LoginUiState
        data class Error(val message: String) : LoginUiState
        data object Expired : LoginUiState
    }

    private data class LoginServices(
        val runtime: AppRuntime,
        val wtService: IWtLoginService,
    )

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap

    private val _statusText = MutableStateFlow("未初始化")
    val statusText: StateFlow<String> = _statusText

    private val _loginUiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginUiState: StateFlow<LoginUiState> = _loginUiState

    private val _logText = MutableStateFlow("")

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _loginResult = MutableSharedFlow<Pair<String, SimpleAccount>>()
    val loginResult: SharedFlow<Pair<String, SimpleAccount>> = _loginResult

    private val serviceInitMutex = Mutex()
    private var appRuntime: AppRuntime? = null
    private var wtService: IWtLoginService? = null
    private var loginObserver: QrWtLoginExtObserver? = null
    private var observerRuntime: AppRuntime? = null
    private var buildTime = 0L
    private var expireTimeSec = 0L
    private var queryTimeSec = 5L
    private var remainingSec = 0L
    private var pollJob: Job? = null
    private var callbackWatchdogJob: Job? = null
    private var ticketRetryJob: Job? = null
    private var ticketAccount: String? = null
    private var ticketAttempt = 0
    private var requestGeneration = 0L

    fun initWtService() {
        viewModelScope.launch {
            val services = awaitLoginServices()
            if (services != null) {
                if (_loginUiState.value == LoginUiState.Idle) {
                    _statusText.value = "就绪"
                }
                appendLog("runtime ready=${services.runtime}, service=${services.wtService.javaClass.name}")
            } else if (_loginUiState.value == LoginUiState.Idle) {
                _statusText.value = "登录服务未就绪"
            }
        }
    }

    fun fetchQrCode() {
        val generation = beginRequest()
        viewModelScope.launch {
            setState(LoginUiState.Preparing, "正在准备登录", busy = true)
            val services = awaitLoginServices()
            if (!isCurrent(generation)) return@launch
            if (services == null) {
                failRequest(generation, "登录服务初始化超时")
                return@launch
            }

            setState(LoginUiState.RequestingQr, "正在获取二维码", busy = true)
            val observer = makeObserver(generation)
            loginObserver = observer
            val registered = runCatching {
                services.runtime.registObserver(observer)
                observerRuntime = services.runtime
            }
                .onFailure { appendLog("注册登录观察者失败: ${it.message}") }
                .isSuccess
            if (!registered || !isCurrent(generation)) {
                if (isCurrent(generation)) failRequest(generation, "登录服务初始化失败")
                return@launch
            }

            val fetchResult = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        services.wtService.fetchCodeSigVerifyLogin(LOGIN_APP_ID, null)
                    }
                }
            }
            if (!isCurrent(generation)) return@launch
            when {
                fetchResult == null -> failRequest(generation, "二维码请求超时")
                fetchResult.isFailure -> failRequest(
                    generation,
                    "二维码请求异常: ${fetchResult.exceptionOrNull()?.message ?: "未知错误"}",
                )
                fetchResult.getOrThrow() != 0 -> failRequest(
                    generation,
                    "二维码请求失败 (${fetchResult.getOrThrow()})",
                )
                else -> {
                    appendLog("fetch=${fetchResult.getOrThrow()}")
                    armCallbackWatchdog(generation, "二维码回调超时")
                }
            }
        }
    }

    fun reset() {
        invalidateRequest(clearQr = true)
        _logText.value = ""
        buildTime = 0L
        expireTimeSec = 0L
        remainingSec = 0L
        _statusText.value = if (wtService != null) "就绪" else "未初始化"
        _loginUiState.value = LoginUiState.Idle
        _isBusy.value = false
    }

    private suspend fun awaitLoginServices(): LoginServices? = serviceInitMutex.withLock {
        val cachedRuntime = appRuntime
        val cachedService = wtService
        if (cachedRuntime != null && cachedService != null && isRuntimeRunning(cachedRuntime)) {
            return@withLock LoginServices(cachedRuntime, cachedService)
        }

        var resolved: LoginServices? = null
        withTimeoutOrNull(SERVICE_INIT_TIMEOUT_MS) {
            while (resolved == null) {
                val runtime = withContext(Dispatchers.IO) {
                    runCatching { MobileQQ.sMobileQQ?.waitAppRuntime() }.getOrNull()
                        ?: QmceApplication.ensureRuntime()
                }
                val service = runtime?.let {
                    runCatching { it.getRuntimeService(IWtLoginService::class.java, "") }.getOrNull()
                }
                if (runtime != null && service != null && isRuntimeRunning(runtime)) {
                    resolved = LoginServices(runtime, service)
                } else {
                    delay(250L)
                }
            }
        }
        resolved?.also {
            appRuntime = it.runtime
            wtService = it.wtService
        }
        resolved
    }

    private fun isRuntimeRunning(runtime: AppRuntime): Boolean = runCatching { runtime.isRunning }.getOrDefault(false)

    private fun beginRequest(): Long {
        invalidateRequest(clearQr = true)
        requestGeneration += 1L
        return requestGeneration
    }

    private fun invalidateRequest(clearQr: Boolean) {
        requestGeneration += 1L
        pollJob?.cancel()
        pollJob = null
        callbackWatchdogJob?.cancel()
        callbackWatchdogJob = null
        ticketRetryJob?.cancel()
        ticketRetryJob = null
        ticketAccount = null
        ticketAttempt = 0
        unregisterLoginObserver()
        if (clearQr) _qrBitmap.value = null
    }

    private fun isCurrent(generation: Long): Boolean = requestGeneration == generation

    private fun setState(state: LoginUiState, status: String, busy: Boolean) {
        _loginUiState.value = state
        _statusText.value = status
        _isBusy.value = busy
    }

    private fun failRequest(generation: Long, message: String) {
        if (!isCurrent(generation)) return
        appendLog(message)
        invalidateRequest(clearQr = false)
        _loginUiState.value = LoginUiState.Error(message)
        _statusText.value = message
        _isBusy.value = false
    }

    private fun expireRequest(generation: Long) {
        if (!isCurrent(generation)) return
        appendLog("二维码已过期")
        invalidateRequest(clearQr = false)
        _loginUiState.value = LoginUiState.Expired
        _statusText.value = "二维码已过期"
        _isBusy.value = false
    }

    private fun armCallbackWatchdog(generation: Long, message: String) {
        callbackWatchdogJob?.cancel()
        callbackWatchdogJob = viewModelScope.launch {
            delay(CALLBACK_TIMEOUT_MS)
            if (isCurrent(generation)) failRequest(generation, message)
        }
    }

    private fun armTicketCallbackWatchdog(generation: Long) {
        callbackWatchdogJob?.cancel()
        callbackWatchdogJob = viewModelScope.launch {
            delay(CALLBACK_TIMEOUT_MS)
            if (isCurrent(generation) && _loginUiState.value == LoginUiState.ExchangingTicket) {
                scheduleTicketRetry(generation, "登录票据返回超时")
            }
        }
    }

    private fun requestTicket(generation: Long) {
        if (!isCurrent(generation)) return
        val account = ticketAccount
        val service = wtService
        if (account.isNullOrBlank()) {
            failRequest(generation, "登录服务未返回账号")
            return
        }
        if (service == null) {
            failRequest(generation, "登录服务已断开")
            return
        }

        ticketAttempt += 1
        val attempt = ticketAttempt
        setState(
            LoginUiState.ExchangingTicket,
            if (attempt == 1) {
                "正在换取登录票据..."
            } else {
                "票据返回较慢，正在重试 ($attempt/$MAX_TICKET_ATTEMPTS)"
            },
            busy = true,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { service.getStWithQrSig(account, LOGIN_APP_ID, null) }
            }
            if (!isCurrent(generation)) return@launch
            val immediateRet = result.getOrNull()
            when {
                result.isFailure -> scheduleTicketRetry(
                    generation,
                    "请求登录票据异常: ${result.exceptionOrNull()?.message ?: "未知错误"}",
                )

                immediateRet != 0 -> scheduleTicketRetry(
                    generation,
                    "换取登录票据失败 ($immediateRet)",
                )

                _loginUiState.value == LoginUiState.ExchangingTicket && ticketAttempt == attempt -> {
                    appendLog("换票请求已发送 attempt=$attempt")
                    armTicketCallbackWatchdog(generation)
                }
            }
        }
    }

    private fun scheduleTicketRetry(generation: Long, reason: String) {
        if (!isCurrent(generation)) return
        callbackWatchdogJob?.cancel()
        if (ticketAttempt >= MAX_TICKET_ATTEMPTS) {
            failRequest(generation, "$reason，请刷新二维码重试")
            return
        }
        appendLog("$reason，准备重试 ${ticketAttempt + 1}/$MAX_TICKET_ATTEMPTS")
        _statusText.value = "$reason，准备重试..."
        _isBusy.value = true
        ticketRetryJob?.cancel()
        ticketRetryJob = viewModelScope.launch {
            delay(TICKET_RETRY_DELAY_MS)
            if (isCurrent(generation)) requestTicket(generation)
        }
    }

    private fun startPolling(service: IWtLoginService, generation: Long) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && isCurrent(generation)) {
                val elapsed = System.currentTimeMillis() - buildTime
                if (expireTimeSec > 0 && elapsed >= expireTimeSec * 1000L) {
                    expireRequest(generation)
                    return@launch
                }
                if (expireTimeSec > 0 && _loginUiState.value == LoginUiState.QrReady) {
                    remainingSec = ((expireTimeSec * 1000L - elapsed) / 1000L).coerceAtLeast(0L)
                    _statusText.value = "请扫码 (${remainingSec}s)"
                }
                val queryRet = withContext(Dispatchers.IO) {
                    runCatching { service.queryCodeResult(null) }.getOrDefault(-1)
                }
                if (queryRet != 0) appendLog("query=$queryRet")
                delay(queryTimeSec.coerceAtLeast(1L) * 1000L)
            }
        }
    }

    private fun makeObserver(generation: Long): QrWtLoginExtObserver = object : QrWtLoginExtObserver() {
        override fun a() {
            viewModelScope.launch {
                failRequest(generation, "登录服务返回异常")
            }
        }

        override fun b(picBuf: ByteArray?, expireTime: Long, queryTime: Long, ret: Int, errMsg: String?) {
            viewModelScope.launch(Dispatchers.Default) {
                val bitmap = if (ret == 0 && picBuf != null) {
                    BitmapFactory.decodeByteArray(picBuf, 0, picBuf.size)
                } else {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (!isCurrent(generation)) return@withContext
                    callbackWatchdogJob?.cancel()
                    if (ret != 0 || picBuf == null) {
                        failRequest(generation, "二维码获取失败 ($ret)${errMsg?.let { ": $it" } ?: ""}")
                        return@withContext
                    }
                    if (bitmap == null) {
                        failRequest(generation, "二维码图片解码失败")
                        return@withContext
                    }
                    _qrBitmap.value = bitmap
                    buildTime = System.currentTimeMillis()
                    expireTimeSec = expireTime.coerceAtLeast(1L)
                    queryTimeSec = queryTime.coerceAtLeast(1L)
                    remainingSec = expireTimeSec
                    setState(LoginUiState.QrReady, "请扫码 (${expireTimeSec}s)", busy = false)
                    appendLog("二维码 ${bitmap.width}x${bitmap.height} expire=$expireTime query=$queryTime")
                    val service = wtService
                    if (service != null) startPolling(service, generation)
                    else failRequest(generation, "登录服务已断开")
                }
            }
        }

        override fun d(account: String?, accountType: Int, sigCreateTime: Long, ret: Int, errMsg: String?) {
            viewModelScope.launch {
                if (!isCurrent(generation)) return@launch
                when (ret) {
                    0 -> {
                        val cleanAccount = account.orEmpty()
                        if (cleanAccount.isBlank()) {
                            failRequest(generation, "登录服务未返回账号")
                            return@launch
                        }
                        appendLog("扫码确认 account=$cleanAccount")
                        pollJob?.cancel()
                        callbackWatchdogJob?.cancel()
                        ticketAccount = cleanAccount
                        ticketAttempt = 0
                        requestTicket(generation)
                    }
                    48 -> Unit
                    53 -> {
                        callbackWatchdogJob?.cancel()
                        setState(LoginUiState.Scanned, "已扫码，等待确认", busy = false)
                    }
                    17, 54 -> expireRequest(generation)
                    else -> appendLog("query ret=$ret ${errMsg.orEmpty()}")
                }
            }
        }

        override fun c(
            userAccount: String?,
            appId: Long,
            mainSigMap: Int,
            subDstAppId: Long,
            ret: Int,
            errMsg: ErrMsg?,
        ) {
            viewModelScope.launch {
                if (!isCurrent(generation)) return@launch
                pollJob?.cancel()
                callbackWatchdogJob?.cancel()
                val cleanAccount = userAccount.orEmpty()
                if (ret != 0 || cleanAccount.isBlank()) {
                    scheduleTicketRetry(
                        generation,
                        "换票失败 ($ret)${errMsg?.title?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}",
                    )
                    return@launch
                }

                ticketRetryJob?.cancel()
                appendLog("换票成功 uin=$cleanAccount")
                setState(LoginUiState.Binding, "正在绑定账号...", busy = true)
                val account = SimpleAccount().apply {
                    uin = cleanAccount
                    loginProcess = BuildConfig.APPLICATION_ID
                    setAttribute(SimpleAccount._ISLOGINED, "true")
                    setAttribute(SimpleAccount._LOGINPROCESS, BuildConfig.APPLICATION_ID)
                    setAttribute(SimpleAccount._LOGINTIME, System.currentTimeMillis().toString())
                }
                val bind = withContext(Dispatchers.IO) {
                    KernelBridge.bindLoggedInAccount(cleanAccount, account)
                }
                if (!isCurrent(generation)) return@launch
                appendLog("绑定: $bind")
                if (bind == "ok") {
                    invalidateRequest(clearQr = true)
                    _statusText.value = "登录成功 $cleanAccount"
                    _isBusy.value = false
                    _loginResult.emit(cleanAccount to account)
                } else {
                    failRequest(generation, "绑定失败")
                    resetFailedLoginSession()
                }
            }
        }

        override fun onReceive(type: Int, isSuccess: Boolean, data: Bundle?) {
            val keys = data?.keySet()?.joinToString(",") ?: ""
            Log.d(TAG, "auth observer type=$type success=$isSuccess keys=$keys")
            super.onReceive(type, isSuccess, data)
        }
    }

    private fun appendLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        _logText.value = "$ts $msg\n${_logText.value}"
        Log.d(TAG, "auth: $msg")
    }

    private fun unregisterLoginObserver() {
        val observer = loginObserver ?: return
        runCatching { (observerRuntime ?: appRuntime)?.unRegistObserver(observer) }
        loginObserver = null
        observerRuntime = null
    }

    private suspend fun resetFailedLoginSession() {
        appRuntime = null
        wtService = null
        withContext(Dispatchers.IO) {
            runCatching {
                (com.tencent.qphone.base.util.BaseApplication.getContext() as? QmceApplication)
                    ?.clearLocalLoginState()
            }
                .onFailure { appendLog("清理失败登录会话失败: ${it.message}") }
        }
    }

    override fun onCleared() {
        reset()
        super.onCleared()
    }
}
