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

# ── R8 9.3.16 + 已混淆 qq-sdk：Kotlin companion 链路 NPE；双 jar strip 仍触发 ──
# 保留 shrink/obfuscate，关掉 optimize 以避开 metadata rewrite 崩溃路径
-dontoptimize

# ── R8 missing classes (qq-sdk 引用但 jar/依赖未提供；Wear 路径不依赖) ──
# 来源: app-new/build/outputs/mapping/release/missing_rules.txt
-dontwarn NS_COMM.COMM$BytesEntry
-dontwarn NS_COMM.COMM$Entry
-dontwarn NS_COMM.COMM$StCommonExt
-dontwarn com.airbnb.lottie.ImageAssetDelegate
-dontwarn com.airbnb.lottie.LottieAnimationView
-dontwarn com.airbnb.lottie.LottieComposition
-dontwarn com.airbnb.lottie.LottieDrawable
-dontwarn com.airbnb.lottie.OnCompositionLoadedListener
-dontwarn com.google.android.material.appbar.AppBarLayout$OnOffsetChangedListener
-dontwarn com.google.android.material.appbar.AppBarLayout
-dontwarn com.google.android.material.button.MaterialButton
-dontwarn com.google.android.material.progressindicator.BaseProgressIndicator
-dontwarn com.google.zxing.Binarizer
-dontwarn com.google.zxing.BinaryBitmap
-dontwarn com.google.zxing.DecodeHintType
-dontwarn com.google.zxing.LuminanceSource
-dontwarn com.google.zxing.MultiFormatReader
-dontwarn com.google.zxing.RGBLuminanceSource
-dontwarn com.google.zxing.Result
-dontwarn com.google.zxing.common.HybridBinarizer
