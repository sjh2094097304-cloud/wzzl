package com.autumn.s44tool

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import android.os.Bundle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val controller = remember { AppController(applicationContext) }
            val music = remember { MusicEngine(applicationContext, controller) }
            val feedback = remember { UiFeedback(applicationContext, controller) }

            DisposableEffect(Unit) {
                onDispose {
                    music.release()
                    feedback.release()
                }
            }

            S44NativeApp(
                controller = controller,
                music = music,
                feedback = feedback
            )
        }
    }
}

class UiFeedback(
    private val context: Context,
    private val controller: AppController
) {
    private val tone = ToneGenerator(AudioManager.STREAM_SYSTEM, 30)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun tap(confirm:Boolean=false) {
        if (controller.haptic) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(if(confirm) 16 else 9, 45))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if(confirm) 16 else 9)
                }
            }
        }
        if (controller.uiSound) {
            runCatching {
                tone.startTone(
                    if(confirm) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP2,
                    if(confirm) 48 else 32
                )
            }
        }
    }

    fun release() {
        runCatching { tone.release() }
    }
}
