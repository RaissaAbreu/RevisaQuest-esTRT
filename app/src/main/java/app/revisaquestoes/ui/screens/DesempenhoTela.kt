package app.revisaquestoes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisaquestoes.data.Ajustes
import app.revisaquestoes.data.Analise
import app.revisaquestoes.data.DiaRespostas
import app.revisaquestoes.data.Filtro
import app.revisaquestoes.data.Incidencia
import app.revisaquestoes.data.Placar
import app.revisaquestoes.ui.BarraProgresso
import app.revisaquestoes.ui.CartaoClicavel
import app.revisaquestoes.ui.LocalRepo
import app.revisaquestoes.ui.Nav
import app.revisaquestoes.ui.TituloSecao
import app.revisaquestoes.ui.theme.LocalCores
import app.revisaquestoes.ui.theme.corDaMateria
import java.time.format.TextStyle
import java.util.Locale

/** Ponto (assunto) de uma matéria com o seu placar. */
private data class PontoPlacar(val materia: String, val ponto: Int, val assunto: String, val placar: Placar, val ids: List<String>)

@Composable
fun DesempenhoTela(nav: Nav) {
    val repo = LocalRepo.current
    val cores = LocalCores.current
    val d = rememberDados()
    val ajustes by repo.ajustes.collectAsStateWithLifecycle(Ajustes())
    val base = d.base
    val total = base?.questoes?.size ?: 0
    val resolvidas = d.estados.keys.count { base?.porId?.containsKey(it) == true }
    val porDia = remember(d) { Analise.porDia(d.respostas, 14) }

    // Assuntos com mais erros (mínimo de 3 respostas)
    val fracos = remember(d) {
        val b = base ?: return@remember emptyList()
        val respostasPorQuestao = d.respostas.groupBy { it.questaoId }
        b.questoes.groupBy { it.materia to it.ponto }.map { (k, qs) ->
            PontoPlacar(k.first, k.second, qs.first().assunto, Placar.de(qs.flatMap { respostasPorQuestao[it.id].orEmpty() }), qs.map { it.id })
        }.filter { it.placar.respostas >= 3 && it.placar.erros > 0 }
            .sortedWith(compareBy<PontoPlacar> { it.placar.fracao }.thenByDescending { it.placar.respostas })
            .take(8)
    }

    TelaBase(titulo = "Desempenho") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Numero("Acerto geral", d.placarGeral.texto, "${d.placarGeral.acertos} de ${d.placarGeral.respostas} respostas", Modifier.weight(1f))
                    Numero("Resolvidas", "$resolvidas", "de $total questões", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Numero("Sequência", "${Analise.sequencia(d.respostas)} dias", "recorde: ${Analise.recorde(d.respostas)} dias", Modifier.weight(1f))
                    Numero(
                        "Hoje", "${Analise.respondidasHoje(d.respostas)}/${ajustes.metaDiaria}",
                        "semana: ${Analise.respondidasNaSemana(d.respostas)}/${ajustes.metaSemanal}", Modifier.weight(1f),
                    )
                }
            }
            item {
                TituloSecao("Questões nos últimos 14 dias")
                GraficoDias(porDia, cores.certo, cores.errado)
            }

            item { TituloSecao("Acerto por incidência") }
            items(Incidencia.niveis) { i ->
                val p = d.placarPorIncidencia[i] ?: Placar()
                LinhaPlacar(Incidencia.nome(i), cores.importancia[i], p)
            }

            item { TituloSecao("Acerto por matéria") }
            items(base?.materias.orEmpty(), key = { it.id }) { m ->
                val p = d.placarPorMateria[m.id] ?: Placar()
                val qtd = base!!.questoes.count { it.materia == m.id }
                val erros = d.cadernoDeErros.count { base.porId[it]?.materia == m.id }
                CartaoClicavel(
                    { repo.mudarFiltro { Filtro(materias = setOf(m.id)) }; nav.aba("questoes") },
                    MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(corDaMateria(cores, m.ordem)))
                            Spacer(Modifier.width(8.dp))
                            Text(m.nome, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(p.texto, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "${d.resolvidasPorMateria[m.id] ?: 0}/$qtd resolvidas · ${p.acertos} acertos · ${p.erros} erros · $erros no caderno",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        BarraProgresso(p.fracao, cor = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (fracos.isNotEmpty()) {
                item {
                    TituloSecao("Assuntos para reforçar")
                    Text(
                        "Pontos com menor % de acerto (com pelo menos 3 respostas). Toque para resolver as questões do ponto.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(fracos, key = { "${it.materia}-${it.ponto}" }) { f ->
                    CartaoClicavel(
                        {
                            repo.iniciarSessao(f.ids, "Ponto ${f.ponto}", embaralhar = false)
                            nav.resolver(0)
                        },
                        cores.atencaoFundo, Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${base?.nomeMateria(f.materia)} · Ponto ${f.ponto}", style = MaterialTheme.typography.labelSmall, color = cores.noAtencao)
                                Text(f.assunto, style = MaterialTheme.typography.bodyMedium, color = cores.noAtencao, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(f.placar.texto, fontWeight = FontWeight.Bold, color = cores.noAtencao)
                                Text("${f.placar.acertos}/${f.placar.respostas}", style = MaterialTheme.typography.labelSmall, color = cores.noAtencao)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaPlacar(rotulo: String, cor: Color, p: Placar) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
            Spacer(Modifier.width(8.dp))
            Text(rotulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (p.respostas == 0) "sem respostas" else "${p.percentual}% · ${p.acertos}/${p.respostas}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        BarraProgresso(p.fracao)
    }
}

@Composable
private fun Numero(rotulo: String, valor: String, detalhe: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Barras empilhadas (acertos embaixo, erros em cima); toque numa barra para ver o dia. */
@Composable
private fun GraficoDias(dias: List<DiaRespostas>, corAcerto: Color, corErro: Color) {
    val grade = MaterialTheme.colorScheme.outlineVariant
    val destaque = MaterialTheme.colorScheme.onSurface
    val fundo = MaterialTheme.colorScheme.surfaceContainerLowest
    var selecionado by remember { mutableStateOf(dias.lastIndex) }
    val max = (dias.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    val br = Locale("pt", "BR")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(fundo).padding(14.dp)) {
        val s = selecionado.coerceIn(0, dias.lastIndex.coerceAtLeast(0))
        dias.getOrNull(s)?.let { x ->
            Text(
                "${x.dia.dayOfWeek.getDisplayName(TextStyle.SHORT, br)} ${Analise.data(x.dia)}: ${x.total} questão(ões)",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (x.total == 0) "Nenhuma resposta" else "${x.acertos} acertos · ${x.erros} erros · ${Math.round(x.acertos * 100f / x.total)}%",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).pointerInput(dias.size) {
                detectTapGestures { o -> selecionado = (o.x / (size.width / dias.size.toFloat())).toInt().coerceIn(0, dias.lastIndex) }
            }
        ) {
            val n = dias.size
            if (n == 0) return@Canvas
            val passo = size.width / n
            val largura = passo * 0.6f
            val alturaUtil = size.height - 4f
            drawLine(grade, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)
            dias.forEachIndexed { i, x ->
                if (x.total == 0) return@forEachIndexed
                val x0 = i * passo + (passo - largura) / 2
                val hA = alturaUtil * x.acertos / max
                val hE = alturaUtil * x.erros / max
                val gap = if (x.acertos > 0 && x.erros > 0) 2.dp.toPx() else 0f
                // erros em cima (cantos arredondados no topo), acertos embaixo
                if (x.erros > 0) barra(Offset(x0, size.height - hA - hE), Size(largura, hE - gap), corErro, true)
                if (x.acertos > 0) barra(Offset(x0, size.height - hA), Size(largura, hA), corAcerto, x.erros == 0)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            dias.forEachIndexed { i, x ->
                Text(
                    if (i % 2 == (dias.size - 1) % 2) x.dia.dayOfMonth.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == s) destaque else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == s) FontWeight.Bold else null,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(corAcerto)); Spacer(Modifier.width(4.dp))
            Text("Acertos", style = MaterialTheme.typography.labelSmall); Spacer(Modifier.width(14.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(corErro)); Spacer(Modifier.width(4.dp))
            Text("Erros", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun DrawScope.barra(topo: Offset, tam: Size, cor: Color, arredondarTopo: Boolean) {
    if (tam.height <= 0f) return
    val r = if (arredondarTopo) minOf(4.dp.toPx(), tam.width / 2, tam.height) else 0f
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = topo.x, top = topo.y, right = topo.x + tam.width, bottom = topo.y + tam.height,
                topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, cor)
}
