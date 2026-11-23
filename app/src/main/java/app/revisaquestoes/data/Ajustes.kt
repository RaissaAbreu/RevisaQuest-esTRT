package app.revisaquestoes.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class Tema(val rotulo: String) { SISTEMA("Seguir o sistema"), CLARO("Claro"), ESCURO("Escuro") }

enum class CorTema(val rotulo: String) { ROSA("Rosa bebê"), AZUL("Azul bebê"), VERDE("Verde-água"), ROXO("Roxo claro") }

/** Lembrete: horário + dias da semana (1 = segunda ... 7 = domingo). */
@Serializable
data class Lembrete(
    val id: Int,
    val hora: Int,
    val minuto: Int,
    val dias: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val ativo: Boolean = true,
)

@Serializable
data class Ajustes(
    val tema: Tema = Tema.SISTEMA,
    val cor: CorTema = CorTema.ROSA,
    /** Questões respondidas por dia. */
    val metaDiaria: Int = 30,
    /** Questões respondidas por semana (segunda a domingo). */
    val metaSemanal: Int = 150,
    val notificacoes: Boolean = false,
    val avisarErros: Boolean = true,
    val lembretes: List<Lembrete> = listOf(Lembrete(1, 19, 0)),
    /** Ordem das questões ao resolver: false = por incidência, true = embaralhadas. */
    val embaralhar: Boolean = false,
) {
    /** Só estes campos vão para os outros aparelhos (notificações e lembretes ficam em cada um). */
    fun sincronizados(): Map<String, Any> = mapOf(
        "tema" to tema.name, "cor" to cor.name, "metaDiaria" to metaDiaria.toLong(),
        "metaSemanal" to metaSemanal.toLong(), "embaralhar" to embaralhar,
    )

    fun comSincronizados(d: Map<String, Any?>): Ajustes = copy(
        tema = (d["tema"] as? String)?.let { runCatching { Tema.valueOf(it) }.getOrNull() } ?: tema,
        cor = (d["cor"] as? String)?.let { runCatching { CorTema.valueOf(it) }.getOrNull() } ?: cor,
        metaDiaria = (d["metaDiaria"] as? Number)?.toInt() ?: metaDiaria,
        metaSemanal = (d["metaSemanal"] as? Number)?.toInt() ?: metaSemanal,
        embaralhar = d["embaralhar"] as? Boolean ?: embaralhar,
    )
}

/** Situação dos ajustes na sincronização: versão = momento da última mudança dos campos sincronizados. */
data class AjustesSync(val ajustes: Ajustes, val versao: Long, val pendente: Boolean)

private val Context.dataStore by preferencesDataStore("ajustes")

class AjustesRepo(private val context: Context) {
    private val kTema = stringPreferencesKey("tema")
    private val kCor = stringPreferencesKey("cor")
    private val kMeta = intPreferencesKey("meta_diaria")
    private val kMetaSemanal = intPreferencesKey("meta_semanal")
    private val kNotif = booleanPreferencesKey("notificacoes")
    private val kErros = booleanPreferencesKey("avisar_erros")
    private val kLembretes = stringPreferencesKey("lembretes")
    private val kEmbaralhar = booleanPreferencesKey("embaralhar")
    private val kSyncVersao = longPreferencesKey("sync_versao")
    private val kSyncPendente = booleanPreferencesKey("sync_pendente")

    private val json = Json { ignoreUnknownKeys = true }

    val ajustes: Flow<Ajustes> = context.dataStore.data.map { ler(it) }

    suspend fun atual(): Ajustes = ajustes.first()

    suspend fun salvar(a: Ajustes) {
        context.dataStore.edit { p ->
            val antes = ler(p)
            gravar(p, a)
            // mudou algo que vai para os outros aparelhos: entra na fila de envio
            if (antes.sincronizados() != a.sincronizados()) {
                p[kSyncVersao] = System.currentTimeMillis()
                p[kSyncPendente] = true
            }
        }
    }

    private fun ler(p: androidx.datastore.preferences.core.Preferences): Ajustes {
        val padrao = Ajustes()
        return Ajustes(
            tema = p[kTema]?.let { runCatching { Tema.valueOf(it) }.getOrNull() } ?: padrao.tema,
            cor = p[kCor]?.let { runCatching { CorTema.valueOf(it) }.getOrNull() } ?: padrao.cor,
            metaDiaria = p[kMeta] ?: padrao.metaDiaria,
            metaSemanal = p[kMetaSemanal] ?: padrao.metaSemanal,
            notificacoes = p[kNotif] ?: padrao.notificacoes,
            avisarErros = p[kErros] ?: padrao.avisarErros,
            lembretes = p[kLembretes]?.let { runCatching { json.decodeFromString<List<Lembrete>>(it) }.getOrNull() }
                ?: padrao.lembretes,
            embaralhar = p[kEmbaralhar] ?: padrao.embaralhar,
        )
    }

    private fun gravar(p: androidx.datastore.preferences.core.MutablePreferences, a: Ajustes) {
        p[kTema] = a.tema.name
        p[kCor] = a.cor.name
        p[kMeta] = a.metaDiaria
        p[kMetaSemanal] = a.metaSemanal
        p[kNotif] = a.notificacoes
        p[kErros] = a.avisarErros
        p[kLembretes] = json.encodeToString(a.lembretes)
        p[kEmbaralhar] = a.embaralhar
    }

    // ---- sincronização

    suspend fun estadoSync(): AjustesSync {
        val p = context.dataStore.data.first()
        // ajustes nunca mudados neste aparelho: versão 0 e nada a enviar
        return AjustesSync(ler(p), p[kSyncVersao] ?: 0L, p[kSyncPendente] ?: false)
    }

    /** Recebe os ajustes de outro aparelho (só os campos sincronizados). */
    suspend fun aplicarRemoto(d: Map<String, Any?>, versao: Long) {
        context.dataStore.edit { p ->
            gravar(p, ler(p).comSincronizados(d))
            p[kSyncVersao] = versao
            p[kSyncPendente] = false
        }
    }

    suspend fun marcarEnviado(versao: Long) {
        context.dataStore.edit { p -> if ((p[kSyncVersao] ?: 0L) == versao) p[kSyncPendente] = false }
    }

    suspend fun reenviar() {
        context.dataStore.edit { p -> p[kSyncPendente] = true }
    }
}
