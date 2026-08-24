package rj.qmce.lite.data.emotion;

import com.tencent.qqnt.emotion.utils.MarketFaceStorageUtil;

/**
 * Java bridge for {@link MarketFaceStorageUtil} path helpers.
 * Kotlin cannot call the obfuscated static methods {@code a}/{@code b}/... because
 * the companion instance field is also named {@code a}.
 */
public final class MarketFaceStoragePaths {
    private MarketFaceStoragePaths() {}

    public static String emoticonAIOPreviewPath(String epId, String eId) {
        return MarketFaceStorageUtil.a(epId, eId);
    }

    public static String emoticonAPNGPath(String epId, String eId) {
        return MarketFaceStorageUtil.b(epId, eId);
    }

    public static String emoticonImagePath(String epId, String eId) {
        return MarketFaceStorageUtil.c(epId, eId);
    }

    public static String emoticonSoundPath(String epId, String eId) {
        return MarketFaceStorageUtil.d(epId, eId);
    }

    public static String emoticonPreviewPath(String epId, String eId) {
        return MarketFaceStorageUtil.e(epId, eId);
    }
}
