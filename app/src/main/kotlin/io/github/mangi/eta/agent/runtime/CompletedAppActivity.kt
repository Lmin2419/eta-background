package io.github.mangi.eta.agent.runtime

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.eta.shadow.ShadowTools
import io.github.mangi.eta.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A user-initiated Activity keeps Android's foreground launch attribution during Root handoff. */
internal class CompletedAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This transparent bridge must never own the App's touch region or keyboard focus.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val runId = intent.getStringExtra(EXTRA_RUN_ID).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (runId.isBlank() || pkg.isBlank()) { finish(); return }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { JSONObject(ShadowTools.presentCompleted(runId, pkg)) }.getOrNull()
            }
            if (result?.optBoolean("ok") != true) {
                if (result?.optString("code") == "NOT_FOUND") {
                    val launcher = packageManager.getLaunchIntentForPackage(pkg)
                    if (launcher != null) runCatching {
                        startActivity(launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle())
                    }.onFailure { showUnavailable() } else showUnavailable()
                } else showUnavailable() // Unknown/busy handoffs must not relaunch an existing game.
            }
            finish()
        }
    }

    private fun showUnavailable() = Toast.makeText(this, R.string.background_handoff_unavailable, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_PACKAGE = "target_package"
    }
}
