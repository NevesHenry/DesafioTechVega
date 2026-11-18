package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Tipo de ingrediente. Também define a capacidade máxima de um compartimento quando ele armazena o tipo.
 *
 * <p>O enunciado usa {@code "SECOS"} no exemplo de cadastro e {@code "SECO"} na consulta de volume. Por isso a
 * leitura aceita singular ou plural, com ou sem acento e em qualquer caixa. A escrita usa sempre o nome
 * canônico da constante.</p>
 */
public enum TipoIngrediente {

    SECO("Secos", "kg", new BigDecimal("600.000")),
    LIQUIDO("Líquidos", "L", new BigDecimal("500.000")),
    REFRIGERADO("Refrigerados", "kg", new BigDecimal("400.000"));

    private final String descricao;
    private final String unidade;
    private final BigDecimal capacidadeMaxima;

    TipoIngrediente(String descricao, String unidade, BigDecimal capacidadeMaxima) {
        this.descricao = descricao;
        this.unidade = unidade;
        this.capacidadeMaxima = capacidadeMaxima;
    }

    @JsonCreator
    public static TipoIngrediente of(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ParametroInvalidoException("Tipo de ingrediente não informado. " + valoresAceitos());
        }
        String chave = Textos.chaveDeEnum(valor);
        for (TipoIngrediente tipo : values()) {
            if (chave.equals(tipo.name()) || chave.equals(tipo.name() + "S")) {
                return tipo;
            }
        }
        throw new ParametroInvalidoException(
                "Tipo de ingrediente inválido: '%s'. %s".formatted(valor, valoresAceitos()));
    }

    private static String valoresAceitos() {
        return "Valores aceitos: " + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))
                + " (singular ou plural, ex.: SECOS).";
    }

    public String getDescricao() {
        return descricao;
    }

    public String getUnidade() {
        return unidade;
    }

    public BigDecimal getCapacidadeMaxima() {
        return capacidadeMaxima;
    }
}
