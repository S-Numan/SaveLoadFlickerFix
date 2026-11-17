package savebackdrop;

import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;

/**
 * Captures the frame right before a campaign save or load starts.
 *
 * Seen on 0.98.5a-RC4: every save and load in CampaignState (autosave, quicksave, quickload, save, save as,
 * save and exit, load) calls its private doubleRender() first, which renders and swaps twice. By then the UI has
 * already reacted to the click (a dialog is gone, say), so the frames it produces are later than the one the
 * player saw. This runs at the entry of doubleRender(), while the back buffer still holds the frame rendered
 * before the click was handled.
 */
@Patch(target = @ClassMatch(typeName = "com.fs.starfarer.campaign.CampaignState"))
public class CampaignFrameCapturePatch {

    @Before(target = @MethodMatch(methodName = "doubleRender", parameterCount = 0))
    public static void beforeDoubleRender(BeforeContext context) {
        BackdropFrame.captureForOverlay();
    }
}
