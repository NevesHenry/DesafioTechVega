package br.com.vega.armazem.dto;

import br.com.vega.armazem.domain.model.Compartimento;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompartimentoResponse(
        Long id,
        String codigo,
        @Schema(description = "Último tipo armazenado (null se nunca usado)") TipoIngrediente tipoAtual,
        String unidade,
        BigDecimal capacidadeMaxima,
        BigDecimal quantidadeAtual,
        BigDecimal espacoLivre,
        BigDecimal percentualOcupacao,
        boolean vazio,
        @Schema(description = "true se está vazio e pode receber qualquer tipo agora") boolean aceitaQualquerTipo,
        LocalDate ultimaMovimentacaoEm) {

    /** Visão do compartimento segundo o tipo que ele armazena. */
    public static CompartimentoResponse de(Compartimento c, LocalDate hoje) {
        TipoIngrediente tipo = c.getTipoAtual();
        return new CompartimentoResponse(c.getId(), c.getCodigo(), tipo,
                tipo == null ? null : tipo.getUnidade(),
                c.getCapacidadeMaxima(),
                c.getQuantidadeAtual(),
                c.getEspacoLivre(),
                c.getPercentualOcupacao(),
                c.estaVazio(),
                c.aceitaQualquerTipo(hoje),
                c.getUltimaMovimentacaoEm());
    }

    /** Visão do compartimento como candidato a receber o {@code tipo}: capacidade e espaço livre desse tipo. */
    public static CompartimentoResponse paraTipo(Compartimento c, TipoIngrediente tipo, LocalDate hoje) {
        return new CompartimentoResponse(c.getId(), c.getCodigo(), c.getTipoAtual(),
                tipo.getUnidade(),
                tipo.getCapacidadeMaxima(),
                c.getQuantidadeAtual(),
                c.espacoLivrePara(tipo, hoje),
                c.getPercentualOcupacao(),
                c.estaVazio(),
                c.aceitaQualquerTipo(hoje),
                c.getUltimaMovimentacaoEm());
    }
}
