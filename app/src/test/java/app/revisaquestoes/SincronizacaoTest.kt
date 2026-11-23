package app.revisaquestoes

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.revisaquestoes.data.Ajustes
import app.revisaquestoes.data.AjustesSync
import app.revisaquestoes.data.Anotacao
import app.revisaquestoes.data.AppDatabase
import app.revisaquestoes.data.Analise
import app.revisaquestoes.data.CorTema
import app.revisaquestoes.data.Favorito
import app.revisaquestoes.data.Resposta
import app.revisaquestoes.sync.ArmazemAjustes
import app.revisaquestoes.sync.Nuvem
import app.revisaquestoes.sync.Sincronizador
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Nuvem em memória que se comporta como o Firestore (merge de campos + aviso aos ouvintes). */
class NuvemFalsa : Nuvem {
    val dados = mutableMapOf<String, MutableMap<String, MutableMap<String, Any?>>>()
    private val ouvintes = mutableMapOf<String, MutableList<(List<Pair<String, Map<String, Any?>>>) -> Unit>>()
    /** Simula falta de internet / regras recusando gravações. */
    var recusar = false

    override suspend fun buscar(colecao: String) = dados[colecao].orEmpty().map { it.key to it.value.toMap() }

    override fun enviar(colecao: String, id: String, dados: Map<String, Any?>, aoTerminar: (Throwable?) -> Unit) {
        if (recusar) { aoTerminar(IllegalStateException("UNAVAILABLE")); return }
        val doc = this.dados.getOrPut(colecao) { mutableMapOf() }.getOrPut(id) { mutableMapOf() }
        doc.putAll(dados)
        ouvintes[colecao].orEmpty().toList().forEach { it(listOf(id to doc.toMap())) }
        aoTerminar(null)
    }

    override fun ouvir(
        colecao: String,
        aoMudar: (List<Pair<String, Map<String, Any?>>>) -> Unit,
        aoErro: (Throwable) -> Unit,
    ): () -> Unit {
        ouvintes.getOrPut(colecao) { mutableListOf() }.add(aoMudar)
        return { ouvintes[colecao]?.remove(aoMudar) }
    }
}

/** Ajustes de um aparelho, em memória (mesma regra do DataStore do app). */
class AjustesFalsos : ArmazemAjustes {
    var estado = AjustesSync(Ajustes(), 0L, false)
    fun mudar(a: Ajustes, quando: Long) { estado = AjustesSync(a, quando, true) }
    override suspend fun estadoSync() = estado
    override suspend fun aplicarRemoto(d: Map<String, Any?>, versao: Long) {
        estado = AjustesSync(estado.ajustes.comSincronizados(d), versao, false)
    }
    override suspend fun marcarEnviado(versao: Long) { if (estado.versao == versao) estado = estado.copy(pendente = false) }
    override suspend fun reenviar() { estado = estado.copy(pendente = true) }
}

