package ar.jornadas.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.jornadas.JornadasApp
import ar.jornadas.data.AgendaRepository
import ar.jornadas.data.Charla
import ar.jornadas.lab.ServicioLocal
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Tema(val etiqueta: String) {
    TODOS("Todos"), ANDROID("Android"), DATOS("Datos"), CALIDAD("Calidad");

    fun incluye(charla: Charla) = this == TODOS || charla.tema == etiqueta
}

data class ProgramaUiState(
    /** Room ya entregó el contenido guardado (hasta entonces no se sabe si hay datos). */
    val inicializado: Boolean = false,
    /** 0 = ambos días. */
    val dia: Int = 0,
    val tema: Tema = Tema.TODOS,
    val todas: List<Charla> = emptyList(),
    val visibles: List<Charla> = emptyList(),
    val agenda: List<Charla> = emptyList(),
    /** Existe una edición válida guardada, aunque tenga cero charlas. */
    val hayEdicion: Boolean = false,
    val cargando: Boolean = false,
    val fallo: Boolean = false,
)

class ProgramaModel(
    private val repo: AgendaRepository,
    val mesa: ServicioLocal,
    private val guardado: SavedStateHandle,
) : ViewModel() {

    // Los filtros viven en SavedStateHandle: sobreviven a rotación y muerte del proceso,
    // pero una tarea nueva arranca sin filtros.
    private val dia = guardado.getStateFlow(CLAVE_DIA, 0)
    private val tema = guardado.getStateFlow(CLAVE_TEMA, Tema.TODOS.name).map(Tema::valueOf)

    private data class Sync(val hayEdicion: Boolean, val cargando: Boolean, val fallo: Boolean)

    private val sync = combine(repo.hayEdicion, repo.cargando, repo.ultimoIntentoFallo, ::Sync)

    val estado: StateFlow<ProgramaUiState> =
        combine(repo.programa, dia, tema, sync) { todas, dia, tema, sync ->
            ProgramaUiState(
                inicializado = true,
                dia = dia,
                tema = tema,
                todas = todas,
                visibles = todas.filter { (dia == 0 || it.dia == dia) && tema.incluye(it) },
                agenda = todas.filter { it.favorita },
                // Las filas guardadas también cuentan: cubre instalaciones previas a la 1.3.
                hayEdicion = sync.hayEdicion || todas.isNotEmpty(),
                cargando = sync.cargando,
                fallo = sync.fallo,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramaUiState(dia = dia.value, tema = Tema.valueOf(guardado[CLAVE_TEMA] ?: Tema.TODOS.name)),
        )

    init {
        repo.cargaAutomatica() // no-op si este proceso ya hizo su carga automática
    }

    fun elegirDia(nuevo: Int) { guardado[CLAVE_DIA] = nuevo }

    fun elegirTema(nuevo: Tema) { guardado[CLAVE_TEMA] = nuevo.name }

    fun actualizar() = repo.actualizar()

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    companion object {
        private const val CLAVE_DIA = "dia"
        private const val CLAVE_TEMA = "tema"

        val Factory = viewModelFactory {
            initializer {
                val servicios = (this[APPLICATION_KEY] as JornadasApp).servicios
                ProgramaModel(servicios.agenda, servicios.mesa, createSavedStateHandle())
            }
        }
    }
}
