package ar.jornadas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.jornadas.ui.*

class MainActivity : ComponentActivity() {
    private val model: ProgramaModel by viewModels { ProgramaModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Se aplica antes de crear el ViewModel, porque su creación dispara la carga automática.
        val desdeRecientes = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !desdeRecientes && intent.hasExtra(EXTRA_SERVICIO)) {
            (application as JornadasApp).servicios.mesa.disponible =
                intent.getBooleanExtra(EXTRA_SERVICIO, true)
            intent.removeExtra(EXTRA_SERVICIO)
        }
        setContent { Portada(model) }
    }

    private companion object {
        const val EXTRA_SERVICIO = "servicio_disponible"
    }
}
