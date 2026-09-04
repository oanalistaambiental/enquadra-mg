package br.com.oanalistaambiental.enquadramento.norma

enum class Grau { P, M, G;
    val extenso: String get() = when (this) { P -> "pequeno"; M -> "médio"; G -> "grande" }
}

enum class Conferencia { CRUZADO, UNICO, DIVERGENTE;
    val aviso: String? get() = when (this) {
        CRUZADO -> null
        UNICO -> "Este dado foi lido de uma única cópia da norma. Confira antes de usar em processo."
        DIVERGENTE -> "As cópias consultadas discordaram em algum campo desta atividade. Veja a nota."
    }
}

data class PotencialPoluidor(val ar: Grau, val agua: Grau, val solo: Grau, val geral: Grau)

data class FaixaCategorica(val rotulo: String, val porte: Grau)

data class Atividade(
    val codigo: String,
    val descricao: String,
    val pp: PotencialPoluidor,
    val tipo: String,                  // "numerico" | "categorico"
    val parametro: String,
    val unidade: String? = null,
    val limiteP: Double? = null,
    val limiteM: Double? = null,
    /** Em A-03-01-8 a faixa pequena é estritamente menor que o limite, não menor ou igual. */
    val limitePExclusivo: Boolean = false,
    /**
     * Mesma ideia para o limite da faixa média. Existe porque algumas atividades da DN escrevem
     * a faixa como "de X a menos de Y" — e nesse caso Y exatos já é porte grande. Nenhuma
     * atividade carregada usa isto hoje; o campo existe para que a norma possa ser expressa sem
     * mudar o motor quando a redação exigir.
     */
    val limiteMExclusivo: Boolean = false,
    val unidadeAlternativa: String? = null,
    val limitePAlt: Double? = null,
    val limiteMAlt: Double? = null,
    val categorias: List<FaixaCategorica> = emptyList(),
    val conferencia: Conferencia = Conferencia.UNICO,
    val nota: String? = null
)

data class CriterioLocacional(
    val id: String,
    val peso: Int,
    val texto: String,
    val camada: String?,
    val automatico: Boolean,
    val nota: String? = null
)

data class FatorRestricao(
    val id: String,
    val nome: String,
    val texto: String,
    val camada: String?,
    val automatico: Boolean
)

data class Modalidade(
    val sigla: String,
    val nome: String,
    val descricao: String,
    val licencas: List<String>,
    val etapas: Int,
    val estudos: List<String>,
    val validadeAnos: Int,
    val validadeTexto: String,
    val validadePorLicenca: Map<String, Int> = emptyMap(),
    val audienciaPublica: Boolean = false,
    val audienciaTexto: String? = null
)

data class RegrasGerais(
    val prazoAnaliseDias: Int,
    val prazoAnaliseTexto: String,
    val prazoAnaliseEiaDias: Int,
    val prazoAnaliseEiaTexto: String,
    val renovacaoAntecedenciaDias: Int,
    val renovacaoTexto: String,
    val observacaoPrazo: String
)

/** Toda a norma carregada — a regra é dado, não código, para sobreviver a alterações da DN. */
data class Regras(
    val tabela1: Map<String, Grau>,
    val tabela2: Map<String, Int>,
    val tabela3: Map<String, String>,
    val criterios: List<CriterioLocacional>,
    val fatores: List<FatorRestricao>,
    val modalidades: List<Modalidade>,
    val gerais: RegrasGerais,
    val atividades: List<Atividade>,
    val procedencia: Map<String, String> = emptyMap()
) {
    fun modalidade(sigla: String): Modalidade? = modalidades.firstOrNull { it.sigla == sigla }
    fun atividade(codigo: String): Atividade? = atividades.firstOrNull { it.codigo == codigo }
    fun criterio(id: String): CriterioLocacional? = criterios.firstOrNull { it.id == id }
}
