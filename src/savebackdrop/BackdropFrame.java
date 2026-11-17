package savebackdrop;

import java.nio.IntBuffer;

import com.fs.starfarer.api.Global;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The GL side of the backdrop: one texture holding a copy of a frame, and a full-screen draw of it.
 * The capture patches fill it right before a save/load starts, and SaveBackdropPatch draws it in the overlay.
 * Everything here runs on the GL thread.
 */
final class BackdropFrame {

    // A captured frame is only meant for the overlay that follows within moments. If none takes it (a load
    // that failed early, say), it expires so a much later overlay can't show it.
    private static final long FRESH_NANOS = 5_000_000_000L;

    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

    // One texture for the whole session, re-filled each time, so nothing leaks.
    private static int texture = 0;
    // The viewport the texture was copied from, and the size of its storage.
    private static int x, y, w, h;
    // True while the texture holds a frame captured for the next overlay and no overlay has taken it yet.
    private static boolean fresh = false;
    private static long freshAt = 0;
    // Set after an error or a driver refusing the copy; everything here then stays off for the rest of the session.
    private static boolean failed = false;

    private BackdropFrame() {}

    /**
     * Called right before the game starts a save or load, ahead of the re-renders it does first. The back buffer
     * still holds the frame rendered earlier in the same loop iteration (the game swaps only after input and
     * advance), which shows the screen as it was when the player clicked.
     */
    static void captureForOverlay() {
        if (failed) return;
        try {
            fresh = capture();
            freshAt = System.nanoTime();
        } catch (Throwable t) {
            fail("error while copying the frame", t);
        }
    }

    /** Hands out the captured frame once. Returns false if there is none, so the caller can fall back to capture(). */
    static boolean takeFresh() {
        boolean ok = fresh && System.nanoTime() - freshAt < FRESH_NANOS;
        fresh = false;
        return ok;
    }

    /** Copies the current read buffer (the back buffer, never changed by the game) into the texture. */
    static boolean capture() {
        if (failed) return false;
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        int nx = VIEWPORT.get(0), ny = VIEWPORT.get(1), nw = VIEWPORT.get(2), nh = VIEWPORT.get(3);
        if (nw <= 0 || nh <= 0) return false;

        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (texture == 0) texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (nw != w || nh != h) {
                GL11.glGetError(); // clear any stale error, so the check below only sees ours
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, nx, ny, nw, nh, 0);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                    // Draw nothing rather than garbage, and leave no error flag set for the game.
                    fail("the driver rejected the frame copy", null);
                    return false;
                }
                w = nw;
                h = nh;
            } else {
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, nx, ny, nw, nh);
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
        }
        x = nx;
        y = ny;
        return true;
    }

    /** Paints the texture over the whole viewport it was captured from. */
    static void draw() {
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
            GL11.glViewport(x, y, w, h);
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

    private static void fail(String reason, Throwable t) {
        failed = true;
        fresh = false;
        Global.getLogger(BackdropFrame.class).error("Save backdrop capture disabled itself: " + reason, t);
    }
}
