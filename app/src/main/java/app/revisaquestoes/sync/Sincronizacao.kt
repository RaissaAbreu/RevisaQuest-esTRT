package app.revisaquestoes.sync

import androidx.room.withTransaction
import app.revisaquestoes.data.AjustesRepo
import app.revisaquestoes.data.AjustesSync
import app.revisaquestoes.data.Anotacao
import app.revisaquestoes.data.AppDatabase
import app.revisaquestoes.data.Favorito
import app.revisaquestoes.data.Resposta
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Armazenamento remoto de documentos da usuária: coleções de documentos, cada um com um id
 * e um mapa de campos simples.
 */
interface Nuvem {
    suspend fun buscar(colecao: String): List<Pair<String, Map<String, Any?>>>
    /** Grava o documento; [aoTerminar] recebe null quando a nuvem confirmar, ou o erro se ela recusar. */
    fun enviar(colecao: String, id: String, dados: Map<String, Any?>, aoTerminar: (Throwable?) -> Unit)
    /** Avisa a cada mudança (inclusive a primeira leitura). Devolve uma função para parar de ouvir. */
    fun ouvir(
        colecao: String,
        aoMudar: (List<Pair<String, Map<String, Any?>>>) -> Unit,
        aoErro: (Throwable) -> Unit = {},
    ): () -> Unit
}

/** Onde ficam os ajustes deste aparelho (no app: DataStore; nos testes: memória). */
interface ArmazemAjustes {
    suspend fun estadoSync(): AjustesSync
    suspend fun aplicarRemoto(d: Map<String, Any?>, versao: Long)
    suspend fun marcarEnviado(versao: Long)
    suspend fun reenviar()
}

fun AjustesRepo.comoArmazem() = object : ArmazemAjustes {
    override suspend fun estadoSync() = this@comoArmazem.estadoSync()
    override suspend fun aplicarRemoto(d: Map<String, Any?>, versao: Long) = this@comoArmazem.aplicarRemoto(d, versao)
    override suspend fun marcarEnviado(versao: Long) = this@comoArmazem.marcarEnviado(versao)
    override suspend fun reenviar() = this@comoArmazem.reenviar()
}

/**
 * Sincroniza o banco local com a nuvem.
 *
 * Regras:
 *  - respostas e anotações têm um uuid igual em todos os aparelhos; favoritos usam o id da questão;
 *  - respostas não mudam depois de criadas (só são acrescentadas);
 *  - apagar anotação / desmarcar favorito = marcar (o apagamento também precisa viajar);
 *  - em conflito vale a alteração mais recente (atualizadoEm); se a nuvem tiver uma versão mais
 *    antiga (aparelho que estava sem internet), quem tem a mais nova a reenvia.
 */
class Sincronizador(private val db: AppDatabase, private val nuvem: Nuvem, private val ajustes: ArmazemAjustes) {
    private val dao = db.dao()

    companion object {
        // Coleções em usuarios/{uid}/..., separadas das do RevisaTRT (mesmo projeto Firebase)
        const val RESPOSTAS = "questoes_respostas"
        const val ANOTACOES = "questoes_anotacoes"
        const val FAVORITOS = "questoes_favoritos"
        const val AJUSTES = "questoes_ajustes"
        const val DOC_AJUSTES = "geral"
    }

    /** Primeira sincronização: baixa tudo e envia o que está pendente. */
    suspend fun sincronizarTudo() {
        aplicarRespostas(nuvem.buscar(RESPOSTAS))
        aplicarAnotacoes(nuvem.buscar(ANOTACOES))
        aplicarFavoritos(nuvem.buscar(FAVORITOS))
        val docAjustes = nuvem.buscar(AJUSTES)
        aplicarAjustes(docAjustes)
        // nuvem ainda sem ajustes: os deste aparelho viram o ponto de partida
        if (docAjustes.none { it.first == DOC_AJUSTES } && ajustes.estadoSync().versao == 0L) ajustes.reenviar()
        enviarPendentes()
    }

    /** Confirmações que chegaram da nuvem e ainda não foram registradas: (tipo, id, versão). */
    private val confirmacoes = ConcurrentLinkedQueue<Triple<String, String, Long>>()
    /** Itens enviados aguardando confirmação (não reenviar enquanto isso). */
    private val emEnvio = Collections.synchronizedSet(mutableSetOf<String>())
    /** Último erro devolvido pela nuvem (para mostrar na tela). */
    @Volatile var ultimoErro: Throwable? = null
        private set
    /** Chamado quando chega uma confirmação (o app usa para registrar logo). */
    var aoConfirmar: () -> Unit = {}

    private fun enviar(colecao: String, tipo: String, idLocal: String, versao: Long, idNuvem: String, dados: Map<String, Any?>) {
        val marca = "$tipo:$idLocal:$versao"
        if (!emEnvio.add(marca)) return
        nuvem.enviar(colecao, idNuvem, dados) { erro ->
            emEnvio.remove(marca)
            if (erro == null) {
                confirmacoes.add(Triple(tipo, idLocal, versao))
                aoConfirmar()
            } else {
                ultimoErro = erro // continua pendente: será reenviado
            }
        }
    }

