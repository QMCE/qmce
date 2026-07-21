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
-keep class com.tencent.qqnt.watch.app.WatchApplicationDelegate { *; }
-keep class com.tencent.qqnt.watch.app.WatchApplicationDelegate$* { *; }
-keep class rj.qmce.lite.viewmodel.GroupManagementViewModel { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }


# Keep lazy call
# 不知道为啥能炸这个
-keepclasseswithmembers class * {
    public static kotlin.Lazy lazy(kotlin.jvm.functions.Function0);
}

# ── Flag, called by QLog ──
-keep class rj.qmce.lite.Flag { *; }

# R8 may emit duplicate field_ids for NavOptions after field renaming even when
# horizontal class merging is disabled. ART rejects the entire containing dex.
-keep class androidx.navigation.NavOptions { *; }


# ── stub ──
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
