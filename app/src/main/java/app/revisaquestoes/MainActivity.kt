package app.revisaquestoes

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisaquestoes.data.Ajustes
import app.revisaquestoes.data.AppDatabase
import app.revisaquestoes.data.Repositorio
import app.revisaquestoes.sync.ServicoSync
import app.revisaquestoes.notify.Lembretes
import app.revisaquestoes.ui.AppNavegacao
import app.revisaquestoes.ui.LocalRepo
import app.revisaquestoes.ui.theme.RevisaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuestoesApp : Application() {
    lateinit var repo: Repositorio
    lateinit var sync: ServicoSync
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repo = Repositorio(this)
        sync = ServicoSync(this, AppDatabase.get(this), repo.ajustesRepo)
        Lembretes.criarCanal(this)
        escopo.launch {
            repo.carregarBase()
            runCatching { sync.iniciar() }
            Lembretes.reagendar(this@QuestoesApp)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as QuestoesApp).repo
        setContent {
            val ajustes by repo.ajustes.collectAsStateWithLifecycle(initialValue = Ajustes())
            RevisaTheme(ajustes.tema, ajustes.cor) {
                CompositionLocalProvider(LocalRepo provides repo) {
                    AppNavegacao()
                }
            }
        }
    }
}

fun versaoDoApp(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }.getOrDefault("")
