package br.com.vega.armazem.dto;

import br.com.vega.armazem.domain.model.Ingrediente;
import br.com.vega.armazem.domain.model.TipoIngrediente;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IngredienteResponse(
        Long id,
        String nome,
        TipoIngrediente tipo,
        String unidade,
        BigDecimal quantidade,
        Long compartimentoId,
        String compartimentoCodigo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static IngredienteResponse de(Ingrediente ingrediente) {
        return new IngredienteResponse(
                ingrediente.getId(),
                ingrediente.getNome(),
                ingrediente.getTipo(),
                ingrediente.getTipo().getUnidade(),
                ingrediente.getQuantidade(),
                ingrediente.getCompartimento().getId(),
                ingrediente.getCompartimento().getCodigo(),
                ingrediente.getCriadoEm(),
                ingrediente.getAtualizadoEm());
    }
}
