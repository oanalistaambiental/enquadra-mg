package br.com.oanalistaambiental.enquadramento.norma

/**
 * Motor de enquadramento da DN COPAM 217/2017.
 *
 * Funções puras, sem Android e sem estado: é o que permite testar a norma sozinha e é o que
 * torna o resultado auditável. Nada aqui adivinha — quando falta dado, o motor diz que falta,
 * em vez de devolver um número plausível.
 *
 * Cada resultado carrega a MEMÓRIA DE CÁLCULO: um profissional precisa poder conferir o
 * caminho, não só o destino.
 */
object Enquadramento {

    data class Passo(val rotulo: String, val valor: String, val fundamento: String)

    data class Resultado(
        val classe: Int,
        val porte: Grau,
        val potencialGeral: Grau,
        val fatorLocacional: Int,
        val criterioDeterminante: CriterioLocacional?,
        val criteriosIncidentes: List<CriterioLocacional>,
        val modalidade: Modalidade,
        val fatoresRestricao: List<FatorRestricao>,
        val prazoAnaliseDias: Int,
        val prazoAnaliseTexto: String,
        val passos: List<Passo>,
        val avisos: List<String>
    )

    class DadoFaltante(mensagem: String) : Exception(mensagem)

    // ------------------------------------------------------------------ porte

    /**
     * Porte a partir do valor do parâmetro. A comparação segue a norma ao pé da letra:
     * a faixa pequena vai até o limite INCLUSIVE, salvo quando a atividade diz o contrário.
     */
    fun porteDe(atividade: Atividade, valor: Double, usarAlternativa: Boolean = false): Grau {
        if (atividade.tipo != "numerico") {
            throw DadoFaltante("A atividade ${atividade.codigo} usa categoria, não valor numérico.")
        }
        val limiteP = (if (usarAlternativa) atividade.limitePAlt else atividade.limiteP)
            ?: throw DadoFaltante("Faixa de porte não carregada para ${atividade.codigo}.")
        val limiteM = (if (usarAlternativa) atividade.limiteMAlt else atividade.limiteM)
            ?: throw DadoFaltante("Faixa de porte não carregada para ${atividade.codigo}.")

        val pequeno = if (atividade.limitePExclusivo) valor < limiteP else valor <= limiteP
        val medio = if (atividade.limiteMExclusivo) valor < limiteM else valor <= limiteM
        return when {
            pequeno -> Grau.P
            medio -> Grau.M
            else -> Grau.G
        }
    }

    fun porteDe(atividade: Atividade, rotuloCategoria: String): Grau =
        atividade.categorias.firstOrNull { it.rotulo.equals(rotuloCategoria, ignoreCase = true) }?.porte
            ?: throw DadoFaltante("Categoria \"$rotuloCategoria\" não existe em ${atividade.codigo}.")

    // ------------------------------------------------- potencial poluidor geral

    /** Tabela 1. Só é necessária quando não se tem o geral pronto da listagem. */
    fun potencialGeral(regras: Regras, ar: Grau, agua: Grau, solo: Grau): Grau =
        regras.tabela1["${ar.name}${agua.name}${solo.name}"]
            ?: throw DadoFaltante("Combinação ${ar.name}${agua.name}${solo.name} ausente na Tabela 1.")

    // ------------------------------------------------------------------ classe

    /** Tabela 2. */
    fun classeDe(regras: Regras, porte: Grau, potencialGeral: Grau): Int =
        regras.tabela2["${porte.name}${potencialGeral.name}"]
            ?: throw DadoFaltante("Combinação porte ${porte.name} + potencial ${potencialGeral.name} ausente na Tabela 2.")

    /**
     * Art. 5º, parágrafo único: regularizando duas ou mais atividades ao mesmo tempo,
     * vale o enquadramento da atividade de MAIOR classe.
     */
    fun classeDoConjunto(classes: List<Int>): Int =
        classes.maxOrNull() ?: throw DadoFaltante("Nenhuma atividade informada.")

    // --------------------------------------------------------- critério locacional

    /**
     * Art. 6º, §2º e §3º: sem nenhum critério, peso 0. Incidindo mais de um, prevalece o de
     * MAIOR peso — os pesos NÃO se somam. É o erro mais comum de quem faz isso na mão.
     */
    fun fatorLocacional(criteriosIncidentes: List<CriterioLocacional>): Int =
        criteriosIncidentes.maxOfOrNull { it.peso } ?: 0

    fun criterioDeterminante(criteriosIncidentes: List<CriterioLocacional>): CriterioLocacional? =
        criteriosIncidentes.maxByOrNull { it.peso }

    // ------------------------------------------------------------- modalidade

    /** Tabela 3. */
    fun modalidadeDe(regras: Regras, classe: Int, fatorLocacional: Int): Modalidade {
        val sigla = regras.tabela3["$classe|$fatorLocacional"]
            ?: throw DadoFaltante("Combinação classe $classe + fator $fatorLocacional ausente na Tabela 3.")
        return regras.modalidade(sigla)
            ?: throw DadoFaltante("Modalidade \"$sigla\" não descrita na base.")
    }

