package app.revisaquestoes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisaquestoes.data.Ajustes
import app.revisaquestoes.data.Analise
import app.revisaquestoes.data.Filtro
import app.revisaquestoes.ui.BarraProgresso
import app.revisaquestoes.ui.CartaoClicavel
import app.revisaquestoes.ui.LocalRepo
import app.revisaquestoes.ui.Nav
import app.revisaquestoes.ui.TituloSecao
import app.revisaquestoes.ui.theme.LocalCores
import app.revisaquestoes.ui.theme.corDaMateria

@Composable
fun InicioTela(nav: Nav) {
    val repo = LocalRepo.current
    val d = rememberDados()
    val ajustes by repo.ajustes.collectAsStateWithLifecycle(Ajustes())
    val cores = LocalCores.current

    val hoje = Analise.respondidasHoje(d.respostas)
    val semana = Analise.respondidasNaSemana(d.respostas)
    val dias = Analise.diasParaProva()
    val base = d.base
    val total = base?.questoes?.size ?: 0
    val resolvidas = d.estados.keys.count { base?.porId?.containsKey(it) == true }
    val novas = base?.questoes?.filter { it.id !in d.estados }.orEmpty()

    TelaBase(titulo = "RevisaQuestõesTRT", subtitulo = "TRT-RS · Analista Judiciário") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Contagem regressiva + metas
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (dias > 0) "Faltam $dias dias para a prova" else if (dias == 0L) "A prova é hoje! Boa prova!" else "Prova realizada",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(Analise.data(Analise.DATA_PROVA), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Meta de hoje: $hoje de ${ajustes.metaDiaria} questões" + if (hoje >= ajustes.metaDiaria) "  ✓" else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    BarraProgresso(hoje / ajustes.metaDiaria.coerceAtLeast(1).toFloat())
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Semana: $semana de ${ajustes.metaSemanal} questões",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    BarraProgresso(semana / ajustes.metaSemanal.coerceAtLeast(1).toFloat(), cor = MaterialTheme.colorScheme.secondary)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CartaoClicavel({ nav.aba("desempenho") }, MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f)) {
                        Column {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.height(6.dp))
                            Text("${d.placarGeral.texto} de acerto", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("$resolvidas de $total resolvidas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    CartaoClicavel({ nav.aba("erros") }, cores.atencaoFundo, Modifier.weight(1f)) {
                        Column {
                            Icon(Icons.Filled.Warning, null, tint = cores.atencao)
                            Spacer(Modifier.height(6.dp))
                            Text("${d.cadernoDeErros.size} para refazer", fontWeight = FontWeight.SemiBold, color = cores.noAtencao)
                            Text("caderno de erros", style = MaterialTheme.typography.bodySmall, color = cores.noAtencao)
                        }
                    }
                }
            }
            if (novas.isNotEmpty()) {
                item {
                    Button(
                        onClick = {
                            repo.iniciarSessao(novas.map { it.id }, "Não resolvidas", ajustes.embaralhar)
                            nav.resolver(0)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Resolver questões novas (${novas.size})")
                    }
                }
            }

            item { TituloSecao("Matérias") }
            if (base == null) item { Text("Carregando questões…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(base?.materias.orEmpty(), key = { it.id }) { m ->
                val qtd = base!!.questoes.count { it.materia == m.id }
                val feitas = d.resolvidasPorMateria[m.id] ?: 0
                val placar = d.placarPorMateria[m.id]
                CartaoClicavel(
                    {
                        repo.mudarFiltro { Filtro(materias = setOf(m.id)) }
                        nav.aba("questoes")
                    },
                    MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(corDaMateria(cores, m.ordem)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            val partes = mutableListOf("$feitas/$qtd resolvidas")
                            if (placar != null && placar.respostas > 0) partes += "${placar.percentual}% de acerto"
                            Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            BarraProgresso(feitas / qtd.coerceAtLeast(1).toFloat())
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
