package br.com.vega.armazem.dto;

import br.com.vega.armazem.domain.model.TipoIngrediente;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Cadastro de ingrediente: registra uma ENTRADA no compartimento informado")
public record CadastroIngredienteRequest(

        @Schema(example = "Farinha de Trigo")
        @NotBlank(message = "é obrigatório")
        @Size(max = 120, message = "deve ter no máximo 120 caracteres")
        String nome,

        @Schema(example = "SECO", description = "SECO, LIQUIDO ou REFRIGERADO (também aceita plural, ex.: SECOS)")
        @NotNull(message = "é obrigatório")
        TipoIngrediente tipo,

        @Schema(example = "400", description = "kg para SECO/REFRIGERADO, litros para LIQUIDO")
        @NotNull(message = "é obrigatório")
        @Positive(message = "deve ser maior que zero")
        @Digits(integer = 9, fraction = 3, message = "deve ter no máximo 9 dígitos inteiros e 3 casas decimais")
        BigDecimal quantidade,

        @Schema(example = "1", description = "Id do compartimento (1 a 5 = C1 a C5)")
        @NotNull(message = "é obrigatório")
        Long compartimentoId,

        @Schema(example = "pedro")
        @NotBlank(message = "é obrigatório")
        @Size(max = 100, message = "deve ter no máximo 100 caracteres")
        String responsavel) {
}