    // ------------------------------------------------------------------ simulação

    fun simular(
        regras: Regras,
        atividade: Atividade,
        porte: Grau,
        criteriosIncidentes: List<CriterioLocacional>,
        fatoresIncidentes: List<FatorRestricao> = emptyList(),
        comEiaOuAudiencia: Boolean = false
    ): Resultado {
        val ppGeral = atividade.pp.geral
        val classe = classeDe(regras, porte, ppGeral)
        val fator = fatorLocacional(criteriosIncidentes)
        val determinante = criterioDeterminante(criteriosIncidentes)
        val modalidade = modalidadeDe(regras, classe, fator)

        // Nao se infere EIA a partir da modalidade: o trifasico so exige EIA quando ha
        // significativo impacto. Quem sabe disso e quem esta simulando, entao o prazo dobrado
        // depende de uma afirmacao explicita, nao de um palpite do aplicativo.
        val exigeEia = comEiaOuAudiencia
        val prazoDias = if (exigeEia) regras.gerais.prazoAnaliseEiaDias else regras.gerais.prazoAnaliseDias
        val prazoTexto = if (exigeEia) regras.gerais.prazoAnaliseEiaTexto else regras.gerais.prazoAnaliseTexto

        val passos = buildList {
            add(Passo("Atividade", "${atividade.codigo} — ${atividade.descricao}",
                "DN 217/2017, Anexo Único, Listagem de Atividades"))
            add(Passo("Porte", porte.extenso.uppercase(),
                if (atividade.tipo == "manual") "DN 217/2017, art. 4º — informado por quem simulou"
                else "DN 217/2017, art. 4º — parâmetro: ${atividade.parametro}"))
            add(Passo("Potencial poluidor/degradador geral", ppGeral.extenso.uppercase(),
                if (atividade.tipo == "manual") "DN 217/2017, art. 3º — informado por quem simulou"
                else "DN 217/2017, art. 3º — ar ${atividade.pp.ar}, água ${atividade.pp.agua}, solo ${atividade.pp.solo}"))
            add(Passo("Classe", classe.toString(),
                "DN 217/2017, art. 5º e Tabela 2 — porte ${porte.name} × potencial ${ppGeral.name}"))
            add(Passo("Critério locacional", "peso $fator" +
                (determinante?.let { " — ${it.texto}" } ?: " — nenhum critério incidente"),
                if (criteriosIncidentes.size > 1)
                    "DN 217/2017, art. 6º, §3º — incidindo mais de um critério, prevalece o de maior peso"
                else "DN 217/2017, art. 6º, §1º e §2º, e Tabela 4"))
            add(Passo("Modalidade", modalidade.sigla,
                "DN 217/2017, art. 6º e Tabela 3 — classe $classe × fator $fator"))
            add(Passo("Prazo de análise", "$prazoDias dias", prazoTexto))
            // No trifásico a LP vale 5 anos e a LI 6. Dizer "10 anos" aqui seria falso — e este
            // passo vai inteiro para o PDF, que pode ser anexado a um parecer.
            add(Passo("Validade",
                if (modalidade.validadePorLicenca.isEmpty()) "${modalidade.validadeAnos} anos"
                else modalidade.validadePorLicenca.entries
                    .sortedBy { it.value }
                    .joinToString(", ") { "${it.key} ${it.value} anos" },
                modalidade.validadeTexto))
            if (modalidade.audienciaPublica && !exigeEia) add(Passo(
                "Atenção", "prazo sujeito a mudança",
                "No trifásico, havendo significativo impacto ambiental incide EIA-Rima e cabe " +
                    "audiência pública — e aí o prazo de análise passa a 12 meses."
            ))
        }

        val avisos = buildList {
            atividade.conferencia.aviso?.let { add(it) }
            atividade.nota?.let { add(it) }
            if (criteriosIncidentes.size > 1) add(
                "Incidiram ${criteriosIncidentes.size} critérios locacionais. Os pesos não se somam: " +
                    "prevaleceu o de maior peso (art. 6º, §3º)."
            )
            if (fatoresIncidentes.isNotEmpty()) add(
                "Há ${fatoresIncidentes.size} fator(es) de restrição ou vedação incidindo. Eles não " +
                    "mudam o enquadramento (art. 6º, §4º), mas precisam ser tratados nos estudos — e " +
                    "alguns vedam a atividade no local."
            )
        }

        return Resultado(
            classe = classe,
            porte = porte,
            potencialGeral = ppGeral,
            fatorLocacional = fator,
            criterioDeterminante = determinante,
            criteriosIncidentes = criteriosIncidentes,
            modalidade = modalidade,
            fatoresRestricao = fatoresIncidentes,
            prazoAnaliseDias = prazoDias,
            prazoAnaliseTexto = prazoTexto,
            passos = passos,
            avisos = avisos
        )
    }
}
