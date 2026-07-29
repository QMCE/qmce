package rj.qmce.lite.data.chat;

import com.tencent.qqnt.watch.ptt.api.ITranslateTextService;

/**
 * Bridges 9.0.7 JVM short name {@code b(...)} to readable {@code onTranslate}.
 * Kotlin metadata still exposes onTranslate, while the jar method is {@code b}.
 */
public abstract class TranslateTextCallbackBridge
        extends ITranslateTextService.AbsTranslateTextCallback {
    @Override
    public final void b(boolean isSuccess, boolean isLast, String text, String curKey) {
        onTranslate(isSuccess, isLast, text, curKey);
    }

    protected abstract void onTranslate(
            boolean isSuccess,
            boolean isLast,
            String text,
            String curKey
    );
}
