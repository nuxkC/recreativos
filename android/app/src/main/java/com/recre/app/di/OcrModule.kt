package com.recre.app.di

import com.recre.app.core.ocr.ContadorOcrRecognizer
import com.recre.app.core.ocr.MlKitContadorOcrRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Vincula el reconocedor OCR de contadores (T-100) a su implementación
 * on-device con ML Kit. Separado de [RepositoryModule] para mantener el
 * subsistema de OCR desacoplado.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindContadorOcrRecognizer(
        impl: MlKitContadorOcrRecognizer,
    ): ContadorOcrRecognizer
}
