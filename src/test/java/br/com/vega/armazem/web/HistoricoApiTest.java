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

import java.time.LocalDateTime;
import java.util.List;

import static br.com.vega.armazem.support.ClienteApi.assertProblema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API de histórico")
class HistoricoApiTest extends TesteIntegracaoBase {

    private ClienteApi api;
    private long farinha;
    private long leite;

    /** Ids das movimentações A..E, na ordem em que foram criadas. */
    private List<Long> mov;

    @BeforeEach
    void iniciarCliente() {
        api = new ClienteApi(mockMvc);
    }

    private static LocalDateTime hora(int hora, int minuto) {
        return LocalDateTime.of(2026, 11, 17, hora, minuto);
    }

    /**
     * Cria cinco movimentações com instantes que se repetem de propósito:
     * A 10:00 ENTRADA Farinha 100 (C2); B 10:05 ENTRADA Leite 200 (C1); C 10:10 SAIDA Farinha 40 (C2);
     * D 10:10 ENTRADA Leite 50 (C1); E 10:10 ENTRADA Leite 10 (C1).
     */
    private void cenario() throws Exception {
        relogio.ajustar(hora(10, 0));
        api.cadastrar("Farinha", "SECO", "100", 2, "  pedro   silva ").andExpect(status().isCreated());
        farinha = jdbc.queryForObject("SELECT MAX(id) FROM ingrediente", Long.class);
        relogio.ajustar(hora(10, 5));
        api.cadastrar("Leite", "LIQUIDO", "200", 1, "ana").andExpect(status().isCreated());
        leite = jdbc.queryForObject("SELECT MAX(id) FROM ingrediente", Long.class);
        relogio.ajustar(hora(10, 10));
        api.saida(farinha, "40", "joao").andExpect(status().isOk());
        api.entrada(leite, "50", "ana").andExpect(status().isOk());
        api.entrada(leite, "10", "ana").andExpect(status().isOk());
        mov = jdbc.queryForList("SELECT id FROM movimentacao ORDER BY id", Long.class);
        assertThat(mov).hasSize(5);
    }

    private List<Long> ordem(String query) throws Exception {
        return ClienteApi.ids(api.consultar("/historico" + query).andExpect(status().isOk()), "$.content[*].id");
    }

    private List<Long> ids(int... posicoes) {
        return java.util.Arrays.stream(posicoes).mapToObj(mov::get).toList();
    }

    @Nested
    @DisplayName("Campos registrados")
    class Campos {

        @Test
        @DisplayName("ENTRADA do cadastro: todos os campos, com dataHora vinda do relógio")
        void entradaDoCadastro() throws Exception {
            cenario();

            api.consultar("/historico?sortBy=date&order=asc")
                    .andExpect(jsonPath("$.content[0].id", is(mov.get(0).intValue())))
                    .andExpect(jsonPath("$.content[0].dataHora", is("2026-11-17T10:00:00")))
                    .andExpect(jsonPath("$.content[0].tipoOperacao", is("ENTRADA")))
                    .andExpect(jsonPath("$.content[0].quantidade", closeTo(100.0, 0.0001)))
                    .andExpect(jsonPath("$.content[0].tipoIngrediente", is("SECO")))
                    .andExpect(jsonPath("$.content[0].unidade", is("kg")))
                    .andExpect(jsonPath("$.content[0].responsavel", is("pedro silva")))
                    .andExpect(jsonPath("$.content[0].ingredienteId", is((int) farinha)))
                    .andExpect(jsonPath("$.content[0].nomeIngrediente", is("Farinha")))
                    .andExpect(jsonPath("$.content[0].compartimentoId", is(2)))
                    .andExpect(jsonPath("$.content[0].compartimentoCodigo", is("C2")))
                    .andExpect(jsonPath("$.content[0].saldoCompartimento", closeTo(100.0, 0.0001)))
                    .andExpect(jsonPath("$.content[0].length()", is(12)));
        }

