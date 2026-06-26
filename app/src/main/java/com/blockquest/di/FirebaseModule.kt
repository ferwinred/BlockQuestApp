// =====================================================================
// FirebaseModule.kt
// Block Quest — Hilt module: Firebase singletons
// =====================================================================

package com.blockquest.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.blockquest.data.ads.AdConsentManager
import com.blockquest.data.ads.AdMobRewardedAdRepository
import com.blockquest.data.firebase.FirebaseAnalyticsRepository
import com.blockquest.data.firebase.FirebaseCosmeticRepository
import com.blockquest.data.firebase.FirebaseDailyRewardConfigRepository
import com.blockquest.data.firebase.FirebaseLevelRepository
import com.blockquest.data.firebase.FirebaseMissionRepository
import com.blockquest.data.firebase.FirebasePlayerRepository
import com.blockquest.data.firebase.FirebaseProgressionRepository
import com.blockquest.data.firebase.FirebaseRemoteConfigRepository
import com.blockquest.data.firebase.FirebaseWorldRepository
import com.blockquest.data.local.BlockQuestDatabase
import com.blockquest.data.local.LevelCacheDao
import com.blockquest.data.local.MissionProgressDao
import com.blockquest.domain.repository.AdRepository
import com.blockquest.domain.repository.AnalyticsRepository
import com.blockquest.domain.repository.CosmeticRepository
import com.blockquest.domain.repository.DailyRewardConfigRepository
import com.blockquest.domain.repository.LevelRepository
import com.blockquest.domain.repository.MissionRepository
import com.blockquest.domain.repository.PieceRepository
import com.blockquest.domain.repository.PlayerRepository
import com.blockquest.domain.repository.ProgressionRepository
import com.blockquest.domain.repository.RemoteConfigRepository
import com.blockquest.domain.repository.WorldRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // 1. Soluciona el MissingBinding de Context usando @ApplicationContext
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    // 2. Soluciona el MissingBinding de CoroutineDispatcher
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .build()
            )
            .build()
        return firestore
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val config = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)  // 1h
            .build()
        config.setConfigSettingsAsync(settings)
        return config
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDataStoreScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("blockquest_prefs") }
    )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BlockQuestDatabase =
        BlockQuestDatabase.build(context)

    @Provides
    fun provideLevelCacheDao(db: BlockQuestDatabase): LevelCacheDao = db.levelCacheDao()

    @Provides
    fun provideMissionProgressDao(db: BlockQuestDatabase): MissionProgressDao =
        db.missionProgressDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @dagger.Binds
    @Singleton
    abstract fun bindLevelRepository(impl: FirebaseLevelRepository): LevelRepository

    @dagger.Binds
    @Singleton
    abstract fun bindProgressionRepository(impl: FirebaseProgressionRepository): ProgressionRepository

    @dagger.Binds
    @Singleton
    abstract fun bindPlayerRepository(impl: FirebasePlayerRepository): PlayerRepository

    @dagger.Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: FirebaseAnalyticsRepository): AnalyticsRepository

    @dagger.Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(impl: FirebaseRemoteConfigRepository): RemoteConfigRepository

    @dagger.Binds
    @Singleton
    abstract fun bindMissionRepository(impl: FirebaseMissionRepository): MissionRepository

    @dagger.Binds
    @Singleton
    abstract fun bindWorldRepository(impl: FirebaseWorldRepository): WorldRepository

    @dagger.Binds
    @Singleton
    abstract fun bindDailyRewardConfigRepository(impl: FirebaseDailyRewardConfigRepository): DailyRewardConfigRepository

    @dagger.Binds
    @Singleton
    abstract fun bindCosmeticRepository(impl: FirebaseCosmeticRepository): CosmeticRepository

    @dagger.Binds
    @Singleton
    abstract fun bindAdRepository(impl: AdMobRewardedAdRepository): AdRepository

    @dagger.Binds
    @Singleton
    abstract fun bindPieceRepository(impl: com.blockquest.data.local.LocalPieceRepository): PieceRepository

    @dagger.Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: com.blockquest.data.local.LocalSettingsRepository): com.blockquest.domain.repository.SettingsRepository

    @dagger.Binds
    @Singleton
    abstract fun bindLeaderboardRepository(impl: com.blockquest.data.firebase.FirebaseLeaderboardRepository): com.blockquest.domain.repository.LeaderboardRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    @Provides
    @Singleton
    fun provideAdConsentManager(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): AdConsentManager =
        AdConsentManager(context)
}
