package io.github.vivekg7.badal.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.vivekg7.badal.Design
import io.github.vivekg7.badal.Fill
import io.github.vivekg7.badal.Motif
import io.github.vivekg7.badal.Painter
import io.github.vivekg7.badal.Presets
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.random.Random

/** Designs drawn small, by design and width; 32 MB holds about two hundred gallery tiles. */
private val drawn =
    object : LruCache<Pair<Design, Int>, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(
            key: Pair<Design, Int>,
            value: ImageBitmap,
        ) = value.width * value.height * 4
    }

fun dropDrawn() = drawn.evictAll()

/** Two at a time, so a gallery full of tiles doesn't starve the preview being dragged. */
private val painting = Dispatchers.Default.limitedParallelism(2)

/** A design as JSON, for rememberSaveable and for handing between pages. */
val designJson = Json { ignoreUnknownKeys = true }

fun Design.encoded() = designJson.encodeToString(Design.serializer(), this)

fun decodeDesign(s: String) = designJson.decodeFromString(Design.serializer(), s)

private val DesignSaver = Saver<Design, String>(save = { it.encoded() }, restore = { decodeDesign(it) })

/** The screen's height over its width: designs are drawn in its shape. */
@Composable
private fun screenRatio(): Float {
    val app = LocalContext.current.app
    return remember { app.images.screen().let { (w, h) -> h / w.toFloat() } }
}

/** [d] drawn [width] pixels wide, off the main thread; the last one stays until the next is ready. */
@Composable
private fun rememberDrawn(
    d: Design,
    width: Int,
): ImageBitmap? {
    val ratio = screenRatio()
    val bitmap by produceState(drawn.get(d to width), d, width) {
        value = drawn.get(d to width) ?: withContext(painting) {
            gpu(Painter.render(d, width, (width * ratio).roundToInt())).also { drawn.put(d to width, it) }
        }
    }
    return bitmap
}

