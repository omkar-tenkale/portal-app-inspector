package io.github.portalappinspector.app.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sebastianneubauer.jsontree.JsonTree
import com.sebastianneubauer.jsontree.TreeState
import com.sebastianneubauer.jsontree.search.rememberSearchState
import io.github.portalappinspector.app.ui.icons.PortalTabIcons
import io.github.portalappinspector.app.util.copyTextToClipboard
import io.github.portalappinspector.app.util.formatJsonOrNull
import kotlinx.coroutines.launch

internal enum class JsonTextMode {
    Json,
    Text,
}

@Composable
internal fun JsonText(
    jsonString: String,
    modifier: Modifier = Modifier,
    onTextChange: ((String) -> Unit)? = null,
    initialMode: JsonTextMode = JsonTextMode.Json,
    onModeChange: ((JsonTextMode) -> Unit)? = null,
    containerColor: Color = PortalColors.codeInput,
    borderColor: Color = PortalColors.codeInputBorder,
    textStyle: TextStyle = TextStyle(
        color = PortalColors.text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
) {
    var selectedMode by remember { mutableStateOf(initialMode) }
    var textValue by remember { mutableStateOf(jsonString) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTextResult by remember { mutableStateOf(0) }
    val jsonBody = remember(textValue) { formatJsonOrNull(textValue) }
    val jsonValid = jsonBody != null
    val jsonSearchState = rememberSearchState()
    val jsonListState = rememberLazyListState()
    val textScrollState = rememberScrollState()
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val lineHeightPx = with(LocalDensity.current) {
        (if (textStyle.lineHeight != TextUnit.Unspecified) textStyle.lineHeight else textStyle.fontSize).toPx()
    }
    val textSearchRanges = remember(textValue, searchQuery) {
        textValue.searchRanges(searchQuery)
    }
    val currentTextResult = selectedTextResult.coerceIn(0, (textSearchRanges.size - 1).coerceAtLeast(0))

    LaunchedEffect(jsonString) {
        if (jsonString != textValue) {
            textValue = jsonString
        }
    }
    LaunchedEffect(jsonValid, selectedMode) {
        if (!jsonValid && selectedMode == JsonTextMode.Json) {
            selectedMode = JsonTextMode.Text
            onModeChange?.invoke(JsonTextMode.Text)
        }
    }
    LaunchedEffect(searchQuery, jsonBody) {
        jsonSearchState.query = searchQuery.trim().takeIf { it.isNotEmpty() && jsonBody != null }
    }
    LaunchedEffect(jsonSearchState.selectedResultListIndex) {
        val index = jsonSearchState.selectedResultListIndex ?: return@LaunchedEffect
        jsonListState.animateScrollToItem(index)
    }
    LaunchedEffect(textSearchRanges.size, searchQuery) {
        selectedTextResult = selectedTextResult.coerceIn(0, (textSearchRanges.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(selectedMode, currentTextResult, textSearchRanges) {
        if (selectedMode == JsonTextMode.Text && textSearchRanges.isNotEmpty()) {
            val lineIndex = textValue.lineIndexBefore(textSearchRanges[currentTextResult].first)
            textScrollState.animateScrollTo(((lineIndex * lineHeightPx) - lineHeightPx).toInt().coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
    ) {
        JsonTextHeader(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            resultLabel = when {
                searchQuery.isBlank() -> "Search"
                selectedMode == JsonTextMode.Json && jsonSearchState.totalResults > 0 ->
                    "${(jsonSearchState.selectedResultIndex ?: 0) + 1}/${jsonSearchState.totalResults}"
                selectedMode == JsonTextMode.Text && textSearchRanges.isNotEmpty() ->
                    "${currentTextResult + 1}/${textSearchRanges.size}"
                else -> "0"
            },
            previousEnabled = searchQuery.isNotBlank() && when (selectedMode) {
                JsonTextMode.Json -> jsonSearchState.totalResults > 0
                JsonTextMode.Text -> textSearchRanges.isNotEmpty()
            },
            nextEnabled = searchQuery.isNotBlank() && when (selectedMode) {
                JsonTextMode.Json -> jsonSearchState.totalResults > 0
                JsonTextMode.Text -> textSearchRanges.isNotEmpty()
            },
            onPrevious = {
                when (selectedMode) {
                    JsonTextMode.Json -> scope.launch { jsonSearchState.selectPrevious() }
                    JsonTextMode.Text -> {
                        if (textSearchRanges.isNotEmpty()) {
                            selectedTextResult =
                                if (currentTextResult == 0) textSearchRanges.lastIndex else currentTextResult - 1
                        }
                    }
                }
            },
            onNext = {
                when (selectedMode) {
                    JsonTextMode.Json -> scope.launch { jsonSearchState.selectNext() }
                    JsonTextMode.Text -> {
                        if (textSearchRanges.isNotEmpty()) {
                            selectedTextResult =
                                if (currentTextResult == textSearchRanges.lastIndex) 0 else currentTextResult + 1
                        }
                    }
                }
            },
            selectedMode = selectedMode,
            jsonValid = jsonValid,
            searchFocusRequester = searchFocusRequester,
            onModeChange = { mode ->
                if (mode != JsonTextMode.Json || jsonValid) {
                    selectedMode = mode
                    onModeChange?.invoke(mode)
                    searchFocusRequester.requestFocus()
                }
            },
            onCopy = { copyTextToClipboard(textValue) },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(borderColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedMode == JsonTextMode.Json) 1f else 0f)
                    .zIndex(if (selectedMode == JsonTextMode.Json) 1f else 0f),
            ) {
                if (jsonBody != null) {
                    JsonTree(
                        modifier = Modifier.fillMaxSize(),
                        json = jsonBody,
                        onLoading = {
                            PulsatingDots(
                                color = PortalColors.accent,
                                modifier = Modifier
                                    .padding(start = 12.dp, top = 12.dp)
                                    .size(width = 42.dp, height = 16.dp),
                            )
                        },
                        initialState = TreeState.EXPANDED,
                        contentPadding = PaddingValues(12.dp),
                        colors = PortalJsonTreeColors,
                        textStyle = textStyle,
                        showIndices = true,
                        searchState = jsonSearchState,
                        lazyListState = jsonListState,
                    )
                }
            }
            BasicTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onTextChange?.invoke(it)
                },
                enabled = selectedMode == JsonTextMode.Text,
                readOnly = onTextChange == null,
                textStyle = textStyle,
                cursorBrush = SolidColor(PortalColors.accent),
                visualTransformation = JsonTextSearchVisualTransformation(
                    ranges = textSearchRanges,
                    selectedIndex = currentTextResult.takeIf { textSearchRanges.isNotEmpty() },
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedMode == JsonTextMode.Text) 1f else 0f)
                    .zIndex(if (selectedMode == JsonTextMode.Text) 1f else 0f)
                    .verticalScroll(textScrollState),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    ) {
                        if (textValue.isEmpty()) {
                            Text(
                                text = "{\n  \n}",
                                color = PortalColors.muted.copy(alpha = 0.42f),
                                fontFamily = textStyle.fontFamily ?: FontFamily.Monospace,
                                fontSize = textStyle.fontSize,
                                lineHeight = textStyle.lineHeight,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun JsonTextHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    resultLabel: String,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    selectedMode: JsonTextMode,
    jsonValid: Boolean,
    searchFocusRequester: FocusRequester,
    onModeChange: (JsonTextMode) -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = PortalColors.text,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
            ),
            cursorBrush = SolidColor(PortalColors.accent),
            modifier = Modifier
                .size(width = 132.dp, height = 20.dp)
                .focusRequester(searchFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.Enter,
                            Key.DirectionDown,
                            Key.DirectionRight -> {
                                if (nextEnabled) {
                                    onNext()
                                }
                                true
                            }
                            Key.DirectionUp,
                            Key.DirectionLeft -> {
                                if (previousEnabled) {
                                    onPrevious()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search",
                            color = PortalColors.muted.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = resultLabel,
            color = PortalColors.muted,
            fontSize = 11.sp,
            maxLines = 1,
        )
        JsonTextSearchButton("<", previousEnabled, onPrevious)
        JsonTextSearchButton(">", nextEnabled, onNext)
        Spacer(Modifier.weight(1f))
        JsonTextCopyButton(onClick = onCopy)
        JsonTextModeSelector(
            selectedMode = selectedMode,
            jsonValid = jsonValid,
            onModeChange = onModeChange,
        )
    }
}

@Composable
private fun JsonTextSearchButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Text(
        text = text,
        color = when {
            !enabled -> PortalColors.muted.copy(alpha = 0.38f)
            hovered -> PortalColors.accent
            else -> PortalColors.text
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

@Composable
private fun JsonTextCopyButton(
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(PortalTabIcons.CopyPath),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(if (hovered) PortalColors.accent else PortalColors.muted),
        )
    }
}

@Composable
private fun JsonTextModeSelector(
    selectedMode: JsonTextMode,
    jsonValid: Boolean,
    onModeChange: (JsonTextMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        JsonTextModeOption(
            text = "JSON",
            selected = selectedMode == JsonTextMode.Json,
            enabled = jsonValid,
            invalid = !jsonValid,
            onClick = { onModeChange(JsonTextMode.Json) },
        )
        Text("|", color = PortalColors.inputBorder, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
        JsonTextModeOption(
            text = "Text",
            selected = selectedMode == JsonTextMode.Text,
            enabled = true,
            invalid = false,
            onClick = { onModeChange(JsonTextMode.Text) },
        )
    }
}

@Composable
private fun JsonTextModeOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    invalid: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        invalid -> PortalColors.error.copy(alpha = 0.78f)
        selected -> PortalColors.accent
        else -> PortalColors.muted
    }
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    )
}

private class JsonTextSearchVisualTransformation(
    private val ranges: List<IntRange>,
    private val selectedIndex: Int?,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (ranges.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val transformed = buildAnnotatedString {
            append(text)
            ranges.forEachIndexed { index, range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                addStyle(
                    style = SpanStyle(
                        color = if (index == selectedIndex) PortalColors.background else Color.Unspecified,
                        background = if (index == selectedIndex) {
                            Color(0xFFFFC01E)
                        } else {
                            Color(0xFF755C18)
                        },
                    ),
                    start = start,
                    end = end,
                )
            }
        }
        return TransformedText(transformed, OffsetMapping.Identity)
    }
}

private fun String.searchRanges(query: String): List<IntRange> {
    val search = query.trim()
    if (search.isEmpty()) {
        return emptyList()
    }
    val ranges = mutableListOf<IntRange>()
    var startIndex = 0
    while (startIndex <= length) {
        val index = indexOf(search, startIndex = startIndex, ignoreCase = true)
        if (index < 0) {
            break
        }
        ranges += index until index + search.length
        startIndex = index + search.length
    }
    return ranges
}

private fun String.lineIndexBefore(offset: Int): Int =
    take(offset.coerceIn(0, length)).count { it == '\n' }


@Preview
@Composable
private fun JsonTextPreview() {
    Box(modifier = Modifier.padding(16.dp).background(PortalColors.background)) {
        JsonText(jsonString = """{
  \"status\": \"success\",
  \"code\": 200
}""")
    }
}
