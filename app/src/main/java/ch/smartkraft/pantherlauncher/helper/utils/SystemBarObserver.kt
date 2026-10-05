package ch.smartkraft.pantherlauncher.helper.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.helper.hideNavigationBar
import ch.smartkraft.pantherlauncher.helper.hideStatusBar
import ch.smartkraft.pantherlauncher.helper.showNavigationBar
import ch.smartkraft.pantherlauncher.helper.showStatusBar

class SystemBarObserver(private val prefs: Prefs) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        val activity = owner as? AppCompatActivity ?: return
        val window = activity.window
        if (prefs.showStatusBar) showStatusBar(window) else hideStatusBar(window)
        if (prefs.showNavigationBar) showNavigationBar(window) else hideNavigationBar(window)
    }
}
