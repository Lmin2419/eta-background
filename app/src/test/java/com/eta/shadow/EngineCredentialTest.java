package com.eta.shadow;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class EngineCredentialTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Before public void clearProcessCache() {
        ReflectionHelpers.setStaticField(EngineCredential.class, "cached", null);
    }

    private Context contextAt(File filesDir) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public File getFilesDir() { return filesDir; }
        };
    }

    private static File tokenFile(File filesDir) {
        return new File(new File(filesDir, "eta-engine-stage"), "engine.token");
    }

    @Test public void repeatedReadsReuseTheTokenWithoutReadingTheFileAgain() throws Exception {
        Context context = contextAt(folder.newFolder("app"));
        String first = EngineCredential.get(context);
        assertTrue(first.matches("[a-f0-9]{64}"));
        // A different valid token on disk proves the hot path no longer reads the file.
        Files.write(tokenFile(context.getFilesDir()).toPath(),
                "b".repeat(64).getBytes(StandardCharsets.UTF_8));
        assertEquals(first, EngineCredential.get(context));
    }

    @Test public void deletedFileIsRestoredWithTheSameToken() throws Exception {
        Context context = contextAt(folder.newFolder("app"));
        String first = EngineCredential.get(context);
        File token = tokenFile(context.getFilesDir());
        assertTrue(token.delete());
        // Deployment copies this file to the engine, so it must reappear unchanged.
        assertEquals(first, EngineCredential.get(context));
        assertEquals(first, new String(Files.readAllBytes(token.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void anotherFilesDirectoryDoesNotReuseTheCachedToken() throws Exception {
        Context first = contextAt(folder.newFolder("first"));
        Context second = contextAt(folder.newFolder("second"));
        Files.createDirectories(tokenFile(second.getFilesDir()).getParentFile().toPath());
        Files.write(tokenFile(second.getFilesDir()).toPath(), "c".repeat(64).getBytes(StandardCharsets.UTF_8));
        String own = EngineCredential.get(first);
        assertEquals("c".repeat(64), EngineCredential.get(second));
        assertNotEquals(own, EngineCredential.get(second));
    }

    @Test public void existingTokenOnDiskIsReadOnce() throws Exception {
        Context context = contextAt(folder.newFolder("app"));
        Files.createDirectories(tokenFile(context.getFilesDir()).getParentFile().toPath());
        Files.write(tokenFile(context.getFilesDir()).toPath(), "d".repeat(64).getBytes(StandardCharsets.UTF_8));
        assertEquals("d".repeat(64), EngineCredential.get(context));
    }
}
