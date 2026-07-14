# ── qq-sdk: 全量保留（内核反射、MSF、QRoute 动态加载） ──
-keep class com.tencent.** { *; }
-keep class mqq.** { *; }
-keep class oicq.** { *; }
-keep class d.c.g.** { *; }
-keep class com.tencent.qqnt.watch.** { *; }

# Required during MSF startup before secondary dex loading is available.
-keep class com.tencent.mobileqq.msf.sdk.AppNetConnInfo { *; }
-dontwarn com.tencent.**
-dontwarn mqq.**
-dontwarn oicq.**

# ── 签名伪装 ──
-keep class moye.** { *; }

# ── 应用代码 ──
#-keep class rj.qmce.lite.** { *; }
-keep class rj.qmce.lite.QmceApplication { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }
-keep class android.** { *; }

# ── Flag, called by QLog ──
-keep class rj.qmce.lite.Flag { *; }

# ── multidex ──
-keep class com.bytedance.** { *; }

# ── Kotlin ──
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# ── JNI / native ──
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── 通用 ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
