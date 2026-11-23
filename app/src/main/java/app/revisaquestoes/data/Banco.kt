package app.revisaquestoes.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun novoUuid(): String = UUID.randomUUID().toString()

/**
 * Cada vez que você responde uma questão (todas as tentativas ficam guardadas).
 * Respostas não mudam depois de criadas; [uuid] é o mesmo em todos os aparelhos.
 */
@Entity(tableName = "resposta", indices = [Index("questaoId"), Index("data"), Index(value = ["uuid"], unique = true)])
data class Resposta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questaoId: String,
    val data: Long,
    /** true = respondeu "Certo". */
    val resposta: Boolean,
    val acertou: Boolean,
    @ColumnInfo(defaultValue = "") val uuid: String = novoUuid(),
    /** Ainda não enviada para a nuvem. */
    @ColumnInfo(defaultValue = "1") val pendente: Boolean = true,
)

@Entity(tableName = "anotacao", indices = [Index("questaoId"), Index(value = ["uuid"], unique = true)])
data class Anotacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questaoId: String,
    val texto: String,
    val criadaEm: Long,
    val editadaEm: Long? = null,
    @ColumnInfo(defaultValue = "") val uuid: String = novoUuid(),
    /** Apagar = marcar como apagada, para o apagamento chegar aos outros aparelhos. */
    @ColumnInfo(defaultValue = "0") val apagada: Boolean = false,
    /** Momento da última alteração (em conflito, vale a mais recente). */
    @ColumnInfo(defaultValue = "0") val atualizadoEm: Long = 0,
    @ColumnInfo(defaultValue = "1") val pendente: Boolean = true,
)

/** Uma linha por questão que já foi favoritada; desmarcar = [ativo] false. */
@Entity(tableName = "favorito")
data class Favorito(
    @PrimaryKey val questaoId: String,
    val desde: Long,
    @ColumnInfo(defaultValue = "1") val ativo: Boolean = true,
    @ColumnInfo(defaultValue = "0") val atualizadoEm: Long = 0,
    @ColumnInfo(defaultValue = "1") val pendente: Boolean = true,
)

@Dao
interface QuestoesDao {
    // ---- respostas
    @Query("SELECT * FROM resposta ORDER BY data")
    fun respostas(): Flow<List<Resposta>>

    @Insert
    suspend fun inserirResposta(r: Resposta): Long

    @Insert
    suspend fun inserirRespostas(r: List<Resposta>)

    @Query("SELECT * FROM resposta WHERE pendente = 1")
    suspend fun respostasPendentes(): List<Resposta>

    @Query("SELECT * FROM resposta WHERE uuid = :uuid")
    suspend fun respostaPorUuid(uuid: String): Resposta?

    @Query("UPDATE resposta SET pendente = 0 WHERE id = :id")
    suspend fun respostaEnviada(id: Long)

    // ---- anotações (as apagadas ficam guardadas só para a sincronização)
    @Query("SELECT * FROM anotacao WHERE apagada = 0 ORDER BY criadaEm")
    fun anotacoes(): Flow<List<Anotacao>>

    @Insert
    suspend fun inserirAnotacao(a: Anotacao): Long

    @Update
    suspend fun atualizarAnotacao(a: Anotacao)

    @Query("SELECT * FROM anotacao WHERE id = :id")
    suspend fun anotacao(id: Long): Anotacao?

    @Query("SELECT * FROM anotacao WHERE pendente = 1")
    suspend fun anotacoesPendentes(): List<Anotacao>

    @Query("SELECT * FROM anotacao WHERE uuid = :uuid")
    suspend fun anotacaoPorUuid(uuid: String): Anotacao?

    @Query("UPDATE anotacao SET pendente = 0 WHERE id = :id AND atualizadoEm = :versao")
    suspend fun anotacaoEnviada(id: Long, versao: Long)

    @Query("UPDATE anotacao SET pendente = 1 WHERE id = :id")
    suspend fun reenviarAnotacao(id: Long)

    // ---- favoritos
    @Query("SELECT * FROM favorito WHERE ativo = 1")
    fun favoritos(): Flow<List<Favorito>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarFavorito(f: Favorito)

    @Query("SELECT * FROM favorito WHERE questaoId = :questaoId")
    suspend fun favorito(questaoId: String): Favorito?

    @Query("SELECT * FROM favorito WHERE pendente = 1")
    suspend fun favoritosPendentes(): List<Favorito>

    @Query("UPDATE favorito SET pendente = 0 WHERE questaoId = :questaoId AND atualizadoEm = :versao")
    suspend fun favoritoEnviado(questaoId: String, versao: Long)

    @Query("UPDATE favorito SET pendente = 1 WHERE questaoId = :questaoId")
    suspend fun reenviarFavorito(questaoId: String)

    // ---- fila de envio
    @Query(
        "SELECT (SELECT COUNT(*) FROM resposta WHERE pendente = 1) + " +
            "(SELECT COUNT(*) FROM anotacao WHERE pendente = 1) + (SELECT COUNT(*) FROM favorito WHERE pendente = 1)"
    )
    fun totalPendentes(): Flow<Int>

    @Query("UPDATE resposta SET pendente = 1")
    suspend fun marcarRespostasPendentes()

    @Query("UPDATE anotacao SET pendente = 1")
    suspend fun marcarAnotacoesPendentes()

    @Query("UPDATE favorito SET pendente = 1")
    suspend fun marcarFavoritosPendentes()
}

/**
 * Versão 2: sincronização entre aparelhos.
 * O que já existia ganha um uuid e fica pendente, para subir na primeira sincronização.
 */
val MIGRACAO_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val uuidSql = "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || " +
            "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"

        db.execSQL("ALTER TABLE resposta ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE resposta ADD COLUMN pendente INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE resposta SET uuid = $uuidSql")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_resposta_uuid ON resposta (uuid)")

        db.execSQL("ALTER TABLE anotacao ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE anotacao ADD COLUMN apagada INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE anotacao ADD COLUMN atualizadoEm INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE anotacao ADD COLUMN pendente INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE anotacao SET uuid = $uuidSql, atualizadoEm = COALESCE(editadaEm, criadaEm)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_anotacao_uuid ON anotacao (uuid)")

        db.execSQL("ALTER TABLE favorito ADD COLUMN ativo INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE favorito ADD COLUMN atualizadoEm INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE favorito ADD COLUMN pendente INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE favorito SET atualizadoEm = desde")
    }
}

@Database(entities = [Resposta::class, Anotacao::class, Favorito::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): QuestoesDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        fun get(context: Context): AppDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "questoes.db")
                .addMigrations(MIGRACAO_1_2)
                .build().also { instancia = it }
        }
    }
}
