package br.com.oanalistaambiental.enquadramento.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.oanalistaambiental.enquadramento.geo.DeteccaoLocacional
import br.com.oanalistaambiental.enquadramento.laudo.SimulacaoPdf
import br.com.oanalistaambiental.enquadramento.norma.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SimulacaoViewModel(app: Application) : AndroidViewModel(app) {

    private val _regras = MutableStateFlow<Regras?>(null)
    val regras: StateFlow<Regras?> = _regras

    private val _erroBase = MutableStateFlow<String?>(null)
    val erroBase: StateFlow<String?> = _erroBase

    // ---- estado da simulação em curso ----
    private val _atividade = MutableStateFlow<Atividade?>(null)
    val atividade: StateFlow<Atividade?> = _atividade

    private val _porte = MutableStateFlow<Grau?>(null)
    val porte: StateFlow<Grau?> = _porte

    /** Modo manual: o usuário informa porte e potencial sem escolher atividade do catálogo. */
    private val _potencialManual = MutableStateFlow<Grau?>(null)
    val potencialManual: StateFlow<Grau?> = _potencialManual

    private val _marcados = MutableStateFlow<Set<String>>(emptySet())
    val marcados: StateFlow<Set<String>> = _marcados

    private val _fatoresMarcados = MutableStateFlow<Set<String>>(emptySet())
    val fatoresMarcados: StateFlow<Set<String>> = _fatoresMarcados

    private val _comEia = MutableStateFlow(false)
    val comEia: StateFlow<Boolean> = _comEia

    private val _resultado = MutableStateFlow<Enquadramento.Resultado?>(null)
    val resultado: StateFlow<Enquadramento.Resultado?> = _resultado

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem

    private val _deteccao = MutableStateFlow<DeteccaoLocacional.Incidencia?>(null)
    val deteccao: StateFlow<DeteccaoLocacional.Incidencia?> = _deteccao

    val arquivoPacote: File
        get() = File(getApplication<Application>().getExternalFilesDir(null), "pacotes/mg-base.gpkg")

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                BaseNormativa.carregar { nome ->
                    getApplication<Application>().assets.open("norma/$nome")
                }
            }.onSuccess { _regras.value = it }
             .onFailure { _erroBase.value = "Não foi possível carregar a base normativa: ${it.message}" }
        }
    }

    // ------------------------------------------------------------------ fluxo

    fun novaSimulacao() {
        _atividade.value = null
        _porte.value = null
        _potencialManual.value = null
        _marcados.value = emptySet()
        _fatoresMarcados.value = emptySet()
        _comEia.value = false
        _resultado.value = null
        _deteccao.value = null
    }

    fun escolherAtividade(a: Atividade) {
        _atividade.value = a
        _porte.value = null
        _potencialManual.value = null
    }

    fun definirPorteManual(g: Grau) { _porte.value = g }
    fun definirPotencialManual(g: Grau) { _potencialManual.value = g }

    fun calcularPorte(valor: Double, usarAlternativa: Boolean) {
        val a = _atividade.value ?: return
        runCatching { Enquadramento.porteDe(a, valor, usarAlternativa) }
            .onSuccess { _porte.value = it }
            .onFailure { _mensagem.value = it.message }
    }

    fun calcularPorteCategoria(rotulo: String) {
        val a = _atividade.value ?: return
        runCatching { Enquadramento.porteDe(a, rotulo) }
            .onSuccess { _porte.value = it }
            .onFailure { _mensagem.value = it.message }
    }

    fun alternarCriterio(id: String) {
        _marcados.value = _marcados.value.let { if (id in it) it - id else it + id }
    }

    fun alternarFator(id: String) {
        _fatoresMarcados.value = _fatoresMarcados.value.let { if (id in it) it - id else it + id }
    }

    fun definirEia(v: Boolean) { _comEia.value = v }

    /** Sugestão automática pelo pacote de camadas — o mesmo do aplicativo de campo. */
    fun detectarPorCoordenada(lat: Double, lon: Double) {
        val r = _regras.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!arquivoPacote.exists()) {
                _mensagem.value = "Pacote de camadas não instalado. Marque os critérios à mão."
                return@launch
            }
            runCatching {
                DeteccaoLocacional.abrir(arquivoPacote).use { it.verificar(r, lat, lon) }
            }.onSuccess { inc ->
                _deteccao.value = inc
                _marcados.value = _marcados.value + inc.criterios.map { it.id }
                _fatoresMarcados.value = _fatoresMarcados.value + inc.fatores.map { it.id }
                _mensagem.value = if (inc.criterios.isEmpty() && inc.fatores.isEmpty())
                    "Nenhuma camada do pacote incide neste ponto. Confira também os critérios que dependem do projeto."
                else "Sugestão aplicada: ${inc.criterios.size} critério(s) e ${inc.fatores.size} fator(es). Confira antes de seguir."
            }.onFailure { _mensagem.value = "Consulta de camadas falhou: ${it.message}" }
        }
    }

    fun calcular() {
        val r = _regras.value ?: return
        val porte = _porte.value ?: run { _mensagem.value = "Informe o porte."; return }
        val incidentes = r.criterios.filter { it.id in _marcados.value }
        val fatores = r.fatores.filter { it.id in _fatoresMarcados.value }

        // BUG corrigido: a atividade sintética do modo manual NÃO é nula (a tela a define para
        // habilitar os seletores), então o elvis nunca disparava e o potencial ficava sempre
        // pequeno — o que resultaria em classe 1 em qualquer simulação manual.
        val escolhida = _atividade.value
        val a = if (escolhida == null || escolhida.tipo == "manual") {
            val pp = _potencialManual.value ?: run {
                _mensagem.value = "Informe o potencial poluidor/degradador geral."; return
            }
            Atividade(
                codigo = "—", descricao = "Enquadramento informado manualmente",
                pp = PotencialPoluidor(pp, pp, pp, pp),
                tipo = "manual", parametro = "informado pelo usuário",
                conferencia = Conferencia.CRUZADO
            )
        } else escolhida

        runCatching { Enquadramento.simular(r, a, porte, incidentes, fatores, _comEia.value) }
            .onSuccess { _resultado.value = it }
            .onFailure { _mensagem.value = it.message }
    }

    /**
     * Gera e compartilha o PDF.
     *
     * Isto ficava no callback de clique da tela, ou seja, desenhava o documento inteiro e
     * gravava o arquivo na thread da UI — e o runCatching descartava a falha, então um erro
     * fazia o botão simplesmente não responder. Agora o trabalho vai para IO e a falha aparece.
     */
    fun exportarPdf(contexto: Context) {
        val r = _regras.value ?: return
        val res = _resultado.value ?: run { _mensagem.value = "Nada calculado ainda."; return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pasta = File(contexto.filesDir, "simulacoes").apply { mkdirs() }
                SimulacaoPdf.gerar(r, res, File(pasta, "simulacao-${System.currentTimeMillis()}.pdf"))
            }.onSuccess { arquivo ->
                // startActivity precisa da thread principal.
                withContext(Dispatchers.Main) { SimulacaoPdf.compartilhar(contexto, arquivo) }
            }.onFailure { _mensagem.value = "Não foi possível gerar o PDF: ${it.message}" }
        }
    }

    fun limparMensagem() { _mensagem.value = null }
}
