package app.revisaquestoes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.revisaquestoes.data.EstadoQuestao
import app.revisaquestoes.data.Incidencia
import app.revisaquestoes.data.Questao
import app.revisaquestoes.data.Repositorio
import app.revisaquestoes.ui.theme.LocalCores

val LocalRepo = compositionLocalOf<Repositorio> { error("Repositorio não fornecido") }

val CorFavorito = Color(0xFFE57FA6)

@Composable
fun Etiqueta(texto: String, fundo: Color, cor: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = cor,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(fundo)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Pílula colorida com a incidência do assunto (Altíssima, Alta, Média, Baixa). */
@Composable
fun EtiquetaIncidencia(incidencia: Int, completa: Boolean = true) {
    val c = LocalCores.current
    Etiqueta(
        if (completa) "Incidência ${Incidencia.nome(incidencia)}" else Incidencia.nome(incidencia),
        c.importancia[incidencia.coerceIn(0, 4)], c.noImportancia,
    )
}

@Composable
fun EtiquetaSituacao(estado: EstadoQuestao?) {
    val c = LocalCores.current
    when {
        estado == null -> Etiqueta("Não resolvida", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        estado.ultimaAcertou -> Etiqueta("Acertou", c.acertoFundo, c.noAcerto)
        else -> Etiqueta("Errou", c.erroFundo, c.noErro)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoQuestao(
    q: Questao,
    estado: EstadoQuestao?,
    favorito: Boolean,
    totalAnotacoes: Int,
    onClick: () -> Unit,
    onFavorito: () -> Unit,
    materia: String? = null,
) {
    val c = LocalCores.current
    val errou = estado?.noCadernoDeErros == true
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (errou) Modifier.border(1.5.dp, c.atencao, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (materia != null) "$materia · " else "") + "Questão ${q.numero} · Ponto ${q.ponto}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(q.enunciado, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EtiquetaIncidencia(q.incidencia, completa = false)
                    EtiquetaSituacao(estado)
                    if (estado != null && estado.respostas > 1) {
                        Etiqueta("${estado.acertos}/${estado.respostas} acertos", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (totalAnotacoes > 0) {
                        Etiqueta(
                            "$totalAnotacoes anotaç${if (totalAnotacoes == 1) "ão" else "ões"}",
                            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            IconButton(onClick = onFavorito) {
                Icon(
                    if (favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (favorito) "Remover dos favoritos" else "Favoritar",
                    tint = if (favorito) CorFavorito else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Faixa com o resultado: acertou (verde) ou errou (pêssego). */
@Composable
fun FaixaResultado(acertou: Boolean, detalhe: String, modifier: Modifier = Modifier) {
    val c = LocalCores.current
    val fundo = if (acertou) c.acertoFundo else c.erroFundo
    val cor = if (acertou) c.noAcerto else c.noErro
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(fundo).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(if (acertou) c.certo else c.errado),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (acertou) Icons.Filled.Done else Icons.Filled.Close, null, tint = c.noBotao)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (acertou) "Você acertou!" else "Você errou",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cor,
            )
            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = cor)
        }
    }
}

@Composable
fun TituloSecao(texto: String, modifier: Modifier = Modifier, acao: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        acao?.invoke()
    }
}

@Composable
fun BarraProgresso(fracao: Float, cor: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fracao.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        color = cor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
}

@Composable
fun Vazio(texto: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CartaoClicavel(onClick: () -> Unit, fundo: Color, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(fundo)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) { conteudo() }
}
