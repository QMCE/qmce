package moye.wearqq.compat.signing;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

final class SigningInfoCompat {
    private SigningInfoCompat() {
    }

    static void replace(PackageInfo packageInfo, Signature signature) {
        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo == null) {
            return;
        }
        Signature[] signatures = signingInfo.getApkContentsSigners();
        if (signatures != null && signatures.length > 0) {
            signatures[0] = signature;
        }
    }
}
