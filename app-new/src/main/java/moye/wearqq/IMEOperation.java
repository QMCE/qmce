package moye.wearqq;

import java.util.ArrayList;
import java.util.List;
import kotlin.jvm.functions.Function0;

/**
 * Empty stub for QQWatch's optional WearQQ IME hook.
 *
 * The real class wires custom input-method behavior.  QMCE Lite X does not need
 * it during startup/login, but WatchApplicationDelegate reflectively loads it.
 */
public final class IMEOperation {
    public static final IMEOperation INSTANCE = new IMEOperation();

    private static final List<ExtraMsgArg> extra = new ArrayList<>();
    @SuppressWarnings("rawtypes")
    public static List extraMsg = new ArrayList();
    public static String extraText = null;
    private static Function0 hideLongMenu = null;
    public static Function0 openIME = null;

    public final boolean extraHasReply = false;

    private IMEOperation() {
    }

    public final void clearExtra() {
        extra.clear();
        extraMsg.clear();
        extraText = null;
    }

    @SuppressWarnings("rawtypes")
    public final List getExtra() {
        return extra;
    }

    public final Function0 getHideLongMenu() {
        return hideLongMenu;
    }

    public final Function0 getOpenIME() {
        return openIME;
    }

    public final void openIME() {
        if (openIME != null) {
            openIME.invoke();
        }
    }

    public final void openIMEWithExtra(ExtraMsgArg arg) {
        setExtra(arg);
        openIME();
    }

    public final void setExtra(ExtraMsgArg arg) {
        if (arg != null) {
            extra.add(0, arg);
        }
    }

    public final void setHideLongMenu(Function0 value) {
        hideLongMenu = value;
    }

    public final void setOpenIME(Function0 value) {
        openIME = value;
    }
}
