package com.quranapp.android.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranapp.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Tema ----------
private val Bg = Color(0xFF07130F)
private val Bg2 = Color(0xFF0D2219)
private val Card = Color(0xFF12301F)
private val Gold = Color(0xFFE6C068)
private val Ink = Color(0xFFF3EEDC)
private val Dim = Color(0xFF9DB3A6)
private val Play = Color(0xFF1F5A3C)
private val QuranFont = FontFamily(Font(R.font.uthmanic_hafs))

private val ARABIC_SIZES = listOf(34, 42, 52, 64)
private val TRANS_SIZES = listOf(19, 22, 26, 30)
private val SIZE_LABEL = listOf("Kecil", "Sedang", "Besar", "Sangat besar")
private val END_LABEL = listOf("Berhenti", "Lanjut ke surah berikutnya", "Ulangi surah ini terus")
private val END_SHORT = listOf("Berhenti", "Lanjut surah", "Ulangi surah")

private sealed class Screen {
    object Home : Screen()
    data class Reader(val chapter: Int, val verse: Int, val autoplay: Boolean) : Screen()
}

@Composable
fun TvApp(vm: TvViewModel, startChapter: Int, startVerse: Int, autoplay: Boolean) {
    var screen by remember {
        mutableStateOf<Screen>(if (startChapter in 1..114) Screen.Reader(startChapter, startVerse, autoplay) else Screen.Home)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg2, Bg)))) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(vm) { ch, v, play -> screen = Screen.Reader(ch, v, play) }
            is Screen.Reader -> key(s) {
                ReaderScreen(vm, s.chapter, s.verse, s.autoplay,
                    onBack = { screen = Screen.Home },
                    onOpen = { ch, v, play -> screen = Screen.Reader(ch, v, play) })
            }
        }
    }
}

// ---------- Komponen dasar ----------
@Composable
private fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(28.dp))
            .background(if (focused) Gold else if (selected) Play else Card)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (focused) Bg else Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
}

// ---------- Beranda ----------
@Composable
private fun HomeScreen(vm: TvViewModel, open: (Int, Int, Boolean) -> Unit) {
    val surahs by vm.surahs.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    val lastCh = vm.prefs.lastChapter
    val lastV = vm.prefs.lastVerse

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Al-Qur'an", color = Gold, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("Pilih surah dengan tombol arah, tekan OK untuk membaca", color = Dim, fontSize = 18.sp)
            }
            Clock()
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (lastCh in 1..114) {
                val name = surahs.getOrNull(lastCh - 1)?.latin ?: "Surah $lastCh"
                TvButton("▶  Lanjutkan: $name : $lastV", selected = true) { open(lastCh, lastV, false) }
            }
            TvButton("Surah", selected = tab == 0, focusRequester = first) { tab = 0 }
            TvButton("Juz", selected = tab == 1) { tab = 1 }
            TvButton("Qari", selected = false) { dialog = "reciter" }
            TvButton("Terjemahan") { dialog = "translation" }
        }
        Spacer(Modifier.height(18.dp))
        if (tab == 0) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 6.dp, start = 6.dp, end = 6.dp),
            ) {
                itemsIndexed(surahs, key = { _, s -> s.no }) { _, s -> SurahCard(s) { open(s.no, 1, false) } }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 6.dp, start = 6.dp, end = 6.dp),
            ) {
                itemsIndexed((1..30).toList()) { _, j ->
                    FocusCard(Modifier.height(110.dp), onClick = {
                        scope.launch { vm.juzStart(j)?.let { open(it.chapterNo, it.verseNo, false) } }
                    }) { f ->
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Juz", color = if (f) Bg else Dim, fontSize = 18.sp)
                            Text("$j", color = if (f) Bg else Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    when (dialog) {
        "reciter" -> ReciterDialog(vm) { dialog = null }
        "translation" -> TranslationDialog(vm) { dialog = null }
    }
}

@Composable
private fun FocusCard(modifier: Modifier, onClick: () -> Unit, content: @Composable (Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val sc by animateFloatAsState(if (focused) 1.06f else 1f, label = "s")
    Box(
        modifier
            .scale(sc)
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) Gold else Card)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) { content(focused) }
}

@Composable
private fun SurahCard(s: TvSurah, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().height(104.dp), onClick) { f ->
        val main = if (f) Bg else Ink
        val sub = if (f) Bg.copy(alpha = .75f) else Dim
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).border(BorderStroke(2.dp, if (f) Bg else Gold), CircleShape), contentAlignment = Alignment.Center) {
                Text("${s.no}", color = main, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.latin, color = main, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.meaning, color = sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.ayahs} ayat · ${if (s.makki) "Makkiyah" else "Madaniyah"}", color = sub, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(s.arabic, color = if (f) Bg else Gold, fontSize = 26.sp, fontFamily = QuranFont, maxLines = 1, softWrap = false)
        }
    }
}

