package savebackdrop;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

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
 * On the first draw, this patch copies the last frame shown to the player into a texture.
 * Every draw then paints that texture over the whole screen before the overlay's widgets.
 */
@Patch(target = @ClassMatch(
        packageName = "com.fs.starfarer.campaign.save",
        methodMatches = {
                @MethodMatch(parameterTypes = {String.class, float.class}, returnType = void.class,
                        methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        }))
public class SaveBackdropPatch {

    private static final String KEY_CAPTURED = "savebackdrop:captured";

    // One texture for the whole session, re-filled each time an overlay appears, so nothing leaks.
    private static int texture = 0;
    private static int texX, texY, texW, texH;
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
                hasFrame = captureFrame();
            }
            if (hasFrame) drawFrame();
        } catch (Throwable t) {
            broken = true;
            System.out.println("[SaveLoadBackdrop] disabled itself after an error: " + t);
        }
    }

    private static boolean captureFrame() {
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int x = viewport.get(0), y = viewport.get(1), w = viewport.get(2), h = viewport.get(3);
        if (w <= 0 || h <= 0) return false;

        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            if (texture == 0) texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glReadBuffer(GL11.GL_FRONT); // the frame the player is looking at right now
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, x, y, w, h, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        } finally {
            GL11.glReadBuffer(prevReadBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
        }
        texX = x; texY = y; texW = w; texH = h;
        return true;
    }

    private static void drawFrame() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_VIEWPORT_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TRANSFORM_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glViewport(texX, texY, texW, texH);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0f, 0f);
            GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(1f, 0f);
            GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(1f, 1f);
            GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(0f, 1f);
            GL11.glEnd();
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
