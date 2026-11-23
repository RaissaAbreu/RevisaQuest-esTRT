package app.revisaquestoes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.revisaquestoes.data.Analise
import app.revisaquestoes.data.Anotacao
import app.revisaquestoes.data.Questao
import app.revisaquestoes.ui.CorFavorito
import app.revisaquestoes.ui.Etiqueta
import app.revisaquestoes.ui.EtiquetaIncidencia
import app.revisaquestoes.ui.FaixaResultado
import app.revisaquestoes.ui.LocalRepo
import app.revisaquestoes.ui.Nav
import app.revisaquestoes.ui.TituloSecao
import app.revisaquestoes.ui.Vazio
import app.revisaquestoes.ui.theme.LocalCores
import kotlinx.coroutines.launch

private fun certoErrado(b: Boolean) = if (b) "Certo" else "Errado"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestaoTela(indiceInicial: Int, nav: Nav) {
    val repo = LocalRepo.current
    val escopo = rememberCoroutineScope()
    val cores = LocalCores.current
    val d = rememberDados()
    val ids = repo.sessao
    var indice by rememberSaveable { mutableStateOf(indiceInicial) }

    val base = d.base
    val q: Questao? = ids.getOrNull(indice)?.let { base?.porId?.get(it) }
    if (base == null || q == null) {
        TelaBase(titulo = "Questão", voltar = nav::voltar) { p ->
            Column(Modifier.padding(p)) { Vazio(if (base == null) "Carregando…" else "Questão não encontrada.") }
        }
        return
    }

    // resposta dada agora nesta tela (null = ainda não respondeu)
    var escolha by rememberSaveable(q.id) { mutableStateOf<Boolean?>(null) }
    var editandoNota by remember { mutableStateOf<Anotacao?>(null) }
    var novaNota by remember { mutableStateOf(false) }
    var notaApagar by remember { mutableStateOf<Anotacao?>(null) }
    val estado = d.estados[q.id]
    val favorito = q.id in d.favoritos
    val notas = d.anotacoes[q.id].orEmpty()
    val lista = rememberLazyListState()

    LaunchedEffect(q.id) { lista.scrollToItem(0) }
    LaunchedEffect(escolha) { if (escolha != null) lista.animateScrollToItem(2) }

    TelaBase(
        titulo = repo.tituloSessao.ifEmpty { base.nomeMateria(q.materia) },
        subtitulo = "Questão ${indice + 1} de ${ids.size}",
        voltar = nav::voltar,
        acoes = {
            IconButton(onClick = { escopo.launch { repo.favoritar(q.id, !favorito) } }) {
                Icon(
                    if (favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (favorito) "Remover dos favoritos" else "Favoritar",
                    tint = if (favorito) CorFavorito else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.weight(1f),
                state = lista,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 0: cabeçalho
                item {
                    Column {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            EtiquetaIncidencia(q.incidencia)
                            Etiqueta(base.nomeMateria(q.materia), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            Etiqueta("Questão ${q.numero}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ponto ${q.ponto} · ${q.assunto}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 1: enunciado + botões
                item {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SelectionContainer {
                                Text(q.enunciado, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (escolha == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(true, false).forEach { opcao ->
                                    Button(
                                        onClick = {
                                            escolha = opcao
                                            escopo.launch { repo.responder(q, opcao) }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (opcao) cores.certo else cores.errado,
                                            contentColor = cores.noBotao,
                                        ),
                                        modifier = Modifier.weight(1f).height(56.dp),
                                    ) {
                                        Icon(if (opcao) Icons.Filled.Done else Icons.Filled.Close, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(certoErrado(opcao), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        if (estado != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Você já respondeu ${estado.respostas}x · ${estado.acertos} acerto(s) · última ${Analise.relativo(estado.ultimaData)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // 2: resultado + gabarito comentado
                val e = escolha
                if (e != null) {
                    item {
                        val acertou = e == q.gabarito
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FaixaResultado(acertou, "Sua resposta: ${certoErrado(e)} · Gabarito: ${certoErrado(q.gabarito)}")
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp)
                            ) {
                                Text("Gabarito comentado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Etiqueta(
                                    certoErrado(q.gabarito).uppercase(),
                                    if (q.gabarito) cores.certo else cores.errado, cores.noBotao,
                                )
                                Spacer(Modifier.height(8.dp))
                                SelectionContainer { Text(q.comentario, style = MaterialTheme.typography.bodyLarge) }
                            }
                            TextButton(onClick = { escolha = null }) {
                                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Refazer esta questão")
                            }
                        }
                    }
                }
                item {
                    TituloSecao("Anotações (${notas.size})") {
                        TextButton(onClick = { novaNota = true }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Nova")
                        }
                    }
                }
                if (notas.isEmpty()) {
                    item {
                        Text(
                            "Escreva aqui o que quiser lembrar desta questão: a pegadinha, o artigo, um macete.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(notas, key = { it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            SelectionContainer { Text(n.texto, style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Analise.dataHora(n.criadaEm) + (n.editadaEm?.let { " · editada ${Analise.dataHora(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editandoNota = n }) { Icon(Icons.Filled.Edit, "Editar anotação", Modifier.size(20.dp)) }
                        IconButton(onClick = { notaApagar = n }) { Icon(Icons.Filled.Delete, "Apagar anotação", Modifier.size(20.dp)) }
                    }
                }
            }

            // Anterior / Próxima
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { indice-- }, enabled = indice > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Anterior")
                    }
                    if (indice < ids.lastIndex) {
                        Button(onClick = { indice++ }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("Próxima"); Spacer(Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                        }
                    } else {
                        Button(onClick = nav::voltar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Concluir") }
                    }
                }
            }
        }
    }

    if (novaNota || editandoNota != null) {
        val existente = editandoNota
        var texto by remember(existente) { mutableStateOf(existente?.texto ?: "") }
        AlertDialog(
            onDismissRequest = { novaNota = false; editandoNota = null },
            title = { Text(if (existente == null) "Nova anotação" else "Editar anotação") },
            text = {
                OutlinedTextField(
                    value = texto, onValueChange = { texto = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 4,
                    placeholder = { Text("Sua anotação") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = texto.trim()
                    escopo.launch { if (t.isNotEmpty()) repo.salvarAnotacao(q.id, t, existente) }
                    novaNota = false; editandoNota = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { novaNota = false; editandoNota = null }) { Text("Cancelar") } },
        )
    }
    notaApagar?.let { n ->
        AlertDialog(
            onDismissRequest = { notaApagar = null },
            title = { Text("Apagar anotação?") },
            text = { Text(n.texto, maxLines = 4) },
            confirmButton = {
                TextButton(onClick = { escopo.launch { repo.apagarAnotacao(n.id) }; notaApagar = null }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { notaApagar = null }) { Text("Cancelar") } },
        )
    }
}