// ---------- Layar baca ----------
@Composable
private fun ReaderScreen(
    vm: TvViewModel, chapter: Int, startVerse: Int, autoplay: Boolean,
    onBack: () -> Unit, onOpen: (Int, Int, Boolean) -> Unit,
) {
    var data by remember { mutableStateOf<TvChapter?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(chapter, reload) { data = vm.loadChapter(chapter) }
    val cur by vm.currentVerse.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    var size by remember { mutableIntStateOf(vm.prefs.size) }
    var showTr by remember { mutableStateOf(vm.prefs.showTranslation) }
    var mode by remember { mutableStateOf(vm.prefs.mode) }
    var endMode by remember { mutableIntStateOf(vm.prefs.endMode) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var murottal by remember { mutableStateOf(false) }
    var focusedVerse by remember { mutableIntStateOf(startVerse) }
    var digits by remember { mutableStateOf("") }
    var sleepAt by remember { mutableLongStateOf(0L) }
    val list = rememberLazyListState()
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    val scope = rememberCoroutineScope()
    val reciterName by vm.reciterName.collectAsState()

    val playingHere = cur.chapterNo == chapter
    val activeVerse = if (playingHere) cur.verseNo else -1

    fun focusVerse(v: Int) {
        val d = data ?: return
        val idx = (v.coerceIn(1, d.verses.size)) - 1
        scope.launch {
            list.scrollToItem(idx + 1, -90)
            delay(60)
            runCatching { requesters[idx + 1]?.requestFocus() }
        }
    }

    // Mulai: fokus ke ayat awal, autoplay bila diminta
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        focusVerse(startVerse)
        if (autoplay) vm.play(chapter, startVerse)
    }
    // Ikuti ayat yang sedang dibaca
    LaunchedEffect(activeVerse, playing) {
        if (playing && activeVerse > 0 && !murottal) focusVerse(activeVerse)
    }
    // Pindah surah otomatis saat audio lanjut ke surah berikutnya
    var audioWasHere by remember { mutableStateOf(false) }
    LaunchedEffect(cur.chapterNo) {
        val c = cur.chapterNo
        if (c == chapter) audioWasHere = true
        else if (audioWasHere && c in 1..114) onOpen(c, cur.verseNo.coerceAtLeast(1), false)
    }
    // Kembali dari murottal: fokus ke ayat terakhir
    LaunchedEffect(murottal) { if (!murottal && data != null) focusVerse(activeVerse.takeIf { it > 0 } ?: focusedVerse) }
    // Timer tidur
    LaunchedEffect(sleepAt) {
        if (sleepAt > 0) { delay((sleepAt - System.currentTimeMillis()).coerceAtLeast(0)); vm.stop(); sleepAt = 0 }
    }
    // Lompat nomor ayat via tombol angka
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1300)
        digits.toIntOrNull()?.let { focusVerse(it) }
        digits = ""
    }

    BackHandler {
        when {
            dialog != null -> dialog = null
            murottal -> murottal = false
            else -> onBack()
        }
    }

    val d = data
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || dialog != null) return@onPreviewKeyEvent false
            val n = e.nativeKeyEvent.keyCode
            when (n) {
                in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                    digits = (digits + (n - android.view.KeyEvent.KEYCODE_0)).takeLast(3); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.playPause(chapter, focusedVerse); true }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT, android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.next(); true }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.previous(); true }
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> { vm.stop(); true }
                android.view.KeyEvent.KEYCODE_MENU -> { dialog = "menu"; true }
                android.view.KeyEvent.KEYCODE_CHANNEL_UP, android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                    if (chapter < 114) onOpen(chapter + 1, 1, false); true
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN, android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                    if (chapter > 1) onOpen(chapter - 1, 1, false); true
                }
                else -> false
            }
        }
    ) {
        if (d != null && murottal) {
            MurottalView(vm, d, activeVerse.takeIf { it > 0 } ?: focusedVerse, playing, size, showTr)
        } else if (d == null) {
            Text("Memuat…", color = Dim, fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 20.dp, bottom = 260.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "head") {
                    ReaderHeader(
                        d, playing, playingHere, mode, endMode, size, showTr, reciterName, sleepAt,
                        requesters.getOrPut(0) { FocusRequester() },
                        onPlay = { vm.playPause(chapter, focusedVerse) },
                        onPlayAll = { vm.play(chapter, 1) },
                        onMenu = { dialog = it },
                        onMurottal = { murottal = true; if (!playing) vm.playPause(chapter, focusedVerse) },
                        onBack = onBack,
                    )
                }
                if (chapter != 1 && chapter != 9) item(key = "bismillah") {
                    Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", color = Gold, fontFamily = QuranFont,
                        fontSize = (ARABIC_SIZES[size] - 4).sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
                itemsIndexed(d.verses, key = { _, v -> v.no }) { i, v ->
                    val fr = requesters.getOrPut(i + 1) { FocusRequester() }
                    VerseItem(
                        v, size, showTr, active = v.no == activeVerse, playing = playing, fr = fr,
                        onFocused = { focusedVerse = v.no },
                        onClick = {
                            if (v.no == activeVerse) vm.playPause(chapter, v.no) else vm.play(chapter, v.no)
                        },
                        onLeft = { if (chapter > 1) onOpen(chapter - 1, 1, false) },
                        onRight = { if (chapter < 114) onOpen(chapter + 1, 1, false) },
                    )
                }
                item(key = "end") {
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center) {
                        if (chapter < 114) TvButton("Surah berikutnya ▸") { onOpen(chapter + 1, 1, false) }
                    }
                }
            }
            // Bilah status bawah
            StatusBar(d, activeVerse, playing, mode, Modifier.align(Alignment.BottomCenter))
        }
        if (digits.isNotEmpty()) {
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Bg.copy(alpha = .92f)).border(2.dp, Gold, RoundedCornerShape(20.dp)).padding(horizontal = 40.dp, vertical = 24.dp)) {
                Text("Ke ayat $digits", color = Gold, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    when (dialog) {
        "mode" -> PickerDialog("Cara membaca ayat", TvPlayMode.entries.map { it.label },
            selected = TvPlayMode.entries.indexOf(mode), onDismiss = { dialog = null }) { i ->
            mode = TvPlayMode.entries[i]; vm.setMode(mode); dialog = null
        }
        "end" -> PickerDialog("Setelah surah selesai", END_LABEL, selected = endMode, onDismiss = { dialog = null }) { i ->
            endMode = i; vm.prefs.endMode = i; vm.applyEnd(); dialog = null
        }
        "size" -> PickerDialog("Ukuran huruf", SIZE_LABEL, selected = size, onDismiss = { dialog = null }) {
            size = it; vm.prefs.size = it; dialog = null; focusVerse(focusedVerse)
        }
        "sleep" -> PickerDialog("Timer tidur", listOf("Mati", "15 menit", "30 menit", "45 menit", "60 menit", "90 menit"),
            selected = -1, onDismiss = { dialog = null }) { i ->
            sleepAt = if (i == 0) 0 else System.currentTimeMillis() + listOf(0, 15, 30, 45, 60, 90)[i] * 60_000L
            dialog = null
        }
        "reciter" -> ReciterDialog(vm) { dialog = null }
        "translation" -> TranslationDialog(vm) {
            dialog = null; showTr = true; vm.prefs.showTranslation = true; reload++
        }
        "tr_toggle" -> { showTr = !showTr; vm.prefs.showTranslation = showTr; dialog = null }
        "menu" -> PickerDialog("Menu", listOf("Mode putar", "Setelah surah selesai", "Ukuran huruf", "Terjemahan tampil/sembunyi", "Pilih terjemahan", "Pilih qari", "Timer tidur", "Hentikan audio", "Layar murottal"),
            selected = -1, onDismiss = { dialog = null }) { i ->
            dialog = listOf("mode", "end", "size", "tr_toggle", "translation", "reciter", "sleep", null, null)[i]
            if (i == 7) vm.stop()
            if (i == 8) murottal = true
        }
    }
}

@Composable
private fun ReaderHeader(
    d: TvChapter, playing: Boolean, playingHere: Boolean, mode: TvPlayMode, endMode: Int, size: Int, showTr: Boolean,
    reciter: String, sleepAt: Long, fr: FocusRequester,
    onPlay: () -> Unit, onPlayAll: () -> Unit, onMenu: (String) -> Unit, onMurottal: () -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${d.surah.no}. ${d.surah.latin}", color = Gold, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("${d.surah.meaning} · ${d.surah.ayahs} ayat · ${if (d.surah.makki) "Makkiyah" else "Madaniyah"}" +
                        (if (reciter.isNotEmpty()) " · Qari: $reciter" else ""), color = Dim, fontSize = 18.sp)
            }
            Text(d.surah.arabic, color = Gold, fontSize = 44.sp, fontFamily = QuranFont)
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
            item { TvButton("◂ Beranda", onClick = onBack) }
            item { TvButton(if (playing && playingHere) "❚❚ Jeda" else "▶ Putar", selected = true, focusRequester = fr, onClick = onPlay) }
            item { TvButton("▶ Dari awal", onClick = onPlayAll) }
            item { TvButton("Mode: ${mode.label}") { onMenu("mode") } }
            item { TvButton("Selesai: ${END_SHORT[endMode]}") { onMenu("end") } }
            item { TvButton("Huruf: ${SIZE_LABEL[size]}") { onMenu("size") } }
            item { TvButton(if (showTr) "Terjemahan: Tampil" else "Terjemahan: Sembunyi") { onMenu("tr_toggle") } }
            item { TvButton("Pilih terjemahan") { onMenu("translation") } }
            item { TvButton("Qari") { onMenu("reciter") } }
            item { TvButton(if (sleepAt > 0) "Timer: ${((sleepAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0)} mnt" else "Timer tidur") { onMenu("sleep") } }
            item { TvButton("Layar murottal", onClick = onMurottal) }
        }
        if (showTr && d.translationName.isNotEmpty())
            Text("Terjemahan: ${d.translationName}", color = Dim, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    }
}

