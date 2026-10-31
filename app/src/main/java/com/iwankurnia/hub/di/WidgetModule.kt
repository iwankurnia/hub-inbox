package com.iwankurnia.hub.di

import com.iwankurnia.hub.data.NoOpWidgetRefresher
import com.iwankurnia.hub.data.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetRefresher(impl: NoOpWidgetRefresher): WidgetRefresher
}
