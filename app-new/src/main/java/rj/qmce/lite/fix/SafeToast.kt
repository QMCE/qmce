package rj.qmce.lite.fix

import android.content.Context
import android.widget.Toast

// QQ Toast redirect
object SafeToast {
    @JvmStatic
    fun show(context: Context?, message: CharSequence?, duration: Int, style: Int) {
        if (context == null || message.isNullOrEmpty()) return
        Toast.makeText(context.applicationContext, message, duration).show()
    }
}
