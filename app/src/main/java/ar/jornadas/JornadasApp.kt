package ar.jornadas

import android.app.Application

/** Dueña de las dependencias de la app: existen una sola vez por proceso. */
class JornadasApp : Application() {
    val servicios by lazy { Servicios(this) }
}
