package com.example.inknotes.di

import com.example.inknotes.recognition.HandwritingRecognizer
import com.example.inknotes.recognition.MlKitInkRecognizer
import com.example.inknotes.recognition.MlKitModelManager
import com.example.inknotes.recognition.ModelManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Milestone 5 recognition foundation to its ML Kit implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecognitionModule {

    @Binds
    @Singleton
    abstract fun bindHandwritingRecognizer(impl: MlKitInkRecognizer): HandwritingRecognizer

    @Binds
    @Singleton
    abstract fun bindModelManager(impl: MlKitModelManager): ModelManager
}
