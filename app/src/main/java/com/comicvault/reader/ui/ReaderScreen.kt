package com.comicvault.reader.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.FormatListNumbered
import com.comicvault.reader.data.chapterLabel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.comicvault.reader.data.Comic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ReadMode(val label: String) {
    Vertical("條漫（上下捲動）"),
    LeftToRight("翻頁（左 → 右）"),
    RightToLeft("日漫（右 → 左）"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(comic: Comic, startPage: Int, onBack: () -> Unit, onPage: (Int) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("reader", Context.MODE_PRIVATE) }
    var mode by remember {
        mutableStateOf(runCatching { ReadMode.valueOf(prefs.getString("mode", null) ?: "") }.getOrDefault(ReadMode.Vertical))
    }
    val count = comic.pages.size
    var page by remember(comic.id) { mutableIntStateOf(startPage.coerceIn(0, (count - 1).coerceAtLeast(0))) }
    var showChapterMenu by remember { mutableStateOf(false) }
    val chapter = comic.chapterOf(page)
    var showBars by remember { mutableStateOf(true) }
    var showModeMenu by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<Int?>(null) }
    var sliderPos by remember { mutableStateOf<Float?>(null) }

    val currentOnPage by rememberUpdatedState(onPage)
    LaunchedEffect(page) {
        delay(400) // 停留一下才存進度
        currentOnPage(page)
    }
    SystemBarsEffect(showBars)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (count == 0) {
            Text("這本漫畫沒有頁面", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            key(mode) {
                when (mode) {
                    ReadMode.Vertical -> VerticalReader(
                        comic = comic,
                        initial = page,
                        jumpTo = jumpTo,
                        onJumpDone = { jumpTo = null },
                        onPageChange = { page = it },
                        onTap = { showBars = !showBars },
                    )
                    else -> PagedReader(
                        comic = comic,
                        initial = page,
                        rtl = mode == ReadMode.RightToLeft,
                        jumpTo = jumpTo,
                        onJumpDone = { jumpTo = null },
                        onPageChange = { page = it },
                        onTapCenter = { showBars = !showBars },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(comic.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (comic.chapters.size > 1) {
                            val sub = comic.chapters.getOrNull(chapter)?.name?.takeIf { it.isNotBlank() && it != chapterLabel(chapter) }
                            Text(
                                chapterLabel(chapter) + (sub?.let { " · $it" } ?: ""),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (comic.chapters.size > 1) {
                        Box {
                            IconButton(onClick = { showChapterMenu = true }) {
                                Icon(Icons.Outlined.FormatListNumbered, contentDescription = "選擇章節")
                            }
                            DropdownMenu(expanded = showChapterMenu, onDismissRequest = { showChapterMenu = false }) {
                                comic.chapters.forEachIndexed { i, ch ->
                                    val sub = ch.name.takeIf { it.isNotBlank() && it != chapterLabel(i) }
                                    DropdownMenuItem(
                                        text = { Text(chapterLabel(i) + (sub?.let { " · $it" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingIcon = { RadioButton(selected = i == chapter, onClick = null) },
                                        onClick = {
                                            showChapterMenu = false
                                            jumpTo = comic.chapterStarts[i]
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { showModeMenu = true }) {
                        Icon(Icons.Outlined.ViewCarousel, contentDescription = "閱讀模式")
                    }
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                        ReadMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                leadingIcon = { RadioButton(selected = m == mode, onClick = null) },
                                onClick = {
                                    mode = m
                                    prefs.edit().putString("mode", m.name).apply()
                                    showModeMenu = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }

        AnimatedVisibility(
            visible = showBars && count > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val cur = sliderPos?.roundToInt() ?: page
                val ch = comic.chapterOf(cur)
                val inChapter = cur - comic.chapterStarts.getOrElse(ch) { 0 } + 1
                val chSize = comic.chapters.getOrNull(ch)?.pages?.size ?: count
                Text(
                    if (comic.chapters.size > 1) "${chapterLabel(ch)}  $inChapter / $chSize　（全書 ${cur + 1} / $count）"
                    else "${cur + 1} / $count",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (count > 1) {
                    // 日漫模式下滑桿也改成右到左
                    val dir = if (mode == ReadMode.RightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides dir) {
                        Slider(
                            value = sliderPos ?: page.toFloat(),
                            onValueChange = { sliderPos = it },
                            onValueChangeFinished = {
                                sliderPos?.let { jumpTo = it.roundToInt().coerceIn(0, count - 1) }
                                sliderPos = null
                            },
                            valueRange = 0f..(count - 1).toFloat(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun pageRequest(file: java.io.File) =
    ImageRequest.Builder(LocalContext.current)
        .data(file)
        .size(2560) // 保留足夠解析度給放大用，又不會讓超大圖吃光記憶體
        .precision(Precision.INEXACT)
        .build()

@Composable
private fun VerticalReader(
    comic: Comic,
    initial: Int,
    jumpTo: Int?,
    onJumpDone: () -> Unit,
    onPageChange: (Int) -> Unit,
    onTap: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val zoom = remember { ZoomState() }
    val tap by rememberUpdatedState(onTap)
    val pageChange by rememberUpdatedState(onPageChange)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { pageChange(it) }
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo != null) {
            listState.scrollToItem(jumpTo)
            onJumpDone()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { zoom.toggle(it, size, allowY = false) },
                )
            }
            .pointerInput(Unit) { verticalZoomGestures(zoom) }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                },
        ) {
            items(comic.pages, key = { it.file }) { p ->
                AsyncImage(
                    model = pageRequest(comic.fileOf(p)),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().aspectRatio(p.ratio),
                )
            }
        }
    }
}

@Composable
private fun PagedReader(
    comic: Comic,
    initial: Int,
    rtl: Boolean,
    jumpTo: Int?,
    onJumpDone: () -> Unit,
    onPageChange: (Int) -> Unit,
    onTapCenter: () -> Unit,
) {
    val count = comic.pages.size
    val pagerState = rememberPagerState(initialPage = initial) { count }
    val scope = rememberCoroutineScope()
    val zoomStates = remember(comic.id, count) { List(count) { ZoomState() } }
    val tapCenter by rememberUpdatedState(onTapCenter)
    val pageChange by rememberUpdatedState(onPageChange)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { cur ->
            pageChange(cur)
            zoomStates.forEachIndexed { i, z -> if (i != cur) z.reset() }
        }
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo != null) {
            pagerState.scrollToPage(jumpTo)
            onJumpDone()
        }
    }

    val zoomed = zoomStates.getOrNull(pagerState.currentPage)?.let { it.scale > 1.01f } ?: false

    HorizontalPager(
        state = pagerState,
        reverseLayout = rtl,
        beyondViewportPageCount = 1,
        userScrollEnabled = !zoomed,
        key = { comic.pages[it].file },
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val zs = zoomStates[index]
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(rtl) {
                    detectTapGestures(
                        onDoubleTap = { zs.toggle(it, size, allowY = true) },
                        onTap = { pos ->
                            // 點左右三分之一翻頁，點中間顯示／隱藏工具列（放大中一律只切換工具列）
                            val third = size.width / 3f
                            val next: Boolean? = when {
                                zs.scale > 1.01f -> null
                                pos.x > third * 2 -> !rtl
                                pos.x < third -> rtl
                                else -> null
                            }
                            val target = when (next) {
                                true -> index + 1
                                false -> index - 1
                                null -> -1
                            }
                            if (next == null) {
                                tapCenter()
                            } else if (target in 0 until count) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        },
                    )
                }
                .pointerInput(Unit) { pagedZoomGestures(zs) }
        ) {
            AsyncImage(
                model = pageRequest(comic.fileOf(comic.pages[index])),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zs.scale
                        scaleY = zs.scale
                        translationX = zs.offset.x
                        translationY = zs.offset.y
                    },
            )
        }
    }
}

/** 縮放狀態：以畫面中心為基準的 scale + 位移 */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun transform(zoomChange: Float, pan: Offset, centroid: Offset, size: IntSize, allowY: Boolean) {
        val old = scale
        val new = (old * zoomChange).coerceIn(1f, 5f)
        val z = new / old
        val center = Offset(size.width / 2f, size.height / 2f)
        val q = if (centroid.isSpecified) centroid - center else Offset.Zero
        // 讓手指中心點下的內容保持不動
        val o = (offset - q) * z + q + pan
        scale = new
        offset = if (new <= 1f) Offset.Zero else clamp(o, new, size, allowY)
    }

    fun toggle(at: Offset, size: IntSize, allowY: Boolean) {
        if (scale > 1.05f) {
            reset()
        } else {
            val s = 2.5f
            val center = Offset(size.width / 2f, size.height / 2f)
            scale = s
            offset = clamp((center - at) * (s - 1f), s, size, allowY)
        }
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp(o: Offset, s: Float, size: IntSize, allowY: Boolean): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), if (allowY) o.y.coerceIn(-maxY, maxY) else 0f)
    }
}

/** 條漫模式：雙指縮放；放大後單指可左右平移，上下仍交給清單捲動 */
private suspend fun PointerInputScope.verticalZoomGestures(zoom: ZoomState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2) {
                zoom.transform(
                    event.calculateZoom(),
                    event.calculatePan(),
                    event.calculateCentroid(useCurrent = true),
                    size,
                    allowY = false,
                )
                event.changes.forEach { it.consume() }
            } else if (zoom.scale > 1.01f && fingers == 1) {
                val pan = event.calculatePan()
                zoom.transform(1f, Offset(pan.x, 0f), Offset.Unspecified, size, allowY = false)
            }
        } while (event.changes.any { it.pressed })
    }
}

/** 翻頁模式：雙指縮放；放大後單指拖曳查看細節（此時不會翻頁） */
private suspend fun PointerInputScope.pagedZoomGestures(zoom: ZoomState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2 || (fingers == 1 && zoom.scale > 1.01f)) {
                zoom.transform(
                    event.calculateZoom(),
                    event.calculatePan(),
                    event.calculateCentroid(useCurrent = true),
                    size,
                    allowY = true,
                )
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** 隱藏工具列時進入全螢幕，閱讀時螢幕不自動關閉 */
@Composable
private fun SystemBarsEffect(visible: Boolean) {
    val view = LocalView.current
    DisposableEffect(visible) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLight = ctl?.isAppearanceLightStatusBars
        if (ctl != null) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.isAppearanceLightStatusBars = false
            if (visible) ctl.show(WindowInsetsCompat.Type.systemBars())
            else ctl.hide(WindowInsetsCompat.Type.systemBars())
        }
        view.keepScreenOn = true
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            if (ctl != null && prevLight != null) ctl.isAppearanceLightStatusBars = prevLight
            view.keepScreenOn = false
        }
    }
}
