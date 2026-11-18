package br.com.vega.armazem.concorrencia;

import br.com.vega.armazem.domain.exception.ArmazemException;
import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.model.Compartimento;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.dto.CadastroIngredienteRequest;
import br.com.vega.armazem.dto.MovimentacaoRequest;
import br.com.vega.armazem.repository.CompartimentoRepository;
import br.com.vega.armazem.repository.IngredienteRepository;
import br.com.vega.armazem.service.EstoqueService;
import br.com.vega.armazem.support.TesteIntegracaoBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dispara operações realmente simultâneas (várias threads liberadas ao mesmo tempo por um portão) contra o
 * serviço transacional e confere que o lock pessimista do compartimento preserva as regras e os invariantes.
 * Qualquer exceção que não seja de negócio, como timeout de lock, faz o teste falhar.
 */
@DisplayName("Concorrência em entradas e saídas")
class ConcorrenciaEstoqueTest extends TesteIntegracaoBase {

    private static final int THREADS = 20;

    @Autowired
    private EstoqueService estoque;

    @Autowired
    private CompartimentoRepository compartimentos;

    @Autowired
    private IngredienteRepository ingredientes;

    private record Resultado(boolean sucesso, CodigoErro codigo) {
    }

    @Test
    @DisplayName("saídas simultâneas nunca deixam o estoque negativo")
    void saidasSimultaneasNaoDeixamEstoqueNegativo() throws Exception {
        Long id = cadastrar("Açúcar", TipoIngrediente.SECO, "100", 1L);

        List<Resultado> resultados = executarAoMesmoTempo(IntStream.range(0, THREADS)
                .<Callable<?>>mapToObj(i -> () -> estoque.registrarSaida(id, movimento("10", "op-" + i)))
                .toList());

        assertThat(resultados).filteredOn(Resultado::sucesso).hasSize(10);
        assertThat(resultados).filteredOn(r -> !r.sucesso()).hasSize(10)
                .allMatch(r -> r.codigo() == CodigoErro.ESTOQUE_INSUFICIENTE);
        assertThat(ingredientes.findById(id).orElseThrow().getQuantidade()).isEqualByComparingTo("0");
        assertThat(compartimento(1L).getQuantidadeAtual()).isEqualByComparingTo("0");
        assertThat(contarMovimentacoes("SAIDA")).isEqualTo(10);
        assertInvariantes();
    }

    @Test
    @DisplayName("entradas simultâneas respeitam a capacidade e consolidam o mesmo ingrediente")
    void entradasSimultaneasRespeitamCapacidade() throws Exception {
        List<Resultado> resultados = executarAoMesmoTempo(IntStream.range(0, THREADS)
                .<Callable<?>>mapToObj(i -> () -> estoque.cadastrar(cadastro("Farinha de Trigo",
                        TipoIngrediente.SECO, "50", 1L)))
                .toList());

        // 600 kg / 50 kg = 12 entradas cabem; as outras 8 estouram a capacidade.
        assertThat(resultados).filteredOn(Resultado::sucesso).hasSize(12);
        assertThat(resultados).filteredOn(r -> !r.sucesso()).hasSize(8)
                .allMatch(r -> r.codigo() == CodigoErro.CAPACIDADE_EXCEDIDA);
        assertThat(compartimento(1L).getQuantidadeAtual()).isEqualByComparingTo("600");
        assertThat(ingredientes.count()).as("entradas do mesmo ingrediente somam no mesmo registro").isEqualTo(1);
        assertThat(contarMovimentacoes("ENTRADA")).isEqualTo(12);
        assertInvariantes();
    }

    @Test
    @DisplayName("tipos diferentes disputando um compartimento vazio: só um tipo vence")
    void tiposDiferentesNaoSeMisturamSobConcorrencia() throws Exception {
        List<Callable<?>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            tarefas.add(() -> estoque.cadastrar(cadastro("Farinha", TipoIngrediente.SECO, "10", 2L)));
            tarefas.add(() -> estoque.cadastrar(cadastro("Leite", TipoIngrediente.LIQUIDO, "10", 2L)));
        }

        List<Resultado> resultados = executarAoMesmoTempo(tarefas);

