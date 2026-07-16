package rj.qmce.lite.fix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.VersionedPackage;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import rj.qmce.lite.BuildConfig;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/** Adapted from Lmoye\signature\KillerApplication. */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public final class LegacyKiller {
    private static final String TAG = "QMCE-Killer";

    private static final String CURRENT_PACKAGE_NAME = BuildConfig.APPLICATION_ID;
    private static final String ORIGIN_PACKAGE_NAME = "com.tencent.qqlite";
    private static final String ORIGIN_VERSION_NAME = "9.0.3";
    private static final int ORIGIN_VERSION_CODE = 2465;

    @SuppressWarnings("TextBlockMigration")
    private static final String ORIGIN_SIGNATURE_BASE64 = "MIICUzCCAbygAwIBAgIES7sDYTANBgkqhkiG9w0BAQUFADBtMQ4wDAYDVQQGEwVDaGluYTEPMA0G\n" +
            "A1UECAwG5YyX5LqsMQ8wDQYDVQQHDAbljJfkuqwxDzANBgNVBAoMBuiFvuiurzEbMBkGA1UECwwS\n" +
            "5peg57q/5Lia5Yqh57O757ufMQswCQYDVQQDEwJRUTAgFw0xMDA0MDYwOTQ4MTdaGA8yMjg0MDEy\n" +
            "MDA5NDgxN1owbTEOMAwGA1UEBhMFQ2hpbmExDzANBgNVBAgMBuWMl+S6rDEPMA0GA1UEBwwG5YyX\n" +
            "5LqsMQ8wDQYDVQQKDAbohb7orq8xGzAZBgNVBAsMEuaXoOe6v+S4muWKoeezu+e7nzELMAkGA1UE\n" +
            "AxMCUVEwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAKFel1Yhb2lMWRXgtSkJUlQ2fE5k+u/w\n" +
            "euE0iNlGYVpY3cMaQV9xfQGe3G0wuWA9Pip7PeCrfgz1Lf7jk3O8Ry+plwJ9eY1Z+B1SWmns8Vbo\n" +
            "hf0eJ5CSQ4ayIwzJDjt63JVgPdz0xAvccvItsPIWqZw3HTv4nLpleMYGmeig1TaVAgMBAAEwDQYJ\n" +
            "KoZIhvcNAQEFBQADgYEAlKm4DoBpFkXdQtZhF3WoVfcbzU13y2Co4pQEA1peALIbzF1KViSCEmvZ\n" +
            "G2sOUHCTd86574wu/RLMixav2aFZ81C7JwsUIE/wZdhDgycgcC4otBSR+8OiBfXy9CUm1n8XYU2K\n" +
            "l03mSHsshm7+3jtOSaD5FrqjwTNv0u4bFillIEk=\n";

    private static volatile boolean creatorInstalled;
    private static volatile boolean pmProxyInstalled;

    static {
        installForCurrentPackage();
    }

    private LegacyKiller() {
    }

    public static void installForCurrentPackage() {
        installForCurrentPackage(null);
    }

    public static void installForCurrentPackage(Context context) {
        // Always install PM proxy for package name mapping (com.tencent.qqlite → rj.qmce.litex).
        // Signature spoofing is gated by Flag.USE_OLD_SIGNKILL.
        killPM();
        if (context != null) {
            hookContextPackageManager(context);
        }
    }

    public static Field a(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> current = cls;
        NoSuchFieldException first = null;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                if (first == null) first = e;
                current = current.getSuperclass();
            }
        }
        throw first != null ? first : new NoSuchFieldException(name);
    }

    private static void killPM() {
        try {
            exemptHiddenApi();
            Signature signature = new Signature(Base64.decode(LegacyKiller.ORIGIN_SIGNATURE_BASE64, Base64.DEFAULT));
            installCreatorHook(signature);
            installPackageManagerProxy();
            clearPmParcelCaches();
        } catch (Throwable e) {
            Log.e(TAG, "install failed", e);
        }
    }

    private static void installCreatorHook(Signature signature) throws Exception {
        if (creatorInstalled) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            Log.w(TAG, "skip PackageInfo.CREATOR hook on Android 17+");
            // fixme: work proper but may cause ban?
            return;
        }
        Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;
        a(PackageInfo.class, "CREATOR").set(null, new PackageInfoCreator(originalCreator, LegacyKiller.CURRENT_PACKAGE_NAME, signature));
        creatorInstalled = true;
    }

    private static void installPackageManagerProxy() throws Exception {
        if (pmProxyInstalled) return;
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Field sPackageManagerField = a(activityThreadClass, "sPackageManager");
        Object rawPm = sPackageManagerField.get(null);
        if (rawPm == null) {
            Method getPackageManager = activityThreadClass.getDeclaredMethod("getPackageManager");
            getPackageManager.setAccessible(true);
            rawPm = getPackageManager.invoke(null);
        }
        if (rawPm == null) return;
        if (Proxy.isProxyClass(rawPm.getClass())) return;

        Class<?> iPackageManagerClass = Class.forName("android.content.pm.IPackageManager");
        Object proxy = Proxy.newProxyInstance(
                iPackageManagerClass.getClassLoader(),
                new Class<?>[]{iPackageManagerClass},
                new PackageManagerHandler(rawPm)
        );
        sPackageManagerField.set(null, proxy);
        pmProxyInstalled = true;
        Log.d(TAG, "sPackageManager proxied: " + rawPm.getClass().getName());
        hookCurrentApplicationPackageManager(proxy);
    }

    private static void hookCurrentApplicationPackageManager(Object proxy) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThreadClass.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object app = currentApplication.invoke(null);
            if (app instanceof Context) {
                hookContextPackageManager((Context) app, proxy);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookContextPackageManager(Context context) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field sPackageManagerField = a(activityThreadClass, "sPackageManager");
            Object proxy = sPackageManagerField.get(null);
            if (proxy != null) hookContextPackageManager(context, proxy);
        } catch (Throwable ignored) {
        }
    }

    private static void hookContextPackageManager(Context context, Object proxy) {
        try {
            PackageManager pm = context.getPackageManager();
            Field mPM = a(pm.getClass(), "mPM");
            Object before = mPM.get(pm);
            mPM.set(pm, proxy);
            Log.d(TAG, "context PackageManager hooked: " + pm.getClass().getName() + " mPM=" + (before == null ? "null" : before.getClass().getName()));
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class PackageManagerHandler implements InvocationHandler {
        private final Object delegate;

        PackageManagerHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String originalPackage = firstPackageArg(args);
            if (method.getName().startsWith("getPackageInfo") || "getPackagesForUid".equals(method.getName())) {
                Log.d(TAG, "PM call " + method.getName() + " firstPkg=" + originalPackage + " args=" + describeArgs(args));
            }
            Object[] callArgs = maybeMapOriginPackageArgs(method, args);
            Object result = method.invoke(delegate, callArgs);
            if ("getPackagesForUid".equals(method.getName()) && result instanceof String[] packages) {
                if (contains(packages) && isSignatureCheckStack()) {
                    Log.d(TAG, "fix getPackagesForUid -> origin package");
                    return new String[]{ORIGIN_PACKAGE_NAME};
                }
            }
            if (result instanceof PackageInfo && shouldSpoofPackage(originalPackage)) {
                PackageInfo packageInfo = (PackageInfo) result;
                if (isAppCenterDeviceInfoStack()) {
                    fixAppCenterPackageInfoVersion(packageInfo);
                } else {
                    fixPackageInfoVersion(packageInfo);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                    // another way to rewrite sign on a17
                    PackageSignatureProvider.rewritePackageInfo(packageInfo);
                }
            }
            return result;
        }

    }

    private static boolean isSignatureCheckStack() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : frames) {
            String c = frame.getClassName();
            if ("com.tencent.mobileqq.msf.core.auth.c".equals(c) || c.startsWith("oicq.wlogin_sdk.")) {
                return true;
            }
            if ("com.tencent.mobileqq.msf.service.MsfService".equals(c) || c.startsWith("com.tencent.mobileqq.msf.service.")) {
                return false;
            }
        }
        return false;
    }

    private static boolean isAppCenterDeviceInfoStack() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : frames) {
            if ("com.microsoft.appcenter.utils.DeviceInfoHelper".equals(frame.getClassName()) &&
                    "getPackageInfo".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String[] array) {
        if (array == null) return false;
        for (String item : array) {
            if (LegacyKiller.CURRENT_PACKAGE_NAME.equals(item)) return true;
        }
        return false;
    }

    private static Object[] maybeMapOriginPackageArgs(Method method, Object[] args) {
        String first = firstPackageArg(args);
        if (!ORIGIN_PACKAGE_NAME.equals(first)) return args;
        String returnName = method.getReturnType().getName();
        String methodName = method.getName();
        if ("getPackageInfo".equals(methodName) || "getPackageInfoVersioned".equals(methodName) ||
                "getApplicationInfo".equals(methodName) || "getPackageUid".equals(methodName) ||
                "getPackageGids".equals(methodName) || "getInstallerPackageName".equals(methodName) ||
                returnName.startsWith("android.content.pm.")) {
            Object[] copy = args.clone();
            if (copy.length > 0) {
                if (copy[0] instanceof String) {
                    copy[0] = CURRENT_PACKAGE_NAME;
                } else if (Build.VERSION.SDK_INT >= 28 && copy[0] instanceof VersionedPackage vp) {
                    copy[0] = new VersionedPackage(CURRENT_PACKAGE_NAME, vp.getLongVersionCode());
                }
            }
            return copy;
        }
        return args;
    }

    private static String firstPackageArg(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return null;
        if (args[0] instanceof String) return (String) args[0];
        if (Build.VERSION.SDK_INT >= 26 && args[0] instanceof VersionedPackage) {
            return ((VersionedPackage) args[0]).getPackageName();
        }
        return null;
    }

    private static String describeArgs(Object[] args) {
        if (args == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(',');
            Object a = args[i];
            sb.append(a == null ? "null" : a.getClass().getName()).append('=').append(a);
        }
        return sb.toString();
    }

    private static boolean shouldSpoofPackage(String packageName) {
        return CURRENT_PACKAGE_NAME.equals(packageName) || ORIGIN_PACKAGE_NAME.equals(packageName);
    }


    /** Spoof version fields only (always safe, needed for wlogin). */
    public static void fixPackageInfoVersion(PackageInfo info) {
        if (info == null) return;
        info.versionName = ORIGIN_VERSION_NAME;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.setLongVersionCode(ORIGIN_VERSION_CODE);
        }
        //noinspection deprecation
        info.versionCode = ORIGIN_VERSION_CODE;
        if (Build.VERSION.SDK_INT >= 28) {
            info.setLongVersionCode(ORIGIN_VERSION_CODE);
        }
        if (info.applicationInfo != null && ORIGIN_PACKAGE_NAME.equals(info.packageName)) {
            info.applicationInfo.packageName = ORIGIN_PACKAGE_NAME;
        }
    }

    private static void fixAppCenterPackageInfoVersion(PackageInfo info) {
        if (info == null) return;
        info.versionName = BuildConfig.VERSION_NAME;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.setLongVersionCode(BuildConfig.VERSION_CODE);
        }
        //noinspection deprecation
        info.versionCode = BuildConfig.VERSION_CODE;
    }

    private static void exemptHiddenApi() {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object runtime = getRuntime.invoke(null);
            Method setHiddenApiExemptions = vmRuntime.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            setHiddenApiExemptions.setAccessible(true);
            setHiddenApiExemptions.invoke(runtime, (Object) new String[]{"Landroid/os/Parcel;", "Landroid/content/pm", "Landroid/app"});
        } catch (Throwable ignored) {
            // The original smali uses MethodHandle/Unsafe helpers. Failure here is non-fatal.
        }
    }

    private static void clearPmParcelCaches() {
        try {
            Class<?> pic = Class.forName("android.app.PropertyInvalidatedCache");
            for (String methodName : new String[]{"disableForTestMode", "disableLocal", "clear", "invalidateCache"}) {
                try {
                    Method m;
                    if ("invalidateCache".equals(methodName)) {
                        m = pic.getDeclaredMethod(methodName, String.class);
                        m.setAccessible(true);
                        m.invoke(null, "cache_key.package_info");
                    } else {
                        m = pic.getDeclaredMethod(methodName);
                        m.setAccessible(true);
                        m.invoke(null);
                    }
                    Log.d(TAG, "PropertyInvalidatedCache." + methodName + " ok");
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object cache = a(PackageManager.class, "sPackageInfoCache").get(null);
            for (String methodName : new String[]{"clear", "disableForTestMode", "disableLocal"}) {
                try {
                    @SuppressWarnings("DataFlowIssue")
                    Method m = cache.getClass().getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    m.invoke(cache);
                    Log.d(TAG, "sPackageInfoCache." + methodName + " ok");
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object creators = a(Parcel.class, "mCreators").get(null);
            if (creators instanceof Map) ((Map<?, ?>) creators).clear();
        } catch (Throwable ignored) {
        }
        try {
            Object pairedCreators = a(Parcel.class, "sPairedCreators").get(null);
            if (pairedCreators instanceof Map) ((Map<?, ?>) pairedCreators).clear();
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal", "ClassCanBeRecord"})
    private static final class PackageInfoCreator implements Parcelable.Creator<PackageInfo> {
        private final Parcelable.Creator<PackageInfo> delegate;
        private final String packageName;
        private final Signature signature;

        PackageInfoCreator(Parcelable.Creator<PackageInfo> delegate, String packageName, Signature signature) {
            this.delegate = delegate;
            this.packageName = packageName;
            this.signature = signature;
        }

        @Override
        public PackageInfo createFromParcel(Parcel source) {
            PackageInfo info = delegate.createFromParcel(source);
            if (info != null && shouldSpoofPackage(info.packageName)) {
                fixPackageInfoVersion(info);
            }
            return info;
        }

        @Override
        public PackageInfo[] newArray(int size) {
            return delegate.newArray(size);
        }
    }
}
