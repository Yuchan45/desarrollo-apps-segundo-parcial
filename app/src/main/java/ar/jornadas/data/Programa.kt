package ar.jornadas.data

import androidx.room.*
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

// No modificar: cualquier cambio de esquema exige una Migration para conservar las marcas instaladas.
@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/** Columnas que publica la organización. No incluye `favorita`, que pertenece al usuario. */
data class DatosCharla(val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String) {
    fun comoCharlaNueva() = Charla(id, titulo, tema, dia, hora, sala, ponente, resumen, favorita = false)
}

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    /** Actualiza solo las columnas de [DatosCharla]; devuelve 0 si la charla no existía. */
    @Update(entity = Charla::class)
    protected abstract suspend fun actualizarDatos(datos: DatosCharla): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertar(charla: Charla)

    @Query("DELETE FROM charlas WHERE id NOT IN (:vigentes)")
    protected abstract suspend fun retirarAusentes(vigentes: List<String>)

    @Query("DELETE FROM charlas")
    protected abstract suspend fun vaciar()

    /**
     * Aplica una instantánea completa del programa:
     * - las charlas ausentes se retiran (y con ellas su marca: si regresan, regresan sin marcar);
     * - las vigentes actualizan sus datos sin tocar `favorita`;
     * - las nuevas entran sin marcar.
     * Como nunca escribe `favorita` en filas existentes, una marca hecha durante la petición se respeta.
     */
    @Transaction
    open suspend fun aplicarEdicion(fichas: List<DatosCharla>) {
        if (fichas.isEmpty()) vaciar() else retirarAusentes(fichas.map { it.id })
        fichas.forEach { if (actualizarDatos(it) == 0) insertar(it.comoCharlaNueva()) }
    }
}

@Database(entities = [Charla::class], version = 1, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}