@Composable
private fun VerseItem(
    v: TvVerse, size: Int, showTr: Boolean, active: Boolean, playing: Boolean, fr: FocusRequester,
    onFocused: () -> Unit, onClick: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border = when { focused -> Gold; active -> Gold.copy(alpha = .45f); else -> Color.Transparent }
    val bg = when { active -> Play.copy(alpha = .55f); focused -> Card; else -> Card.copy(alpha = .35f) }
    Column(
        Modifier
            .fillMaxWidth()
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onLeft(); true }
                    Key.DirectionRight -> { onRight(); true }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(3.dp, border, RoundedCornerShape(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (active) Gold else Bg2), contentAlignment = Alignment.Center) {
                Text("${v.no}", color = if (active) Bg else Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            if (active) Text(if (playing) "  ♪ sedang dibaca" else "  ❚❚ dijeda di sini", color = Gold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (focused) Text("OK = putar ayat ini", color = Dim, fontSize = 15.sp)
        }
        Text(
            v.arabic,
            style = TextStyle(
                fontFamily = QuranFont, fontSize = ARABIC_SIZES[size].sp, lineHeight = (ARABIC_SIZES[size] * 1.9f).sp,
                color = if (active) Color.White else Ink, textAlign = TextAlign.Right, textDirection = TextDirection.Rtl,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (showTr && v.translation.isNotEmpty()) {
            Text(
                v.translation, color = if (active) Ink else Dim, fontSize = TRANS_SIZES[size].sp,
                lineHeight = (TRANS_SIZES[size] * 1.45f).sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun StatusBar(d: TvChapter, active: Int, playing: Boolean, mode: TvPlayMode, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = .95f))))
            .padding(horizontal = 64.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (active > 0) "${if (playing) "♪" else "❚❚"}  ${d.surah.latin} : $active / ${d.surah.ayahs}" else "${d.surah.latin} · ${d.surah.ayahs} ayat",
            color = Ink, fontSize = 18.sp, modifier = Modifier.weight(1f),
        )
        Text("▲▼ ayat · OK putar · ◂▸ surah · angka = lompat ayat · ⏯ jeda", color = Dim, fontSize = 15.sp)
    }
}

@Composable
private fun MurottalView(vm: TvViewModel, d: TvChapter, verse: Int, playing: Boolean, size: Int, showTr: Boolean) {
    val v = d.verses.getOrNull(verse - 1) ?: return
    val word by vm.wordIndex.collectAsState()
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }
    val fs = ARABIC_SIZES[size] + 14
    // Sorot kata yang sedang dibaca (bila qari punya data waktu per kata)
    val text = remember(v, word) {
        androidx.compose.ui.text.buildAnnotatedString {
            v.words.forEachIndexed { i, w ->
                if (i > 0) append(" ")
                if (i == word) withStyle(androidx.compose.ui.text.SpanStyle(color = Gold)) { append(w) } else append(w)
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Bg2, Color.Black)))
            .focusRequester(fr).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter -> { vm.playPause(d.surah.no, verse); true }
                    Key.DirectionRight, Key.DirectionDown -> { vm.next(); true }
                    Key.DirectionLeft, Key.DirectionUp -> { vm.previous(); true }
                    else -> false
                }
            }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 90.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.surah.latin} · ayat ${v.no} dari ${d.surah.ayahs}", color = Dim, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            Text(text, style = TextStyle(fontFamily = QuranFont, fontSize = fs.sp, lineHeight = (fs * 1.9f).sp,
                color = Color.White, textAlign = TextAlign.Center, textDirection = TextDirection.Rtl),
                maxLines = 7, overflow = TextOverflow.Ellipsis)
            if (showTr && v.translation.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(v.translation, color = Ink, fontSize = (TRANS_SIZES[size] + 2).sp, textAlign = TextAlign.Center,
                    lineHeight = ((TRANS_SIZES[size] + 2) * 1.45f).sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Text(if (playing) "♪ Sedang diputar — OK jeda · ◂▸ ayat · BACK kembali" else "Dijeda — OK putar · BACK kembali", color = Gold, fontSize = 18.sp)
        }
    }
}

