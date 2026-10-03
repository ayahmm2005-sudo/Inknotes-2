package com.example.inknotes.recognition

import com.example.inknotes.model.RecognitionLanguage
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** [ModelManager] backed by ML Kit's [RemoteModelManager]. */
@Singleton
class MlKitModelManager @Inject constructor() : ModelManager {

    private val remoteModelManager = RemoteModelManager.getInstance()

    override suspend fun status(language: RecognitionLanguage): ModelStatus {
        val model = modelFor(language) ?: return ModelStatus.FAILED
        return try {
            val downloaded = remoteModelManager.isModelDownloaded(model).awaitResult()
            if (downloaded) ModelStatus.DOWNLOADED else ModelStatus.NOT_DOWNLOADED
        } catch (e: Exception) {
            ModelStatus.FAILED
        }
    }

    override fun download(language: RecognitionLanguage): Flow<ModelStatus> = flow {
        val model = modelFor(language)
        if (model == null) {
            emit(ModelStatus.FAILED)
            return@flow
        }
        emit(ModelStatus.DOWNLOADING)
        try {
            remoteModelManager.download(model, DownloadConditions.Builder().build()).awaitResult()
            emit(ModelStatus.DOWNLOADED)
        } catch (e: Exception) {
            emit(ModelStatus.FAILED)
        }
    }

    override suspend fun delete(language: RecognitionLanguage): Boolean {
        val model = modelFor(language) ?: return false
        return try {
            remoteModelManager.deleteDownloadedModel(model).awaitResult()
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /** Null for a language with no fixed ML Kit tag (e.g. [RecognitionLanguage.AUTO]). */
        fun modelFor(language: RecognitionLanguage): DigitalInkRecognitionModel? {
            val tag = language.tag ?: return null
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) ?: return null
            return DigitalInkRecognitionModel.builder(identifier).build()
        }
    }
}
