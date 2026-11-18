package br.com.vega.armazem.web.controller;

import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.dto.CadastroIngredienteRequest;
import br.com.vega.armazem.dto.IngredienteResponse;
import br.com.vega.armazem.dto.MovimentacaoRequest;
import br.com.vega.armazem.dto.VolumeResponse;
import br.com.vega.armazem.service.EstoqueService;
import br.com.vega.armazem.service.IngredienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/ingredientes")
@Tag(name = "Ingredientes", description = "Cadastro, consulta e movimentação de ingredientes")
public class IngredienteController {

    private final EstoqueService estoqueService;
    private final IngredienteService ingredienteService;

    public IngredienteController(EstoqueService estoqueService, IngredienteService ingredienteService) {
        this.estoqueService = estoqueService;
        this.ingredienteService = ingredienteService;
    }

    @PostMapping
    @Operation(summary = "Cadastra um ingrediente em um compartimento (gera ENTRADA no histórico)",
            description = "Se o compartimento já tiver um ingrediente com o mesmo nome e tipo, a quantidade é "
                    + "somada a ele e a resposta é 200. Senão, um novo ingrediente é criado e a resposta é 201.")
    @ApiResponse(responseCode = "201", description = "Ingrediente criado")
    @ApiResponse(responseCode = "200", description = "Quantidade somada a ingrediente existente")
    @ApiResponse(responseCode = "400", description = "Dados inválidos, tipo incompatível, troca de tipo bloqueada "
            + "ou capacidade excedida")
    public ResponseEntity<IngredienteResponse> cadastrar(@Valid @RequestBody CadastroIngredienteRequest requisicao) {
        EstoqueService.ResultadoCadastro resultado = estoqueService.cadastrar(requisicao);
        if (!resultado.criado()) {
            return ResponseEntity.ok(resultado.ingrediente());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(resultado.ingrediente().id()).toUri();
        return ResponseEntity.created(location).body(resultado.ingrediente());
    }

    @GetMapping
    @Operation(summary = "Lista ingredientes, com filtros opcionais")
    public List<IngredienteResponse> listar(
            @RequestParam(required = false) TipoIngrediente tipo,
            @RequestParam(required = false) Long compartimentoId,
            @Parameter(description = "true para listar apenas ingredientes com quantidade > 0")
            @RequestParam(defaultValue = "false") boolean comEstoque) {
        return ingredienteService.listar(tipo, compartimentoId, comEstoque);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um ingrediente pelo id")
    @ApiResponse(responseCode = "404", description = "Ingrediente não encontrado")
    public IngredienteResponse buscar(@PathVariable Long id) {
        return ingredienteService.buscar(id);
    }

    @GetMapping("/volume")
    @Operation(summary = "Volume total armazenado por tipo de ingrediente")
    public List<VolumeResponse> volume() {
        return ingredienteService.volumePorTipo();
    }

    @PostMapping("/{id}/entradas")
    @Operation(summary = "Registra uma ENTRADA (reposição) em um ingrediente existente")
    @ApiResponse(responseCode = "400", description = "Capacidade excedida, tipo incompatível ou dados inválidos")
    @ApiResponse(responseCode = "404", description = "Ingrediente não encontrado")
    public IngredienteResponse registrarEntrada(@PathVariable Long id,
                                                @Valid @RequestBody MovimentacaoRequest requisicao) {
        return estoqueService.registrarEntrada(id, requisicao);
    }

    @PostMapping("/{id}/saidas")
    @Operation(summary = "Registra uma SAÍDA (retirada/venda) de um ingrediente")
    @ApiResponse(responseCode = "400", description = "Estoque insuficiente ou dados inválidos")
    @ApiResponse(responseCode = "404", description = "Ingrediente não encontrado")
    public IngredienteResponse registrarSaida(@PathVariable Long id,
                                              @Valid @RequestBody MovimentacaoRequest requisicao) {
        return estoqueService.registrarSaida(id, requisicao);
    }
}
