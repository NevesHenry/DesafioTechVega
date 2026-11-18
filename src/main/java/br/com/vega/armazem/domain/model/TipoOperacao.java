package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import com.fasterxml.jackson.annotation.JsonCreator;

/** Tipo de movimentação registrada no histórico. Na leitura, aceita {@code "SAÍDA"} com acento. */
public enum TipoOperacao {
    ENTRADA,
    SAIDA;

    @JsonCreator
    public static TipoOperacao of(String valor) {
        if (valor != null && !valor.isBlank()) {
            String chave = Textos.chaveDeEnum(valor);
            for (TipoOperacao operacao : values()) {
                if (operacao.name().equals(chave)) {
                    return operacao;
                }
            }
        }
        throw new ParametroInvalidoException(
                "Tipo de operação inválido: '%s'. Valores aceitos: ENTRADA, SAIDA.".formatted(valor));
    }
}
