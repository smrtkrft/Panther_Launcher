package ch.smartkraft.pantherlauncher.ui.onboarding

import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.appcompat.app.AppCompatActivity
import ch.smartkraft.common.CrashHandler
import ch.smartkraft.pantherlauncher.R

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize ch.smartkraft.common.CrashHandler to catch uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext))

        setContentView(R.layout.activity_onboarding)

        // Load the OnboardingFragment dynamically
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, OnboardingFragment())  // FragmentContainer is a FrameLayout or other container in the activity layout
            .commit()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
    }
}


