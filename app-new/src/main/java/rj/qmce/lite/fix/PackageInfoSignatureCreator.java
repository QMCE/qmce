package rj.qmce.lite.fix;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

final class PackageInfoSignatureCreator implements Parcelable.Creator<PackageInfo> {
    private final Parcelable.Creator<PackageInfo> delegate;
    private final String packageName;
    private final String installedPackageName;
    private final Signature signature;

    PackageInfoSignatureCreator(
            Parcelable.Creator<PackageInfo> delegate,
            String packageName,
            String installedPackageName,
            Signature signature
    ) {
        this.delegate = delegate;
        this.packageName = packageName;
        this.installedPackageName = installedPackageName;
        this.signature = signature;
    }

    @Override
    public PackageInfo createFromParcel(Parcel source) {
        PackageInfo packageInfo = delegate.createFromParcel(source);
        if (packageName.equals(packageInfo.packageName)
                || installedPackageName.equals(packageInfo.packageName)) {
            Signature[] signatures = packageInfo.signatures;
            if (signatures != null && signatures.length > 0) {
                signatures[0] = signature;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfoCompat.replace(packageInfo, signature);
            }
        }
        return packageInfo;
    }

    @Override
    public PackageInfo[] newArray(int size) {
        return delegate.newArray(size);
    }
}
