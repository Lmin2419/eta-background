import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class CapturePixelsTest {
    @Test public void completePlaneSharesStorageAndDoesNotConsumeOriginalBuffer() {
        ByteBuffer source = ByteBuffer.allocateDirect(40);
        source.position(4);
        ByteBuffer view = CapturePixels.prepare(source, 3, 2, 16, 4);
        assertTrue(view.isDirect());
        assertEquals(32, view.remaining());
        source.put(4, (byte) 42);
        assertEquals(42, view.get());
        assertEquals(4, source.position());
    }

    @Test public void missingFinalPaddingIsZeroFilledWithoutLosingPixels() {
        ByteBuffer source = ByteBuffer.allocate(28); // 16-byte first row, 12-byte final row.
        source.put(27, (byte) 99);
        ByteBuffer pixels = CapturePixels.prepare(source, 3, 2, 16, 4);
        assertEquals(32, pixels.remaining());
        assertEquals(99, pixels.get(27));
        for (int i = 28; i < 32; i++) assertEquals(0, pixels.get(i));
        assertEquals(0, source.position());
    }

    @Test public void malformedPlanesFailBeforeAllocatingOrReadingPastTheBuffer() {
        assertThrows(IllegalArgumentException.class, () -> CapturePixels.prepare(ByteBuffer.allocate(27), 3, 2, 16, 4));
        assertThrows(IllegalArgumentException.class, () -> CapturePixels.prepare(ByteBuffer.allocate(32), 5, 2, 16, 4));
        assertThrows(IllegalArgumentException.class, () -> CapturePixels.prepare(ByteBuffer.allocate(32), 3, 2, 16, 8));
        assertThrows(IllegalArgumentException.class, () -> CapturePixels.prepare(ByteBuffer.allocate(32), 3, Integer.MAX_VALUE, 16, 4));
    }
}
