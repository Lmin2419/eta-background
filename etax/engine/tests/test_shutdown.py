"""The resident's exit deadline must not wait for a blocked JVM shutdown hook."""
import os
import pathlib
import subprocess
import tempfile
import time
import unittest

ENGINE = pathlib.Path(__file__).resolve().parents[1]

class ShutdownTest(unittest.TestCase):
    def test_deadline_exits_only_the_child_even_with_blocked_shutdown_hook(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            main = root / 'ShutdownProbe.java'
            main.write_text('''public class ShutdownProbe {
                public static void main(String[] args) throws Exception {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try { Thread.sleep(60000); } catch (Exception ignored) { }
                    }));
                    EngineShutdown guard = new EngineShutdown();
                    System.out.println("READY"); System.out.flush();
                    guard.request(75);
                    Thread.sleep(60000);
                }
            }''')
            subprocess.run([os.environ.get('JAVAC', 'javac'), '-d', temp,
                            str(ENGINE / 'src/EngineShutdown.java'), str(main)],
                           capture_output=True, check=True)
            started = time.monotonic()
            result = subprocess.run([os.environ.get('JAVA', 'java'), '-cp', temp, 'ShutdownProbe'],
                                    capture_output=True, text=True, timeout=6)
            self.assertEqual('READY', result.stdout.strip())
            self.assertEqual(75, result.returncode, result.stderr)
            self.assertLess(time.monotonic() - started, 6)

if __name__ == '__main__': unittest.main()
