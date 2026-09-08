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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private enum class Rota { INICIO, ATIVIDADE, PORTE, LOCACIONAL, RESULTADO, DISPENSA, NORMA }

@Composable
private fun App() {
    val vm: SimulacaoViewModel = viewModel()
    val contexto = LocalContext.current
    // rememberSaveable: com `remember`, girar o aparelho recriava a Activity e jogava o
    // usuario de volta para a tela inicial no meio do preenchimento.
    var rota by rememberSaveable { mutableStateOf(Rota.INICIO) }

    /**
     * O Voltar do sistema. Nao havia BackHandler nenhum: na tela de resultado, o gesto de
     * voltar — que e o gesto natural — fechava o app e levava junto a simulacao inteira.
     */
    BackHandler(enabled = rota != Rota.INICIO) {
        rota = when (rota) {
            Rota.ATIVIDADE, Rota.NORMA -> Rota.INICIO
            Rota.PORTE -> Rota.ATIVIDADE
            Rota.LOCACIONAL -> Rota.PORTE
            Rota.RESULTADO -> Rota.LOCACIONAL
            Rota.DISPENSA -> Rota.PORTE
            Rota.INICIO -> Rota.INICIO
        }
    }

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
                // Porte inferior nao segue para criterio locacional: ja e resultado final.
                avancarDispensa = { rota = Rota.DISPENSA },
                voltar = { rota = Rota.ATIVIDADE })
            Rota.LOCACIONAL -> TelaLocacional(vm,
                avancar = { rota = Rota.RESULTADO },
                voltar = { rota = Rota.PORTE })
            Rota.RESULTADO -> TelaResultado(vm,
                exportar = { vm.exportarPdf(contexto) },
                novaSimulacao = { vm.novaSimulacao(); rota = Rota.ATIVIDADE },
                voltar = { rota = Rota.LOCACIONAL })
            Rota.DISPENSA -> TelaDispensa(vm,
                exportar = { vm.exportarDispensaPdf(contexto) },
                novaSimulacao = { vm.novaSimulacao(); rota = Rota.ATIVIDADE },
                voltar = { rota = Rota.PORTE })
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
