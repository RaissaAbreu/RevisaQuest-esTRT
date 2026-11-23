package app.revisaquestoes.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Matéria (uma por PDF). */
@Serializable
data class Materia(val id: String, val nome: String, val ordem: Int)

/**
 * Questão Certo/Errado, gerada a partir dos PDFs por tools/gerar_questoes.py.
 * [gabarito] = true quando a afirmação está CERTA.
 * [incidencia]: 4 = Altíssima, 3 = Alta, 2 = Média, 1 = Baixa.
 */
@Serializable
data class Questao(
    val id: String,
    val materia: String,
    val numero: Int,
    val incidencia: Int,
    val ponto: Int,
    val assunto: String,
    val enunciado: String,
    val gabarito: Boolean,
    val comentario: String,
)

@Serializable
data class BaseQuestoes(
    val versao: String,
    val materias: List<Materia>,
    val questoes: List<Questao>,
) {
    val porId: Map<String, Questao> by lazy { questoes.associateBy { it.id } }
    val materiaPorId: Map<String, Materia> by lazy { materias.associateBy { it.id } }

    fun nomeMateria(id: String) = materiaPorId[id]?.nome ?: id

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun carregar(context: Context): BaseQuestoes =
            context.assets.open("questoes.json").bufferedReader(Charsets.UTF_8).use {
                json.decodeFromString(serializer(), it.readText())
            }
    }
}

object Incidencia {
    val niveis = listOf(4, 3, 2, 1)

    fun nome(i: Int) = when (i) {
        4 -> "Altíssima"
        3 -> "Alta"
        2 -> "Média"
        else -> "Baixa"
    }
}
