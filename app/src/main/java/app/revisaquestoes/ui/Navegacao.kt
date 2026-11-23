package app.revisaquestoes.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.revisaquestoes.ui.screens.AjustesTela
import app.revisaquestoes.ui.screens.DesempenhoTela
import app.revisaquestoes.ui.screens.ErrosTela
import app.revisaquestoes.ui.screens.InicioTela
import app.revisaquestoes.ui.screens.QuestaoTela
import app.revisaquestoes.ui.screens.QuestoesTela

private data class Aba(val rota: String, val rotulo: String, val icone: ImageVector)

private val abas = listOf(
    Aba("inicio", "Início", Icons.Filled.Home),
    Aba("questoes", "Questões", Icons.AutoMirrored.Filled.List),
    Aba("erros", "Erros", Icons.Filled.Warning),
    Aba("desempenho", "Desempenho", Icons.Filled.Info),
    Aba("ajustes", "Ajustes", Icons.Filled.Settings),
)

/** Ações de navegação usadas pelas telas. */
class Nav(private val nav: NavHostController) {
    fun voltar() { nav.popBackStack() }
    /** Abre a questão [indice] da sessão atual (Repositorio.sessao). */
    fun resolver(indice: Int = 0) = nav.navigate("resolver/$indice")
    fun aba(rota: String) = nav.navigate(rota) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavegacao() {
    val controller = rememberNavController()
    val nav = Nav(controller)
    val entrada by controller.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route
    Scaffold(
        bottomBar = {
            if (abas.any { it.rota == rotaAtual }) {
                NavigationBar {
                    abas.forEach { a ->
                        NavigationBarItem(
                            selected = rotaAtual == a.rota,
                            onClick = { nav.aba(a.rota) },
                            icon = { Icon(a.icone, contentDescription = null) },
                            label = { Text(a.rotulo, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = "inicio", modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable("inicio") { InicioTela(nav) }
            composable("questoes") { QuestoesTela(nav) }
            composable("erros") { ErrosTela(nav) }
            composable("desempenho") { DesempenhoTela(nav) }
            composable("ajustes") { AjustesTela() }
            composable("resolver/{indice}", arguments = listOf(navArgument("indice") { type = NavType.IntType })) {
                QuestaoTela(it.arguments!!.getInt("indice"), nav)
            }
        }
    }
}
