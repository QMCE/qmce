package rj.qmce.lite.fix

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import oicq.wlogin_sdk.tools.MD5
import oicq.wlogin_sdk.tools.util
import java.security.MessageDigest

object SignatureProbe {
    @JvmStatic
    fun dump(context: Context) {
        runCatching {
            @Suppress("DEPRECATION")
            val pi = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val sig = pi.signatures?.firstOrNull()
            val md5Bytes = sig?.toByteArray()?.let { md5Hex(it) }
            val md5Chars = sig?.toCharsString()?.let { md5Hex(it.toByteArray()) }
            Log.d("QMCE-Sign", "pm pkg=${context.packageName} version=${pi.versionName} sigBytesMd5=$md5Bytes sigCharsMd5=$md5Chars sigLen=${sig?.toByteArray()?.size}")
        }.onFailure { Log.e("QMCE-Sign", "pm dump failed", it) }
        runCatching {
            val apkId = util.get_apk_id(context).toString(Charsets.UTF_8)
            val apkV = util.get_apk_v(context, apkId).toString(Charsets.UTF_8)
            val pkgSig = util.getPkgSigFromApkName(context, context.packageName).joinToString("") { "%02x".format(it) }
            Log.d("QMCE-Sign", "wlogin apkId=$apkId apkV=$apkV pkgSigMd5Bytes=$pkgSig")
        }.onFailure { Log.e("QMCE-Sign", "wlogin dump failed", it) }
    }

    private fun md5Hex(bytes: ByteArray): String = MessageDigest.getInstance("MD5")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
