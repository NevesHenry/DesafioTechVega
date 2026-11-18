package br.com.vega.armazem.web.controller;

import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.dto.CompartimentoResponse;
import br.com.vega.armazem.service.CompartimentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/compartimentos")
@Tag(name = "Compartimentos", description = "Situação e disponibilidade dos compartimentos C1 a C5")
public class CompartimentoController {

    private final CompartimentoService compartimentoService;

    public CompartimentoController(CompartimentoService compartimentoService) {
        this.compartimentoService = compartimentoService;
    }

    @GetMapping
    @Operation(summary = "Lista os compartimentos com tipo, ocupação e espaço livre")
    public List<CompartimentoResponse> listar() {
        return compartimentoService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um compartimento pelo id")
    public CompartimentoResponse buscar(@PathVariable Long id) {
        return compartimentoService.buscar(id);
    }

    @GetMapping("/disponiveis")
    @Operation(summary = "Compartimentos com espaço livre para armazenar a quantidade do tipo informado",
            description = "Ordenados por melhor encaixe: primeiro os que já guardam o tipo e ficariam mais "
                    + "cheios, depois os vazios. capacidadeMaxima e espacoLivre são calculados para o tipo pedido.")
    public List<CompartimentoResponse> disponiveisParaArmazenamento(
            @RequestParam
            @Positive(message = "deve ser maior que zero")
            @Digits(integer = 9, fraction = 3, message = "deve ter no máximo 9 dígitos inteiros e 3 casas decimais")
            BigDecimal quantidade,
            @RequestParam TipoIngrediente tipo) {
        return compartimentoService.disponiveisParaArmazenamento(tipo, quantidade);
    }

    @GetMapping("/disponiveis-para-venda")
    @Operation(summary = "Compartimentos com estoque (> 0) do tipo informado, disponíveis para retirada/venda")
    public List<CompartimentoResponse> disponiveisParaVenda(@RequestParam TipoIngrediente tipo) {
        return compartimentoService.disponiveisParaVenda(tipo);
    }
}
