package com.eta.shadow;

import android.app.Application;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Iterator;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class ShadowSchemaTest {
    @Test public void everyToolPropertyHasAValidTypeIncludingOrientationAndFrameId() throws Exception {
        JSONArray tools = new JSONArray(); ShadowSchema.append(tools);
        assertTrue(tools.length() > 10);
        boolean orientation = false, frame = false;
        for (int i = 0; i < tools.length(); i++) {
            JSONObject fn = tools.getJSONObject(i).getJSONObject("function");
            JSONObject properties = fn.getJSONObject("parameters").getJSONObject("properties");
            for (Iterator<String> keys = properties.keys(); keys.hasNext();) {
                String key = keys.next();
                assertFalse(fn.getString("name") + "." + key, properties.getJSONObject(key).optString("type").isEmpty());
            }
            if (fn.getString("name").equals("screen_open")) {
                assertEquals("landscape", properties.getJSONObject("orientation").getJSONArray("enum").getString(1));
                orientation = true;
            }
            if (fn.getString("name").equals("screen_tap")) {
                assertEquals("string", properties.getJSONObject("frame_id").getString("type")); frame = true;
            }
        }
        assertTrue(orientation && frame);
    }
}
