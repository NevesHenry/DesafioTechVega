package br.com.vega.armazem.dto;

import br.com.vega.armazem.domain.model.TipoIngrediente;

import java.math.BigDecimal;

/** Total armazenado por tipo. Os nomes dos campos seguem o contrato do enunciado ({@code type}, {@code totalQuantity}). */
public record VolumeResponse(TipoIngrediente type, BigDecimal totalQuantity) {
}
