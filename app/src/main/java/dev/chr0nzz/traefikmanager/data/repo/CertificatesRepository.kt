package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.CertsResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertificatesRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {
    suspend fun load(): CertsResponse = apiProvider.api().certs()
}
