package com.example.inknotes.recognition

import com.example.inknotes.model.RecognitionLanguage
import com.example.inknotes.model.Stroke
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit-backed [HandwritingRecognizer].
 *
 * Strokes are converted to ML Kit's vector [com.google.mlkit.vision.digitalink.Ink] via
 * [toInk] — nothing is rasterized. Recognition always runs on [dispatcher] (off the caller's
 * thread), so it never blocks handwriting input, and this class never touches the canvas.
 */
@Singleton
class MlKitInkRecognizer @Inject constructor(
    private val modelManager: ModelManager,
) : HandwritingRecognizer {

    private val dispatcher: CoroutineDispatcher = Dispatchers.Default

    // One recognizer per language, built lazily and kept until close(): each wraps a loaded
    // on-device model, so rebuilding it on every call would be wasteful.
    private val recognizers = HashMap<RecognitionLanguage, DigitalInkRecognizer>()

    override suspend fun recognize(strokes: List<Stroke>, language: RecognitionLanguage): RecognitionOutcome =
        withContext(dispatcher) {
            if (strokes.isEmpty()) {
                return@withContext RecognitionOutcome.Failure(RecognitionFailureReason.NO_STROKES)
            }

            val model = MlKitModelManager.modelFor(language)
                ?: return@withContext RecognitionOutcome.Failure(RecognitionFailureReason.UNSUPPORTED_LANGUAGE)

            if (modelManager.status(language) != ModelStatus.DOWNLOADED) {
                return@withContext RecognitionOutcome.Failure(RecognitionFailureReason.MODEL_NOT_DOWNLOADED)
            }

            try {
                val recognizer = recognizerFor(language, model)
                val result = recognizer.recognize(strokes.toInk()).awaitResult()
                val candidates = result.candidates.map { it.text }
                if (candidates.isEmpty()) {
                    RecognitionOutcome.Failure(RecognitionFailureReason.RECOGNIZER_ERROR)
                } else {
                    RecognitionOutcome.Success(candidates)
                }
            } catch (e: Exception) {
                RecognitionOutcome.Failure(RecognitionFailureReason.RECOGNIZER_ERROR)
            }
        }

    override fun close(language: RecognitionLanguage) {
        recognizers.remove(language)?.close()
    }

    private fun recognizerFor(language: RecognitionLanguage, model: DigitalInkRecognitionModel): DigitalInkRecognizer =
        recognizers.getOrPut(language) {
            DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        }
}
