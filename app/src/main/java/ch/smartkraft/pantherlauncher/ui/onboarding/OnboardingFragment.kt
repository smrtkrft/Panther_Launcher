package ch.smartkraft.pantherlauncher.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.data.Constants
import ch.smartkraft.pantherlauncher.data.Prefs
import ch.smartkraft.pantherlauncher.databinding.FragmentOnboardingBinding
import ch.smartkraft.pantherlauncher.helper.isSystemInDarkMode
import ch.smartkraft.pantherlauncher.helper.setThemeMode
import ch.smartkraft.pantherlauncher.ui.adapter.OnboardingAdapter

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {
    private lateinit var prefs: Prefs

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager: ViewPager2 = view.findViewById(R.id.viewPager)
        val adapter = OnboardingAdapter(this)  // Pass the Fragment (not Activity) to the adapter
        viewPager.adapter = adapter

        val isDark = when (prefs.appTheme) {
            Constants.Theme.Light -> false
            Constants.Theme.Dark -> true
            Constants.Theme.System -> isSystemInDarkMode(requireContext())
        }

        setThemeMode(requireContext(), isDark, binding.mainScreen)
    }
}
