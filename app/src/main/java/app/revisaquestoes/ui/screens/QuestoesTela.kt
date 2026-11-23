package app.revisaquestoes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisaquestoes.data.Ajustes
import app.revisaquestoes.data.Filtro
import app.revisaquestoes.data.Incidencia
import app.revisaquestoes.data.Repositorio
import app.revisaquestoes.data.Situacao
import app.revisaquestoes.ui.CartaoQuestao
import app.revisaquestoes.ui.LocalRepo
import app.revisaquestoes.ui.Nav
import app.revisaquestoes.ui.Vazio
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestoesTela(nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val d = rememberDados()
    val f by repo.filtro.collectAsStateWithLifecycle()
    val ajustes by repo.ajustes.collectAsStateWithLifecycle(Ajustes())
    val base = d.base

    val lista = remember(d, f) {
        if (base == null) emptyList() else Repositorio.filtrar(base, f, d.estados, d.favoritos, d.anotacoes)
    }
    val estadoLista = rememberLazyListState()

    fun titulo(): String {
        val partes = mutableListOf<String>()
        if (f.materias.size == 1) partes += base?.nomeMateria(f.materias.first()).orEmpty()
        if (f.incidencias.isNotEmpty()) partes += f.incidencias.sortedDescending().joinToString("/") { Incidencia.nome(it) }
        if (f.situacao != Situacao.TODAS) partes += f.situacao.rotulo
        return partes.joinToString(" · ").ifEmpty { "Questões" }
    }

    TelaBase(titulo = "Questões", subtitulo = if (base == null) "Carregando…" else "${lista.size} de ${base.questoes.size}") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = estadoLista,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = f.busca,
                    onValueChange = { t -> repo.mudarFiltro { it.copy(busca = t) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar no enunciado, comentário ou anotações") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (f.busca.isNotEmpty()) IconButton(onClick = { repo.mudarFiltro { it.copy(busca = "") } }) {
                            Icon(Icons.Filled.Clear, "Limpar busca")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                Rotulo("Matéria")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    base?.materias?.forEach { m ->
                        FilterChip(
                            selected = m.id in f.materias,
                            onClick = { repo.mudarFiltro { it.copy(materias = alternar(it.materias, m.id)) } },
                            label = { Text(m.nome) },
                        )
                    }
                }
            }
            item {
                Rotulo("Incidência")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Incidencia.niveis.forEach { i ->
                        FilterChip(
                            selected = i in f.incidencias,
                            onClick = { repo.mudarFiltro { it.copy(incidencias = alternar(it.incidencias, i)) } },
                            label = { Text(Incidencia.nome(i)) },
                        )
                    }
                }
            }
            item {
                Rotulo("Situação")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Situacao.entries.forEach { s ->
                        FilterChip(
                            selected = f.situacao == s,
                            onClick = { repo.mudarFiltro { it.copy(situacao = s) } },
                            label = { Text(s.rotulo) },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            repo.iniciarSessao(lista.map { it.id }, titulo(), ajustes.embaralhar)
                            nav.resolver(0)
                        },
                        enabled = lista.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Resolver ${lista.size} questões")
                    }
                    if (f.ativo) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            repo.mudarFiltro { Filtro() }
                            escopo.launch { estadoLista.scrollToItem(0) }
                        }) { Text("Limpar filtros") }
                    }
                }
            }
            if (base != null && lista.isEmpty()) item { Vazio("Nenhuma questão com esses filtros.") }
            itemsIndexed(lista, key = { _, q -> q.id }) { i, q ->
                CartaoQuestao(
                    q = q,
                    estado = d.estados[q.id],
                    favorito = q.id in d.favoritos,
                    totalAnotacoes = d.anotacoes[q.id]?.size ?: 0,
                    materia = if (f.materias.size == 1) null else base?.nomeMateria(q.materia),
                    onClick = {
                        // a ordem da lista é mantida para poder seguir com "Próxima"
                        repo.iniciarSessao(lista.map { it.id }, titulo(), embaralhar = false)
                        nav.resolver(i)
                    },
                    onFavorito = { escopo.launch { repo.favoritar(q.id, q.id !in d.favoritos) } },
                )
            }
        }
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
    )
}

private fun <T> alternar(conjunto: Set<T>, item: T) = if (item in conjunto) conjunto - item else conjunto + item
