package com.newoether.agora.voice

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import com.newoether.agora.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidVoiceRecognizerTest {
    private fun start(errors: MutableList<Int>, texts: MutableList<String>): Pair<AndroidVoiceRecognizer, ShadowSpeechRecognizer> {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        val recognizer = AndroidVoiceRecognizer(RuntimeEnvironment.getApplication(), {}, texts::add, errors::add)
        recognizer.start("en", onDevice = true)
        shadowOf(Looper.getMainLooper()).idle()
        return recognizer to Shadow.extract(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test fun platformErrorsAndEmptyResultsReachTheUiAndReleaseTheRecognizer() {
        val errors = mutableListOf<Int>()
        val texts = mutableListOf<String>()
        val (_, failed) = start(errors, texts)
        failed.triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf(R.string.voice_typing_no_speech), errors)
        assertTrue(failed.isDestroyed)

        val (_, empty) = start(errors, texts)
        empty.triggerOnResults(Bundle())
        assertEquals(listOf(R.string.voice_typing_no_speech, R.string.voice_typing_no_speech), errors)
        assertTrue(empty.isDestroyed)
        assertTrue(texts.isEmpty())
    }

    @Test fun cancellationRejectsLatePlatformResults() {
        val errors = mutableListOf<Int>()
        val texts = mutableListOf<String>()
        val (recognizer, platform) = start(errors, texts)
        recognizer.cancel()
        platform.triggerOnResults(Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("late result"))
        })
        assertTrue(platform.isDestroyed)
        assertTrue(texts.isEmpty())
        assertTrue(errors.isEmpty())
    }
}