        Compartimento c2 = compartimento(2L);
        assertThat(c2.getTipoAtual()).isNotNull();
        assertThat(resultados).filteredOn(Resultado::sucesso).hasSize(THREADS / 2);
        assertThat(resultados).filteredOn(r -> !r.sucesso()).hasSize(THREADS / 2)
                .allMatch(r -> r.codigo() == CodigoErro.TIPO_INCOMPATIVEL);
        assertThat(c2.getQuantidadeAtual()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT tipo) FROM ingrediente WHERE compartimento_id = 2",
                Integer.class)).isEqualTo(1);
        assertInvariantes();
    }

    @Test
    @DisplayName("carga mista em vários compartimentos mantém estoque, compartimento e histórico consistentes")
    void cargaMistaMantemInvariantes() throws Exception {
        List<Long> ids = List.of(
                cadastrar("Farinha", TipoIngrediente.SECO, "300", 1L),
                cadastrar("Leite", TipoIngrediente.LIQUIDO, "250", 2L),
                cadastrar("Manteiga", TipoIngrediente.REFRIGERADO, "200", 3L));
        Random aleatorio = new Random(42);
        List<Callable<?>> tarefas = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            Long id = ids.get(aleatorio.nextInt(ids.size()));
            MovimentacaoRequest requisicao = movimento(String.valueOf(1 + aleatorio.nextInt(80)), "op-" + i);
            tarefas.add(aleatorio.nextBoolean()
                    ? () -> estoque.registrarEntrada(id, requisicao)
                    : () -> estoque.registrarSaida(id, requisicao));
        }

        List<Resultado> resultados = executarAoMesmoTempo(tarefas);

        long sucessos = resultados.stream().filter(Resultado::sucesso).count();
        assertThat(resultados).filteredOn(r -> !r.sucesso())
                .allMatch(r -> r.codigo() == CodigoErro.CAPACIDADE_EXCEDIDA
                        || r.codigo() == CodigoErro.ESTOQUE_INSUFICIENTE);
        assertThat(contarMovimentacoes("ENTRADA") + contarMovimentacoes("SAIDA"))
                .as("cada operação bem-sucedida gera exatamente um registro de histórico (+3 cadastros iniciais)")
                .isEqualTo(sucessos + 3);
        assertInvariantes();
    }

    // ------------------------------------------------------------------ apoio

    /** Para cada compartimento: 0 <= saldo <= capacidade, saldo = soma dos ingredientes = entradas - saídas. */
    private void assertInvariantes() {
        for (Compartimento c : compartimentos.findAll()) {
            BigDecimal somaIngredientes = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(quantidade), 0) FROM ingrediente WHERE compartimento_id = ?",
                    BigDecimal.class, c.getId());
            BigDecimal saldoHistorico = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(CASE WHEN tipo_operacao = 'ENTRADA' THEN quantidade ELSE -quantidade END), 0)
                      FROM movimentacao WHERE compartimento_id = ?""", BigDecimal.class, c.getId());
            assertThat(c.getQuantidadeAtual()).as("saldo de %s", c.getCodigo())
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO)
                    .isEqualByComparingTo(somaIngredientes)
                    .isEqualByComparingTo(saldoHistorico);
            if (c.getTipoAtual() != null) {
                assertThat(c.getQuantidadeAtual()).isLessThanOrEqualTo(c.getTipoAtual().getCapacidadeMaxima());
            }
        }
    }

    private List<Resultado> executarAoMesmoTempo(List<Callable<?>> tarefas)
            throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tarefas.size(), 32));
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Resultado>> futuros = tarefas.stream().map(tarefa -> pool.submit(() -> {
                largada.await();
                try {
                    tarefa.call();
                    return new Resultado(true, null);
                } catch (ArmazemException e) {
                    return new Resultado(false, e.getCodigo());
                }
            })).toList();
            largada.countDown();
            List<Resultado> resultados = new ArrayList<>();
            for (Future<Resultado> futuro : futuros) {
                resultados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("Operações concorrentes não terminaram a tempo", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private Long cadastrar(String nome, TipoIngrediente tipo, String quantidade, Long compartimentoId) {
        return estoque.cadastrar(cadastro(nome, tipo, quantidade, compartimentoId)).ingrediente().id();
    }

    private static CadastroIngredienteRequest cadastro(String nome, TipoIngrediente tipo, String quantidade,
                                                       Long compartimentoId) {
        return new CadastroIngredienteRequest(nome, tipo, new BigDecimal(quantidade), compartimentoId, "teste");
    }

    private static MovimentacaoRequest movimento(String quantidade, String responsavel) {
        return new MovimentacaoRequest(new BigDecimal(quantidade), responsavel);
    }

    private Compartimento compartimento(Long id) {
        return compartimentos.findById(id).orElseThrow();
    }

    private long contarMovimentacoes(String tipoOperacao) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao WHERE tipo_operacao = ?", Long.class,
                tipoOperacao);
    }
}
