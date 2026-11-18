package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.RecursoNaoEncontradoException;
import br.com.vega.armazem.domain.model.Quantidades;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.dto.IngredienteResponse;
import br.com.vega.armazem.dto.VolumeResponse;
import br.com.vega.armazem.repository.CompartimentoRepository;
import br.com.vega.armazem.repository.IngredienteRepository;
import br.com.vega.armazem.repository.VolumePorTipo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Consultas de ingredientes e de volume armazenado. */
@Service
@Transactional(readOnly = true)
public class IngredienteService {

    private final IngredienteRepository ingredientes;
    private final CompartimentoRepository compartimentos;

    public IngredienteService(IngredienteRepository ingredientes, CompartimentoRepository compartimentos) {
        this.ingredientes = ingredientes;
        this.compartimentos = compartimentos;
    }

    public List<IngredienteResponse> listar(TipoIngrediente tipo, Long compartimentoId, boolean somenteComEstoque) {
        return ingredientes.buscar(tipo, compartimentoId, somenteComEstoque).stream()
                .map(IngredienteResponse::de)
                .toList();
    }

    public IngredienteResponse buscar(Long id) {
        return ingredientes.findComCompartimentoById(id)
                .map(IngredienteResponse::de)
                .orElseThrow(() -> RecursoNaoEncontradoException.ingrediente(id));
    }

    /**
     * Total armazenado por tipo, sempre com os 3 tipos (zero quando não há estoque) e em ordem alfabética.
     * A soma usa os totais desnormalizados dos 5 compartimentos, então não depende do volume de ingredientes.
     */
    public List<VolumeResponse> volumePorTipo() {
        Map<TipoIngrediente, BigDecimal> totais = compartimentos.somarVolumePorTipo().stream()
                .collect(Collectors.toMap(VolumePorTipo::tipo, VolumePorTipo::total));
        return Arrays.stream(TipoIngrediente.values())
                .sorted(Comparator.comparing(TipoIngrediente::name))
                .map(tipo -> new VolumeResponse(tipo, totais.getOrDefault(tipo, Quantidades.ZERO)))
                .toList();
    }
}
