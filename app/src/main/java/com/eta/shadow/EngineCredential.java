package com.eta.shadow;

import android.content.Context;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;

final class EngineCredential {
    private EngineCredential() { }

    /** The token never rotates, so one read per process and file is enough. */
    private static final class Cached {
        final String path;
        final String token;
        Cached(String path, String token) { this.path = path; this.token = token; }
    }
    private static volatile Cached cached;

    static String get(Context context) throws IOException {
        if (context == null) throw new IOException("application context unavailable");
        File file = new File(new File(context.getFilesDir(), "eta-engine-stage"), "engine.token");
        // Every engine and observer request authenticates: skip the cross-process lock and read.
        Cached hit = cached;
        if (hit != null && hit.path.equals(file.getPath()) && file.exists()) return hit.token;
        return load(file);
    }

    private static synchronized String load(File file) throws IOException {
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("cannot create private engine directory");
        // Shared by the runtime and accessibility processes; never rotate on ordinary startup.
        try (RandomAccessFile lock = new RandomAccessFile(new File(directory, ".credential.lock"), "rw");
             FileLock held = lock.getChannel().lock()) {
            String token;
            if (file.exists()) {
                token = EngineWire.readToken(file);
            } else {
                // A deleted file is restored with the token the deployed engine already holds.
                Cached hit = cached;
                token = hit != null && hit.path.equals(file.getPath()) ? hit.token : newToken();
                File temporary = new File(directory, ".engine.token.new");
                try (FileOutputStream out = new FileOutputStream(temporary)) {
                    out.write(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            cached = new Cached(file.getPath(), token);
            return token;
        }
    }

    private static String newToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        StringBuilder token = new StringBuilder();
        for (byte value : random) token.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return token.toString();
    }
}
