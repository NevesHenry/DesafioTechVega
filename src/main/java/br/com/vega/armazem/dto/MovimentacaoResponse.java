package br.com.vega.armazem.dto;

import br.com.vega.armazem.domain.model.Movimentacao;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.domain.model.TipoOperacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentacaoResponse(
        Long id,
        LocalDateTime dataHora,
        TipoOperacao tipoOperacao,
        BigDecimal quantidade,
        TipoIngrediente tipoIngrediente,
        String unidade,
        String responsavel,
        Long ingredienteId,
        String nomeIngrediente,
        Long compartimentoId,
        String compartimentoCodigo,
        BigDecimal saldoCompartimento) {

    public static MovimentacaoResponse de(Movimentacao m) {
        return new MovimentacaoResponse(
                m.getId(),
                m.getDataHora(),
                m.getTipoOperacao(),
                m.getQuantidade(),
                m.getTipoIngrediente(),
                m.getTipoIngrediente().getUnidade(),
                m.getResponsavel(),
                m.getIngrediente().getId(),
                m.getNomeIngrediente(),
                m.getCompartimento().getId(),
                m.getCompartimentoCodigo(),
                m.getSaldoCompartimento());
    }
}