@Composable
private fun DesignTile(
    d: Design,
    modifier: Modifier = Modifier,
    width: Int = 240,
    square: Boolean = false,
) {
    val bitmap = rememberDrawn(d, width)
    Box(modifier.aspectRatio(if (square) 1f else 1 / screenRatio()).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/**
 * The premade designs, by kind. Tap one to change it before adding; long-press
 * to pick several and add them as they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryPage(
    onBack: () -> Unit,
    onOpen: (Design) -> Unit,
    onAdd: (List<Design>) -> Unit,
) {
    var selected by remember { mutableStateOf(emptyList<Design>()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) "Make a wallpaper" else "${selected.size} selected") },
                navigationIcon = {
                    if (selected.isEmpty()) {
                        IconButton(onBack) { Icon(Icons.Back, "Back") }
                    } else {
                        IconButton({ selected = emptyList() }) { Icon(Icons.Close, "Clear selection") }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) TextButton({ onAdd(selected) }) { Text("Add ${selected.size}") }
                },
            )
        },
    ) { pad ->
        val grid = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        // Grid index of each section's title: the intro, then per section its title and its designs.
        val starts =
            remember {
                var at = 1
                Presets.groups.map { (title, designs) -> (title to at).also { at += 1 + designs.size } }
            }
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                starts.forEach { (title, at) -> AssistChip({ scope.launch { grid.scrollToItem(at) } }, { Text(title) }) }
            }
            LazyVerticalGrid(
                GridCells.Fixed(4),
                state = grid,
                contentPadding = PaddingValues(8.dp, 0.dp, 8.dp, 32.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onOpen(Design()) }) { Text("Start blank") }
                            OutlinedButton({ onOpen(Presets.random(Random.nextInt())) }) {
                                Icon(Icons.Dice, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Surprise me")
                            }
                        }
                    }
                }
                for ((title, designs) in Presets.groups) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(0.dp, 16.dp, 0.dp, 4.dp),
                        )
                    }
                    items(designs) { d ->
                        val isSelected = d in selected
                        DesignTile(
                            d,
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .then(if (isSelected) Modifier.border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            selected.isEmpty() -> onOpen(d)
                                            isSelected -> selected = selected - d
                                            else -> selected = selected + d
                                        }
                                    },
                                    onLongClick = { if (!isSelected) selected = selected + d },
                                ),
                            width = 200,
                        )
                    }
                }
            }
        }
    }
}

/** One design, being changed: a live preview over its settings. [editing]: it replaces a picture already in the album. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignPage(
    start: Design,
    editing: Boolean,
    onBack: () -> Unit,
    onDone: (Design) -> Unit,
) {
    var d by rememberSaveable(stateSaver = DesignSaver) { mutableStateOf(start) }
    val ratio = screenRatio()
    val movable = d.motif.centred || d.fill == Fill.RADIAL || d.fill == Fill.SWEEP
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit wallpaper" else "New wallpaper") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Back, "Back") } },
                actions = { IconButton({ d = Presets.random(Random.nextInt()) }) { Icon(Icons.Dice, "Surprise me") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button({ onDone(d) }, Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 8.dp)) {
                    Text(if (editing) "Save" else "Add to album")
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(0.42f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val heightPx = with(density) { maxHeight.toPx() }
                val widthPx = (heightPx / ratio).roundToInt().coerceAtLeast(16)
                val preview by produceState<ImageBitmap?>(null, widthPx) {
                    // Latest only: while a slider moves, a drawing that is already stale is dropped.
                    snapshotFlow { d }.collectLatest { now ->
                        delay(25)
                        value = withContext(Dispatchers.Default) { Painter.render(now, widthPx, (widthPx * ratio).roundToInt()).asImageBitmap() }
                    }
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1 / ratio)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (movable) {
                                Modifier.pointerInput(Unit) {
                                    fun at(p: Offset) {
                                        d = d.copy(x = (p.x / this.size.width).coerceIn(0f, 1f), y = (p.y / this.size.height).coerceIn(0f, 1f))
                                    }
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        at(down.position)
                                        drag(down.id) {
                                            at(it.position)
                                            it.consume()
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    preview?.let { Image(it, "Preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
            Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Section("Background") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Fill.entries.forEach { f -> FilterChip(d.fill == f, { d = d.copy(fill = f) }, { Text(f.label) }) }
                    }
                    ColorRow(d.colors, if (d.fill == Fill.SOLID) 1 else 5) { d = d.copy(colors = it) }
                    if (d.fill == Fill.LINEAR || d.fill == Fill.SWEEP) Setting("Angle", d.angle, 0..359, "°") { d = d.copy(angle = it) }
                    if (d.fill == Fill.MESH) {
                        OutlinedButton({ d = d.copy(seed = Random.nextInt(1, 100_000)) }, Modifier.padding(horizontal = 16.dp)) {
                            Icon(Icons.Shuffle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Shuffle")
                        }
                    }
                }
                Section("Pattern") {
                    MotifPicker(d) { m ->
                        val b = Presets.base(m)
                        d = d.copy(motif = m, opacity = b.opacity, scale = b.scale, weight = b.weight, turn = b.turn, x = b.x, y = b.y)
                    }
                    if (d.motif != Motif.NONE) {
                        Text(
                            if (d.motif.samples) "Gap colour" else "Pattern colours",
                            Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ColorRow(d.inks, if (d.motif.samples) 1 else 5) { d = d.copy(inks = it) }
                        Setting(if (d.motif.samples) "Shading" else "Opacity", d.opacity, 0..100, "%") { d = d.copy(opacity = it) }
                        Setting("Size", d.scale, 0..100) { d = d.copy(scale = it) }
                        Setting("Weight", d.weight, 0..100) { d = d.copy(weight = it) }
                        if (d.motif.turns) Setting("Rotation", d.turn, 0..180, "°") { d = d.copy(turn = it) }
                        if (d.motif.seeded) {
                            OutlinedButton({ d = d.copy(seed = Random.nextInt(1, 100_000)) }, Modifier.padding(horizontal = 16.dp)) {
                                Icon(Icons.Shuffle, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                }
                Section("Finish") {
                    Setting("Grain", d.grain, 0..100, "%") { d = d.copy(grain = it) }
                    Setting("Vignette", d.vignette, 0..100, "%") { d = d.copy(vignette = it) }
                }
            }
        }
    }
}

/** Every pattern, drawn small in the design's own colours. */
@Composable
private fun MotifPicker(
    d: Design,
    onPick: (Motif) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Motif.entries) { m ->
            val sample = Presets.base(m).copy(fill = d.fill, colors = d.colors, angle = d.angle, inks = d.inks, seed = d.seed)
            val chosen = d.motif == m
            Column(Modifier.width(64.dp).clickable { onPick(m) }, horizontalAlignment = Alignment.CenterHorizontally) {
                DesignTile(
                    sample,
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier),
                    width = 120,
                    square = true,
                )
                Text(
                    m.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Setting(
    label: String,
    value: Int,
    range: IntRange,
    unit: String = "",
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("$value$unit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value.toFloat(),
            { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.height(36.dp),
        )
    }
}

/** A row of colour swatches: tap one to change or remove it, + to add one, up to [max]. Only the first [max] show. */
@Composable
private fun ColorRow(
    colors: List<Int>,
    max: Int,
    onChange: (List<Int>) -> Unit,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(16.dp, 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.take(max).forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { editing = i },
            )
        }
        if (colors.size < max) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { editing = colors.size },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Add, "Add a colour") }
        }
    }
    editing?.let { i ->
        ColorDialog(
            initial = colors.getOrElse(i) { colors.lastOrNull() ?: Color.White.toArgb() },
            onDone = { c ->
                onChange(if (i < colors.size) colors.toMutableList().also { it[i] = c } else colors + c)
                editing = null
            },
            onRemove =
                if (i < colors.size && colors.size > 1) {
                    {
                        onChange(colors.toMutableList().also { it.removeAt(i) })
                        editing = null
                    }
                } else {
                    null
                },
            onDismiss = { editing = null },
        )
    }
}

