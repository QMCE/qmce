package moye.wearqq.compat.signing;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class PackageSignatureProvider {
    private static final String TAG = "PackageSignatureProvider";
    private static final int API_ANDROID_17 = 37;
    private static final Signature SPOOFED_SIGNATURE = AppSignatureCertificate.createSignature();
    private static volatile boolean installed;

    private PackageSignatureProvider() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        if (Build.VERSION.SDK_INT >= API_ANDROID_17) {
            Log.w(TAG, "skip PackageInfo.CREATOR provider on Android 17+");
            installed = true;
            return;
        }
        Signature signature = SPOOFED_SIGNATURE;
        replacePackageInfoCreator(signature);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiAccess.addHiddenApiExemptions(
                    "Landroid/os/Parcel;",
                    "Landroid/content/pm",
                    "Landroid/app"
            );
        }
        clearPackageInfoCache();
        clearParcelCreatorCache("mCreators");
        clearParcelCreatorCache("sPairedCreators");
        installed = true;
    }

    public static void rewritePackageInfo(PackageInfo packageInfo) {
        if (packageInfo == null) {
            return;
        }
        if (!AppSignatureCertificate.PACKAGE_NAME.equals(packageInfo.packageName)
                && !AppSignatureCertificate.INSTALLED_PACKAGE_NAME.equals(packageInfo.packageName)) {
            return;
        }
        Signature signature = SPOOFED_SIGNATURE;
        Signature[] signatures = packageInfo.signatures;
        if (signatures != null && signatures.length > 0) {
            signatures[0] = signature;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfoCompat.replace(packageInfo, signature);
        }
    }

    @SuppressWarnings("unchecked")
    private static void replacePackageInfoCreator(Signature signature) {
        try {
            Parcelable.Creator<PackageInfo> creator = PackageInfo.CREATOR;
            if (creator instanceof PackageInfoSignatureCreator) {
                return;
            }
            Field creatorField = findField(PackageInfo.class, "CREATOR");
            creatorField.set(null, new PackageInfoSignatureCreator(
                    creator,
                    AppSignatureCertificate.PACKAGE_NAME,
                    AppSignatureCertificate.INSTALLED_PACKAGE_NAME,
                    signature
            ));
        } catch (Exception error) {
            throw new IllegalStateException("install package signature provider failed", error);
        }
    }

    private static void clearPackageInfoCache() {
        try {
            Field cacheField = findField(PackageManager.class, "sPackageInfoCache");
            Object cache = cacheField.get(null);
            if (cache == null) {
                return;
            }
            Method clearMethod = cache.getClass().getMethod("clear");
            clearMethod.invoke(cache);
        } catch (Throwable error) {
            Log.d(TAG, "skip PackageManager cache clear", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearParcelCreatorCache(String fieldName) {
        try {
            Object cache = findField(Parcel.class, fieldName).get(null);
            if (cache instanceof Map) {
                ((Map<Object, Object>) cache).clear();
            }
        } catch (NoSuchFieldException ignored) {
        } catch (Throwable error) {
            Log.d(TAG, "skip Parcel cache clear: " + fieldName, error);
        }
    }

    static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        NoSuchFieldException firstError = null;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException error) {
                if (firstError == null) {
                    firstError = error;
                }
                current = current.getSuperclass();
            }
        }
        throw firstError != null ? firstError : new NoSuchFieldException(name);
    }

    static boolean isProviderInstalledForTests() {
        return installed;
    }
}
