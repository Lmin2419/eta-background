package com.eta.shadow;

import android.app.Application;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** e52：发给引擎的每条请求都带上「后台屏醒不过来时能否点亮主屏」，由运行时服务按会话给出。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37, application = Application.class)
public class ShadowToolsMainWakeTest {
    @After public void reset() { ShadowTools.mainWakePolicy = id -> false; }

    private List<JSONObject> closeOut(String sessionId) throws Exception {
        List<JSONObject> requests = new ArrayList<>();
        ShadowTools tools = new ShadowTools(sessionId, (port, payload, timeout) -> {
            requests.add(new JSONObject(payload));
            return "{\"ok\":true,\"parked\":true}";
        });
        ReflectionHelpers.setField(tools, "mode", "hidden");
        ReflectionHelpers.setField(tools, "display", 42);
        ReflectionHelpers.setField(tools, "openedPackage", "app.sign");
        assertTrue(tools.closeSession(true));
        return requests;
    }

    /** 走 engine() 的命令请求（这里是 finish）；release_session 不经过 engine()，不需要这个标记。 */
    private JSONObject finish(List<JSONObject> requests) throws Exception {
        for (JSONObject request : requests) if ("finish".equals(request.optString("cmd"))) return request;
        throw new AssertionError("no finish request: " + requests);
    }

    @Test public void requestsCarryThePolicyForTheirOwnSession() throws Exception {
        ShadowTools.mainWakePolicy = id -> id.startsWith("scheduled-run:");
        assertTrue(finish(closeOut("scheduled-run:abc")).getBoolean("wakeMain"));
        assertFalse(finish(closeOut("chat-run:abc")).getBoolean("wakeMain"));
    }

    @Test public void aFailingPolicyNeverLightsTheScreen() throws Exception {
        ShadowTools.mainWakePolicy = id -> { throw new IllegalStateException("service gone"); };
        assertFalse(finish(closeOut("scheduled-run:abc")).getBoolean("wakeMain"));
    }
}
