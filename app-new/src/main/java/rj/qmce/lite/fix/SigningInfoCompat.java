package rj.qmce.lite.fix;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class SigningInfoCompat {
    private static final String TAG = "PackageSignatureProvider";

    private SigningInfoCompat() {
    }

    static void replace(PackageInfo packageInfo, Signature signature) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        ensureLegacySignatures(packageInfo, signature);
        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo == null) {
            signingInfo = tryCreateSigningInfo(signature);
            if (signingInfo == null) {
                return;
            }
            packageInfo.signingInfo = signingInfo;
        }
        Signature[] signatures = signingInfo.getApkContentsSigners();
        if (signatures != null && signatures.length > 0) {
            for (int i = 0; i < signatures.length; i++) {
                signatures[i] = signature;
            }
        } else {
            // Empty signer list: rebuild SigningInfo when possible.
            SigningInfo rebuilt = tryCreateSigningInfo(signature);
            if (rebuilt != null) {
                packageInfo.signingInfo = rebuilt;
                signingInfo = rebuilt;
            }
        }
        // Also rewrite certificate history when present (API 28+).
        try {
            Signature[] history = signingInfo.getSigningCertificateHistory();
            if (history != null) {
                for (int i = 0; i < history.length; i++) {
                    history[i] = signature;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ensureLegacySignatures(PackageInfo packageInfo, Signature signature) {
        Signature[] signatures = packageInfo.signatures;
        if (signatures == null || signatures.length == 0) {
            packageInfo.signatures = new Signature[]{signature};
        } else {
            signatures[0] = signature;
        }
    }

    /**
     * Best-effort SigningInfo construction for API 28+ when PM returns null/empty signingInfo.
     * Uses hidden SigningDetails APIs; failure is non-fatal (legacy signatures still set).
     */
    private static SigningInfo tryCreateSigningInfo(Signature signature) {
        try {
            Class<?> signingDetailsClass = Class.forName("android.content.pm.SigningDetails");
            Object signatureSchemeFlags = null;
            try {
                // SignatureScheme.JAR = 1 on many API levels; prefer enum if present.
                Class<?> schemeClass = Class.forName("android.content.pm.SigningDetails$SignatureScheme");
                Object[] constants = schemeClass.getEnumConstants();
                if (constants != null && constants.length > 0) {
                    signatureSchemeFlags = constants[0];
                }
            } catch (Throwable ignored) {
            }

            Object signingDetails = null;
            for (Constructor<?> ctor : signingDetailsClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                Class<?>[] params = ctor.getParameterTypes();
                try {
                    if (params.length == 2
                            && params[0].isArray()
                            && params[0].getComponentType() == Signature.class
                            && params[1] == int.class) {
                        signingDetails = ctor.newInstance(new Signature[]{signature}, 1);
                        break;
                    }
                    if (params.length == 2
                            && params[0].isArray()
                            && params[0].getComponentType() == Signature.class
                            && signatureSchemeFlags != null
                            && params[1].isAssignableFrom(signatureSchemeFlags.getClass())) {
                        signingDetails = ctor.newInstance(new Signature[]{signature}, signatureSchemeFlags);
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (signingDetails == null) {
                Method make = null;
                for (Method method : signingDetailsClass.getDeclaredMethods()) {
                    if ("make".equals(method.getName()) || "of".equals(method.getName())) {
                        make = method;
                        break;
                    }
                }
                if (make != null) {
                    make.setAccessible(true);
                    Class<?>[] params = make.getParameterTypes();
                    if (params.length >= 1 && params[0].isArray()) {
                        signingDetails = make.invoke(null, (Object) new Signature[]{signature});
                    }
                }
            }
            if (signingDetails == null) {
                return null;
            }

            Constructor<SigningInfo> ctor = SigningInfo.class.getDeclaredConstructor(signingDetailsClass);
            ctor.setAccessible(true);
            return ctor.newInstance(signingDetails);
        } catch (Throwable error) {
            Log.d(TAG, "skip SigningInfo rebuild", error);
            return null;
        }
    }
}
