package br.com.vega.armazem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Entrada (reposição) ou saída (retirada/venda) de um ingrediente já cadastrado")
public record MovimentacaoRequest(

        @Schema(example = "50")
        @NotNull(message = "é obrigatório")
        @Positive(message = "deve ser maior que zero")
        @Digits(integer = 9, fraction = 3, message = "deve ter no máximo 9 dígitos inteiros e 3 casas decimais")
        BigDecimal quantidade,

        @Schema(example = "ana")
        @NotBlank(message = "é obrigatório")
        @Size(max = 100, message = "deve ter no máximo 100 caracteres")
        String responsavel) {
}