    /** Registra as confirmações recebidas (só sai da fila o que a nuvem aceitou). */
    suspend fun processarConfirmacoes() {
        while (true) {
            val (tipo, id, versao) = confirmacoes.poll() ?: break
            when (tipo) {
                "r" -> dao.respostaEnviada(id.toLong())
                "a" -> dao.anotacaoEnviada(id.toLong(), versao)
                "f" -> dao.favoritoEnviado(id, versao)
                "j" -> ajustes.marcarEnviado(versao)
            }
            ultimoErro = null
        }
    }

    /** Envia as alterações locais pendentes. */
    suspend fun enviarPendentes() {
        processarConfirmacoes()
        for (r in dao.respostasPendentes()) {
            enviar(RESPOSTAS, "r", r.id.toString(), 0, r.uuid, mapOf(
                "questao" to r.questaoId, "data" to r.data, "resposta" to r.resposta, "acertou" to r.acertou,
            ))
        }
        for (a in dao.anotacoesPendentes()) {
            enviar(ANOTACOES, "a", a.id.toString(), a.atualizadoEm, a.uuid, mapOf(
                "questao" to a.questaoId, "texto" to a.texto, "criadaEm" to a.criadaEm, "editadaEm" to a.editadaEm,
                "apagada" to a.apagada, "atualizadoEm" to a.atualizadoEm,
            ))
        }
        for (f in dao.favoritosPendentes()) {
            enviar(FAVORITOS, "f", f.questaoId, f.atualizadoEm, f.questaoId, mapOf(
                "ativo" to f.ativo, "desde" to f.desde, "atualizadoEm" to f.atualizadoEm,
            ))
        }
        val aj = ajustes.estadoSync()
        if (aj.pendente) {
            val versao = if (aj.versao == 0L) System.currentTimeMillis() else aj.versao
            if (aj.versao == 0L) ajustes.aplicarRemoto(aj.ajustes.sincronizados(), versao).also { ajustes.reenviar() }
            enviar(AJUSTES, "j", DOC_AJUSTES, versao, DOC_AJUSTES, aj.ajustes.sincronizados() + ("atualizadoEm" to versao))
        }
        processarConfirmacoes()
    }

    // ---- Recebimento

    suspend fun aplicarRespostas(docs: List<Pair<String, Map<String, Any?>>>) = db.withTransaction {
        for ((uuid, d) in docs) {
            if (dao.respostaPorUuid(uuid) != null) continue
            val questao = d.str("questao") ?: continue
            dao.inserirResposta(
                Resposta(
                    questaoId = questao, data = d.long("data"), resposta = d.bool("resposta"), acertou = d.bool("acertou"),
                    uuid = uuid, pendente = false,
                )
            )
        }
    }

    suspend fun aplicarAnotacoes(docs: List<Pair<String, Map<String, Any?>>>) = db.withTransaction {
        for ((uuid, d) in docs) {
            val versao = d.long("atualizadoEm")
            val local = dao.anotacaoPorUuid(uuid)
            // a nuvem ficou com uma versão mais antiga que a deste aparelho: reenvia a nossa
            if (local != null && local.atualizadoEm > versao) { dao.reenviarAnotacao(local.id); continue }
            if (local != null && local.atualizadoEm >= versao) continue
            val a = Anotacao(
                id = local?.id ?: 0, questaoId = d.str("questao") ?: continue, texto = d.str("texto") ?: "",
                criadaEm = d.long("criadaEm"), editadaEm = (d["editadaEm"] as? Number)?.toLong(), uuid = uuid,
                apagada = d.bool("apagada"), atualizadoEm = versao, pendente = false,
            )
            if (local == null) dao.inserirAnotacao(a) else dao.atualizarAnotacao(a)
        }
    }

    suspend fun aplicarFavoritos(docs: List<Pair<String, Map<String, Any?>>>) = db.withTransaction {
        for ((questaoId, d) in docs) {
            val versao = d.long("atualizadoEm")
            val local = dao.favorito(questaoId)
            if (local != null && local.atualizadoEm > versao) { dao.reenviarFavorito(questaoId); continue }
            if (local != null && local.atualizadoEm >= versao) continue
            dao.salvarFavorito(Favorito(questaoId, d.long("desde"), d.bool("ativo"), versao, pendente = false))
        }
    }

    suspend fun aplicarAjustes(docs: List<Pair<String, Map<String, Any?>>>) {
        val d = docs.firstOrNull { it.first == DOC_AJUSTES }?.second ?: return
        val versao = d.long("atualizadoEm")
        val local = ajustes.estadoSync()
        when {
            local.versao > versao -> ajustes.reenviar()
            local.versao < versao -> ajustes.aplicarRemoto(d, versao)
        }
    }
}

private fun Map<String, Any?>.long(k: String) = (this[k] as? Number)?.toLong() ?: 0L
private fun Map<String, Any?>.bool(k: String) = this[k] as? Boolean ?: false
private fun Map<String, Any?>.str(k: String) = this[k] as? String
