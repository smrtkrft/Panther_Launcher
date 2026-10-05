package ch.smartkraft.pantherlauncher.helper.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import ch.smartkraft.common.AppLogger
import ch.smartkraft.pantherlauncher.data.Prefs

/**
 * Context whose startActivity does not open an app the user has locked. The launch is handed to
 * [onLocked] instead, together with a way to run it after authentication, so quick actions that
 * start an app indirectly (dialer, camera, browser, ...) cannot bypass the app lock.
 */
class LockGuardContext(
    base: Context,
    private val onLocked: (packageName: String, launch: () -> Unit) -> Unit
) : ContextWrapper(base) {

    override fun startActivity(intent: Intent) = startActivity(intent, null)

    override fun startActivity(intent: Intent, options: Bundle?) {
        val lockedPackage = lockedTargetOf(intent)
        if (lockedPackage == null) {
            super.startActivity(intent, options)
            return
        }
        AppLogger.d("LockGuard", "Launch of locked app $lockedPackage needs authentication")
        onLocked(lockedPackage) {
            try {
                super.startActivity(intent, options)
            } catch (e: Exception) {
                AppLogger.e("LockGuard", "Could not start $lockedPackage", e)
            }
        }
    }

    /** The package [intent] would open, when that app is locked. */
    private fun lockedTargetOf(intent: Intent): String? {
        val target = intent.component?.packageName
            ?: intent.`package`
            ?: packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return target?.takeIf { it in Prefs(this).lockedApps }
    }
}
