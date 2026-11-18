package br.com.vega.armazem.web.controller;

import br.com.vega.armazem.domain.model.TipoOperacao;
import br.com.vega.armazem.dto.MovimentacaoResponse;
import br.com.vega.armazem.service.HistoricoService;
import br.com.vega.armazem.service.OrdenacaoHistorico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/historico")
@Tag(name = "Histórico", description = "Histórico de entradas e saídas")
public class HistoricoController {

    private final HistoricoService historicoService;

    public HistoricoController(HistoricoService historicoService) {
        this.historicoService = historicoService;
    }

    @GetMapping
    @Operation(summary = "Lista movimentações ordenadas por data e compartimento",
            description = "sortBy define o critério principal (o outro vira desempate). order vale para ambos.")
    public PagedModel<MovimentacaoResponse> listar(
            @Parameter(description = "date (padrão) ou compartimento")
            @RequestParam(defaultValue = "date") String sortBy,
            @Parameter(description = "asc ou desc (padrão: desc, mais recentes primeiro)")
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) TipoOperacao tipoOperacao,
            @RequestParam(required = false) Long compartimentoId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "deve ser maior ou igual a 0") int page,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "deve ser no mínimo 1")
            @Max(value = 500, message = "deve ser no máximo 500") int size) {
        return new PagedModel<>(historicoService.listar(OrdenacaoHistorico.de(sortBy, order), tipoOperacao,
                compartimentoId, page, size));
    }
}
