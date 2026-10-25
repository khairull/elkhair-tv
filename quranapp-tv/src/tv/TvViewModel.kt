package com.quranapp.android.tv

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quranapp.android.api.models.recitation2.RecitationQuranModel
import com.quranapp.android.api.models.translation.TranslationBookInfoModel
import com.quranapp.android.api.models.mediaplayer.RecitationAudioKind
import com.quranapp.android.components.reader.ChapterVersePair
import com.quranapp.android.compose.components.player.dialogs.AudioEndBehaviour
import com.quranapp.android.compose.utils.preferences.ReaderPreferences
import com.quranapp.android.compose.utils.preferences.RecitationPreferences
import com.quranapp.android.db.DatabaseProvider
import com.quranapp.android.db.relations.SurahWithLocalizations
import com.quranapp.android.utils.mediaplayer.RecitationController
import com.quranapp.android.utils.mediaplayer.RecitationModelManager
import com.quranapp.android.utils.reader.QuranScriptUtils
import com.quranapp.android.utils.reader.factory.QuranTranslationFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.quranapp.android.api.JsonHelper
import com.quranapp.android.api.RetrofitInstance
import com.quranapp.android.utils.managers.TranslationDownloadManager
import com.quranapp.android.utils.managers.ResourceDownloadStatus
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Mode putar di TV. */
enum class TvPlayMode(val label: String, val repeat: Int, val single: Boolean) {
    CONTINUOUS("Putar terus", 1, false),
    SINGLE("Per ayat", 1, true),
    REPEAT3("Ulangi tiap ayat 3×", 3, false),
    REPEAT5("Ulangi tiap ayat 5×", 5, false),
    REPEAT10("Ulangi tiap ayat 10×", 10, false),
}

data class TvSurah(val no: Int, val latin: String, val meaning: String, val arabic: String, val ayahs: Int, val makki: Boolean)
data class TvVerse(val no: Int, val arabic: String, val translation: String, val juz: Int, val words: List<String> = emptyList())
data class TvChapter(val surah: TvSurah, val verses: List<TvVerse>, val translationName: String)

class TvViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val repo = DatabaseProvider.getQuranRepository(app)
    val controller: RecitationController = RecitationController.getInstance(app).also { it.connect() }
    val recitation = controller.state
    val isPlaying = controller.isPlayingState
    /** Hanya berubah saat ayat berganti (bukan tiap kata) agar UI ringan. */
    val currentVerse: StateFlow<ChapterVersePair> = recitation.map { it.currentVerse }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChapterVersePair(-1, -1))
    val wordIndex: StateFlow<Int> = recitation.map { it.currentWordIndex }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)
    val prefs = TvPrefs(app)

    val surahs: StateFlow<List<TvSurah>> = repo.getAllSurahs()
        .map { list -> list.map { it.toTv() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _reciters = MutableStateFlow<List<RecitationQuranModel>>(emptyList())
    val reciters = _reciters.asStateFlow()
    private val _reciterName = MutableStateFlow("")
    val reciterName = _reciterName.asStateFlow()

    private val _translations = MutableStateFlow<List<TranslationBookInfoModel>>(emptyList())
    val translations = _translations.asStateFlow()

    /** Ayat target mode per-ayat: jeda saat ayat berpindah. */
    private var singleTarget: ChapterVersePair? = null

    init {
        refreshReciters()
        refreshTranslations()
        autoIndonesian()
        applyRepeat(); applyEnd()
        viewModelScope.launch {
            currentVerse.collect { cur ->
                val t = singleTarget ?: return@collect
                if (isPlaying.value && cur.chapterNo > 0 && (cur.chapterNo != t.chapterNo || cur.verseNo != t.verseNo)) {
                    singleTarget = null
                    controller.pause()
                }
            }
        }
    }

    fun refreshReciters() = viewModelScope.launch {
        val mgr = RecitationModelManager.get(ctx)
        _reciters.value = mgr.getAllQuranModel()?.reciters.orEmpty()
        _reciterName.value = mgr.getSelectedQuranModel()?.getReciterName().orEmpty()
    }

    fun refreshTranslations() = viewModelScope.launch {
        _translations.value = withContext(Dispatchers.IO) {
            QuranTranslationFactory(ctx).use { it.getAvailableTranslationBooksInfo().values.sortedBy { b -> b.displayName } }
        }
    }

    /** Terjemahan yang bisa diunduh (bahasa Indonesia & Inggris dulu). */
    private val _downloadable = MutableStateFlow<List<TranslationBookInfoModel>>(emptyList())
    val downloadable = _downloadable.asStateFlow()
    private val _downloadMsg = MutableStateFlow("")
    val downloadMsg = _downloadMsg.asStateFlow()

    fun loadDownloadable() = viewModelScope.launch {
        _downloadMsg.value = "Memuat daftar terjemahan…"
        runCatching {
            val data = RetrofitInstance.github.getAvailableTranslations().string()
            val have = _translations.value.map { it.slug }.toSet()
            val tr = JsonHelper.json.parseToJsonElement(data).jsonObject["translations"]!!.jsonObject
            val out = ArrayList<TranslationBookInfoModel>()
            for (lang in listOf("in", "id", "en", "ms", "ar")) {
                val group = tr[lang]?.jsonObject ?: continue
                for ((slug, el) in group) {
                    if (slug in have) continue
                    val o = el.jsonObject
                    out += TranslationBookInfoModel(slug).apply {
                        langCode = lang
                        bookName = o["book"]?.jsonPrimitive?.contentOrNull ?: ""
                        authorName = o["author"]?.jsonPrimitive?.contentOrNull ?: ""
                        displayName = o["displayName"]?.jsonPrimitive?.contentOrNull ?: slug
                        langName = o["langName"]?.jsonPrimitive?.contentOrNull ?: lang
                        version = o["version"]?.jsonPrimitive?.longOrNull ?: 1L
                        lastUpdated = version
                        downloadPath = o["downloadPath"]?.jsonPrimitive?.contentOrNull ?: ""
                    }
                }
            }
            _downloadable.value = out
            _downloadMsg.value = if (out.isEmpty()) "Semua terjemahan sudah terunduh." else ""
        }.onFailure { _downloadMsg.value = "Gagal memuat daftar (cek internet)." }
    }

    /** Sekali saja: unduh terjemahan Kemenag (Bahasa Indonesia) dan jadikan bawaan. */
    private fun autoIndonesian() = viewModelScope.launch {
        if (prefs.autoIdDone) return@launch
        val have = withContext(Dispatchers.IO) { QuranTranslationFactory(ctx).use { it.getAvailableTranslationBooksInfo().values.toList() } }
        have.firstOrNull { it.langCode == "in" || it.langCode == "id" }?.let { prefs.translation = it.slug; prefs.autoIdDone = true; refreshTranslations(); return@launch }
        loadDownloadable().join()
        val ids = _downloadable.value.filter { it.langCode == "in" || it.langCode == "id" }
        val pick = ids.firstOrNull { (it.slug + it.displayName + it.authorName + it.bookName).contains(Regex("in_ministry|kemenag|kementer|ministry|agama", RegexOption.IGNORE_CASE)) } ?: ids.firstOrNull()
        if (pick != null) { prefs.autoIdDone = true; download(pick) }
    }

    fun download(b: TranslationBookInfoModel) {
        if (TranslationDownloadManager.isAnyDownloading()) { _downloadMsg.value = "Tunggu unduhan sebelumnya selesai…"; return }
        _downloadMsg.value = "Mengunduh ${b.displayName}…"
        TranslationDownloadManager.startDownload(ctx, b)
        viewModelScope.launch {
            TranslationDownloadManager.observeDownloadsAsFlow().collect { (slug, st) ->
                if (slug != b.slug) return@collect
                when (st) {
                    is ResourceDownloadStatus.InProgress -> _downloadMsg.value = "Mengunduh ${b.displayName}… ${st.progress}%"
                    ResourceDownloadStatus.Completed -> {
                        _downloadMsg.value = "Selesai: ${b.displayName}"
                        prefs.translation = b.slug
                        refreshTranslations()
                        _downloadable.value = _downloadable.value.filter { it.slug != b.slug }
                    }
                    is ResourceDownloadStatus.Failed, ResourceDownloadStatus.Cancelled -> _downloadMsg.value = "Gagal mengunduh ${b.displayName}"
                    else -> {}
                }
            }
        }
    }

    fun selectReciter(m: RecitationQuranModel) {
        controller.setReciter(m.id, RecitationAudioKind.QURAN)
        _reciterName.value = m.getReciterName()
    }

    fun selectTranslation(slug: String?) = viewModelScope.launch {
        prefs.translation = slug ?: ""
    }

    suspend fun loadChapter(no: Int): TvChapter? = withContext(Dispatchers.IO) {
        val s = repo.getSurahWithLocalizations(no) ?: return@withContext null
        val count = s.surah.ayahCount
        val batch = repo.loadVersesBatch(no, 1, count, QuranScriptUtils.SCRIPT_UTHMANI, true) ?: return@withContext null
        var slug = prefs.translation
        if (slug == null) slug = ReaderPreferences.primaryTranslationSlug()
        var tName = ""
        val tr: List<String> = if (slug.isEmpty()) List(count) { "" } else QuranTranslationFactory(ctx).use { f ->
            tName = f.getAvailableTranslationBooksInfo()[slug]?.displayName.orEmpty()
            f.getTranslationsVerseRange(setOf(slug), no, 1, count).map { l -> l.firstOrNull()?.text?.let(::cleanTranslation).orEmpty() }
        }
        val verses = (1..count).map { v ->
            val words = batch.wordsByVerseNo[v].orEmpty().sortedBy { it.wordIndex }
            TvVerse(v, words.joinToString(" ") { it.text }, tr.getOrElse(v - 1) { "" }, batch.ayahByVerseNo[v]?.juzNo ?: 0, words.map { it.text })
        }
        TvChapter(s.toTv(), verses, tName)
    }

    suspend fun juzStart(juz: Int): ChapterVersePair? = withContext(Dispatchers.IO) {
        repo.getChapterVerseRangesInJuz(juz).firstOrNull()?.let { ChapterVersePair(it.first, it.second.first) }
    }

    /** Putar mulai ayat [verse] dengan mode sekarang. */
    fun play(chapter: Int, verse: Int) {
        val mode = prefs.mode
        applyRepeat()
        applyEnd()
        singleTarget = if (mode.single) ChapterVersePair(chapter, verse) else null
        controller.start(ChapterVersePair(chapter, verse))
        prefs.lastChapter = chapter
        prefs.lastVerse = verse
    }

    /** 0 = berhenti, 1 = lanjut surah berikutnya, 2 = ulangi surah ini. */
    fun applyEnd() {
        val b = when (prefs.endMode) {
            1 -> AudioEndBehaviour.NEXT_CHAPTER
            2 -> AudioEndBehaviour.REPEAT_CHAPTER
            else -> AudioEndBehaviour.STOP_PLAYBACK
        }
        controller.setAudioEndBehaviour(b)
        // Simpan juga ke setelan pemutar agar tetap berlaku bila layanan audio dibuat ulang
        viewModelScope.launch { runCatching { RecitationPreferences.setAudioEndBehaviour(b) } }
    }

    fun applyRepeat() {
        val r = prefs.mode.repeat - 1 // 0 = sekali baca
        controller.setRepeatCount(r)
        viewModelScope.launch { runCatching { RecitationPreferences.setRepeatCount(r) } }
    }

    fun setMode(m: TvPlayMode) { prefs.mode = m; applyRepeat() }

    fun playPause(chapter: Int, verse: Int) {
        applyRepeat(); applyEnd()
        val st = recitation.value
        if (st.currentVerse.chapterNo == chapter && st.currentVerse.verseNo > 0) {
            if (!isPlaying.value && prefs.mode.single) singleTarget = st.currentVerse
            controller.playPause()
        } else play(chapter, verse)
    }

    fun next() { if (prefs.mode.single) singleTarget = null; controller.nextVerse() }
    fun previous() { if (prefs.mode.single) singleTarget = null; controller.previousVerse() }
    fun stop() { singleTarget = null; controller.stop() }

    override fun onCleared() {
        controller.disconnect()
        super.onCleared()
    }

    private fun SurahWithLocalizations.toTv(): TvSurah {
        fun loc(lang: String) = localizations.firstOrNull { it.langCode == lang }
        val id = loc("id"); val en = loc("en")
        return TvSurah(
            surah.surahNo,
            id?.name ?: en?.name ?: getCurrentName(),
            id?.meaning ?: en?.meaning ?: getCurrentMeaning(),
            loc("ar")?.name.orEmpty(),
            surah.ayahCount,
            surah.revelationType.toString().lowercase().contains("mak") || surah.revelationType.toString().lowercase().contains("mec"),
        )
    }

    companion object {
        private val SUP = Regex("<(sup|fn)[^>]*>.*?</(sup|fn)>", RegexOption.IGNORE_CASE)
        private val TAG = Regex("<[^>]*>")
        private val FN = Regex("\\[\\d+]|\\(\\d+\\)$")
        fun cleanTranslation(s: String): String = s.replace(SUP, "").replace(TAG, "").replace(FN, "").replace(Regex("\\s+"), " ").trim()
    }
}

/** Setelan khusus mode TV. */
class TvPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tv_mode", Context.MODE_PRIVATE)
    var mode: TvPlayMode
        get() = runCatching { TvPlayMode.valueOf(sp.getString("mode", "CONTINUOUS")!!) }.getOrDefault(TvPlayMode.CONTINUOUS)
        set(v) = sp.edit().putString("mode", v.name).apply()
    var endMode: Int
        get() = sp.getInt("end_mode", if (sp.getBoolean("next_surah", false)) 1 else 0)
        set(v) = sp.edit().putInt("end_mode", v).apply()
    /** 0 kecil, 1 sedang, 2 besar, 3 sangat besar. */
    var size: Int
        get() = sp.getInt("size", 1)
        set(v) = sp.edit().putInt("size", v).apply()
    var showTranslation: Boolean
        get() = sp.getBoolean("show_tr", true)
        set(v) = sp.edit().putBoolean("show_tr", v).apply()
    /** null = ikuti aplikasi; "" = tanpa terjemahan. */
    var translation: String?
        get() = sp.getString("translation", null)
        set(v) = sp.edit().putString("translation", v).apply()
    var autoIdDone: Boolean
        get() = sp.getBoolean("auto_id", false)
        set(v) = sp.edit().putBoolean("auto_id", v).apply()
    var lastChapter: Int
        get() = sp.getInt("last_ch", 0)
        set(v) = sp.edit().putInt("last_ch", v).apply()
    var lastVerse: Int
        get() = sp.getInt("last_v", 1)
        set(v) = sp.edit().putInt("last_v", v).apply()
}
