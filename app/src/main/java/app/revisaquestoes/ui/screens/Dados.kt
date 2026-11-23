package app.revisaquestoes.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisaquestoes.data.Anotacao
import app.revisaquestoes.data.Analise
import app.revisaquestoes.data.BaseQuestoes
import app.revisaquestoes.data.EstadoQuestao
import app.revisaquestoes.data.Placar
import app.revisaquestoes.data.Resposta
import app.revisaquestoes.ui.LocalRepo

/** Tudo o que as telas precisam: questões + seu progresso. */
class Dados(
    val base: BaseQuestoes?,
    val respostas: List<Resposta>,
    val estados: Map<String, EstadoQuestao>,
    val favoritos: Set<String>,
    val anotacoes: Map<String, List<Anotacao>>,
) {
    val cadernoDeErros: List<String> by lazy {
        base?.questoes?.filter { estados[it.id]?.noCadernoDeErros == true }?.map { it.id }.orEmpty()
    }

    /** Placar por matéria (todas as tentativas contam). */
    val placarPorMateria: Map<String, Placar> by lazy {
        val b = base ?: return@lazy emptyMap()
        respostas.groupBy { b.porId[it.questaoId]?.materia }.filterKeys { it != null }
            .mapKeys { it.key!! }.mapValues { Placar.de(it.value) }
    }

    val placarPorIncidencia: Map<Int, Placar> by lazy {
        val b = base ?: return@lazy emptyMap()
        respostas.groupBy { b.porId[it.questaoId]?.incidencia }.filterKeys { it != null }
            .mapKeys { it.key!! }.mapValues { Placar.de(it.value) }
    }

    /** Questões distintas já resolvidas por matéria. */
    val resolvidasPorMateria: Map<String, Int> by lazy {
        val b = base ?: return@lazy emptyMap()
        estados.keys.mapNotNull { b.porId[it]?.materia }.groupingBy { it }.eachCount()
    }

    /** Respostas só das questões que ainda existem na base. */
    val placarGeral: Placar by lazy {
        val b = base ?: return@lazy Placar()
        Placar.de(respostas.filter { it.questaoId in b.porId })
    }
}

@Composable
fun rememberDados(): Dados {
    val repo = LocalRepo.current
    val base by repo.base.collectAsStateWithLifecycle()
    val respostas by repo.respostas.collectAsStateWithLifecycle(emptyList())
    val favoritos by repo.favoritos.collectAsStateWithLifecycle(emptyList())
    val anotacoes by repo.anotacoes.collectAsStateWithLifecycle(emptyList())
    return remember(base, respostas, favoritos, anotacoes) {
        Dados(
            base = base,
            respostas = respostas,
            estados = Analise.estados(respostas),
            favoritos = favoritos.map { it.questaoId }.toSet(),
            anotacoes = anotacoes.groupBy { it.questaoId },
        )
    }
}
