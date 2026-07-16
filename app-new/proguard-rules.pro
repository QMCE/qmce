# ── qq-sdk: 全量保留（内核反射、MSF、QRoute 动态加载） ──
-keep class com.tencent.** { *; }
-keep class com.tencent.qqnt.kernel.nativeinterface.** { *; }
-keep class d.c.k.o.a.a.r8 { *; }
-keep class mqq.** { *; }
-keep class oicq.** { *; }

# Stubs
-keep class android.** { *; }
-keep class d.c.g.** { *; }

# QQ Native bindings
-keep class NS_** { *; }
-keep class NS_MOBILE_FEEDS.** { *; }
-keep class NS_MOBILE_COMM.** { *; }
-keep class NS_MOBILE_OPERATION.** { *; }

# Kotlin stdlib
-keep class kotlin.** { *; }

# QQ PB Micro resolves message fields by their original names via Class.getField().
# Preserve every generated message and its fields so R8 cannot break serialization.
-keep class * extends com.tencent.mobileqq.pb.MessageMicro { *; }
-keep class com.tencent.mobileqq.qfix.ApplicationDelegate { *; }
-keep class * extends com.tencent.mobileqq.qfix.ApplicationDelegate { *; }
-keep class tencent.im.** { *; }
-dontwarn com.tencent.**
-dontwarn mqq.**
-dontwarn oicq.**
-keep class moye.** { *; }

# ── 签名伪装 ──
-keep class rj.qmce.lite.fix.** { *; }

# ── 应用代码 ──
#-keep class rj.qmce.lite.** { *; }
-keep class rj.qmce.lite.QmceApplication { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }


# Keep lazy call (?)
-keepclasseswithmembers class * {
    public static kotlin.Lazy lazy(kotlin.jvm.functions.Function0);
}

# ── Flag, called by QLog ──
-keep class rj.qmce.lite.Flag { *; }

# R8 处理 Navigation 2.9 的 NavOptions 字段重命名时会生成重复 field-id，
# Android ART 因此拒绝加载整个 APK。保持该类原始字段布局。
-keep class androidx.navigation.NavOptions { *; }

# 当前 R8 优化阶段会生成重复的 dex type_id，导致 classes3.dex 无法被 ART 校验。
# 保留 shrink/obfuscate，仅关闭有问题的优化阶段。
-dontoptimize



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
