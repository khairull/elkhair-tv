package com.quranapp.android.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Mode TV: layar Al-Qur'an yang nyaman dengan remote. */
class TvActivity : ComponentActivity() {
    private val vm: TvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startChapter = intent.getIntExtra("chapter", 0)
        val startVerse = intent.getIntExtra("verse", 1)
        val autoplay = intent.getBooleanExtra("play", false)
        lifecycleScope.launch {
            vm.isPlaying.collect { playing ->
                if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        setContent { TvApp(vm, startChapter, startVerse, autoplay) }
    }

    override fun onDestroy() {
        // Keluar dari aplikasi tanpa audio berjalan: hentikan sesi agar widget pemutar di Beranda kosong.
        if (isFinishing && !vm.isPlaying.value) vm.stop()
        super.onDestroy()
    }
}
