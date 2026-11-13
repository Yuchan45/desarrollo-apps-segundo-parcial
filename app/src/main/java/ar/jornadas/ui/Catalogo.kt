package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.jornadas.data.Charla

private val DIAS = listOf("Ambos días", "Día 1", "Día 2")

@Composable
fun Catalogo(
    estado: ProgramaUiState,
    lista: LazyListState,
    elegirDia: (Int) -> Unit,
    elegirTema: (Tema) -> Unit,
    actualizar: () -> Unit,
    abrir: (String) -> Unit,
) {
    // Los filtros quedan habilitados también durante la espera.
    Selector(DIAS, estado.dia, elegirDia)
    Selector(Tema.entries.map { it.etiqueta }, estado.tema.ordinal) { elegirTema(Tema.entries[it]) }
    TextButton(onClick = actualizar) { Text(if (estado.cargando) "Actualizando…" else "Actualizar") }
    if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
    when {
        !estado.inicializado -> Unit
        !estado.hayEdicion -> if (estado.fallo) {
            Text("No se pudo descargar el programa.")
            OutlinedButton(onClick = actualizar) { Text("Reintentar") }
        }
        else -> {
            if (estado.fallo) Text("Sin conexión: se están usando datos guardados.",
                color = MaterialTheme.colorScheme.secondary)
            when {
                estado.todas.isEmpty() -> Text("Sin charlas programadas.")
                estado.visibles.isEmpty() -> Text("Ninguna charla coincide con los filtros.")
                else -> Grilla(estado.visibles, abrir, lista)
            }
        }
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, abrir: (String) -> Unit, lista: LazyListState = rememberLazyListState()) {
    LazyColumn(state = lista, verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
