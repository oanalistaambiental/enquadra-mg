package br.com.oanalistaambiental.enquadramento

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.oanalistaambiental.enquadramento.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Cores.acento,
                    background = Cores.fundo,
                    surface = Cores.superficie,
                    onBackground = Cores.texto,
                    onSurface = Cores.texto
                )
            ) { Surface(color = Cores.fundo) { App() } }
        }
    }
}

private enum class Rota { INICIO, ATIVIDADE, PORTE, LOCACIONAL, RESULTADO, NORMA }

@Composable
private fun App() {
    val vm: SimulacaoViewModel = viewModel()
    val contexto = LocalContext.current
    var rota by remember { mutableStateOf(Rota.INICIO) }

    Box(Modifier.fillMaxSize()) {
        when (rota) {
            Rota.INICIO -> TelaInicio(vm,
                irParaSimulacao = { rota = Rota.ATIVIDADE },
                irParaNorma = { rota = Rota.NORMA })
            Rota.ATIVIDADE -> TelaAtividade(vm,
                avancar = { rota = Rota.PORTE },
                voltar = { rota = Rota.INICIO })
            Rota.PORTE -> TelaPorte(vm,
                avancar = { rota = Rota.LOCACIONAL },
                voltar = { rota = Rota.ATIVIDADE })
            Rota.LOCACIONAL -> TelaLocacional(vm,
                avancar = { rota = Rota.RESULTADO },
                voltar = { rota = Rota.PORTE })
            Rota.RESULTADO -> TelaResultado(vm,
                exportar = { vm.exportarPdf(contexto) },
                novaSimulacao = { vm.novaSimulacao(); rota = Rota.ATIVIDADE },
                voltar = { rota = Rota.LOCACIONAL })
            Rota.NORMA -> TelaNorma(vm) { rota = Rota.INICIO }
        }
        Mensagem(vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Mensagem(vm: SimulacaoViewModel, modifier: Modifier) {
    val mensagem by vm.mensagem.collectAsState()
    mensagem?.let { texto ->
        LaunchedEffect(texto) { delay(5000); vm.limparMensagem() }
        Text(
            texto, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp).background(Color(0xF2222629)).padding(14.dp)
        )
    }
}
