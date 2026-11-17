package savebackdrop;

import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;

/**
 * Captures the frame right before a load or new game starts from the title screen.
 *
 * Seen on 0.98.5a-RC4: TitleScreenState re-renders and swaps inline, in menuItemSelected(...) and
 * dialogDismissed(...), right before it starts a load or new game. These hooks run at the entry of those two
 * methods, while the back buffer still holds the frame rendered before the click was handled. They also fire for
 * clicks that don't lead to a save or load, which is harmless: one copy per click, and the result expires.
 */
@Patch(target = @ClassMatch(typeName = "com.fs.starfarer.title.TitleScreenState"))
public class TitleFrameCapturePatch {

    @Before(target = @MethodMatch(methodName = "menuItemSelected", parameterCount = 1))
    public static void beforeMenuItemSelected(BeforeContext context) {
        BackdropFrame.captureForOverlay();
    }

    @Before(target = @MethodMatch(methodName = "dialogDismissed", parameterCount = 2))
    public static void beforeDialogDismissed(BeforeContext context) {
        BackdropFrame.captureForOverlay();
    }
}
