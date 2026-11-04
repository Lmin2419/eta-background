package com.eta.shadow;

/** Internal display protocol and historical trace names; never published as model tools. */
public final class ShadowSchema {
    public static final int MAX_CONCURRENT_APPS = 2;
    public static final java.util.Set<String> NAMES = java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(java.util.Arrays.asList("screen_open", "screen_state",
                    "screen_tap", "screen_swipe", "screen_press", "screen_drag", "screen_text",
                    "screen_click", "screen_wait", "screen_scroll", "screen_list", "screen_dump",
                    "screen_shot", "screen_diag", "screen_finish", "screen_back")));
    private ShadowSchema() { }
}