/** Hue, saturation and brightness by hand, a hex code, or a swatch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(
    initial: Int,
    onDone: (Int) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember { mutableStateOf(hexOf(initial)) }

    fun set(c: Int) {
        val f = FloatArray(3)
        android.graphics.Color.colorToHSV(c, f)
        hue = f[0]
        sat = f[1]
        value = f[2]
        hex = hexOf(c)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Saturation across, brightness down, for the chosen hue.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .pointerInput(Unit) {
                            fun at(p: Offset) {
                                sat = (p.x / size.width).coerceIn(0f, 1f)
                                value = 1 - (p.y / size.height).coerceIn(0f, 1f)
                                hex = hexOf(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                at(down.position)
                                drag(down.id) {
                                    at(it.position)
                                    it.consume()
                                }
                            }
                        },
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val px = with(LocalDensity.current) { maxWidth.toPx() }
                        val py = with(LocalDensity.current) { maxHeight.toPx() }
                        val r = with(LocalDensity.current) { 9.dp.toPx() }
                        Box(
                            Modifier
                                .offset { IntOffset((sat * px - r).roundToInt(), ((1 - value) * py - r).roundToInt()) }
                                .size(18.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .border(3.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
                        )
                    }
                }
                // Hue.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                        .pointerInput(Unit) {
                            fun at(p: Offset) {
                                hue = (p.x / size.width).coerceIn(0f, 1f) * 359.9f
                                hex = hexOf(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                at(down.position)
                                drag(down.id) {
                                    at(it.position)
                                    it.consume()
                                }
                            }
                        },
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val px = with(LocalDensity.current) { maxWidth.toPx() }
                        val r = with(LocalDensity.current) { 14.dp.toPx() }
                        Box(
                            Modifier
                                .offset { IntOffset((hue / 360f * px - r).roundToInt().coerceIn(0, (px - 2 * r).roundToInt()), 0) }
                                .size(28.dp)
                                .border(3.dp, Color.White, CircleShape),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    OutlinedTextField(
                        hex,
                        { t ->
                            hex = t.take(7)
                            parseHex(hex)?.let { c ->
                                val f = FloatArray(3)
                                android.graphics.Color.colorToHSV(c, f)
                                hue = f[0]
                                sat = f[1]
                                value = f[2]
                            }
                        },
                        singleLine = true,
                        label = { Text("Hex") },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.swatches.forEach { c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { set(c) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton({ onDone(color) }) { Text("Done") } },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onRemove) { Text("Remove") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun hexOf(c: Int) = "#%06X".format(c and 0xFFFFFF)

private fun parseHex(s: String): Int? {
    val t = s.removePrefix("#")
    if (t.length != 6) return null
    return t.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}
