package app.revisaquestoes.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Cada vez que você responde uma questão (todas as tentativas ficam guardadas). */
@Entity(tableName = "resposta", indices = [Index("questaoId"), Index("data")])
data class Resposta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questaoId: String,
    val data: Long,
    /** true = respondeu "Certo". */
    val resposta: Boolean,
    val acertou: Boolean,
)

@Entity(tableName = "anotacao", indices = [Index("questaoId")])
data class Anotacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questaoId: String,
    val texto: String,
    val criadaEm: Long,
    val editadaEm: Long? = null,
)

@Entity(tableName = "favorito")
data class Favorito(
    @PrimaryKey val questaoId: String,
    val desde: Long,
)

@Dao
interface QuestoesDao {
    @Query("SELECT * FROM resposta ORDER BY data")
    fun respostas(): Flow<List<Resposta>>

    @Query("SELECT * FROM resposta WHERE questaoId = :questaoId ORDER BY data DESC")
    fun respostasDa(questaoId: String): Flow<List<Resposta>>

    @Insert
    suspend fun inserirResposta(r: Resposta): Long

    @Insert
    suspend fun inserirRespostas(r: List<Resposta>)

    @Query("SELECT * FROM anotacao ORDER BY criadaEm")
    fun anotacoes(): Flow<List<Anotacao>>

    @Insert
    suspend fun inserirAnotacao(a: Anotacao): Long

    @Update
    suspend fun atualizarAnotacao(a: Anotacao)

    @Query("DELETE FROM anotacao WHERE id = :id")
    suspend fun apagarAnotacao(id: Long)

    @Query("SELECT * FROM favorito")
    fun favoritos(): Flow<List<Favorito>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirFavorito(f: Favorito)

    @Delete
    suspend fun apagarFavorito(f: Favorito)
}

@Database(entities = [Resposta::class, Anotacao::class, Favorito::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): QuestoesDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        fun get(context: Context): AppDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "questoes.db")
                .build().also { instancia = it }
        }
    }
}