/** Dois aparelhos (tablet e celular) com a mesma conta, sincronizando pela mesma nuvem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SincronizacaoTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private fun banco() = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()

    private val nuvem = NuvemFalsa()
    private val tablet = banco()
    private val celular = banco()
    private val ajTablet = AjustesFalsos()
    private val ajCelular = AjustesFalsos()
    private val sTablet = Sincronizador(tablet, nuvem, ajTablet)
    private val sCelular = Sincronizador(celular, nuvem, ajCelular)

    @After
    fun fim() { tablet.close(); celular.close() }

    @Test
    fun resolvidoNoTabletApareceNoCelular() = runBlocking {
        // o celular já tinha progresso antes de entrar na conta
        celular.dao().inserirResposta(Resposta(questaoId = "q9", data = 500, resposta = true, acertou = true))
        sCelular.sincronizarTudo()

        // no tablet: responde (errando), comenta e favorita
        sTablet.sincronizarTudo()
        assertEquals(1, tablet.dao().respostas().first().size) // recebeu o progresso antigo do celular
        tablet.dao().inserirResposta(Resposta(questaoId = "q1", data = 1000, resposta = true, acertou = false))
        tablet.dao().inserirAnotacao(Anotacao(questaoId = "q1", texto = "pegadinha: é 1/3", criadaEm = 1000, atualizadoEm = 1000))
        tablet.dao().salvarFavorito(Favorito("q1", 1000, ativo = true, atualizadoEm = 1000))
        sTablet.enviarPendentes()
        assertEquals(0, tablet.dao().totalPendentes().first())

        // o celular recebe tudo
        sCelular.sincronizarTudo()
        val rs = celular.dao().respostas().first()
        assertEquals(setOf("q1", "q9"), rs.map { it.questaoId }.toSet())
        assertTrue(Analise.estados(rs)["q1"]!!.noCadernoDeErros)
        assertEquals(listOf("pegadinha: é 1/3"), celular.dao().anotacoes().first().map { it.texto })
        assertEquals(listOf("q1"), celular.dao().favoritos().first().map { it.questaoId })
        assertEquals(0, celular.dao().totalPendentes().first())

        // sincronizar de novo não duplica nada
        sCelular.sincronizarTudo(); sTablet.sincronizarTudo()
        assertEquals(2, celular.dao().respostas().first().size)
        assertEquals(2, tablet.dao().respostas().first().size)
    }

    @Test
    fun edicaoApagamentoEFavoritoValeOMaisRecente() = runBlocking {
        tablet.dao().inserirAnotacao(Anotacao(questaoId = "q1", texto = "v1", criadaEm = 1000, atualizadoEm = 1000))
        tablet.dao().inserirAnotacao(Anotacao(questaoId = "q2", texto = "vou apagar", criadaEm = 1000, atualizadoEm = 1000))
        tablet.dao().salvarFavorito(Favorito("q1", 1000, ativo = true, atualizadoEm = 1000))
        sTablet.sincronizarTudo()
        sCelular.sincronizarTudo()

        // celular edita a nota e desmarca o favorito; tablet apaga a outra nota
        val nota = celular.dao().anotacoes().first().first { it.questaoId == "q1" }
        celular.dao().atualizarAnotacao(nota.copy(texto = "v2", editadaEm = 2000, atualizadoEm = 2000, pendente = true))
        celular.dao().salvarFavorito(Favorito("q1", 1000, ativo = false, atualizadoEm = 2000))
        val apagar = tablet.dao().anotacoes().first().first { it.questaoId == "q2" }
        tablet.dao().atualizarAnotacao(apagar.copy(apagada = true, atualizadoEm = 2500, pendente = true))
        sCelular.enviarPendentes()
        sTablet.enviarPendentes()

        sTablet.sincronizarTudo(); sCelular.sincronizarTudo()
        for (db in listOf(tablet, celular)) {
            assertEquals(listOf("v2"), db.dao().anotacoes().first().map { it.texto })
            assertTrue(db.dao().favoritos().first().isEmpty())
        }

        // aparelho que estava sem internet com uma versão mais antiga não sobrescreve a mais nova
        val n = tablet.dao().anotacoes().first().first()
        nuvem.dados[Sincronizador.ANOTACOES]!![n.uuid]!!.putAll(mapOf("texto" to "velha", "atualizadoEm" to 1500L))
        sTablet.sincronizarTudo()
        assertEquals("v2", nuvem.dados[Sincronizador.ANOTACOES]!![n.uuid]!!["texto"])
    }

    @Test
    fun semInternetFicaNaFilaEEnviaDepois() = runBlocking {
        sTablet.sincronizarTudo()
        nuvem.recusar = true
        tablet.dao().inserirResposta(Resposta(questaoId = "q1", data = 1000, resposta = false, acertou = true))
        sTablet.enviarPendentes()
        assertEquals(1, tablet.dao().totalPendentes().first())
        assertTrue(sTablet.ultimoErro != null)

        nuvem.recusar = false
        sTablet.enviarPendentes()
        assertEquals(0, tablet.dao().totalPendentes().first())
        sCelular.sincronizarTudo()
        assertEquals(1, celular.dao().respostas().first().size)
    }

    @Test
    fun ajustesSincronizamMenosLembretes() = runBlocking {
        // primeiro aparelho na conta: seus ajustes viram o ponto de partida
        ajTablet.estado = AjustesSync(Ajustes(metaDiaria = 40, notificacoes = true), 0L, false)
        sTablet.sincronizarTudo()
        sCelular.sincronizarTudo()
        assertEquals(40, ajCelular.estado.ajustes.metaDiaria)
        assertFalse(ajCelular.estado.ajustes.notificacoes) // notificações ficam em cada aparelho

        // celular muda a cor e a meta semanal: chega no tablet
        val novoTempo = ajCelular.estado.versao + 1000
        ajCelular.mudar(ajCelular.estado.ajustes.copy(cor = CorTema.ROXO, metaSemanal = 300), novoTempo)
        sCelular.enviarPendentes()
        sTablet.aplicarAjustes(nuvem.buscar(Sincronizador.AJUSTES))
        assertEquals(CorTema.ROXO, ajTablet.estado.ajustes.cor)
        assertEquals(300, ajTablet.estado.ajustes.metaSemanal)
        assertTrue(ajTablet.estado.ajustes.notificacoes)
    }
}
