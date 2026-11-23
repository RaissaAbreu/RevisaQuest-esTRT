package app.revisaquestoes.data

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.temporal.ChronoUnit

/** Como está cada questão, a partir de todas as tentativas. */
data class EstadoQuestao(
    val respostas: Int,
    val acertos: Int,
    /** Resultado da tentativa mais recente. */
    val ultimaAcertou: Boolean,
    val ultimaData: Long,
) {
    val erros get() = respostas - acertos
    /** Fica no caderno de erros enquanto a última tentativa estiver errada. */
    val noCadernoDeErros get() = !ultimaAcertou
}

/** Acertos sobre respostas (todas as tentativas contam). */
data class Placar(val respostas: Int = 0, val acertos: Int = 0) {
    val erros get() = respostas - acertos
    val fracao: Float get() = if (respostas == 0) 0f else acertos / respostas.toFloat()
    val percentual: Int get() = if (respostas == 0) 0 else Math.round(acertos * 100f / respostas)
    val texto: String get() = if (respostas == 0) "–" else "$percentual%"

    operator fun plus(r: Resposta) = Placar(respostas + 1, acertos + if (r.acertou) 1 else 0)

    companion object {
        fun de(rs: Iterable<Resposta>) = rs.fold(Placar()) { p, r -> p + r }
    }
}

data class DiaRespostas(val dia: LocalDate, val acertos: Int, val erros: Int) {
    val total get() = acertos + erros
}

object Analise {
    private val zona: ZoneId get() = ZoneId.systemDefault()
    private val br = Locale("pt", "BR")
    private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy", br)
    private val fmtDataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", br)

    val DATA_PROVA: LocalDate = LocalDate.of(2027, 1, 31)

    fun dia(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
    fun hoje(): LocalDate = LocalDate.now(zona)
    fun inicioDoDia(d: LocalDate): Long = d.atStartOfDay(zona).toInstant().toEpochMilli()

    fun diasParaProva(): Long = ChronoUnit.DAYS.between(hoje(), DATA_PROVA)

    fun data(ms: Long): String = dia(ms).format(fmtData)
    fun data(d: LocalDate): String = d.format(fmtData)
    fun dataHora(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zona).format(fmtDataHora)

    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    /** Estado de cada questão já respondida (as respostas devem vir em ordem de data). */
    fun estados(respostas: List<Resposta>): Map<String, EstadoQuestao> {
        val m = HashMap<String, EstadoQuestao>()
        for (r in respostas.sortedBy { it.data }) {
            val a = m[r.questaoId]
            m[r.questaoId] = EstadoQuestao(
                respostas = (a?.respostas ?: 0) + 1,
                acertos = (a?.acertos ?: 0) + if (r.acertou) 1 else 0,
                ultimaAcertou = r.acertou,
                ultimaData = r.data,
            )
        }
        return m
    }

    fun respondidasHoje(respostas: List<Resposta>): Int {
        val inicio = inicioDoDia(hoje())
        return respostas.count { it.data >= inicio }
    }

    fun respondidasNaSemana(respostas: List<Resposta>): Int {
        val h = hoje()
        val inicio = inicioDoDia(h.minusDays((h.dayOfWeek.value - 1).toLong()))
        return respostas.count { it.data >= inicio }
    }

    /** Dias seguidos com pelo menos uma resposta (até hoje ou ontem). */
    fun sequencia(respostas: List<Resposta>): Int {
        val dias = respostas.map { dia(it.data) }.toSet()
        var d = hoje()
        if (d !in dias) d = d.minusDays(1)
        var n = 0
        while (d in dias) { n++; d = d.minusDays(1) }
        return n
    }

    fun recorde(respostas: List<Resposta>): Int {
        val dias = respostas.map { dia(it.data) }.toSortedSet()
        var melhor = 0; var atual = 0; var anterior: LocalDate? = null
        for (d in dias) {
            atual = if (anterior != null && anterior.plusDays(1) == d) atual + 1 else 1
            melhor = maxOf(melhor, atual); anterior = d
        }
        return melhor
    }

    /** Acertos e erros por dia nos últimos [n] dias (mais antigo primeiro). */
    fun porDia(respostas: List<Resposta>, n: Int): List<DiaRespostas> {
        val grupos = respostas.groupBy { dia(it.data) }
        val h = hoje()
        return (n - 1 downTo 0).map { h.minusDays(it.toLong()) }.map { d ->
            val rs = grupos[d].orEmpty()
            DiaRespostas(d, rs.count { it.acertou }, rs.count { !it.acertou })
        }
    }

    fun relativo(ms: Long): String {
        val d = ChronoUnit.DAYS.between(dia(ms), hoje())
        return when (d) {
            0L -> "hoje"
            1L -> "ontem"
            else -> "há $d dias"
        }
    }
}
