package app.revisaquestoes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisaquestoes.data.Ajustes
import app.revisaquestoes.ui.CartaoQuestao
import app.revisaquestoes.ui.LocalRepo
import app.revisaquestoes.ui.Nav
import app.revisaquestoes.ui.Vazio
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrosTela(nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val d = rememberDados()
    val ajustes by repo.ajustes.collectAsStateWithLifecycle(Ajustes())
    val base = d.base
    var materia by rememberSaveable { mutableStateOf<String?>(null) }

    val todas = remember(d) { d.cadernoDeErros.mapNotNull { base?.porId?.get(it) } }
    val lista = todas.filter { materia == null || it.materia == materia }
    val porMateria = todas.groupingBy { it.materia }.eachCount()

    TelaBase(titulo = "Caderno de erros", subtitulo = "Saem daqui quando você acerta ao refazer") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (todas.isEmpty()) {
                item { Vazio(if (d.respostas.isEmpty()) "Resolva questões: as que você errar aparecem aqui." else "Nenhuma questão para refazer. Muito bem!") }
            } else {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = materia == null, onClick = { materia = null }, label = { Text("Todas (${todas.size})") })
                        base?.materias?.filter { (porMateria[it.id] ?: 0) > 0 }?.forEach { m ->
                            FilterChip(
                                selected = materia == m.id,
                                onClick = { materia = if (materia == m.id) null else m.id },
                                label = { Text("${m.nome} (${porMateria[m.id]})") },
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            repo.iniciarSessao(lista.map { it.id }, "Caderno de erros", ajustes.embaralhar)
                            nav.resolver(0)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp))
                        Text("Refazer ${lista.size} questões erradas")
                    }
                }
                itemsIndexed(lista, key = { _, q -> q.id }) { i, q ->
                    CartaoQuestao(
                        q = q,
                        estado = d.estados[q.id],
                        favorito = q.id in d.favoritos,
                        totalAnotacoes = d.anotacoes[q.id]?.size ?: 0,
                        materia = if (materia == null) base?.nomeMateria(q.materia) else null,
                        onClick = {
                            repo.iniciarSessao(lista.map { it.id }, "Caderno de erros", embaralhar = false)
                            nav.resolver(i)
                        },
                        onFavorito = { escopo.launch { repo.favoritar(q.id, q.id !in d.favoritos) } },
                    )
                }
                item {
                    Text(
                        "Toque numa questão para refazer. Se acertar, ela sai do caderno; se errar de novo, continua aqui.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
