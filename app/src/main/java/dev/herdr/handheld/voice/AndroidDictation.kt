package dev.herdr.handheld.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Audio stays with Android's selected recognition provider; no recordings or transcripts are logged. */
class AndroidDictation(private val context: Context,private val update: (Long,VoicePhase,String,String?,Float?)->Unit) {
    private var recognizer: SpeechRecognizer?=null
    private var current=0L
    private val handler=Handler(Looper.getMainLooper())
    private var timeout: Runnable?=null
    fun available()=SpeechRecognizer.isRecognitionAvailable(context)
    fun start(id: Long,language: String) {
        cancel();current=id
        if(!available()) { update(id,VoicePhase.ERROR,"No speech provider is available. Use the keyboard.",null,null);return }
        try {
            val speech=SpeechRecognizer.createSpeechRecognizer(context);recognizer=speech
            fun finish(phase: VoicePhase,message: String,text: String?=null) {
                if(current!=id)return
                update(id,phase,message,text,null);cancel()
            }
            speech.setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if(current==id)update(id,VoicePhase.LISTENING,"Speak now. Release Y or choose Finish.",null,null) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { if(current==id)update(id,VoicePhase.LISTENING,"",null,(rmsdB/10f).coerceIn(0f,1f)) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if(current==id)update(id,VoicePhase.TRANSCRIBING,"Finishing transcript…",null,null) }
                override fun onError(error: Int) { finish(VoicePhase.ERROR,when(error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"Microphone permission is required. Try again or use the keyboard."
                    SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->"Speech provider could not connect. Retry or use the keyboard."
                    SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->"No speech recognized. Hold Y and try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY->"Microphone is busy. Try again."
                    else->"Speech recognition failed (code $error). Use the keyboard or retry."
                }) }
                override fun onResults(results: Bundle?) {
                    val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    finish(if(text.isBlank())VoicePhase.ERROR else VoicePhase.REVIEW,if(text.isBlank())"No speech recognized."else "Review the transcript. Nothing has been sent.",text)
                }
                override fun onPartialResults(partialResults: Bundle?) { if(current==id)update(id,VoicePhase.LISTENING,"",partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(),null) }
                override fun onEvent(eventType: Int,params: Bundle?) {}
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,language.ifBlank { Locale.getDefault().toLanguageTag() })
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            })
            timeout=Runnable { if(current==id)finish(VoicePhase.ERROR,"Recording timed out. Hold Y to try again.") }.also { handler.postDelayed(it,45000) }
        } catch(_: Exception) { update(id,VoicePhase.ERROR,"Speech provider could not start. Use the keyboard.",null,null);cancel() }
    }
    fun stop() { runCatching { recognizer?.stopListening() } }
    fun cancel() { current=0;timeout?.let(handler::removeCallbacks);timeout=null;runCatching { recognizer?.cancel();recognizer?.destroy() };recognizer=null }
}
