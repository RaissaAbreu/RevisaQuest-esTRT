package app.revisaquestoes

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.revisaquestoes.data.AppDatabase
import app.revisaquestoes.data.MIGRACAO_1_2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Simula o aparelho com a 1.0.2 (banco v1) e progresso salvo, aplica a atualização e confere
 * que respostas, anotações e favoritos continuam lá e ficam na fila para subir para a nuvem.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigracaoTest {
    private val nome = "migracao-teste.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun atualizarDa102MantemProgresso() {
        helper.createDatabase(nome, 1).apply {
            execSQL("INSERT INTO resposta (questaoId, data, resposta, acertou) VALUES ('q1', 1000, 1, 0), ('q1', 2000, 0, 1), ('q2', 3000, 1, 1)")
            execSQL("INSERT INTO anotacao (questaoId, texto, criadaEm, editadaEm) VALUES ('q1', 'minha nota', 1000, 5000), ('q2', 'outra', 2000, NULL)")
            execSQL("INSERT INTO favorito (questaoId, desde) VALUES ('q3', 7000)")
            close()
        }
        // confere o esquema final contra o que o Room espera
        helper.runMigrationsAndValidate(nome, 2, true, MIGRACAO_1_2).close()

        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, nome).addMigrations(MIGRACAO_1_2).allowMainThreadQueries().build()
        runBlocking {
            val dao = db.dao()
            val rs = dao.respostas().first()
            assertEquals(3, rs.size)
            assertEquals(listOf(false, true, true), rs.map { it.acertou })
            assertEquals(3, rs.map { it.uuid }.toSet().size)
            assertTrue(rs.all { it.uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) })
            assertTrue(rs.all { it.pendente })

            val notas = dao.anotacoes().first().sortedBy { it.criadaEm }
            assertEquals(listOf("minha nota", "outra"), notas.map { it.texto })
            assertEquals(listOf(5000L, 2000L), notas.map { it.atualizadoEm })
            assertTrue(notas.all { it.pendente && !it.apagada && it.uuid.isNotEmpty() })

            val favs = dao.favoritos().first()
            assertEquals(listOf("q3"), favs.map { it.questaoId })
            assertEquals(7000L, favs[0].atualizadoEm)

            // tudo entra na fila de envio
            assertEquals(6, dao.totalPendentes().first())
        }
        db.close()
    }
}
