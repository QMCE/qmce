package rj.qmce.lite

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.microsoft.appcenter.crashes.Crashes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

data class CrashReport(
    val id: String,
    val process: String,
    val thread: String,
    val error: String,
    val stacktrace: String,
)

object CrashCatcher {

    const val PREFS_NAME = "qmce_crash_report"
    private const val KEY_ID = "id"
    private const val KEY_PROCESS = "process"
    private const val KEY_THREAD = "thread"
    private const val KEY_ERROR = "error"
    private const val KEY_STACKTRACE = "stacktrace"
    private const val TAG = "CrashCatcher"

    private val installed = AtomicBoolean(false)
    private val handlingCrash = AtomicBoolean(false)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(context, thread, throwable)
        }
        Log.d(TAG, "installed process=${currentProcessName(context)}")
    }

    fun readLatestReport(context: Context, intent: Intent? = null): CrashReport {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fun value(key: String, fallback: String): String {
            return intent?.getStringExtra(key)
                ?: preferences.getString(key, null)
                ?: fallback
        }
        return CrashReport(
            id = value(KEY_ID, "UNKNOWN"),
            process = value(KEY_PROCESS, "[Unknown]"),
            thread = value(KEY_THREAD, "unknown"),
            error = value(KEY_ERROR, "未知错误"),
            stacktrace = value(KEY_STACKTRACE, "暂无堆栈信息"),
        )
    }

    private fun handleUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        if (!handlingCrash.compareAndSet(false, true)) {
            Log.e(TAG, "CrashActivity itself crashed; terminating process", throwable)
            previousHandler?.uncaughtException(thread, throwable)
                ?: terminateProcess()
            return
        }

        val report = CrashReport(
            id = createReportId(),
            process = processTag(context),
            thread = thread.name.ifBlank { "unknown" },
            error = "${throwable.javaClass.name}: ${throwable.message ?: "无错误消息"}",
            stacktrace = Log.getStackTraceString(throwable),
        )
        Log.e(
            TAG,
            "${report.process} ${report.thread} report=${report.id}: ${report.error}",
            throwable
        )
        persistReport(context, report)
        reportToAppCenter(report, throwable)

        val intent = Intent(context, rj.qmce.lite.ui.CrashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(KEY_ID, report.id)
            putExtra(KEY_PROCESS, report.process)
            putExtra(KEY_THREAD, report.thread)
            putExtra(KEY_ERROR, report.error)
            putExtra(KEY_STACKTRACE, report.stacktrace)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "Unable to open CrashActivity", it) }
    }

    private fun persistReport(context: Context, report: CrashReport) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ID, report.id)
            .putString(KEY_PROCESS, report.process)
            .putString(KEY_THREAD, report.thread)
            .putString(KEY_ERROR, report.error)
            .putString(KEY_STACKTRACE, report.stacktrace)
            .putLong("timestamp", System.currentTimeMillis())
            .commit()
    }

    private fun reportToAppCenter(report: CrashReport, throwable: Throwable) {
        runCatching {
            Crashes.trackError(
                throwable,
                mapOf(
                    "qmce_report_id" to report.id,
                    "qmce_process" to report.process,
                    "qmce_thread" to report.thread,
                    "qmce_fatal" to "true",
                ),
                emptyList(),
            )
        }.onFailure {
            Log.w(TAG, "App Center Crashes unavailable; local report is still saved", it)
        }
    }

    private fun createReportId(): String {
        val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
        return "QMCE-$date-$suffix"
    }

    private fun processTag(context: Context): String {
        val name = currentProcessName(context)
        return if (name.endsWith(":MSF", ignoreCase = true)) "[MSF]" else "[Main]"
    }

    private fun currentProcessName(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            context.packageName
        }
    }

    private fun terminateProcess(): Nothing {
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }
}
