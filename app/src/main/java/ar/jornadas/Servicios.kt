package ar.jornadas

import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Se crea una única vez por proceso desde [JornadasApp]; no instanciar en otro lugar. */
class Servicios(context: Context) {
    /** Vive lo mismo que el proceso: las descargas no dependen de ninguna pantalla. */
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val mesa = ServicioLocal(context)
    private val archivo = Room.databaseBuilder(context, Archivo::class.java, "cartelera.db").build()
    private val api = Retrofit.Builder().baseUrl("https://programa.invalid/")
        .client(OkHttpClient.Builder().addInterceptor(mesa).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ProgramaApi::class.java)
    val agenda = AgendaRepository(
        api,
        archivo.cartelera(),
        context.getSharedPreferences("agenda", Context.MODE_PRIVATE),
        alcance,
    )
}
