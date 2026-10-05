package ch.smartkraft.pantherlauncher.ui.iconpack

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import ch.smartkraft.common.AppLogger
import ch.smartkraft.common.getLocalizedString
import ch.smartkraft.pantherlauncher.MainViewModel
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.data.Constants
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.helper.IconCacheTarget
import ch.smartkraft.pantherlauncher.helper.IconPackHelper
import ch.smartkraft.pantherlauncher.helper.iconPackActions
import ch.smartkraft.pantherlauncher.helper.iconPackBlacklist
import ch.smartkraft.pantherlauncher.helper.utils.AppReloader
import ch.smartkraft.pantherlauncher.helper.utils.BiometricHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

class ApplyIconPackActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Any app can start this screen, so nothing in the intent is trusted: the pack must be an
        // installed icon pack, and its name is read from the system instead of the intent.
        val packageClass = intent.getStringExtra("packageClass").orEmpty()
        val packLabel = iconPackLabel(packageClass)
        if (packLabel == null) {
            AppLogger.w("ApplyIconPack", "Ignoring request for '$packageClass': not an installed icon pack")
            finish()
            return
        }

        if (prefs.settingsLocked) {
            // Changing the look is a setting; a locked launcher asks who is there first
            BiometricHelper(this).startBiometricSettingsAuth(object : BiometricHelper.CallbackSettings {
                override fun onAuthenticationSucceeded() = askToApply(packageClass, packLabel)
                override fun onAuthenticationFailed() = Unit
                override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) = finish()
            })
        } else {
            askToApply(packageClass, packLabel)
        }
    }

    /** The app's own label when [packageName] is an installed icon pack, otherwise null. */
    private fun iconPackLabel(packageName: String): String? {
        if (packageName.isBlank() || packageName in iconPackBlacklist) return null
        val isIconPack = iconPackActions.any { action ->
            packageManager.queryIntentActivities(Intent(action).setPackage(packageName), 0).isNotEmpty()
        }
        if (!isIconPack) return null
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun askToApply(packageClass: String, packLabel: String) {
        run {
            // Create a vertical LinearLayout programmatically
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }

            // Create the CheckBoxes
            val checkBoxHome = CheckBox(this).apply {
                text = getLocalizedString(R.string.apply_to_home) // e.g., "Apply to Home"
                isChecked = true // default value
            }

            val checkBoxAppList = CheckBox(this).apply {
                text = getLocalizedString(R.string.apply_to_app_list) // e.g., "Apply to App List"
                isChecked = true // default value
            }

            // Add the CheckBoxes to the layout
            layout.addView(checkBoxHome)
            layout.addView(checkBoxAppList)

            MaterialAlertDialogBuilder(this)
                .setTitle(getLocalizedString(R.string.apply_icon_pack))
                .setMessage(getLocalizedString(R.string.apply_icon_pack_are_you_sure, packLabel))
                .setView(layout)
                .setPositiveButton(getLocalizedString(R.string.apply)) { _, _ ->

                    val iconPackType = Constants.IconPacks.Custom
                    val customIconPackType = packageClass

                    if (checkBoxHome.isChecked) {
                        val executor = Executors.newSingleThreadExecutor()
                        executor.execute {
                            IconPackHelper.preloadIcons(this, customIconPackType, IconCacheTarget.HOME)
                        }
                        prefs.iconPackHome = iconPackType
                        viewModel.iconPackHome.value = iconPackType
                        prefs.customIconPackHome = customIconPackType
                        viewModel.customIconPackHome.value = customIconPackType
                    }

                    if (checkBoxAppList.isChecked) {
                        val executor = Executors.newSingleThreadExecutor()
                        executor.execute {
                            IconPackHelper.preloadIcons(this, customIconPackType, IconCacheTarget.APP_LIST)
                        }
                        prefs.iconPackAppList = iconPackType
                        viewModel.iconPackAppList.value = iconPackType
                        prefs.customIconPackAppList = customIconPackType
                        viewModel.customIconPackAppList.value = customIconPackType
                    }

                    AppReloader.restartApp(this)
                }
                .setNegativeButton(getLocalizedString(R.string.cancel)) { _, _ ->
                    finish()
                }
                .setOnCancelListener { finish() }
                .show()
        }
    }
}

