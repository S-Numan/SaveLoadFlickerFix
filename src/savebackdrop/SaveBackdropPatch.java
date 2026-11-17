package savebackdrop;

import com.fs.starfarer.api.Global;

import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.match.StaticType;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import patchlib.api.store.PatchData;

/**
 * Prevent flickering between two frames or a black screen during the save/load progress overlay.
 *
 * The overlay's draw method paints only its own widgets and then swaps buffers, so the back buffer holds stale contents behind them. This can cause issues as mentioned above.
 * On the first draw, this patch takes the frame that the capture patches copied right before the save or load
 * started, which is the frame the player was looking at when they clicked. If there is none, it copies the back
 * buffer as it is right now, which still holds the frame rendered during the same loop iteration unless the game
 * re-rendered since.
 * Every draw then paints that texture over the whole screen before the overlay's widgets.
 *
 * Seen on 0.98.5a-RC4: the overlay is com.fs.starfarer.campaign.save.B, with o00000(String, float) setting the
 * text and progress and o00000(float) drawing. These names change between builds; the package and the shape
 * (the only (float) void instance method of the class) are what to look for after a game update.
 */
@Patch(target = @ClassMatch(
        packageName = "com.fs.starfarer.campaign.save",
        methodMatches = {
                @MethodMatch(parameterTypes = {String.class, float.class}, returnType = void.class,
                        methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        }))
public class SaveBackdropPatch {

    private static final String KEY_CAPTURED = "savebackdrop:captured";

    private static boolean hasFrame = false;
    private static boolean broken = false;

    @Before(target = @MethodMatch(parameterTypes = {float.class}, returnType = void.class,
            methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    public static void beforeOverlayDraw(BeforeContext context) {
        if (broken) return;
        // This is purely cosmetic, it shouldn't ever throw an exception on save/load game.
        try {
            PatchData data = context.getData();
            if (!data.has(KEY_CAPTURED)) {
                data.put(KEY_CAPTURED, Boolean.TRUE);
                hasFrame = BackdropFrame.takeFresh() || BackdropFrame.capture();
            }
            if (hasFrame) BackdropFrame.draw();
        } catch (Throwable t) {
            broken = true;
            Global.getLogger(SaveBackdropPatch.class).error("Save backdrop disabled itself after an error", t);
        }
    }
}
