package ch.smartkraft.pantherlauncher.helper.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.smartkraft.common.getLocalizedString
import ch.smartkraft.common.showLongToast
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.helper.isPantherLauncherDefault

class PrivateSpaceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_USER_UNLOCKED) {
            // Check if Panther Launcher is the default launcher
            if (isPantherLauncherDefault(context)) {
                // Notify the user that Panther Launcher is now accessible in the private space
                val message = getLocalizedString(R.string.toast_private_space_unlocked)
                context.showLongToast(message)
            }
        }
    }
}