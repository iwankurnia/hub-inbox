package com.iwankurnia.hub.di

import android.content.Context
import androidx.room.Room
import com.iwankurnia.hub.capture.LiveActions
import com.iwankurnia.hub.capture.LiveActionsRegistry
import com.iwankurnia.hub.data.HubDao
import com.iwankurnia.hub.data.HubDatabase
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.RoomHubRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindHubRepository(impl: RoomHubRepository): HubRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext ctx: Context): HubDatabase =
            Room.databaseBuilder(ctx, HubDatabase::class.java, "hub.db")
                // Hub is sideloaded and keeps nothing worth migrating: a schema change starts afresh.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        @Provides
        fun provideHubDao(db: HubDatabase): HubDao = db.hubDao()

        @Provides
        fun provideLiveActions(registry: LiveActionsRegistry): LiveActions = registry
    }
}
