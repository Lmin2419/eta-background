package io.github.mangi.eta.agent.runtime

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskPhoneIconTest {
    @Test fun systemMonochromeTintKeepsThePhoneScreenTransparentAndDetailsVisible() {
        val app = RuntimeEnvironment.getApplication()
        val drawable = AgentExecutionService.taskPhoneIcon(app).loadDrawable(app)!!
        val bitmap = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, 96, 144)
            drawable.colorFilter = BlendModeColorFilter(Color.WHITE, BlendMode.SRC_IN)
            drawable.draw(Canvas(bitmap))
            assertEquals("Screen gap must survive system tint", 0, Color.alpha(bitmap.getPixel(48, 60)))
            assertEquals(Color.WHITE, bitmap.getPixel(9, 72)) // phone border
            assertEquals(Color.WHITE, bitmap.getPixel(36, 48)) // app cell
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            var filled = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 127) filled++
            }
            assertTrue("Icon must not become a solid white rectangle", filled < bitmap.width * bitmap.height / 2)
        } finally { bitmap.recycle() }
    }
}
