package br.com.oanalistaambiental.enquadramento.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.oanalistaambiental.enquadramento.norma.Grau

/* ------------------------------------------------------- CRITÉRIO LOCACIONAL */

@Composable
fun TelaLocacional(vm: SimulacaoViewModel, avancar: () -> Unit, voltar: () -> Unit) {
    val regras by vm.regras.collectAsState()
    val marcados by vm.marcados.collectAsState()
    val fatores by vm.fatoresMarcados.collectAsState()
    val deteccao by vm.deteccao.collectAsState()
    val comEia by vm.comEia.collectAsState()
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    val r = regras ?: return

    val incidentes = r.criterios.filter { it.id in marcados }
    val peso = incidentes.maxOfOrNull { it.peso } ?: 0

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("3. Critério locacional", "Tabela 4 da DN 217 — prevalece o de maior peso", voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                Rotulo("SUGESTÃO PELA COORDENADA")
                Cartao {
                    Text(
                        "O art. 6º, §5º da DN 217 manda consultar o IDE-Sisema para verificar a " +
                            "incidência. O aplicativo faz essa consulta no mesmo pacote de camadas do " +
                            "app de campo, sem internet — e a resposta é sugestão, não decisão.",
                        color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Botao("Verificar camadas neste ponto") {
                        val la = lat.replace(',', '.').toDoubleOrNull()
                        val lo = lon.replace(',', '.').toDoubleOrNull()
                        if (la != null && lo != null) vm.detectarPorCoordenada(la, lo)
                    }
                    deteccao?.let { d ->
                        Spacer(Modifier.height(8.dp))
                        Mono("pacote ${d.versaoPacote}")
                        if (d.camadasNaoInstaladas.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Mono("camadas ausentes no pacote: ${d.camadasNaoInstaladas.joinToString(", ")}",
                                Cores.atencao, 10)
                        }
                    }
                }

                Rotulo("CRITÉRIOS — TABELA 4")
                r.criterios.forEach { c ->
                    ItemMarcavel(
                        marcado = c.id in marcados,
                        titulo = c.texto,
                        etiqueta = "peso ${c.peso}",
                        destaque = c.peso == 2,
                        nota = c.nota ?: if (!c.automatico) "Depende do projeto — o app não consegue deduzir do ponto." else null,
                        aoAlternar = { vm.alternarCriterio(c.id) }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Cartao {
                    Text("Fator locacional: peso $peso", color = Cores.acento,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (incidentes.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Você marcou ${incidentes.size} critérios. Os pesos NÃO se somam: pelo " +
                                "art. 6º, §3º prevalece o de maior peso.",
                            color = Cores.textoFraco, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }

                Rotulo("FATORES DE RESTRIÇÃO OU VEDAÇÃO — TABELA 5")
                Aviso(
                    "Não conferem peso e não mudam o enquadramento (art. 6º, §4º). Entram na " +
                        "abordagem dos estudos — e alguns vedam a atividade no local.", TipoAviso.INFO
                )
                r.fatores.forEach { f ->
                    ItemMarcavel(
                        marcado = f.id in fatores,
                        titulo = f.nome,
                        etiqueta = null,
                        destaque = false,
                        nota = f.texto,
                        aoAlternar = { vm.alternarFator(f.id) }
                    )
                }

                Rotulo("ESTUDO")
                Cartao {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { vm.definirEia(!comEia) }) {
                        Checkbox(checked = comEia, onCheckedChange = { vm.definirEia(it) })
                        Spacer(Modifier.width(6.dp))
                        Text("Haverá EIA-Rima ou audiência pública", color = Cores.texto, fontSize = 13.sp)
                    }
                    Text("Muda o prazo de análise de 6 para 12 meses (Decreto 47.383/2018, art. 22).",
                        color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                }

                Spacer(Modifier.height(18.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Botao("Calcular enquadramento", principal = true) { vm.calcular(); avancar() }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ItemMarcavel(
    marcado: Boolean, titulo: String, etiqueta: String?, destaque: Boolean,
    nota: String?, aoAlternar: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(if (marcado) Cores.acentoClaro else Cores.superficie, RoundedCornerShape(6.dp))
            .clickable { aoAlternar() }.padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = marcado, onCheckedChange = { aoAlternar() })
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Cores.texto, fontSize = 12.5.sp, lineHeight = 17.sp)
            nota?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        etiqueta?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, color = if (destaque) Cores.alerta else Cores.textoFraco,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* --------------------------------------------------------------- RESULTADO */

@Composable
fun TelaResultado(
    vm: SimulacaoViewModel,
    exportar: () -> Unit,
    novaSimulacao: () -> Unit,
    voltar: () -> Unit
) {
    val res by vm.resultado.collectAsState()
    val r = res ?: run {
        Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Cabecalho("Resultado", voltar = voltar)
            Aviso("Nada calculado ainda.", TipoAviso.ATENCAO)
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("Resultado da simulação", voltar = voltar)

        LazyColumn(Modifier.weight(1f)) {
            item {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(Cores.acento, RoundedCornerShape(8.dp)).padding(20.dp)
                ) {
                    Text("MODALIDADE", color = Color(0xCCFFFFFF), fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(r.modalidade.sigla, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(r.modalidade.nome, color = Color(0xE6FFFFFF), fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Indicador("CLASSE", r.classe.toString())
                        Spacer(Modifier.width(28.dp))
                        Indicador("PORTE", r.porte.name)
                        Spacer(Modifier.width(28.dp))
                        Indicador("POTENCIAL", r.potencialGeral.name)
                        Spacer(Modifier.width(28.dp))
                        Indicador("LOCACIONAL", r.fatorLocacional.toString())
                    }
                }

                r.avisos.forEach { Spacer(Modifier.height(8.dp)); Aviso(it, TipoAviso.ATENCAO) }

                Rotulo("O QUE ISSO SIGNIFICA")
                Cartao {
                    Text(r.modalidade.descricao, color = Cores.texto, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    LinhaDado("Licenças", r.modalidade.licencas.joinToString(", "))
                    LinhaDado("Etapas", "${r.modalidade.etapas}")
                    LinhaDado("Prazo de análise", "${r.prazoAnaliseDias} dias", destaque = true)
                    LinhaDado("Validade", r.modalidade.validadeTexto)
                }

                Rotulo("ESTUDOS EXIGIDOS")
                Cartao {
                    r.modalidade.estudos.forEach {
                        Text("• $it", color = Cores.texto, fontSize = 12.5.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 3.dp))
                    }
                    r.modalidade.audienciaTexto?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }

                if (r.fatoresRestricao.isNotEmpty()) {
                    Rotulo("FATORES DE RESTRIÇÃO OU VEDAÇÃO INCIDENTES")
                    r.fatoresRestricao.forEach { f ->
                        Cartao {
                            Text(f.nome, color = Cores.alerta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(f.texto, color = Cores.texto, fontSize = 11.5.sp, lineHeight = 16.sp)
                        }
                    }
                }

                Rotulo("MEMÓRIA DE CÁLCULO")
                r.passos.forEachIndexed { i, p ->
                    Cartao {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}", color = Cores.acento, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Column {
                                Text(p.rotulo, color = Cores.textoFraco, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(p.valor, color = Cores.texto, fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(p.fundamento, color = Cores.textoFraco, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }

                vm.regras.value?.let { regras ->
                    Rotulo("RENOVAÇÃO")
                    Cartao {
                        Text(regras.gerais.renovacaoTexto, color = Cores.texto,
                            fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Botao("Exportar simulação em PDF", principal = true) { exportar() }
                    Botao("Nova simulação") { novaSimulacao() }
                }

                Spacer(Modifier.height(16.dp))
                Aviso(
                    "Simulação com base em norma pública. Não substitui o enquadramento do órgão " +
                        "ambiental nem vincula a Administração. Ferramenta independente, sem vínculo " +
                        "com o SISEMA.", TipoAviso.INFO
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun Indicador(rotulo: String, valor: String) {
    Column {
        Text(rotulo, color = Color(0xB3FFFFFF), fontSize = 9.5.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(2.dp))
        Text(valor, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

/* ------------------------------------------------------------------ NORMA */

@Composable
fun TelaNorma(vm: SimulacaoViewModel, voltar: () -> Unit) {
    val regras by vm.regras.collectAsState()
    val r = regras ?: return

    Column(Modifier.fillMaxSize().background(Cores.fundo).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Cabecalho("A norma", r.procedencia["norma"], voltar)
        LazyColumn(Modifier.weight(1f)) {
            item {
                Rotulo("TABELA 2 — CLASSE POR PORTE E POTENCIAL")
                Cartao {
                    Row {
                        Text("", modifier = Modifier.width(70.dp))
                        listOf("PP P", "PP M", "PP G").forEach {
                            Text(it, color = Cores.textoFraco, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    Grau.entries.forEach { porte ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("Porte ${porte.name}", color = Cores.textoFraco, fontSize = 11.sp,
                                modifier = Modifier.width(70.dp))
                            Grau.entries.forEach { pp ->
                                Text("${r.tabela2["${porte.name}${pp.name}"]}", color = Cores.texto,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Rotulo("TABELA 3 — MODALIDADE POR CLASSE E CRITÉRIO LOCACIONAL")
                Cartao {
                    Row {
                        Text("", modifier = Modifier.width(58.dp))
                        listOf("peso 0", "peso 1", "peso 2").forEach {
                            Text(it, color = Cores.textoFraco, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    (1..6).forEach { classe ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("Classe $classe", color = Cores.textoFraco, fontSize = 11.sp,
                                modifier = Modifier.width(58.dp))
                            (0..2).forEach { fator ->
                                Text(r.tabela3["$classe|$fator"] ?: "—", color = Cores.texto,
                                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Rotulo("TABELA 4 — CRITÉRIOS LOCACIONAIS")
                r.criterios.forEach { c ->
                    Cartao {
                        Row {
                            Text("peso ${c.peso}", color = if (c.peso == 2) Cores.alerta else Cores.textoFraco,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
                            Text(c.texto, color = Cores.texto, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }

                Rotulo("TABELA 5 — FATORES DE RESTRIÇÃO OU VEDAÇÃO")
                r.fatores.forEach { f ->
                    Cartao {
                        Text(f.nome, color = Cores.texto, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(f.texto, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }

                Rotulo("PRAZOS")
                Cartao {
                    LinhaDado("Análise", r.gerais.prazoAnaliseTexto)
                    LinhaDado("Com EIA-Rima", r.gerais.prazoAnaliseEiaTexto)
                    LinhaDado("Renovação", r.gerais.renovacaoTexto)
                    Spacer(Modifier.height(6.dp))
                    Text(r.gerais.observacaoPrazo, color = Cores.textoFraco, fontSize = 11.5.sp, lineHeight = 16.sp)
                }

                Rotulo("PROCEDÊNCIA DESTA BASE")
                Cartao {
                    Text(r.procedencia["aviso"] ?: "", color = Cores.atencao, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    LinhaDado("Extraída em", r.procedencia["extraido_em"] ?: "—")
                    LinhaDado("Cobertura", r.procedencia["cobertura"] ?: "—")
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
