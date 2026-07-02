package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.GeoRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Provincia INE (capa data; el ViewModel la mapea a FkOption id=codigo/label=nombre). */
data class Provincia(val codigo: String, val nombre: String, val comunidadAutonoma: String)

/** Municipio INE con su provincia. */
data class Municipio(val codigo: String, val nombre: String, val provinciaCodigo: String)

interface GeoRepository {
    /** Las 52 provincias (barato, se carga al abrir el form). Solo lectura. */
    suspend fun cargarProvincias(): GestionResult<List<Provincia>>

    /** Municipios de UNA provincia (bajo demanda al elegirla). Solo lectura. */
    suspend fun cargarMunicipios(provinciaCodigo: String): GestionResult<List<Municipio>>
}

@Singleton
class GeoRepositoryImpl @Inject constructor(
    private val remote: GeoRemoteDataSource,
) : GeoRepository {

    override suspend fun cargarProvincias(): GestionResult<List<Provincia>> =
        runCatching {
            remote.fetchProvincias().map { Provincia(it.codigo, it.nombre, it.comunidadAutonoma) }
        }.fold(
            onSuccess = { GestionResult.Success(it) },
            onFailure = { throwable ->
                val (error, code) = clasificarErrorGestion(throwable)
                Timber.w(throwable, "Cargar provincias falló: %s", code)
                GestionResult.Failure(error, code)
            },
        )

    override suspend fun cargarMunicipios(provinciaCodigo: String): GestionResult<List<Municipio>> =
        runCatching {
            remote.fetchMunicipios(provinciaCodigo)
                .map { Municipio(it.codigo, it.nombre, it.provinciaCodigo) }
        }.fold(
            onSuccess = { GestionResult.Success(it) },
            onFailure = { throwable ->
                val (error, code) = clasificarErrorGestion(throwable)
                Timber.w(throwable, "Cargar municipios falló: %s", code)
                GestionResult.Failure(error, code)
            },
        )
}
