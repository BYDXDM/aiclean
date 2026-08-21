package com.example.aiclean.di

import android.content.Context
import com.example.aiclean.core.ai.AIService
import com.example.aiclean.core.cleaner.StorageCleaner
import com.example.aiclean.core.root.RootManager
import com.example.aiclean.core.scanner.StorageScanner
import com.example.aiclean.core.settings.SettingsRepository
import com.example.aiclean.core.shizuku.ShizukuManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setPrettyPrinting()
            .create()
    }

    @Provides
    @Singleton
    fun provideStorageScanner(
        @ApplicationContext context: Context
    ): StorageScanner {
        return StorageScanner(context)
    }

    @Provides
    @Singleton
    fun provideStorageCleaner(
        @ApplicationContext context: Context,
        rootManager: RootManager,
        shizukuManager: ShizukuManager
    ): StorageCleaner {
        return StorageCleaner(context, rootManager, shizukuManager)
    }

    @Provides
    @Singleton
    fun provideAIService(
        gson: Gson
    ): AIService {
        return AIService(gson)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideRootManager(
        @ApplicationContext context: Context
    ): RootManager {
        return RootManager(context)
    }

    @Provides
    @Singleton
    fun provideShizukuManager(
        @ApplicationContext context: Context
    ): ShizukuManager {
        return ShizukuManager(context)
    }
}
