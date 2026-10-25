package dev.chr0nzz.traefikmanager.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chr0nzz.traefikmanager.data.store.CredentialCipher
import dev.chr0nzz.traefikmanager.data.store.CryptoManager
import dev.chr0nzz.traefikmanager.data.store.LegacySecureStore
import dev.chr0nzz.traefikmanager.data.store.LegacyStoreReader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StoreModule {

    @Binds
    @Singleton
    abstract fun bindLegacyStoreReader(impl: LegacySecureStore): LegacyStoreReader

    @Binds
    @Singleton
    abstract fun bindCredentialCipher(impl: CryptoManager): CredentialCipher
}