        @Test
        @DisplayName("SAIDA: tipo, responsável, quantidade e saldo após a operação")
        void saida() throws Exception {
            cenario();

            api.consultar("/historico?tipoOperacao=SAIDA")
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id", is(mov.get(2).intValue())))
                    .andExpect(jsonPath("$.content[0].dataHora", is("2026-11-17T10:10:00")))
                    .andExpect(jsonPath("$.content[0].tipoOperacao", is("SAIDA")))
                    .andExpect(jsonPath("$.content[0].quantidade", closeTo(40.0, 0.0001)))
                    .andExpect(jsonPath("$.content[0].tipoIngrediente", is("SECO")))
                    .andExpect(jsonPath("$.content[0].responsavel", is("joao")))
                    .andExpect(jsonPath("$.content[0].compartimentoCodigo", is("C2")))
                    .andExpect(jsonPath("$.content[0].saldoCompartimento", closeTo(60.0, 0.0001)));
        }

        @Test
        @DisplayName("saldoCompartimento acompanha cada operação do compartimento")
        void saldos() throws Exception {
            cenario();

            api.consultar("/historico?compartimentoId=1&sortBy=date&order=asc")
                    .andExpect(jsonPath("$.content[*].saldoCompartimento",
                            org.hamcrest.Matchers.contains(closeTo(200.0, 0.0001), closeTo(250.0, 0.0001),
                                    closeTo(260.0, 0.0001))));
        }

        @Test
        @DisplayName("o nome registrado é um retrato do momento da operação")
        void nomeNoMomento() throws Exception {
            api.cadastrar("Farinha de Trigo", "SECO", "10", 1, "ana");
            api.cadastrar("farínha  DE trigo", "SECO", "5", 1, "ana");

            api.consultar("/historico?sortBy=date&order=asc")
                    .andExpect(jsonPath("$.content[*].nomeIngrediente",
                            org.hamcrest.Matchers.contains("Farinha de Trigo", "Farinha de Trigo")));
        }
    }

    @Nested
    @DisplayName("Ordenação")
    class Ordenacao {

        @BeforeEach
        void preparar() throws Exception {
            cenario();
        }

        @Test
        @DisplayName("padrão: data decrescente (desempate por compartimento e id decrescentes)")
        void padrao() throws Exception {
            // 10:10 em C2 (C), depois C1 pelos ids E, D; 10:05 B; 10:00 A
            assertThat(ordem("")).containsExactlyElementsOf(ids(2, 4, 3, 1, 0));
            assertThat(ordem("?sortBy=date&order=desc")).containsExactlyElementsOf(ids(2, 4, 3, 1, 0));
        }

        @Test
        @DisplayName("sortBy=date&order=asc")
        void dataCrescente() throws Exception {
            assertThat(ordem("?sortBy=date&order=asc")).containsExactlyElementsOf(ids(0, 1, 3, 4, 2));
        }

        @Test
        @DisplayName("sortBy=compartimento&order=asc: por compartimento, desempate por data crescente")
        void compartimentoCrescente() throws Exception {
            // C1: B 10:05, D, E (10:10, id); C2: A 10:00, C 10:10
            assertThat(ordem("?sortBy=compartimento&order=asc")).containsExactlyElementsOf(ids(1, 3, 4, 0, 2));
        }

        @Test
        @DisplayName("sortBy=compartimento&order=desc: por compartimento, desempate por data decrescente")
        void compartimentoDecrescente() throws Exception {
            // C2: C 10:10, A 10:00; C1: E, D (10:10, id desc), B 10:05
            assertThat(ordem("?sortBy=compartimento&order=desc")).containsExactlyElementsOf(ids(2, 0, 4, 3, 1));
        }

        @Test
        @DisplayName("sortBy e order aceitam variações de caixa e apelidos")
        void apelidos() throws Exception {
            assertThat(ordem("?sortBy=DATA&order=ASC")).containsExactlyElementsOf(ids(0, 1, 3, 4, 2));
            assertThat(ordem("?sortBy=Compartimento&order=Asc")).containsExactlyElementsOf(ids(1, 3, 4, 0, 2));
        }

        @Test
        @DisplayName("somente order informado mantém sortBy=date")
        void apenasOrder() throws Exception {
            assertThat(ordem("?order=asc")).containsExactlyElementsOf(ids(0, 1, 3, 4, 2));
        }

        @Test
        @DisplayName("somente sortBy informado mantém order=desc")
        void apenasSortBy() throws Exception {
            assertThat(ordem("?sortBy=compartimento")).containsExactlyElementsOf(ids(2, 0, 4, 3, 1));
        }

