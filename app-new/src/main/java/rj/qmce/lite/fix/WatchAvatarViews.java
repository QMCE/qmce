package rj.qmce.lite.fix;

import android.content.Context;
import android.util.AttributeSet;

import com.tencent.qqnt.avatar.WatchAvatarView;

/**
 * Runtime qq-sdk exposes {@code WatchAvatarView(Context, AttributeSet, int, int)}
 * (Kotlin default-bitmask ctor) but compile Metadata advertises a 3-arg
 * {@code @JvmOverloads} signature. Calling from Kotlin emits a missing
 * {@code DefaultConstructorMarker} ctor; Java can invoke the real 4-arg form.
 */
public final class WatchAvatarViews {
    private WatchAvatarViews() {
    }

    public static WatchAvatarView create(Context context) {
        return new WatchAvatarView(context, null, 0, 4);
    }

    public static WatchAvatarView create(Context context, AttributeSet attrs) {
        return new WatchAvatarView(context, attrs, 0, 4);
    }
}
