package ch.smartkraft.pantherlauncher.ui.compose

import android.content.Intent
import android.text.Html
import android.text.style.URLSpan
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.em
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import ch.smartkraft.pantherlauncher.R
import ch.smartkraft.pantherlauncher.helper.FontManager
import ch.smartkraft.pantherlauncher.services.HapticFeedbackService
import ch.smartkraft.pantherlauncher.style.SettingsTheme
import ch.smartkraft.components.views.FontAppCompatTextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SettingsComposable {

    @Composable
    fun PageHeader(
        @DrawableRes iconRes: Int,
        title: String,
        onClick: () -> Unit = {},
        iconSize: Dp = 24.dp,
        fontColor: Color = SettingsTheme.typography.title.color,
        titleFontSize: TextUnit = 18.sp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon on the left
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                colorFilter = ColorFilter.tint(SettingsTheme.color.image),
                modifier = Modifier
                    .size(iconSize)
                    .clickable(onClick = onClick)
            )

            Spacer(modifier = Modifier.width(12.dp)) // small spacing between icon and title

            // Title text
            FontText(
                text = title,
                fontSize = titleFontSize,
                color = fontColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f) // take remaining space
            )
        }
    }

    @Composable
    fun TopMainHeader(
        @DrawableRes iconRes: Int,
        title: String,
        description: String? = null,
        iconSize: Dp = 96.dp,
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        fontColor: Color = SettingsTheme.typography.title.color,
        onIconClick: (() -> Unit)? = null // Optional click callback
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clickable Image
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier
                    .size(iconSize)
                    .padding(bottom = 16.dp)
                    .let { if (onIconClick != null) it.clickable { onIconClick() } else it }
            )

            FontText(
                text = title,
                fontSize = if (titleFontSize != TextUnit.Unspecified) titleFontSize else 18.sp,
                color = fontColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .wrapContentSize()
            )


            description?.let {
                Spacer(modifier = Modifier.height(2.dp))

                ClickableHtmlText(
                    html = it,
                    color = fontColor,
                    fontSize = if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize else 12.sp,
                    modifier = Modifier.wrapContentHeight(),
                )
            }
        }
    }

    /**
     * Click handling shared by the home entries: a plain click, or (for hidden options) a count of
     * quick taps where a single tap still acts after [multiClickInterval].
     */
    @Composable
    private fun rememberEntryClick(
        onClick: () -> Unit,
        onMultiClick: (Int) -> Unit,
        enableMultiClick: Boolean,
        multiClickCount: Int,
        multiClickInterval: Long
    ): () -> Unit {
        val scope = rememberCoroutineScope()
        val multiClickState = remember {
            object {
                var tapCount = 0
                var lastTapTime = 0L
                var clickJob: Job? = null
            }
        }
        return {
            if (!enableMultiClick) {
                onClick()
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - multiClickState.lastTapTime > multiClickInterval) {
                    multiClickState.tapCount = 0
                }

                multiClickState.tapCount++
                multiClickState.lastTapTime = currentTime
                multiClickState.clickJob?.cancel()

                if (multiClickState.tapCount >= multiClickCount) {
                    multiClickState.tapCount = 0
                    onMultiClick(multiClickCount)
                } else {
                    onMultiClick(multiClickState.tapCount)
                    multiClickState.clickJob = scope.launch {
                        delay(multiClickInterval)
                        if (multiClickState.tapCount == 1) onClick()
                        multiClickState.tapCount = 0
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsHomeItem(
        title: String,
        description: String? = null,
        @DrawableRes iconRes: Int,
        onClick: () -> Unit = {},
        onMultiClick: (Int) -> Unit = {},
        enableMultiClick: Boolean = false,
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        headerColor: Color = SettingsTheme.typography.title.color,
        optionColor: Color = SettingsTheme.typography.option.color,
        iconSize: Dp = 18.dp,
        multiClickCount: Int = 5,
        multiClickInterval: Long = 2000L
    ) {
        val click = rememberEntryClick(onClick, onMultiClick, enableMultiClick, multiClickCount, multiClickInterval)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { click() }
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier.size(iconSize)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                FontText(
                    text = title,
                    color = headerColor,
                    fontSize = if (titleFontSize != TextUnit.Unspecified) titleFontSize else 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.wrapContentHeight()
                )

                description?.let {
                    Spacer(modifier = Modifier.height(1.dp))
                    FontText(
                        text = it,
                        color = optionColor,
                        fontSize = if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize else 12.sp,
                        modifier = Modifier.wrapContentHeight()
                    )
                }
            }
        }
    }

    /**
     * One entry of the settings home screen as a card: an icon tile, the title, a short
     * description and a chevron. Surface and border are tints of the text colour, so the card
     * follows whatever background and theme the user picked.
     */
    @Composable
    fun SettingsHomeCard(
        title: String,
        description: String? = null,
        @DrawableRes iconRes: Int,
        onClick: () -> Unit = {},
        onMultiClick: (Int) -> Unit = {},
        enableMultiClick: Boolean = false,
        titleFontSize: TextUnit = TextUnit.Unspecified,
        descriptionFontSize: TextUnit = TextUnit.Unspecified,
        headerColor: Color = SettingsTheme.typography.title.color,
        optionColor: Color = SettingsTheme.typography.option.color,
        iconSize: Dp = 22.dp,
        tintIcon: Boolean = true,
        multiClickCount: Int = 5,
        multiClickInterval: Long = 2000L
    ) {
        val click = rememberEntryClick(onClick, onMultiClick, enableMultiClick, multiClickCount, multiClickInterval)
        val shape = RoundedCornerShape(20.dp)
        val tileShape = RoundedCornerShape(14.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .clip(shape)
                .background(headerColor.copy(alpha = 0.06f))
                .border(1.dp, headerColor.copy(alpha = 0.12f), shape)
                .clickable { click() }
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(tileShape)
                    .background(headerColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    colorFilter = if (tintIcon) ColorFilter.tint(headerColor) else null,
                    modifier = Modifier.size(if (tintIcon) iconSize else 32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                FontText(
                    text = title,
                    color = headerColor,
                    fontSize = if (titleFontSize != TextUnit.Unspecified) titleFontSize else 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.wrapContentHeight()
                )

                description?.let {
                    Spacer(modifier = Modifier.height(3.dp))
                    FontText(
                        text = it,
                        color = optionColor,
                        fontSize = if (descriptionFontSize != TextUnit.Unspecified) descriptionFontSize else 12.sp,
                        modifier = Modifier.wrapContentHeight()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            FontText(
                text = "›",
                color = optionColor.copy(alpha = 0.7f),
                fontSize = if (titleFontSize != TextUnit.Unspecified) titleFontSize * 1.2f else 22.sp,
                modifier = Modifier.wrapContentHeight()
            )
        }
    }

    @Composable
    fun TitleWithHtmlLinks(
        title: String,
        descriptions: List<String> = emptyList(),
        titleFontSize: TextUnit = 18.sp,
        descriptionFontSize: TextUnit = 12.sp,
        titleColor: Color = SettingsTheme.typography.title.color,
        descriptionColor: Color = SettingsTheme.typography.title.color,
        columns: Boolean = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            FontText(
                text = title,
                color = titleColor,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.wrapContentHeight()
            )

            if (descriptions.isNotEmpty()) {
                val layoutModifier = Modifier
                    .wrapContentHeight()
                    .padding(vertical = if (columns) 2.dp else 0.dp, horizontal = if (!columns) 12.dp else 0.dp)

                if (columns) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(4.dp))
                        descriptions.forEach { htmlString ->
                            ClickableHtmlText(
                                html = htmlString,
                                color = descriptionColor,
                                fontSize = descriptionFontSize,
                                modifier = layoutModifier
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        descriptions.forEach { htmlString ->
                            ClickableHtmlText(
                                html = htmlString,
                                color = descriptionColor,
                                fontSize = descriptionFontSize,
                                modifier = layoutModifier
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun ClickableHtmlText(
        html: String,
        color: Color,
        fontSize: TextUnit,
        modifier: Modifier = Modifier,
        underlineLinks: Boolean = true // optional parameter
    ) {
        val context = LocalContext.current
        val spanned = remember(html) { Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) }

        val annotatedString = buildAnnotatedString {
            append(spanned.toString())
            val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
            urlSpans.forEach { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)

                addStyle(
                    SpanStyle(
                        color = color, // same as normal text
                        textDecoration = if (underlineLinks) TextDecoration.Underline else TextDecoration.None
                    ),
                    start,
                    end
                )
                addStringAnnotation(tag = "URL", annotation = span.url, start = start, end = end)
            }
        }

        val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

        FontText(
            text = annotatedString,
            style = TextStyle(color = color, fontSize = fontSize),
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val result = layoutResult.value ?: return@detectTapGestures
                    val position = result.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations(tag = "URL", start = position, end = position)
                        .firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                            context.startActivity(intent)
                        }
                }
            },
            onTextLayout = { layoutResult.value = it }
        )
    }

    @Composable
    fun SettingsTitle(
        text: String,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = TextUnit.Unspecified,
        onClick: () -> Unit = {}
    ) {
        // Section label above a card: small, spaced capitals in a dimmed text colour
        val base = if (fontSize != TextUnit.Unspecified) fontSize.value else 18f
        val fontColor = SettingsTheme.typography.title.color.copy(alpha = 0.6f)

        FontText(
            text = text.uppercase(),
            fontSize = (base * 0.68f).sp,
            color = fontColor,
            fontWeight = FontWeight.Medium,
            style = TextStyle(letterSpacing = 0.08.em),
            modifier = modifier
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
                .clickable(onClick = onClick)
                .wrapContentSize()
        )
    }

    /** A row that leads to another screen: the title with a chevron at the end. */
    @Composable
    fun SettingsLink(
        title: String,
        fontSize: TextUnit = 24.sp,
        onClick: () -> Unit = {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontText(
                text = title,
                fontSize = fontSize,
                color = SettingsTheme.typography.title.color,
                modifier = Modifier.weight(1f).wrapContentHeight()
            )
            FontText(
                text = "›",
                fontSize = (fontSize.value * 1.3f).sp,
                color = SettingsTheme.typography.option.color,
                modifier = Modifier.wrapContentHeight()
            )
        }
    }

    /** Colour of the thin outline used by the home tiles and the section cards. */
    @Composable
    fun outlineColor(): Color = SettingsTheme.typography.title.color.copy(alpha = 0.15f)

    /**
     * A rounded, outlined box that groups the rows of one settings section. A hairline is drawn
     * between the rows.
     */
    @Composable
    fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        val line = SettingsTheme.typography.title.color.copy(alpha = 0.07f)
        val outline = outlineColor()
        val rowTops = remember { mutableListOf<Int>() }

        Layout(
            content = content,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(shape)
                .border(1.dp, outline, shape)
                .drawBehind {
                    // Separators above every row but the first
                    rowTops.drop(1).forEach { y ->
                        drawLine(line, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1.dp.toPx())
                    }
                }
        ) { measurables, constraints ->
            val childConstraints = constraints.copy(minHeight = 0)
            val placeables = measurables.map { it.measure(childConstraints) }
            val height = placeables.sumOf { it.height }
            rowTops.clear()
            var y = 0
            placeables.forEach { rowTops.add(y); y += it.height }
            layout(constraints.maxWidth, height) {
                var top = 0
                placeables.forEach { it.placeRelative(0, top); top += it.height }
            }
        }
    }

    /**
     * One tile of the settings home grid: an outlined box with the icon centred above the title
     * and nothing else. [subtitle] is only used for a state such as "Locked".
     */
    @Composable
    fun SettingsTile(
        title: String,
        @DrawableRes iconRes: Int,
        modifier: Modifier = Modifier,
        subtitle: String? = null,
        tintIcon: Boolean = true,
        titleFontSize: TextUnit = 13.sp,
        iconSize: Dp = 26.dp,
        onClick: () -> Unit = {}
    ) {
        val shape = RoundedCornerShape(16.dp)
        val color = SettingsTheme.typography.title.color
        Column(
            modifier = modifier
                .clip(shape)
                .border(1.dp, outlineColor(), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = if (tintIcon) ColorFilter.tint(color) else null,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(12.dp))
            FontText(
                text = title,
                color = color,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.wrapContentHeight()
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                FontText(
                    text = it,
                    color = SettingsTheme.typography.option.color,
                    fontSize = (titleFontSize.value * 0.82f).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.wrapContentHeight()
                )
            }
        }
    }

    @Composable
    fun SettingsSwitch(
        text: String,
        fontSize: TextUnit = 14.sp,
        titleColor: Color = SettingsTheme.typography.title.color,
        defaultState: Boolean = false,
        onCheckedChange: (Boolean) -> Unit
    ) {
        var isChecked by remember { mutableStateOf(defaultState) }
        // Extract font size and color from theme safely in composable scope
        val resolvedFontSizeSp = if (fontSize != TextUnit.Unspecified) fontSize.value else 16f
        val context = LocalContext.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isChecked = !isChecked
                    onCheckedChange(isChecked)

                    HapticFeedbackService.trigger(
                        context,
                        if (isChecked)
                            HapticFeedbackService.EffectType.ON
                        else
                            HapticFeedbackService.EffectType.OFF
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Label
            FontText(
                text = text,
                fontSize = resolvedFontSizeSp.sp,
                color = titleColor,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
            )

            // Custom switch
            CustomSwitch(
                checked = isChecked
            )
        }
    }


    @Composable
    fun CustomSwitch(
        checked: Boolean,
        modifier: Modifier = Modifier
    ) {
        val thumbSize = 14.dp
        val trackWidth = 32.dp
        val trackHeight = 16.dp

        val thumbOffset by animateDpAsState(
            targetValue = if (checked) trackWidth - thumbSize - 2.dp else 2.dp,
            label = "thumbOffset"
        )

        Box(
            modifier = modifier
                .size(width = trackWidth, height = trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(
                    if (checked)
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF4CAF73),
                                Color(0xFF2E8B57)
                            )
                        )
                    else
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFD6D6D6),
                                Color(0xFFD6D6D6)
                            )
                        )

                )
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            )
        }
    }


    @Composable
    fun SettingsSelect(
        title: String,
        option: String,
        fontSize: TextUnit = 24.sp,
        titleColor: Color = SettingsTheme.typography.title.color,
        optionColor: Color = SettingsTheme.typography.option.color,
        onClick: () -> Unit = {},
    ) {
        val fontSizeSp = fontSize.value

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp)
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Title
            FontText(
                text = title,
                fontSize = fontSizeSp.sp,
                color = titleColor,
                modifier = Modifier.wrapContentHeight()
            )

            // Option / secondary text
            FontText(
                text = option,
                fontSize = (fontSizeSp / 1.3f).sp,
                color = optionColor,
                modifier = Modifier.wrapContentHeight()
            )
        }
    }

    @Composable
    fun FontText(
        text: Any, // String or AnnotatedString
        modifier: Modifier = Modifier,
        fontSize: TextUnit = 16.sp,
        color: Color = SettingsTheme.typography.title.color,
        fontWeight: FontWeight? = null,
        style: TextStyle? = null, // Optional additional style
        textAlign: TextAlign? = null,
        onClick: (() -> Unit)? = null,
        onTextLayout: ((TextLayoutResult) -> Unit)? = null
    ) {
        val context = LocalContext.current

        // Get Typeface from FontManager (like your FontEditText)
        val typeface = remember { FontManager.getTypeface(context) }

        // Convert Typeface to Compose FontFamily
        val fontFamily: FontFamily = remember(typeface) {
            typeface?.let { FontFamily(it) } ?: FontFamily.Default
        }

        val finalStyle = (style ?: TextStyle()).copy(
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        )

        val clickableModifier = if (onClick != null) {
            Modifier.clickable(
                onClick = onClick,
            )
        } else Modifier

        when (text) {
            is String -> Text(
                text = text,
                modifier = modifier.then(clickableModifier),
                style = finalStyle,
                textAlign = textAlign,
                onTextLayout = onTextLayout // nullable is fine for String
            )

            is AnnotatedString -> Text(
                text = text,
                modifier = modifier.then(clickableModifier),
                style = finalStyle,
                textAlign = textAlign,
                onTextLayout = onTextLayout ?: {} // must be non-null for AnnotatedString
            )

            else -> throw IllegalArgumentException("FontText supports only String or AnnotatedString")
        }
    }

}