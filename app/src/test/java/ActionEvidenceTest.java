import com.eta.shadow.ActionEvidence;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActionEvidenceTest {
    private JSONObject scene(String hash, boolean observable) throws Exception {
        return new JSONObject().put("fingerprint", hash).put("observable", observable).put("targetKey", "list:7");
    }
    @Test public void acceptedInputIsNotAClaimOfChangedContent() throws Exception {
        JSONObject before = scene("one", true);
        JSONObject result = ActionEvidence.attach(new JSONObject().put("ok", true), before, before);
        assertTrue(result.getBoolean("accepted")); assertFalse(result.getBoolean("verified"));
        assertEquals("NO_OBSERVED_CHANGE", result.getString("outcome"));
        assertEquals("ACTION_OUTCOME_UNKNOWN", result.getString("code"));
        assertFalse(result.getJSONObject("after").getBoolean("screen_changed"));
    }
    @Test public void visualOnlyAndReplacedTargetsRemainUnknown() throws Exception {
        JSONObject before = scene("one", false), after = scene("two", false);
        assertEquals("UNKNOWN", ActionEvidence.attach(new JSONObject().put("ok", true), before, after).getString("outcome"));
        before = scene("one", true); after = scene("two", true).put("targetKey", "other-window");
        assertFalse(ActionEvidence.changed(before, after));
        assertFalse(ActionEvidence.comparable(scene("one", true).put("scanLimited", true), scene("two", true)));
    }
    @Test public void noProgressBlocksOnlySameEvidenceAndResetsOnRealChange() throws Exception {
        ActionEvidence.Tracker tracker = new ActionEvidence.Tracker();
        JSONObject before = scene("one", true);
        for (int i = 0; i < 3; i++) {
            assertNull(tracker.blocked("up", before, i)); tracker.record("up", before, before, true, i);
        }
        JSONObject blocked = tracker.blocked("up", before, 4);
        assertEquals("NO_PROGRESS", blocked.getString("code")); assertFalse(blocked.getBoolean("operationMayHaveRun"));
        assertNull(tracker.blocked("down", before, 4));
        assertNull(tracker.blocked("up", scene("new-history", true), 5));
        assertNull(tracker.blocked("up", before, 6));
        for (int i = 0; i < 3; i++) tracker.record("up", before, before, true, i + 7);
        tracker.clear(); assertNull(tracker.blocked("up", before, 11));
    }
    @Test public void blindCanvasIsNeverBlockedBecauseItsTreeIsStatic() throws Exception {
        ActionEvidence.Tracker tracker = new ActionEvidence.Tracker();
        JSONObject canvas = scene("static", false);
        for (int i = 0; i < 10; i++) {
            tracker.record("swipe", canvas, canvas, true, i);
            assertNull(tracker.blocked("swipe", canvas, i));
        }
    }
    @Test public void completedNodeWriteMustNotBecomeVerifiedAfterRejection() throws Exception {
        JSONObject result = ActionEvidence.attach(new JSONObject().put("ok", false).put("code", "ACTION_REJECTED"), scene("one", true), scene("two", true));
        assertEquals("REJECTED", result.getString("outcome")); assertFalse(result.getBoolean("verified"));
        assertEquals("ACTION_REJECTED", result.getString("code"));
    }
}
