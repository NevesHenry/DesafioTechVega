package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.RecursoNaoEncontradoException;
import br.com.vega.armazem.domain.exception.RegraNegocioException;
import br.com.vega.armazem.domain.model.Compartimento;
import br.com.vega.armazem.domain.model.Ingrediente;
import br.com.vega.armazem.domain.model.Movimentacao;
import br.com.vega.armazem.dto.CadastroIngredienteRequest;
import br.com.vega.armazem.dto.IngredienteResponse;
import br.com.vega.armazem.dto.MovimentacaoRequest;
import br.com.vega.armazem.repository.CompartimentoRepository;
import br.com.vega.armazem.repository.IngredienteRepository;
import br.com.vega.armazem.repository.MovimentacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Único ponto de escrita de estoque. Cada operação roda em uma transação que:
 * <ol>
 *   <li>trava o compartimento envolvido ({@code SELECT ... FOR UPDATE}), serializando operações concorrentes
 *       no mesmo compartimento;</li>
 *   <li>aplica as regras do domínio ({@link Movimentacao#entrada} / {@link Movimentacao#saida});</li>
 *   <li>grava estoque e histórico juntos. Se qualquer passo falhar, nada é gravado.</li>
 * </ol>
 */
@Service
@Transactional
public class EstoqueService {

    private static final Logger log = LoggerFactory.getLogger(EstoqueService.class);

    private final CompartimentoRepository compartimentos;
    private final IngredienteRepository ingredientes;
    private final MovimentacaoRepository movimentacoes;
    private final Clock clock;

    public EstoqueService(CompartimentoRepository compartimentos, IngredienteRepository ingredientes,
                          MovimentacaoRepository movimentacoes, Clock clock) {
        this.compartimentos = compartimentos;
        this.ingredientes = ingredientes;
        this.movimentacoes = movimentacoes;
        this.clock = clock;
    }

    /** Resultado do cadastro: {@code criado} é falso quando a entrada somou em um ingrediente já existente. */
    public record ResultadoCadastro(IngredienteResponse ingrediente, boolean criado) {
    }

    /**
     * Cadastra um ingrediente no compartimento, registrando uma ENTRADA. Se já existir ingrediente com o mesmo
     * nome e tipo no compartimento, a quantidade é somada a ele.
     */
    public ResultadoCadastro cadastrar(CadastroIngredienteRequest requisicao) {
        Compartimento compartimento = compartimentos.findByIdParaAtualizacao(requisicao.compartimentoId())
                .orElseThrow(() -> compartimentoInexistente(requisicao.compartimentoId()));
        LocalDateTime agora = agora();

        Optional<Ingrediente> existente = ingredientes.findByCompartimentoIdAndTipoAndNomeNormalizado(
                compartimento.getId(), requisicao.tipo(), Ingrediente.normalizarNome(requisicao.nome()));
        Ingrediente ingrediente = existente.orElseGet(
                () -> Ingrediente.novo(requisicao.nome(), requisicao.tipo(), compartimento, agora));

        Movimentacao movimentacao = Movimentacao.entrada(ingrediente, requisicao.quantidade(),
                requisicao.responsavel(), agora);
        if (existente.isEmpty()) {
            ingredientes.save(ingrediente);
        }
        movimentacoes.save(movimentacao);

        log.info("ENTRADA de {} {} de '{}' ({}) em {} por {}", movimentacao.getQuantidade(),
                ingrediente.getTipo().getUnidade(), ingrediente.getNome(), ingrediente.getTipo(),
                compartimento.getCodigo(), movimentacao.getResponsavel());
        return new ResultadoCadastro(IngredienteResponse.de(ingrediente), existente.isEmpty());
    }

    /** Entrada (reposição) em um ingrediente já cadastrado. */
    public IngredienteResponse registrarEntrada(Long ingredienteId, MovimentacaoRequest requisicao) {
        return movimentar(ingredienteId, (ingrediente, agora) ->
                Movimentacao.entrada(ingrediente, requisicao.quantidade(), requisicao.responsavel(), agora));
    }

    /** Saída (retirada/venda) de um ingrediente. */
    public IngredienteResponse registrarSaida(Long ingredienteId, MovimentacaoRequest requisicao) {
        return movimentar(ingredienteId, (ingrediente, agora) ->
                Movimentacao.saida(ingrediente, requisicao.quantidade(), requisicao.responsavel(), agora));
    }

    private IngredienteResponse movimentar(Long ingredienteId,
                                           BiFunction<Ingrediente, LocalDateTime, Movimentacao> operacao) {
        // 1) descobre o compartimento sem carregar o ingrediente; 2) trava o compartimento;
        // 3) só então lê o ingrediente, já com o estado protegido pelo lock.
        Long compartimentoId = ingredientes.findCompartimentoIdById(ingredienteId)
                .orElseThrow(() -> RecursoNaoEncontradoException.ingrediente(ingredienteId));
        compartimentos.findByIdParaAtualizacao(compartimentoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.compartimento(compartimentoId));
        Ingrediente ingrediente = ingredientes.findById(ingredienteId)
                .orElseThrow(() -> RecursoNaoEncontradoException.ingrediente(ingredienteId));

        Movimentacao movimentacao = operacao.apply(ingrediente, agora());
        movimentacoes.save(movimentacao);

        log.info("{} de {} {} de '{}' em {} por {}", movimentacao.getTipoOperacao(), movimentacao.getQuantidade(),
                ingrediente.getTipo().getUnidade(), ingrediente.getNome(), movimentacao.getCompartimentoCodigo(),
                movimentacao.getResponsavel());
        return IngredienteResponse.de(ingrediente);
    }

    /** Data e hora atuais em milissegundos, mesma precisão devolvida antes e depois de gravar. */
    private LocalDateTime agora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private RegraNegocioException compartimentoInexistente(Long id) {
        String validos = compartimentos.findAllByOrderByIdAsc().stream()
                .map(c -> "%d (%s)".formatted(c.getId(), c.getCodigo()))
                .collect(Collectors.joining(", "));
        return new RegraNegocioException(CodigoErro.COMPARTIMENTO_INEXISTENTE,
                "O compartimento %d não existe. Compartimentos válidos: %s.".formatted(id, validos));
    }
}
