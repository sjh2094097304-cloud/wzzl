package com.autumn.s44tool

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MusicEngine(
    private val context: Context,
    private val controller: AppController
) {
    private var player: MediaPlayer? = null
    private var customMode = false

    private val tracks = listOf(
        Track(R.raw.music_witch_adventure, "魔女大冒险"),
        Track(R.raw.music_rainy_day, "那天下雨了"),
        Track(R.raw.music_antique_shop, "伊波恩古董店")
    )

    var currentIndex by mutableIntStateOf(0)
        private set
    var currentName by mutableStateOf(tracks.first().name)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var prepared by mutableStateOf(false)
        private set

    fun prepareDefault(playNow: Boolean) {
        val custom = controller.customMusicUri
        if (!custom.isNullOrBlank()) {
            runCatching { prepareCustom(Uri.parse(custom), playNow) }
                .onFailure { prepareBuiltIn(0, playNow) }
        } else {
            prepareBuiltIn(0, playNow)
        }
    }

    fun prepareBuiltIn(index: Int, playNow: Boolean) {
        releasePlayer()
        customMode = false
        currentIndex = index.coerceIn(0, tracks.lastIndex)
        currentName = tracks[currentIndex].name
        val mp = MediaPlayer.create(context, tracks[currentIndex].resId)
        player = mp
        prepared = mp != null
        mp?.setVolume(controller.musicVolume, controller.musicVolume)
        mp?.isLooping = false
        mp?.setOnCompletionListener {
            if (currentIndex < tracks.lastIndex) {
                prepareBuiltIn(currentIndex + 1, true)
            } else if (controller.musicLoop) {
                prepareBuiltIn(0, true)
            } else {
                isPlaying = false
                it.seekTo(0)
            }
        }
        if (playNow) play()
    }

    fun prepareCustom(uri: Uri, playNow: Boolean) {
        releasePlayer()
        customMode = true
        currentName = "自定义音乐"
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        mp.setDataSource(context, uri)
        mp.setOnPreparedListener {
            prepared = true
            it.setVolume(controller.musicVolume, controller.musicVolume)
            it.isLooping = controller.musicLoop
            if (playNow) {
                it.start()
                isPlaying = true
            }
        }
        mp.setOnCompletionListener {
            if (!customMode) return@setOnCompletionListener
            if (controller.musicLoop) {
                it.seekTo(0)
                it.start()
                isPlaying = true
            } else {
                isPlaying = false
            }
        }
        player = mp
        prepared = false
        mp.prepareAsync()
    }

    fun play() {
        val mp = player
        if (mp == null || !prepared) {
            prepareDefault(true)
            return
        }
        runCatching {
            mp.start()
            isPlaying = true
        }
    }

    fun pause() {
        runCatching { player?.pause() }
        isPlaying = false
    }

    fun toggle() {
        if (isPlaying) pause() else play()
    }

    fun next() {
        if (customMode) {
            prepareBuiltIn(0, true)
            return
        }
        val next = if (currentIndex >= tracks.lastIndex) 0 else currentIndex + 1
        prepareBuiltIn(next, true)
    }

    fun previous() {
        if (customMode) {
            prepareBuiltIn(tracks.lastIndex, true)
            return
        }
        val previous = if (currentIndex <= 0) tracks.lastIndex else currentIndex - 1
        prepareBuiltIn(previous, true)
    }

    fun setLoop(loop: Boolean) {
        if (customMode) player?.isLooping = loop
    }

    fun setVolume(volume: Float) {
        player?.setVolume(volume, volume)
    }

    fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
        prepared = false
        isPlaying = false
    }

    private data class Track(val resId: Int, val name: String)
}
