package app.revisaquestoes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

enum class Situacao(val rotulo: String) {
    TODAS("Todas"),
    NOVAS("Não resolvidas"),
    ERRADAS("Caderno de erros"),
    ACERTADAS("Acertadas"),
    FAVORITAS("Favoritas"),
    ANOTADAS("Com anotações"),
}

/** Filtros da aba Questões (vazio = sem filtro). */
data class Filtro(
    val materias: Set<String> = emptySet(),
    val incidencias: Set<Int> = emptySet(),
    val situacao: Situacao = Situacao.TODAS,
    val busca: String = "",
) {
    val ativo get() = materias.isNotEmpty() || incidencias.isNotEmpty() || situacao != Situacao.TODAS || busca.isNotBlank()
}

class Repositorio(context: Context) {
    private val app = context.applicationContext
    val dao = AppDatabase.get(app).dao()
    val ajustesRepo = AjustesRepo(app)

    private val _base = MutableStateFlow<BaseQuestoes?>(null)
    /** Questões dos PDFs (null enquanto carrega). */
    val base: StateFlow<BaseQuestoes?> = _base.asStateFlow()

    val respostas: Flow<List<Resposta>> = dao.respostas()
    val anotacoes: Flow<List<Anotacao>> = dao.anotacoes()
    val favoritos: Flow<List<Favorito>> = dao.favoritos()
    val ajustes: Flow<Ajustes> = ajustesRepo.ajustes

    private val _filtro = MutableStateFlow(Filtro())
    val filtro: StateFlow<Filtro> = _filtro.asStateFlow()

    /** Lista de questões que está sendo resolvida (na ordem em que aparecem). */
    var sessao: List<String> = emptyList()
        private set
    var tituloSessao: String = ""
        private set

    suspend fun carregarBase(): BaseQuestoes {
        _base.value?.let { return it }
        val b = withContext(Dispatchers.IO) { BaseQuestoes.carregar(app) }
        _base.value = b
        return b
    }

    fun mudarFiltro(f: (Filtro) -> Filtro) = _filtro.update(f)

    fun iniciarSessao(ids: List<String>, titulo: String, embaralhar: Boolean) {
        sessao = if (embaralhar) ids.shuffled() else ids
        tituloSessao = titulo
    }

    suspend fun responder(q: Questao, respostaCerto: Boolean): Resposta {
        val r = Resposta(
            questaoId = q.id,
            data = System.currentTimeMillis(),
            resposta = respostaCerto,
            acertou = respostaCerto == q.gabarito,
        )
        return r.copy(id = dao.inserirResposta(r))
    }

    suspend fun favoritar(questaoId: String, favorito: Boolean) {
        val agora = System.currentTimeMillis()
        val atual = dao.favorito(questaoId)
        dao.salvarFavorito(
            Favorito(
                questaoId = questaoId,
                desde = if (favorito) agora else atual?.desde ?: agora,
                ativo = favorito, atualizadoEm = agora, pendente = true,
            )
        )
    }

    suspend fun salvarAnotacao(questaoId: String, texto: String, existente: Anotacao?) {
        val agora = System.currentTimeMillis()
        if (existente == null) dao.inserirAnotacao(Anotacao(questaoId = questaoId, texto = texto, criadaEm = agora, atualizadoEm = agora))
        else dao.atualizarAnotacao(existente.copy(texto = texto, editadaEm = agora, atualizadoEm = agora, pendente = true))
    }

    /** Marca como apagada (o apagamento também vai para os outros aparelhos). */
    suspend fun apagarAnotacao(id: Long) {
        val a = dao.anotacao(id) ?: return
        dao.atualizarAnotacao(a.copy(apagada = true, atualizadoEm = System.currentTimeMillis(), pendente = true))
    }

    suspend fun salvarAjustes(a: Ajustes) = ajustesRepo.salvar(a)

    companion object {
        /** Aplica o filtro, mantendo a ordem dos PDFs (por incidência, da Altíssima à Baixa). */
        fun filtrar(
            base: BaseQuestoes,
            f: Filtro,
            estados: Map<String, EstadoQuestao>,
            favoritos: Set<String>,
            anotacoes: Map<String, List<Anotacao>>,
        ): List<Questao> {
            val termos = Analise.normalizar(f.busca).split(Regex("\\s+")).filter { it.isNotBlank() }
            return base.questoes.filter { q ->
                (f.materias.isEmpty() || q.materia in f.materias) &&
                    (f.incidencias.isEmpty() || q.incidencia in f.incidencias) &&
                    when (f.situacao) {
                        Situacao.TODAS -> true
                        Situacao.NOVAS -> q.id !in estados
                        Situacao.ERRADAS -> estados[q.id]?.noCadernoDeErros == true
                        Situacao.ACERTADAS -> estados[q.id]?.ultimaAcertou == true
                        Situacao.FAVORITAS -> q.id in favoritos
                        Situacao.ANOTADAS -> !anotacoes[q.id].isNullOrEmpty()
                    } &&
                    (termos.isEmpty() || run {
                        val texto = Analise.normalizar(
                            q.enunciado + " " + q.comentario + " " + q.assunto + " " +
                                anotacoes[q.id].orEmpty().joinToString(" ") { it.texto }
                        )
                        termos.all { it in texto }
                    })
            }
        }
    }
}
