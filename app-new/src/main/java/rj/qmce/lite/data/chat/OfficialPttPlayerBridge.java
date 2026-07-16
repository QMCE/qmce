package rj.qmce.lite.data.chat;

import com.tencent.watch.aio_impl.ui.cell.ptt.AIOPttAudioPlayerManager;
import com.tencent.watch.aio_impl.ui.cell.ptt.AIOPttAudioPlayerStateListener;

public final class OfficialPttPlayerBridge {
    private OfficialPttPlayerBridge() {
    }

    public static void register(long messageId, String path, AIOPttAudioPlayerStateListener listener) {
        AIOPttAudioPlayerManager.a.k().put(
                messageId,
                new AIOPttAudioPlayerManager.AudioData(messageId, path, listener)
        );
    }

    public static void toggle(long messageId, String path, int offsetMillis) {
        AIOPttAudioPlayerManager.a.p(path, offsetMillis, 1f, messageId);
    }

    public static void stop() {
        AIOPttAudioPlayerManager.a.m(false);
    }

    public static void unregister(long messageId) {
        AIOPttAudioPlayerManager.a.k().remove(messageId);
    }
}
