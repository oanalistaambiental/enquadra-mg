package br.com.oanalistaambiental.enquadramento.laudo

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import br.com.oanalistaambiental.enquadramento.norma.Enquadramento
import br.com.oanalistaambiental.enquadramento.norma.Regras
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Relatório da simulação em PDF.
 *
 * O documento leva a memória de cálculo inteira e o aviso de independência — é o que permite
 * anexar a simulação a um parecer sem que ela seja lida como decisão do órgão.
 */
object SimulacaoPdf {

    private const val LARGURA = 595
    private const val ALTURA = 842
    private const val MARGEM = 44f
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    fun gerar(regras: Regras, r: Enquadramento.Resultado, destino: File): File {
        val doc = PdfDocument()
        var numero = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
        var c: Canvas = pagina.canvas
        var y = MARGEM + 12f

        fun titulo(size: Float, bold: Boolean = true) = Paint().apply {
            color = Color.BLACK; textSize = size; isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun cinza(size: Float) = Paint().apply {
            color = Color.DKGRAY; textSize = size; isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        fun quebrar(texto: String, p: Paint, largura: Float): List<String> {
            val linhas = mutableListOf<String>()
            var atual = StringBuilder()
            texto.split(" ").forEach { palavra ->
                val teste = if (atual.isEmpty()) palavra else "$atual $palavra"
                if (p.measureText(teste) > largura && atual.isNotEmpty()) {
                    linhas += atual.toString(); atual = StringBuilder(palavra)
                } else atual = StringBuilder(teste)
            }
            if (atual.isNotEmpty()) linhas += atual.toString()
            return linhas
        }

        fun escrever(texto: String, p: Paint, recuo: Float = 0f, espaco: Float = 14f) {
            quebrar(texto, p, LARGURA - 2 * MARGEM - recuo).forEach { linha ->
                if (y > ALTURA - MARGEM) {
                    doc.finishPage(pagina)
                    numero += 1
                    pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
                    c = pagina.canvas
                    y = MARGEM + 12f
                }
                c.drawText(linha, MARGEM + recuo, y, p)
                y += espaco
            }
        }

        escrever("SIMULAÇÃO DE ENQUADRAMENTO AMBIENTAL", titulo(16f), espaco = 24f)
        escrever(regras.procedencia["norma"] ?: "", cinza(10f))
        escrever("Gerada em ${fmt.format(Date())}", cinza(10f), espaco = 22f)

        escrever("RESULTADO", titulo(12f), espaco = 18f)
        escrever("Modalidade: ${r.modalidade.sigla} — ${r.modalidade.nome}", titulo(11f, false))
        escrever("Classe ${r.classe} · porte ${r.porte.extenso} · potencial poluidor geral " +
            "${r.potencialGeral.extenso} · critério locacional peso ${r.fatorLocacional}", cinza(10f))
        escrever("Prazo de análise: ${r.prazoAnaliseDias} dias. ${r.prazoAnaliseTexto}", cinza(10f))
        escrever("Validade: ${r.modalidade.validadeTexto}", cinza(10f), espaco = 20f)

        escrever("ESTUDOS EXIGIDOS", titulo(12f), espaco = 16f)
        r.modalidade.estudos.forEach { escrever("• $it", cinza(10f), recuo = 8f) }
        y += 8f

        if (r.criteriosIncidentes.isNotEmpty()) {
            escrever("CRITÉRIOS LOCACIONAIS INCIDENTES", titulo(12f), espaco = 16f)
            r.criteriosIncidentes.forEach {
                escrever("• (peso ${it.peso}) ${it.texto}", cinza(9.5f), recuo = 8f, espaco = 12f)
            }
            escrever("Prevalece o de maior peso — DN 217/2017, art. 6º, §3º. Os pesos não se somam.",
                cinza(9.5f), espaco = 18f)
        }

        if (r.fatoresRestricao.isNotEmpty()) {
            escrever("FATORES DE RESTRIÇÃO OU VEDAÇÃO", titulo(12f), espaco = 16f)
            r.fatoresRestricao.forEach {
                escrever("• ${it.nome}: ${it.texto}", cinza(9.5f), recuo = 8f, espaco = 12f)
            }
            escrever("Não conferem peso ao enquadramento (art. 6º, §4º), mas devem ser tratados nos estudos.",
                cinza(9.5f), espaco = 18f)
        }

        escrever("MEMÓRIA DE CÁLCULO", titulo(12f), espaco = 16f)
        r.passos.forEachIndexed { i, p ->
            escrever("${i + 1}. ${p.rotulo}: ${p.valor}", titulo(10f, false), espaco = 13f)
            escrever(p.fundamento, cinza(9f), recuo = 12f, espaco = 12f)
            y += 3f
        }

        y += 10f
        if (r.avisos.isNotEmpty()) {
            escrever("AVISOS", titulo(12f), espaco = 16f)
            r.avisos.forEach { escrever("• $it", cinza(9.5f), recuo = 8f, espaco = 12f) }
            y += 8f
        }

        escrever("PROCEDÊNCIA E LIMITES", titulo(12f), espaco = 16f)
        escrever(regras.procedencia["aviso"] ?: "", cinza(9.5f), espaco = 12f)
        escrever("Cobertura do catálogo: ${regras.procedencia["cobertura"] ?: "—"}", cinza(9.5f), espaco = 12f)
        y += 6f
        escrever(
            "Este documento é uma SIMULAÇÃO produzida por ferramenta independente, sem vínculo " +
                "com o SISEMA/SEMAD/FEAM. Não substitui o enquadramento realizado pelo órgão " +
                "ambiental competente e não vincula a Administração Pública.",
            cinza(9.5f), espaco = 12f
        )

        doc.finishPage(pagina)
        try {
            FileOutputStream(destino).use { doc.writeTo(it) }
        } finally {
            doc.close()   // segura memória nativa: precisa fechar mesmo se a escrita falhar
        }
        return destino
    }

    fun compartilhar(context: Context, arquivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Simulação de enquadramento ambiental")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
