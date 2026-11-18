package br.com.vega.armazem.web;

import br.com.vega.armazem.domain.model.Movimentacao;
import br.com.vega.armazem.repository.MovimentacaoRepository;
import br.com.vega.armazem.support.ClienteApi;
import br.com.vega.armazem.support.TesteIntegracaoBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static br.com.vega.armazem.support.ClienteApi.assertProblema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que o estoque nunca muda sem histórico: se a gravação da movimentação falha, a transação inteira
 * (ingrediente e compartimento incluídos) é desfeita.
 */
@DisplayName("Histórico obrigatório: sem histórico, sem alteração de estoque")
class HistoricoObrigatorioTest extends TesteIntegracaoBase {

    @MockitoSpyBean
    private MovimentacaoRepository movimentacaoRepository;

    private ClienteApi api;

    @BeforeEach
    void iniciarCliente() {
        api = new ClienteApi(mockMvc);
    }

    @AfterEach
    void limparStub() {
        Mockito.reset(movimentacaoRepository);
    }

    private void falharAoGravarHistorico() {
        doThrow(new RuntimeException("falha simulada ao gravar o histórico"))
                .when(movimentacaoRepository).save(any(Movimentacao.class));
    }

    private BigDecimal saldo(long compartimentoId) {
        return jdbc.queryForObject("SELECT quantidade_atual FROM compartimento WHERE id = ?", BigDecimal.class,
                compartimentoId);
    }

    private long contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
    }

    @Test
    @DisplayName("falha ao gravar a movimentação no cadastro: 500, sem ingrediente e compartimento intacto")
    void cadastroSemHistoricoNaoGravaNada() throws Exception {
        falharAoGravarHistorico();

        assertProblema(api.cadastrar("Farinha de Trigo", "SECO", "400", 1, "pedro"), 500, "ERRO_INTERNO")
                .andExpect(jsonPath("$.detail", not(containsString("falha simulada"))));

        assertThat(contar("ingrediente")).as("nenhum ingrediente gravado").isZero();
        assertThat(contar("movimentacao")).isZero();
        assertThat(saldo(1)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT tipo_atual FROM compartimento WHERE id = 1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT ultima_movimentacao_em FROM compartimento WHERE id = 1",
                java.sql.Date.class)).isNull();
    }

    @Test
    @DisplayName("falha ao gravar a movimentação em uma saída: ingrediente e compartimento mantêm as quantidades")
    void saidaSemHistoricoNaoAlteraEstoque() throws Exception {
        long id = api.cadastrar("Farinha", "SECO", "400", 1);
        assertThat(contar("movimentacao")).isEqualTo(1);

        falharAoGravarHistorico();
        assertProblema(api.saida(id, "150", "joao"), 500, "ERRO_INTERNO");

        assertThat(jdbc.queryForObject("SELECT quantidade FROM ingrediente WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("400");
        assertThat(saldo(1)).isEqualByComparingTo("400");
        assertThat(contar("movimentacao")).isEqualTo(1);
    }

    @Test
    @DisplayName("falha ao gravar a movimentação em uma entrada de reposição: quantidades mantidas")
    void entradaSemHistoricoNaoAlteraEstoque() throws Exception {
        long id = api.cadastrar("Farinha", "SECO", "100", 1);

        falharAoGravarHistorico();
        assertProblema(api.entrada(id, "50", "ana"), 500, "ERRO_INTERNO");

        assertThat(jdbc.queryForObject("SELECT quantidade FROM ingrediente WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("100");
        assertThat(saldo(1)).isEqualByComparingTo("100");
        assertThat(contar("movimentacao")).isEqualTo(1);
    }

    @Test
    @DisplayName("falha ao gravar a movimentação ao mesclar em ingrediente existente: nada muda")
    void mesclagemSemHistoricoNaoAlteraEstoque() throws Exception {
        long id = api.cadastrar("Farinha", "SECO", "100", 1);

        falharAoGravarHistorico();
        assertProblema(api.cadastrar("Farinha", "SECO", "50", 1, "ana"), 500, "ERRO_INTERNO");

        assertThat(jdbc.queryForObject("SELECT quantidade FROM ingrediente WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("100");
        assertThat(saldo(1)).isEqualByComparingTo("100");
        assertThat(contar("ingrediente")).isEqualTo(1);
    }

    @Test
    @DisplayName("depois de limpar o stub, as operações voltam a funcionar normalmente")
    void recuperaAposRemoverFalha() throws Exception {
        falharAoGravarHistorico();
        assertProblema(api.cadastrar("Farinha", "SECO", "100", 1, "ana"), 500, "ERRO_INTERNO");

        Mockito.reset(movimentacaoRepository);

        api.cadastrar("Farinha", "SECO", "100", 1, "ana").andExpect(status().isCreated());
        assertThat(saldo(1)).isEqualByComparingTo("100");
        assertThat(contar("movimentacao")).isEqualTo(1);
    }
}