// ---------- Dialog ----------
@Composable
private fun PickerDialog(title: String, items: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(min = 460.dp, max = 760.dp).heightIn(max = 620.dp).clip(RoundedCornerShape(24.dp)).background(Bg2)
                .border(2.dp, Gold.copy(alpha = .5f), RoundedCornerShape(24.dp)).padding(26.dp)
        ) {
            Text(title, color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items) { i, t ->
                    TvButton((if (i == selected) "✓ " else "") + t, Modifier.fillMaxWidth(), selected = i == selected,
                        focusRequester = if (i == maxOf(selected, 0)) first else null) { onPick(i) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
}

@Composable
private fun ReciterDialog(vm: TvViewModel, onDismiss: () -> Unit) {
    val list by vm.reciters.collectAsState()
    val cur by vm.reciterName.collectAsState()
    LaunchedEffect(Unit) { vm.refreshReciters() }
    if (list.isEmpty()) {
        PickerDialog("Qari", listOf("Daftar qari belum tersedia (butuh internet)"), -1, onDismiss) { onDismiss() }
        return
    }
    val labels = list.map { r -> r.getReciterName() + (r.getStyleName()?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") }
    PickerDialog("Pilih qari", labels, list.indexOfFirst { it.getReciterName() == cur }, onDismiss) { i ->
        vm.selectReciter(list[i]); onDismiss()
    }
}

@Composable
private fun TranslationDialog(vm: TvViewModel, onDismiss: () -> Unit) {
    val have by vm.translations.collectAsState()
    val dl by vm.downloadable.collectAsState()
    val msg by vm.downloadMsg.collectAsState()
    LaunchedEffect(Unit) { vm.refreshTranslations(); vm.loadDownloadable() }
    val cur = vm.prefs.translation
    val labels = listOf("Tanpa terjemahan") + have.map { it.displayName } +
            dl.map { "⬇ Unduh: ${it.displayName} (${it.langName})" } + (if (msg.isNotEmpty()) listOf("ℹ $msg") else emptyList())
    val sel = if (cur == "") 0 else have.indexOfFirst { it.slug == cur }.let { if (it >= 0) it + 1 else -1 }
    PickerDialog("Terjemahan", labels, sel, onDismiss) { i ->
        when {
            i == 0 -> { vm.selectTranslation(""); onDismiss() }
            i <= have.size -> { vm.selectTranslation(have[i - 1].slug); onDismiss() }
            i <= have.size + dl.size -> vm.download(dl[i - 1 - have.size])
            else -> {}
        }
    }
}
