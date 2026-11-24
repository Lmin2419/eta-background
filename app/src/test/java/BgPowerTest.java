import android.app.Application;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** e52：息屏时后台屏的唤醒顺序和边界（模拟环境里没有这块屏，所以每一步都「醒不过来」）。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class BgPowerTest {
    private final List<Integer> actions = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();

    private BgPower power() {
        return new BgPower(RuntimeEnvironment.getApplication(),
                (displayId, downTime, action, x, y) -> actions.add(action), logs::add);
    }

    @Test public void mainScreenIsNeverTreatedAsABackgroundScreen() {
        assertNull(power().ensureAwake(null, 0, true, "test", true));
        assertTrue(actions.isEmpty());
    }

    @Test public void touchWakeIsPressAndCancelSoNothingGetsClicked() throws Exception {
        JSONObject asleep = power().ensureAwake(null, 77, false, "test", false);
        assertNotNull(asleep);
        assertEquals("BG_SCREEN_ASLEEP", asleep.getString("code"));
        assertFalse(asleep.getBoolean("allowMainWake"));
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), actions);
        assertFalse(actions.contains(MotionEvent.ACTION_UP));
        // 不允许时不点亮主屏，提示里告诉用户可以去设置里打开。
        assertTrue(logs.stream().noneMatch(line -> line.contains("wakeMain")));
        assertTrue(asleep.getString("message").contains("设置"));
    }

    @Test public void mainScreenWakeIsOnlyTriedWhenAllowedAndIsLogged() throws Exception {
        BgPower power = power();
        JSONObject asleep = power.ensureAwake(null, 77, true, "test", false);
        assertNotNull(asleep);
        assertTrue(asleep.getBoolean("allowMainWake"));
        assertTrue(String.join("\n", logs), logs.stream().anyMatch(line -> line.contains("asleep (test)")));
        assertTrue(String.join("\n", logs), logs.stream().anyMatch(line -> line.contains("still asleep after wake attempts (allowMainWake=true)")));
        // 点亮主屏后还会在后台屏上再按一次：两组「按下 → 取消」。
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), actions);
        power.releaseMain();
    }

    @Test public void powerApisAreListedOnlyOnce() {
        BgPower power = power();
        power.ensureAwake(null, 77, false, "first", false);
        power.ensureAwake(null, 77, false, "second", false);
        assertEquals(1, logs.stream().filter(line -> line.startsWith("power apis:")).count());
    }

    @Test public void stateNamesAreReadable() {
        assertEquals("ON", BgPower.stateName(android.view.Display.STATE_ON));
        assertEquals("OFF", BgPower.stateName(android.view.Display.STATE_OFF));
        assertEquals("MISSING", BgPower.stateName(-1));
    }
}
