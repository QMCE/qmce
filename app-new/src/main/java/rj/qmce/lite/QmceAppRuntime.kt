package rj.qmce.lite

import android.os.Bundle
import com.tencent.qphone.base.util.BaseApplication
import mqq.app.AppRuntime

class QmceAppRuntime(private val app: BaseApplication) : AppRuntime() {
    override fun getApp(): BaseApplication = app
    override fun getCurrentAccountUin(): String = getAccount() ?: "0"
    override fun getCurrentUin(): String {
        val uin = currentAccountUin
        return if (uin == "0") "" else uin
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // 不 override getRuntimeService，让 AppRuntime 默认走 QRoute → WtLoginServiceImpl
    override fun getAccount(): String? = null
    override fun exit(needPCActive: Boolean) {}
    override fun getMessagePushSSOCommands(): Array<String> = emptyArray()
}