        @Test
        @DisplayName("ordenação vale também com filtros")
        void comFiltros() throws Exception {
            assertThat(ordem("?compartimentoId=1&sortBy=date&order=desc")).containsExactlyElementsOf(ids(4, 3, 1));
            assertThat(ordem("?tipoOperacao=ENTRADA&sortBy=compartimento&order=asc"))
                    .containsExactlyElementsOf(ids(1, 3, 4, 0));
        }
    }

    @Nested
    @DisplayName("Paginação e filtros")
    class PaginacaoEFiltros {

        @BeforeEach
        void preparar() throws Exception {
            cenario();
        }

        @Test
        @DisplayName("formato padrão: content e page, size=50, number=0")
        void formatoPadrao() throws Exception {
            api.consultar("/historico")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(5)))
                    .andExpect(jsonPath("$.page.size", is(50)))
                    .andExpect(jsonPath("$.page.number", is(0)))
                    .andExpect(jsonPath("$.page.totalElements", is(5)))
                    .andExpect(jsonPath("$.page.totalPages", is(1)))
                    .andExpect(jsonPath("$.length()", is(2)));
        }

        @Test
        @DisplayName("paginação: page, size, totalElements e totalPages; páginas são fatias consecutivas")
        void paginacao() throws Exception {
            api.consultar("/historico?sortBy=date&order=asc&page=0&size=2")
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.page.size", is(2)))
                    .andExpect(jsonPath("$.page.number", is(0)))
                    .andExpect(jsonPath("$.page.totalElements", is(5)))
                    .andExpect(jsonPath("$.page.totalPages", is(3)));
            assertThat(ordem("?sortBy=date&order=asc&page=0&size=2")).containsExactlyElementsOf(ids(0, 1));
            assertThat(ordem("?sortBy=date&order=asc&page=1&size=2")).containsExactlyElementsOf(ids(3, 4));
            assertThat(ordem("?sortBy=date&order=asc&page=2&size=2")).containsExactlyElementsOf(ids(2));
            api.consultar("/historico?sortBy=date&order=asc&page=2&size=2")
                    .andExpect(jsonPath("$.page.number", is(2)))
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("página além do fim: content vazio, totais preservados")
        void paginaAlemDoFim() throws Exception {
            api.consultar("/historico?page=10&size=2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.number", is(10)))
                    .andExpect(jsonPath("$.page.totalElements", is(5)))
                    .andExpect(jsonPath("$.page.totalPages", is(3)));
        }

        @ParameterizedTest(name = "tipoOperacao={0} devolve {1} registros")
        @CsvSource({"ENTRADA, 4", "entrada, 4", "SAIDA, 1", "saida, 1", "SAÍDA, 1", "Saída, 1"})
        void filtroPorTipoDeOperacao(String tipoOperacao, int esperado) throws Exception {
            mockMvc.perform(get("/historico").param("tipoOperacao", tipoOperacao))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(esperado)))
                    .andExpect(jsonPath("$.page.totalElements", is(esperado)));
        }

        @Test
        @DisplayName("filtro por compartimentoId e combinação com tipoOperacao")
        void filtroPorCompartimento() throws Exception {
            api.consultar("/historico?compartimentoId=2")
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[*].compartimentoCodigo",
                            org.hamcrest.Matchers.everyItem(is("C2"))));
            api.consultar("/historico?compartimentoId=1&tipoOperacao=SAIDA")
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.totalElements", is(0)))
                    .andExpect(jsonPath("$.page.totalPages", is(0)));
            api.consultar("/historico?compartimentoId=2&tipoOperacao=saída")
                    .andExpect(jsonPath("$.content", hasSize(1)));
            api.consultar("/historico?compartimentoId=5")
                    .andExpect(jsonPath("$.page.totalElements", is(0)));
        }

        @Test
        @DisplayName("filtros são aplicados antes da paginação")
        void filtroComPaginacao() throws Exception {
            api.consultar("/historico?tipoOperacao=ENTRADA&size=3&page=1")
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.page.totalElements", is(4)))
                    .andExpect(jsonPath("$.page.totalPages", is(2)));
        }
    }

    @Nested
    @DisplayName("Parâmetros inválidos")
    class Invalidos {

        @ParameterizedTest(name = "sortBy={0}")
        @ValueSource(strings = {"nome", "id", "xyz"})
        void sortByInvalido(String sortBy) throws Exception {
            assertProblema(api.consultar("/historico?sortBy=" + sortBy), 400, "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("sortBy")));
        }

        @ParameterizedTest(name = "order={0}")
        @ValueSource(strings = {"up", "ascending", "x"})
        void orderInvalido(String order) throws Exception {
            assertProblema(api.consultar("/historico?order=" + order), 400, "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("order")));
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(strings = {"0", "501", "-1"})
        void tamanhoForaDosLimites(String size) throws Exception {
            assertProblema(api.consultar("/historico?size=" + size), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[0].campo", is("size")));
        }

        @Test
        @DisplayName("size=500 e size=1 são aceitos")
        void tamanhosNosLimites() throws Exception {
            api.consultar("/historico?size=500").andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.size", is(500)));
            api.consultar("/historico?size=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.size", is(1)));
        }

        @Test
        @DisplayName("page negativa: 400 VALIDACAO")
        void paginaNegativa() throws Exception {
            assertProblema(api.consultar("/historico?page=-1"), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[0].campo", is("page")));
        }

        @Test
        @DisplayName("valores não numéricos ou enum inválido: 400 PARAMETRO_INVALIDO")
        void tiposIncorretos() throws Exception {
            assertProblema(api.consultar("/historico?page=abc"), 400, "PARAMETRO_INVALIDO");
            assertProblema(api.consultar("/historico?size=abc"), 400, "PARAMETRO_INVALIDO");
            assertProblema(api.consultar("/historico?compartimentoId=abc"), 400, "PARAMETRO_INVALIDO");
            assertProblema(api.consultar("/historico?tipoOperacao=xyz"), 400, "PARAMETRO_INVALIDO");
        }
    }

    @Test
    @DisplayName("histórico vazio")
    void vazio() throws Exception {
        api.consultar("/historico")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements", is(0)))
                .andExpect(jsonPath("$.page.totalPages", is(0)));
    }

    @Test
    @DisplayName("operações que falham não geram registro no histórico")
    void falhaNaoGeraHistorico() throws Exception {
        long id = api.cadastrar("Farinha", "SECO", "500", 1);
        api.sair(id, "100");
        assertThat(totalNoHistorico()).isEqualTo(2);

        assertProblema(api.cadastrar("Farinha", "SECO", "600", 1, "ana"), 400, "CAPACIDADE_EXCEDIDA");
        assertProblema(api.entrada(id, "700", "ana"), 400, "CAPACIDADE_EXCEDIDA");
        assertProblema(api.saida(id, "401", "ana"), 400, "ESTOQUE_INSUFICIENTE");
        assertProblema(api.cadastrar("Leite", "LIQUIDO", "10", 1, "ana"), 400, "TIPO_INCOMPATIVEL");
        assertProblema(api.cadastrar("Leite", "LIQUIDO", "10", 99, "ana"), 400, "COMPARTIMENTO_INEXISTENTE");
        assertProblema(api.saida(9999, "1", "ana"), 404, "INGREDIENTE_NAO_ENCONTRADO");
        assertProblema(api.entrada(id, "-1", "ana"), 400, "VALIDACAO");

        assertThat(totalNoHistorico()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("cada operação bem-sucedida gera exatamente um registro")
    void umRegistroPorOperacao() throws Exception {
        long id = api.cadastrar("Farinha", "SECO", "100", 1);
        assertThat(totalNoHistorico()).isEqualTo(1);
        api.entrar(id, "10");
        assertThat(totalNoHistorico()).isEqualTo(2);
        api.sair(id, "10");
        assertThat(totalNoHistorico()).isEqualTo(3);
        api.cadastrar("Farinha", "SECO", "5", 1, "ana").andExpect(status().isOk());
        assertThat(totalNoHistorico()).as("mesclagem também gera um registro").isEqualTo(4);
    }

    private int totalNoHistorico() throws Exception {
        ResultActions resposta = api.consultar("/historico?size=1").andExpect(status().isOk());
        Number total = com.jayway.jsonpath.JsonPath.read(resposta.andReturn().getResponse().getContentAsString(),
                "$.page.totalElements");
        return total.intValue();
    }
}
