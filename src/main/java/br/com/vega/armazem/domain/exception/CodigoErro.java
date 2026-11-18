package br.com.vega.armazem.domain.exception;

/**
 * Códigos de erro estáveis, devolvidos no campo {@code codigo} das respostas de erro,
 * para que clientes não dependam do texto da mensagem.
 */
public enum CodigoErro {
    // Regras de negócio (400)
    CAPACIDADE_EXCEDIDA,
    TIPO_INCOMPATIVEL,
    TROCA_DE_TIPO_BLOQUEADA,
    ESTOQUE_INSUFICIENTE,
    QUANTIDADE_INVALIDA,
    COMPARTIMENTO_INEXISTENTE,

    // Requisição malformada (400)
    REQUISICAO_INVALIDA,
    PARAMETRO_INVALIDO,
    VALIDACAO,

    // Recurso inexistente (404)
    INGREDIENTE_NAO_ENCONTRADO,
    COMPARTIMENTO_NAO_ENCONTRADO,

    // Concorrência / integridade (409)
    CONFLITO_DE_CONCORRENCIA,
    VIOLACAO_DE_INTEGRIDADE,

    // Inesperado (500)
    ERRO_INTERNO
}
