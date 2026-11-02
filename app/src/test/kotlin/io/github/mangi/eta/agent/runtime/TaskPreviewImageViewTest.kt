package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.SystemClock
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TaskPreviewImageViewTest {
    @Test fun featurelessBlackAndWhiteFramesArePlaceholdersButRealContentIsUsable() {
        val bitmap = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLACK); assertFalse(PreviewFrameContent.usable(bitmap))
            bitmap.eraseColor(Color.WHITE); assertFalse(PreviewFrameContent.usable(bitmap))
            bitmap.eraseColor(Color.rgb(40, 60, 180)); assertTrue(PreviewFrameContent.usable(bitmap))
            bitmap.eraseColor(Color.WHITE)
            bitmap.setPixels(IntArray(80 * 120) { Color.BLACK }, 0, 80, 10, 100, 80, 120)
            assertTrue(PreviewFrameContent.usable(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun actualImageTransformAndPaddingDetermineTouchCoordinates() {
        val view = TaskPreviewImageView(RuntimeEnvironment.getApplication())
        val frame = Bitmap.createBitmap(108, 240, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        try {
            view.setPadding(10, 20, 10, 20)
            view.setImageBitmap(frame); view.layout(0, 0, 300, 400)
            val center = view.displayPoint(150f, 200f, 1080, 2400)!!
            assertEquals(540f, center.first, .1f); assertEquals(1200f, center.second, .1f)
            assertNull(view.displayPoint(20f, 200f, 1080, 2400))
            view.scaleType = ImageView.ScaleType.MATRIX
            view.imageMatrix = Matrix().apply { setScale(2f, 1.2f); postTranslate(14f, 7f) }
            val transformed = view.displayPoint(132f, 171f, 1080, 2400)!!
            assertEquals(540f, transformed.first, .1f); assertEquals(1200f, transformed.second, .1f)
            assertNull(view.displayPoint(Float.NaN, 171f, 1080, 2400))
            view.setImageDrawable(null)
            assertNull(view.displayPoint(150f, 200f, 1080, 2400))
        } finally { view.setImageDrawable(null); frame.recycle() }
        val landscape = Bitmap.createBitmap(240, 108, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        try {
            view.setPadding(0, 0, 0, 0); view.scaleType = ImageView.ScaleType.FIT_CENTER
            view.setImageBitmap(landscape); view.layout(0, 0, 300, 300)
            val center = view.displayPoint(150f, 150f, 2400, 1080)!!
            assertEquals(1200f, center.first, .1f); assertEquals(540f, center.second, .1f)
            assertNull(view.displayPoint(150f, 0f, 2400, 1080))
        } finally { view.setImageDrawable(null); landscape.recycle() }
    }

    @Test fun feedbackIsBoundedExpiresAndNeverChangesTheCapturedFrame() {
        val view = TaskPreviewImageView(RuntimeEnvironment.getApplication())
        val frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val output = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        try {
            view.setImageBitmap(frame); view.layout(0, 0, 200, 200)
            view.touchStart(20f, 30f)
            for (i in 0..1000) view.touchMove(20f + i % 100, 30f + i % 100)
            assertEquals(64, view.feedbackPointCount)
            view.draw(Canvas(output))
            assertEquals(Color.BLUE, frame.getPixel(50, 50))
            view.touchEnd(); SystemClock.sleep(500); view.draw(Canvas(output))
            assertEquals(0, view.feedbackPointCount)
            view.touchStart(10f, 10f); view.clearTouch()
            assertEquals(0, view.feedbackPointCount)
        } finally { view.setImageDrawable(null); frame.recycle(); output.recycle() }
    }
}
