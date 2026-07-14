package rj.qmce.lite

/**
 * Runtime feature flags, accessible from ASM-patched qq-sdk.jar code.
 */
object Flag {
    /** false = disable part of KillerApplication; use PackageSignatureProvider instead */
    @JvmField
    var USE_OLD_SIGNKILL = false // Killer is deprecated and will cause many bugs

    /** Prevent QQ SDK QLog from writing local files while keeping Android logcat output.
     * THIS IS USED IN QLOG CODE.
     * DO NOT DELETE.
     */
    @Suppress("unused")
    @JvmField
    var DISABLE_QLOG_LOCAL_WRITE = true
}
