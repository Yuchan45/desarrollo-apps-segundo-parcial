package ar.jornadas.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Única instancia por proceso (ver JornadasApp). Además de los datos, es dueño de la política de
 * descargas: una carga automática por proceso y una por cada actualización explícita, coalesciendo
 * los pedidos que llegan mientras hay una petición en curso.
 *
 * [cargaAutomatica] y [actualizar] se llaman desde el hilo principal, igual que [alcance]; por eso
 * los flags de control no necesitan sincronización.
 */
class AgendaRepository(
    private val api: ProgramaApi,
    private val dao: CarteleraDao,
    private val prefs: SharedPreferences,
    private val alcance: CoroutineScope,
) {
    val programa: Flow<List<Charla>> = dao.observar()

    private val _hayEdicion = MutableStateFlow(prefs.getBoolean(CLAVE_EDICION, false))
    /** Hubo al menos una descarga exitosa (aunque haya traído cero charlas). */
    val hayEdicion: StateFlow<Boolean> = _hayEdicion.asStateFlow()

    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    private val _ultimoIntentoFallo = MutableStateFlow(false)
    val ultimoIntentoFallo: StateFlow<Boolean> = _ultimoIntentoFallo.asStateFlow()

    private var automaticaHecha = false
    private var enCurso: Job? = null
    private var pendiente = false

    fun cargaAutomatica() {
        if (automaticaHecha) return
        automaticaHecha = true
        solicitar()
    }

    fun actualizar() = solicitar()

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)

    private fun solicitar() {
        if (enCurso?.isActive == true) {
            pendiente = true // los toques extra durante la espera se agrupan en una sola recarga
            return
        }
        enCurso = alcance.launch {
            _cargando.value = true
            try {
                do {
                    pendiente = false
                    intentar()
                } while (pendiente)
            } finally {
                _cargando.value = false
            }
        }
    }

    private suspend fun intentar() {
        try {
            sincronizar()
            _ultimoIntentoFallo.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Red caída, HTTP no exitoso o JSON inválido: se conservan los datos guardados.
            _ultimoIntentoFallo.value = true
        }
    }

    private suspend fun sincronizar() {
        val fichas = api.consultar().map {
            DatosCharla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.aplicarEdicion(fichas)
        prefs.edit().putBoolean(CLAVE_EDICION, true).apply()
        _hayEdicion.value = true
    }

    private companion object {
        const val CLAVE_EDICION = "hay_edicion"
    }
}
