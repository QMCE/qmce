package rj.qmce.lite.data.chat;

import com.tencent.mobileqq.aio.msglist.holder.base.PicSize;
import com.tencent.qqnt.kernel.nativeinterface.MsgElement;
import com.tencent.qqnt.kernel.utils.RecentThreadDispatcher;
import com.tencent.watch.aio_impl.ext.AIOPicDownloader;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/** Java accessors for obfuscated qq-sdk public fields that Kotlin metadata may hide. */
public final class MediaSdkAccess {
    private MediaSdkAccess() {}

    public static String getPicImagePath(MsgElement element, PicSize size) {
        return AIOPicDownloader.a.d(element, size);
    }

    public static void dispatchOnRecentThread(Runnable action) {
        RecentThreadDispatcher.a.a(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                action.run();
                return Unit.INSTANCE;
            }
        });
    }
}
