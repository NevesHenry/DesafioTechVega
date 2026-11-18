package br.com.vega.armazem.repository;

import br.com.vega.armazem.domain.model.TipoIngrediente;

import java.math.BigDecimal;

/** Projeção da soma de estoque por tipo. */
public record VolumePorTipo(TipoIngrediente tipo, BigDecimal total) {
}
