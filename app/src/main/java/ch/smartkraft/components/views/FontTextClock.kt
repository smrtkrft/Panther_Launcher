package ch.smartkraft.components.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextClock
import ch.smartkraft.pantherlauncher.helper.CustomFontView
import ch.smartkraft.pantherlauncher.helper.FontManager

class FontTextClock @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextClock(context, attrs), CustomFontView {

    init {
        FontManager.register(this)
    }

    override fun applyFont(typeface: Typeface?) {
        this.typeface = typeface
    }
}


