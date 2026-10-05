package ch.smartkraft.pantherlauncher

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import ch.smartkraft.common.CrashHandler
import ch.smartkraft.pantherlauncher.data.Constants
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.helper.FontManager
import ch.smartkraft.pantherlauncher.helper.IconCacheTarget
import ch.smartkraft.pantherlauncher.helper.IconPackHelper
import java.util.concurrent.Executors

class PantherLauncher : Application() {
    companion object {
        private var appContext: Context? = null

        fun getContext(): Context {
            return appContext ?: throw IllegalStateException(
                "PantherLauncher not initialized. Ensure PantherLauncher.initialize(context) is called early."
            )
        }

        fun initialize(context: Context) {
            if (appContext != null) return // already initialized
            appContext = context

            val prefs = Prefs(context)

            // 🌓 Set theme mode once at app startup
            val themeMode = when (prefs.appTheme) {
                Constants.Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
                Constants.Theme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                Constants.Theme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(themeMode)

            // Optional: preload icons, init crash handler, etc. if needed
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                if (prefs.iconPackHome == Constants.IconPacks.Custom) {
                    IconPackHelper.preloadIcons(appContext!!, prefs.customIconPackHome, IconCacheTarget.HOME)
                }

                if (prefs.iconPackAppList == Constants.IconPacks.Custom) {
                    IconPackHelper.preloadIcons(appContext!!, prefs.customIconPackAppList, IconCacheTarget.APP_LIST)
                }
            }

            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext!!))

            CrashHandler.logUserAction("App Launched")

            FontManager.reloadFont(context)
        }
    }
}
