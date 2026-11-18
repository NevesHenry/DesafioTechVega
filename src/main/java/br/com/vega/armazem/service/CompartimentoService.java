package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.RecursoNaoEncontradoException;
import br.com.vega.armazem.domain.model.Quantidades;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.dto.CompartimentoResponse;
import br.com.vega.armazem.repository.CompartimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Consultas de compartimentos: situação atual e disponibilidade para armazenar ou vender. */
@Service
@Transactional(readOnly = true)
public class CompartimentoService {

    private final CompartimentoRepository compartimentos;
    private final Clock clock;

    public CompartimentoService(CompartimentoRepository compartimentos, Clock clock) {
        this.compartimentos = compartimentos;
        this.clock = clock;
    }

    public List<CompartimentoResponse> listar() {
        LocalDate hoje = LocalDate.now(clock);
        return compartimentos.findAllByOrderByIdAsc().stream()
                .map(c -> CompartimentoResponse.de(c, hoje))
                .toList();
    }

    public CompartimentoResponse buscar(Long id) {
        return compartimentos.findById(id)
                .map(c -> CompartimentoResponse.de(c, LocalDate.now(clock)))
                .orElseThrow(() -> RecursoNaoEncontradoException.compartimento(id));
    }

    /**
     * Compartimentos que comportam {@code quantidade} do {@code tipo} agora, ordenados por melhor encaixe.
     * Usa uma única consulta sobre os totais já mantidos em cada compartimento. Se a quantidade passa da
     * capacidade do tipo, nem consulta o banco.
     */
    public List<CompartimentoResponse> disponiveisParaArmazenamento(TipoIngrediente tipo, BigDecimal quantidade) {
        BigDecimal pedida = Quantidades.positiva(quantidade);
        BigDecimal ocupacaoMaxima = tipo.getCapacidadeMaxima().subtract(pedida);
        if (ocupacaoMaxima.signum() < 0) {
            return List.of();
        }
        LocalDate hoje = LocalDate.now(clock);
        return compartimentos.findDisponiveisParaArmazenamento(tipo, ocupacaoMaxima, hoje).stream()
                .map(c -> CompartimentoResponse.paraTipo(c, tipo, hoje))
                .toList();
    }

    /** Compartimentos com saldo maior que zero do tipo informado. */
    public List<CompartimentoResponse> disponiveisParaVenda(TipoIngrediente tipo) {
        LocalDate hoje = LocalDate.now(clock);
        return compartimentos.findDisponiveisParaVenda(tipo).stream()
                .map(c -> CompartimentoResponse.de(c, hoje))
                .toList();
    }
}
