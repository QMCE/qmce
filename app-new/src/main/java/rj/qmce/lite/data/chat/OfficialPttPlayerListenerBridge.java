package rj.qmce.lite.data.chat;

import com.tencent.watch.aio_impl.ui.cell.ptt.AIOPttAudioPlayerStateListener;

/**
 * Bridges 9.0.7 JVM short names (a…f) to readable callbacks so Kotlin/R8 cannot drop them.
 */
public abstract class OfficialPttPlayerListenerBridge implements AIOPttAudioPlayerStateListener {
    @Override
    public final void a(long msgId, boolean isNearToEar) {
        onNearToEar(msgId, isNearToEar);
    }

    @Override
    public final void b(long msgId, String path) {
        onStart(msgId, path);
    }

    @Override
    public final void c(long msgId, float speed) {
        onComplete(msgId, speed);
    }

    @Override
    public final void d(long msgId, String path, int currentPosition, int duration) {
        onProgressChanged(msgId, path, currentPosition, duration);
    }

    @Override
    public final void e(long msgId, String path, int currentPosition) {
        onPause(msgId, path, currentPosition);
    }

    @Override
    public final void f(long msgId, String path) {
        onStop(msgId, path);
    }

    protected void onNearToEar(long msgId, boolean isNearToEar) {
    }

    protected abstract void onStart(long msgId, String path);

    protected void onComplete(long msgId, float speed) {
    }

    protected abstract void onProgressChanged(long msgId, String path, int currentPosition, int duration);

    protected abstract void onPause(long msgId, String path, int currentPosition);

    protected abstract void onStop(long msgId, String path);
}
