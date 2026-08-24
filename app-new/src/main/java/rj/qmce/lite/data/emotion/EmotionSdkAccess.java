package rj.qmce.lite.data.emotion;

import com.tencent.mobileqq.ptt.IQQRecorder;
import com.tencent.qqnt.aio.anisticker.download.BaseLottieResDownloader;
import com.tencent.qqnt.aio.anisticker.download.LoadListener;
import com.tencent.qqnt.aio.anisticker.view.AniStickerHelper;
import com.tencent.qqnt.aio.anisticker.view.AniStickerLottie;
import com.tencent.qqnt.watch.ptt.PttRecordCallback;

/** Java accessors for obfuscated public fields that Kotlin metadata marks private. */
public final class EmotionSdkAccess {
    private EmotionSdkAccess() {}

    public static void setPttRecordPanel(
            PttRecordCallback callback,
            IQQRecorder.OnQQRecorderListener panel
    ) {
        callback.c = panel;
    }

    public static boolean isLottieSoLoaded() {
        return AniStickerLottie.b;
    }

    public static void loadLottieWithPath(
            BaseLottieResDownloader<?> downloader,
            String path,
            AniStickerHelper.Builder builder,
            LoadListener listener
    ) {
        downloader.c(path, builder, listener);
    }
}
