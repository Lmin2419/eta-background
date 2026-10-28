import java.nio.ByteBuffer;

/** RGBA frame view for Bitmap.copyPixelsFromBuffer; no full-frame heap copy in the usual case. */
final class CapturePixels {
    static ByteBuffer prepare(ByteBuffer source, int width, int height, int rowStride, int pixelStride) {
        long rowBytes = (long) width * 4;
        long size = (long) rowStride * height;
        long required = (long) rowStride * (height - 1) + rowBytes;
        if (width <= 0 || height <= 0 || pixelStride != 4 || rowStride % 4 != 0
                || rowStride < rowBytes || size > Integer.MAX_VALUE || source.remaining() < required) {
            throw new IllegalArgumentException("Invalid RGBA capture dimensions or buffer");
        }
        ByteBuffer pixels = source.duplicate();
        if (pixels.remaining() >= size) {
            pixels.limit(pixels.position() + (int) size);
            return pixels;
        }
        // Some ROMs omit only the final row's padding; initialize that padding without reading past the plane.
        ByteBuffer padded = ByteBuffer.allocate((int) size);
        padded.put(pixels);
        padded.rewind();
        return padded;
    }
}
