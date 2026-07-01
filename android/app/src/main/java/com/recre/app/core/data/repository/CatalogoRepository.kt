package com.recre.app.core.data.repository

import com.recre.app.core.data.remote.CatalogoRemoteDataSource
import com.recre.app.core.data.remote.clasificarErrorGestion
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Fabricante del catálogo (capa data; el ViewModel lo mapea a FkOption). */
data class FabricanteCatalogo(val id: String, val nombre: String)

/** Modelo del catálogo con su fabricante (para la cascada en cliente). */
data class ModeloCatalogo(val id: String, val nombre: String, val fabricanteId: String)

/** Catálogo global completo, leído on-demand al abrir el formulario. */
data class CatalogoMaquinas(
    val fabricantes: List<FabricanteCatalogo>,
    val modelos: List<ModeloCatalogo>,
)

interface CatalogoRepository {
    /**
     * Lee el catálogo global (solo lectura). El alta de fabricante/modelo la
     * hace la RPC `crear_maquina` al guardar la máquina; aquí no se escribe.
     */
    suspend fun cargar(): GestionResult<CatalogoMaquinas>
}

@Singleton
class CatalogoRepositoryImpl @Inject constructor(
    private val remote: CatalogoRemoteDataSource,
) : CatalogoRepository {

    override suspend fun cargar(): GestionResult<CatalogoMaquinas> =
        runCatching {
            val fabricantes = remote.fetchFabricantes().map { FabricanteCatalogo(it.id, it.nombre) }
            val modelos = remote.fetchModelos().map { ModeloCatalogo(it.id, it.nombre, it.fabricanteId) }
            CatalogoMaquinas(fabricantes, modelos)
        }.fold(
            onSuccess = { GestionResult.Success(it) },
            onFailure = { throwable ->
                val (error, code) = clasificarErrorGestion(throwable)
                Timber.w(throwable, "Cargar catálogo falló: %s", code)
                GestionResult.Failure(error, code)
            },
        )
}
