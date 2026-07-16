package rj.qmce.lite.data

import android.content.Context
import com.tencent.qphone.base.remote.SimpleAccount

object LoginPrefs {
    private const val PREFS_NAME = "qmce_login"
    private const val KEY_ACCOUNT = "account"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAccount(context: Context): SimpleAccount? {
        val stored = prefs(context).getString(KEY_ACCOUNT, null) ?: return null
        return runCatching { SimpleAccount.parseSimpleAccount(stored) }.getOrNull()
            ?.takeIf { it.isLogined }
    }

    fun saveAccount(context: Context, account: SimpleAccount) {
        prefs(context).edit().putString(KEY_ACCOUNT, account.toStoreString()).commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ACCOUNT).apply()
    }
}
