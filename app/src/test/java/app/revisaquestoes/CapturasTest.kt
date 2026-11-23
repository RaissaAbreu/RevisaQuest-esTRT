package app.revisaquestoes

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.revisaquestoes.data.Analise
import app.revisaquestoes.data.Anotacao
import app.revisaquestoes.data.CorTema
import app.revisaquestoes.data.Filtro
import app.revisaquestoes.data.Repositorio
import app.revisaquestoes.data.Resposta
import app.revisaquestoes.data.Situacao
import app.revisaquestoes.data.Tema
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Confere a base gerada dos PDFs e a lógica de desempenho/caderno de erros,
 * e percorre as telas salvando capturas em docs/capturas.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CapturasTest {
    @get:Rule
    val regra = createAndroidComposeRule<MainActivity>()

    private val dia = 24L * 60 * 60 * 1000

    @Test
    fun percorrerTelas() {
        val repo = (regra.activity.application as QuestoesApp).repo
        val base = runBlocking { repo.carregarBase() }
        assertEquals(3500, base.questoes.size)
        assertEquals(7, base.materias.size)
        assertEquals(base.questoes.size, base.questoes.map { it.id }.toSet().size)
        assertTrue(base.questoes.all { it.incidencia in 1..4 && it.enunciado.isNotBlank() && it.comentario.isNotBlank() })

        val trabalho = base.questoes.filter { it.materia == "direito_do_trabalho" }
        val civil = base.questoes.filter { it.materia == "direito_civil" }
        val agora = System.currentTimeMillis()

        runBlocking {
            // respostas de exemplo espalhadas nos últimos dias
            val rs = mutableListOf<Resposta>()
            fun resp(q: app.revisaquestoes.data.Questao, diasAtras: Long, acertou: Boolean, min: Long = 0) =
                Resposta(questaoId = q.id, data = agora - diasAtras * dia - min * 60_000, resposta = if (acertou) q.gabarito else !q.gabarito, acertou = acertou)
            trabalho.drop(5).take(60).forEachIndexed { i, q -> rs += resp(q, (i % 9).toLong(), i % 4 != 0) }
            civil.take(40).forEachIndexed { i, q -> rs += resp(q, (i % 6).toLong(), i % 3 != 0) }
            base.questoes.filter { it.materia == "direito_constitucional" }.take(25).forEachIndexed { i, q -> rs += resp(q, (i % 5).toLong(), i % 5 != 0) }
            // errou e depois acertou: sai do caderno de erros
            rs += resp(civil[100], 3, false); rs += resp(civil[100], 1, true)
            // acertou e depois errou: entra no caderno
            rs += resp(civil[101], 3, true); rs += resp(civil[101], 0, false, min = 5)
            repo.dao.inserirRespostas(rs)
            repo.dao.inserirAnotacao(Anotacao(questaoId = trabalho[0].id, texto = "Abono: até 1/3 das férias, requerido até 15 dias antes do fim do período AQUISITIVO.", criadaEm = agora - dia))
            repo.favoritar(trabalho[2].id, true)

            val estados = Analise.estados(repo.respostas.first())
            assertFalse(estados[civil[100].id]!!.noCadernoDeErros)
            assertTrue(estados[civil[101].id]!!.noCadernoDeErros)
            assertEquals(2, estados[civil[101].id]!!.respostas)

            // filtros
            val anot = repo.anotacoes.first().groupBy { it.questaoId }
            val fav = repo.favoritos.first().map { it.questaoId }.toSet()
            val alt = Repositorio.filtrar(base, Filtro(materias = setOf("direito_civil"), incidencias = setOf(4)), estados, fav, anot)
            assertTrue(alt.isNotEmpty() && alt.all { it.materia == "direito_civil" && it.incidencia == 4 })
            assertEquals(listOf(trabalho[2].id), Repositorio.filtrar(base, Filtro(situacao = Situacao.FAVORITAS), estados, fav, anot).map { it.id })
            assertEquals(listOf(trabalho[0].id), Repositorio.filtrar(base, Filtro(busca = "periodo aquisitivo abono", situacao = Situacao.ANOTADAS), estados, fav, anot).map { it.id })
        }

        fun esperar(texto: String) = regra.waitUntil(10_000) {
            regra.onAllNodes(hasText(texto, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        fun rolarAte(texto: String) {
            regra.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(texto, substring = true))
        }
        fun foto(nome: String) { regra.waitForIdle(); captureScreenRoboImage("../docs/capturas/$nome.png") }
        fun voltar() = regra.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        esperar("Direito Processual do Trabalho")
        foto("01-inicio")

        // Questões: filtro por matéria e incidência
        regra.onNodeWithText("Direito do Trabalho").performClick()
        esperar("Resolver 500 questões")
        regra.onAllNodesWithText("Altíssima").onFirst().performClick()
        esperar("Resolver 140 questões")
        foto("02-questoes-filtro")

        // Resolver a 1ª questão errando, para ver o gabarito comentado
        regra.onNodeWithText("Resolver 140 questões").performClick()
        esperar("Questão 1 de 140")
        regra.onNodeWithText("Incidência Altíssima").assertExists()
        foto("03-questao")
        val q1 = trabalho[0]
        regra.onNodeWithText(if (q1.gabarito) "Errado" else "Certo").performClick()
        esperar("Você errou")
        foto("04-errou-gabarito")
        rolarAte("Anotações (1)")
        foto("05-anotacoes")

        // Próxima: acertar
        regra.onNodeWithText("Próxima").performClick()
        esperar("Questão 2 de 140")
        regra.onNodeWithText(if (trabalho[1].gabarito) "Certo" else "Errado").performClick()
        esperar("Você acertou!")
        foto("06-acertou")

        // nova anotação
        rolarAte("Anotações (0)")
        // no Robolectric a animação do diálogo não termina sozinha: o relógio é avançado à mão
        regra.mainClock.autoAdvance = false
        regra.onNodeWithText("Nova").performClick()
        repeat(20) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            regra.mainClock.advanceTimeBy(100)
        }
        regra.onNodeWithText("Sua anotação").performTextInput("Prazo para pagar férias: 2 dias antes do início.")
        regra.mainClock.advanceTimeBy(300)
        regra.onNode(androidx.compose.ui.test.isDialog()).captureRoboImage("../docs/capturas/06b-nova-anotacao.png")
        regra.onNodeWithText("Salvar").performClick()
        repeat(20) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            regra.mainClock.advanceTimeBy(100)
        }
        regra.mainClock.autoAdvance = true
        esperar("Prazo para pagar férias")
        voltar()

        runBlocking {
            val estados = Analise.estados(repo.respostas.first())
            assertTrue(estados[q1.id]!!.noCadernoDeErros)
            assertTrue(estados[trabalho[1].id]!!.ultimaAcertou)
            assertEquals(1, repo.anotacoes.first().count { it.questaoId == trabalho[1].id })
        }

        esperar("Limpar filtros")
        regra.onNodeWithText("Limpar filtros").performClick()
        regra.onNodeWithText("Buscar no enunciado", substring = true).performTextInput("terceirização")
        esperar("Resolver ")
        foto("07-busca")

        regra.onNodeWithText("Erros").performClick()
        esperar("Refazer ")
        foto("08-caderno-erros")

        regra.onNodeWithText("Desempenho").performClick()
        esperar("Acerto geral")
        foto("09-desempenho")
        rolarAte("Assuntos para reforçar")
        foto("10-desempenho-materias")

        regra.onNodeWithText("Ajustes").performClick()
        esperar("Aparência")
        foto("11-ajustes")

        runBlocking { repo.salvarAjustes(repo.ajustes.first().copy(tema = Tema.ESCURO, cor = CorTema.ROXO)) }
        regra.onNodeWithText("Início").performClick()
        esperar("Matérias")
        foto("12-inicio-escuro")
        regra.onNodeWithText("Erros").performClick()
        esperar("Refazer ")
        regra.onAllNodesWithText("Questão ", substring = true).onFirst().performClick()
        esperar("Certo")
        regra.onNodeWithText("Certo").performClick()
        esperar("Gabarito comentado")
        foto("13-questao-escuro")
    }
}
