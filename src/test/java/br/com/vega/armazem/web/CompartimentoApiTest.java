package br.com.vega.armazem.web;

import br.com.vega.armazem.support.ClienteApi;
import br.com.vega.armazem.support.TesteIntegracaoBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;

import static br.com.vega.armazem.support.ClienteApi.assertProblema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API de compartimentos")
class CompartimentoApiTest extends TesteIntegracaoBase {

    private ClienteApi api;

    @BeforeEach
    void iniciarCliente() {
        api = new ClienteApi(mockMvc);
    }

    private List<Long> idsDisponiveis(String quantidade, String tipo) throws Exception {
        ResultActions resposta = api.consultar("/compartimentos/disponiveis?quantidade=%s&tipo=%s"
                .formatted(quantidade, tipo)).andExpect(status().isOk());
        return ClienteApi.ids(resposta, "$[*].id");
    }

    @Nested
    @DisplayName("GET /compartimentos e /compartimentos/{id}")
    class Consulta {

        @Test
        @DisplayName("lista C1..C5 vazios com os campos documentados")
        void listaInicial() throws Exception {
            api.consultar("/compartimentos")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.contains(1, 2, 3, 4, 5)))
                    .andExpect(jsonPath("$[*].codigo",
                            org.hamcrest.Matchers.contains("C1", "C2", "C3", "C4", "C5")))
                    .andExpect(jsonPath("$[0].tipoAtual", nullValue()))
                    .andExpect(jsonPath("$[0].unidade", nullValue()))
                    .andExpect(jsonPath("$[0].capacidadeMaxima", nullValue()))
                    .andExpect(jsonPath("$[0].espacoLivre", nullValue()))
                    .andExpect(jsonPath("$[0].quantidadeAtual", closeTo(0.0, 0.0001)))
                    .andExpect(jsonPath("$[0].percentualOcupacao", closeTo(0.0, 0.0001)))
                    .andExpect(jsonPath("$[0].vazio", is(true)))
                    .andExpect(jsonPath("$[0].aceitaQualquerTipo", is(true)))
                    .andExpect(jsonPath("$[0].ultimaMovimentacaoEm", nullValue()))
                    .andExpect(jsonPath("$[4].codigo", is("C5")));
        }

        @Test
        @DisplayName("os 11 campos documentados estão presentes")
        void camposDocumentados() throws Exception {
            api.consultar("/compartimentos/1")
                    .andExpect(jsonPath("$.length()", is(11)))
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.codigo").exists())
                    .andExpect(jsonPath("$.tipoAtual").hasJsonPath())
                    .andExpect(jsonPath("$.unidade").hasJsonPath())
                    .andExpect(jsonPath("$.capacidadeMaxima").hasJsonPath())
                    .andExpect(jsonPath("$.quantidadeAtual").exists())
                    .andExpect(jsonPath("$.espacoLivre").hasJsonPath())
                    .andExpect(jsonPath("$.percentualOcupacao").exists())
                    .andExpect(jsonPath("$.vazio").exists())
                    .andExpect(jsonPath("$.aceitaQualquerTipo").exists())
                    .andExpect(jsonPath("$.ultimaMovimentacaoEm").hasJsonPath());
        }

        @Test
        @DisplayName("compartimento ocupado mostra tipo, ocupação e espaço livre")
        void compartimentoOcupado() throws Exception {
            api.cadastrar("Farinha", "SECO", "300", 1);
            api.cadastrar("Leite", "LIQUIDO", "125", 2);

            api.consultar("/compartimentos/1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.codigo", is("C1")))
                    .andExpect(jsonPath("$.tipoAtual", is("SECO")))
                    .andExpect(jsonPath("$.unidade", is("kg")))
                    .andExpect(jsonPath("$.capacidadeMaxima", closeTo(600.0, 0.0001)))
                    .andExpect(jsonPath("$.quantidadeAtual", closeTo(300.0, 0.0001)))
                    .andExpect(jsonPath("$.espacoLivre", closeTo(300.0, 0.0001)))
                    .andExpect(jsonPath("$.percentualOcupacao", closeTo(50.0, 0.0001)))
                    .andExpect(jsonPath("$.vazio", is(false)))
                    .andExpect(jsonPath("$.aceitaQualquerTipo", is(false)))
                    .andExpect(jsonPath("$.ultimaMovimentacaoEm", is("2026-11-17")));
            api.consultar("/compartimentos/2")
                    .andExpect(jsonPath("$.tipoAtual", is("LIQUIDO")))
                    .andExpect(jsonPath("$.unidade", is("L")))
                    .andExpect(jsonPath("$.capacidadeMaxima", closeTo(500.0, 0.0001)))
                    .andExpect(jsonPath("$.espacoLivre", closeTo(375.0, 0.0001)))
                    .andExpect(jsonPath("$.percentualOcupacao", closeTo(25.0, 0.0001)));
        }

        @Test
        @DisplayName("compartimento esvaziado hoje: vazio, mas não aceita qualquer tipo; amanhã aceita")
        void esvaziadoHoje() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);
            api.sair(id, "100");

            api.consultar("/compartimentos/1")
                    .andExpect(jsonPath("$.tipoAtual", is("SECO")))
                    .andExpect(jsonPath("$.vazio", is(true)))
                    .andExpect(jsonPath("$.aceitaQualquerTipo", is(false)))
                    .andExpect(jsonPath("$.espacoLivre", closeTo(600.0, 0.0001)))
                    .andExpect(jsonPath("$.percentualOcupacao", closeTo(0.0, 0.0001)));

            relogio.avancar(Duration.ofDays(1));

            api.consultar("/compartimentos/1").andExpect(jsonPath("$.aceitaQualquerTipo", is(true)));
        }

        @Test
        @DisplayName("id inexistente: 404 COMPARTIMENTO_NAO_ENCONTRADO")
        void naoEncontrado() throws Exception {
            assertProblema(api.consultar("/compartimentos/99"), 404, "COMPARTIMENTO_NAO_ENCONTRADO")
                    .andExpect(jsonPath("$.instance", is("/compartimentos/99")))
                    .andExpect(jsonPath("$.detail", containsString("99")));
        }

        @Test
        @DisplayName("id não numérico: 400 PARAMETRO_INVALIDO")
        void idInvalido() throws Exception {
            assertProblema(api.consultar("/compartimentos/abc"), 400, "PARAMETRO_INVALIDO");
        }
    }

    @Nested
    @DisplayName("GET /compartimentos/disponiveis")
    class Disponiveis {

        /** C1 SECO 100, C2 SECO 300, C3 LIQUIDO 50, C4 e C5 vazios. */
        private void estadoBase() throws Exception {
            api.cadastrar("Farinha", "SECO", "100", 1);
            api.cadastrar("Trigo", "SECO", "300", 2);
            api.cadastrar("Leite", "LIQUIDO", "50", 3);
        }

        @Test
        @DisplayName("melhor encaixe: mais ocupados do mesmo tipo primeiro, depois vazios por id; exclui outro tipo")
        void ordenacaoPorEncaixe() throws Exception {
            estadoBase();

            assertThat(idsDisponiveis("100", "SECO")).containsExactly(2L, 1L, 4L, 5L);
            assertThat(idsDisponiveis("100", "secos")).containsExactly(2L, 1L, 4L, 5L);
        }

        @Test
        @DisplayName("capacidadeMaxima e espacoLivre são calculados para o tipo pedido")
        void camposParaOTipoPedido() throws Exception {
            estadoBase();

            api.consultar("/compartimentos/disponiveis?quantidade=100&tipo=SECO")
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].codigo", is("C2")))
                    .andExpect(jsonPath("$[0].unidade", is("kg")))
                    .andExpect(jsonPath("$[0].capacidadeMaxima", closeTo(600.0, 0.0001)))
                    .andExpect(jsonPath("$[0].quantidadeAtual", closeTo(300.0, 0.0001)))
                    .andExpect(jsonPath("$[0].espacoLivre", closeTo(300.0, 0.0001)))
                    .andExpect(jsonPath("$[1].codigo", is("C1")))
                    .andExpect(jsonPath("$[1].espacoLivre", closeTo(500.0, 0.0001)))
                    .andExpect(jsonPath("$[2].codigo", is("C4")))
                    .andExpect(jsonPath("$[2].tipoAtual", nullValue()))
                    .andExpect(jsonPath("$[2].espacoLivre", closeTo(600.0, 0.0001)))
                    .andExpect(jsonPath("$[2].aceitaQualquerTipo", is(true)));

            api.consultar("/compartimentos/disponiveis?quantidade=100&tipo=LIQUIDO")
                    .andExpect(jsonPath("$[*].codigo", org.hamcrest.Matchers.contains("C3", "C4", "C5")))
                    .andExpect(jsonPath("$[0].unidade", is("L")))
                    .andExpect(jsonPath("$[0].capacidadeMaxima", closeTo(500.0, 0.0001)))
                    .andExpect(jsonPath("$[0].espacoLivre", closeTo(450.0, 0.0001)))
                    .andExpect(jsonPath("$[1].espacoLivre", closeTo(500.0, 0.0001)));

            api.consultar("/compartimentos/disponiveis?quantidade=10&tipo=REFRIGERADO")
                    .andExpect(jsonPath("$[*].codigo", org.hamcrest.Matchers.contains("C4", "C5")))
                    .andExpect(jsonPath("$[0].capacidadeMaxima", closeTo(400.0, 0.0001)))
                    .andExpect(jsonPath("$[0].espacoLivre", closeTo(400.0, 0.0001)));
        }

        @Test
        @DisplayName("a quantidade filtra pela capacidade restante (fronteira inclusive)")
        void fronteiraDaQuantidade() throws Exception {
            estadoBase();

            assertThat(idsDisponiveis("300", "SECO")).as("C2 tem exatamente 300 livres")
                    .containsExactly(2L, 1L, 4L, 5L);
            assertThat(idsDisponiveis("300.001", "SECO")).containsExactly(1L, 4L, 5L);
            assertThat(idsDisponiveis("500", "SECO")).containsExactly(1L, 4L, 5L);
            assertThat(idsDisponiveis("500.001", "SECO")).containsExactly(4L, 5L);
            assertThat(idsDisponiveis("600", "SECO")).containsExactly(4L, 5L);
        }

        @ParameterizedTest(name = "{1} acima da capacidade ({0}) devolve lista vazia")
        @CsvSource({"600.001, SECO", "500.001, LIQUIDO", "400.001, REFRIGERADO", "100000, SECO"})
        void quantidadeAcimaDaCapacidadeDoTipo(String quantidade, String tipo) throws Exception {
            estadoBase();

            api.consultar("/compartimentos/disponiveis?quantidade=%s&tipo=%s".formatted(quantidade, tipo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("compartimento esvaziado hoje só aparece para o mesmo tipo; no dia seguinte, para todos")
        void bloqueioDoDia() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 5);
            api.sair(id, "100");

            assertThat(idsDisponiveis("10", "SECO")).as("mesmo tipo, mesmo dia").containsExactly(1L, 2L, 3L, 4L, 5L);
            assertThat(idsDisponiveis("10", "LIQUIDO")).as("outro tipo, bloqueado hoje")
                    .containsExactly(1L, 2L, 3L, 4L);
            assertThat(idsDisponiveis("10", "REFRIGERADO")).containsExactly(1L, 2L, 3L, 4L);

            relogio.avancar(Duration.ofDays(1));

            assertThat(idsDisponiveis("10", "LIQUIDO")).as("outro tipo, no dia seguinte")
                    .containsExactly(1L, 2L, 3L, 4L, 5L);
            assertThat(idsDisponiveis("10", "REFRIGERADO")).containsExactly(1L, 2L, 3L, 4L, 5L);
        }

        @Test
        @DisplayName("compartimento ocupado por outro tipo nunca aparece, mesmo no dia seguinte")
        void outroTipoOcupado() throws Exception {
            api.cadastrar("Leite", "LIQUIDO", "10", 1);

            assertThat(idsDisponiveis("1", "SECO")).containsExactly(2L, 3L, 4L, 5L);
            relogio.avancar(Duration.ofDays(3));
            assertThat(idsDisponiveis("1", "SECO")).containsExactly(2L, 3L, 4L, 5L);
        }

        @Test
        @DisplayName("compartimento cheio do tipo não aparece para qualquer quantidade positiva")
        void compartimentoCheio() throws Exception {
            api.cadastrar("Farinha", "SECO", "600", 1);

            assertThat(idsDisponiveis("0.001", "SECO")).containsExactly(2L, 3L, 4L, 5L);
        }

        @Test
        @DisplayName("parâmetros ausentes: 400 PARAMETRO_INVALIDO")
        void parametrosAusentes() throws Exception {
            assertProblema(api.consultar("/compartimentos/disponiveis?tipo=SECO"), 400, "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", containsString("quantidade")));
            assertProblema(api.consultar("/compartimentos/disponiveis?quantidade=10"), 400, "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", containsString("tipo")));
            assertProblema(api.consultar("/compartimentos/disponiveis"), 400, "PARAMETRO_INVALIDO");
        }

        @Test
        @DisplayName("quantidade não numérica ou tipo inválido: 400 PARAMETRO_INVALIDO")
        void parametrosMalFormados() throws Exception {
            assertProblema(api.consultar("/compartimentos/disponiveis?quantidade=abc&tipo=SECO"), 400,
                    "PARAMETRO_INVALIDO");
            assertProblema(api.consultar("/compartimentos/disponiveis?quantidade=10&tipo=gelado"), 400,
                    "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", containsString("gelado")));
        }

        @ParameterizedTest(name = "quantidade={0}: 400 VALIDACAO")
        @ValueSource(strings = {"0", "-1", "-0.5", "1.0001"})
        void quantidadeInvalida(String quantidade) throws Exception {
            assertProblema(api.consultar("/compartimentos/disponiveis?quantidade=%s&tipo=SECO".formatted(quantidade)),
                    400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[0].campo", is("quantidade")));
        }
    }

    @Nested
    @DisplayName("GET /compartimentos/disponiveis-para-venda")
    class ParaVenda {

        @Test
        @DisplayName("só compartimentos do tipo com saldo maior que zero, ordenados por id")
        void soComEstoqueDoTipo() throws Exception {
            api.cadastrar("Farinha", "SECO", "100", 3);
            api.cadastrar("Trigo", "SECO", "50", 1);
            api.cadastrar("Leite", "LIQUIDO", "20", 2);

            ResultActions secos = api.consultar("/compartimentos/disponiveis-para-venda?tipo=SECO")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].codigo", is("C1")))
                    .andExpect(jsonPath("$[0].quantidadeAtual", closeTo(50.0, 0.0001)))
                    .andExpect(jsonPath("$[0].tipoAtual", is("SECO")))
                    .andExpect(jsonPath("$[1].codigo", is("C3")));
            assertThat(ClienteApi.ids(secos, "$[*].id")).containsExactly(1L, 3L);
            api.consultar("/compartimentos/disponiveis-para-venda?tipo=liquidos")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].codigo", is("C2")));
            api.consultar("/compartimentos/disponiveis-para-venda?tipo=REFRIGERADO")
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("compartimento esvaziado deixa de aparecer")
        void esvaziadoSome() throws Exception {
            long farinha = api.cadastrar("Farinha", "SECO", "100", 1);
            api.cadastrar("Trigo", "SECO", "50", 2);

            api.sair(farinha, "100");

            api.consultar("/compartimentos/disponiveis-para-venda?tipo=SECO")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].codigo", is("C2")));
        }

        @Test
        @DisplayName("saída parcial mantém o compartimento na lista")
        void saidaParcial() throws Exception {
            long farinha = api.cadastrar("Farinha", "SECO", "100", 1);
            api.sair(farinha, "99.999");

            api.consultar("/compartimentos/disponiveis-para-venda?tipo=SECO")
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].quantidadeAtual", closeTo(0.001, 0.00001)));
        }

        @Test
        @DisplayName("tipo ausente ou inválido: 400 PARAMETRO_INVALIDO")
        void tipoInvalido() throws Exception {
            assertProblema(api.consultar("/compartimentos/disponiveis-para-venda"), 400, "PARAMETRO_INVALIDO");
            assertProblema(api.consultar("/compartimentos/disponiveis-para-venda?tipo=xyz"), 400,
                    "PARAMETRO_INVALIDO");
        }
    }
}
