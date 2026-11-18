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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static br.com.vega.armazem.support.ClienteApi.assertProblema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API de ingredientes")
class IngredienteApiTest extends TesteIntegracaoBase {

    private static final String PAYLOAD_DO_DESAFIO = """
            {"nome":"Farinha de Trigo","tipo":"SECOS","quantidade":400,"compartimentoId":1,"responsavel":"pedro"}""";

    private ClienteApi api;

    @BeforeEach
    void iniciarCliente() {
        api = new ClienteApi(mockMvc);
    }

    private long contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Long.class);
    }

    @Nested
    @DisplayName("POST /ingredientes")
    class Cadastro {

        @Test
        @DisplayName("payload de exemplo do desafio: 201, Location e corpo")
        void payloadDoDesafio() throws Exception {
            ResultActions resposta = api.postar("/ingredientes", PAYLOAD_DO_DESAFIO);

            resposta.andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/ingredientes/" + idCriado())))
                    .andExpect(header().string("Location", containsString("/ingredientes/")))
                    .andExpect(jsonPath("$.id", is((int) idCriado())))
                    .andExpect(jsonPath("$.nome", is("Farinha de Trigo")))
                    .andExpect(jsonPath("$.tipo", is("SECO")))
                    .andExpect(jsonPath("$.unidade", is("kg")))
                    .andExpect(jsonPath("$.quantidade", closeTo(400.0, 0.0001)))
                    .andExpect(jsonPath("$.compartimentoId", is(1)))
                    .andExpect(jsonPath("$.compartimentoCodigo", is("C1")))
                    .andExpect(jsonPath("$.criadoEm", startsWith("2026-11-17T10:00:00")))
                    .andExpect(jsonPath("$.atualizadoEm", startsWith("2026-11-17T10:00:00")));
            assertThat(contar("movimentacao")).isEqualTo(1);
        }

        private long idCriado() {
            return jdbc.queryForObject("SELECT MAX(id) FROM ingrediente", Long.class);
        }

        @Test
        @DisplayName("repetir o payload de exemplo estoura a capacidade do C1, com mensagem clara")
        void repetirPayloadExcedeCapacidade() throws Exception {
            api.postar("/ingredientes", PAYLOAD_DO_DESAFIO).andExpect(status().isCreated());

            assertProblema(api.postar("/ingredientes", PAYLOAD_DO_DESAFIO), 400, "CAPACIDADE_EXCEDIDA")
                    .andExpect(jsonPath("$.title", is("Regra de negócio violada")))
                    .andExpect(jsonPath("$.instance", is("/ingredientes")))
                    .andExpect(jsonPath("$.detail", containsString("C1")))
                    .andExpect(jsonPath("$.detail", containsString("600 kg")))
                    .andExpect(jsonPath("$.detail", containsString("200 kg")))
                    .andExpect(jsonPath("$.detail", containsString("400 kg")));
            assertThat(contar("movimentacao")).isEqualTo(1);
            assertThat(contar("ingrediente")).isEqualTo(1);
        }

        @Test
        @DisplayName("mesmo nome (caixa, acentos e espaços ignorados) e tipo no mesmo compartimento soma e devolve 200")
        void mesclaIngredienteExistente() throws Exception {
            long id = api.cadastrar("Farinha de Trigo", "SECO", "100", 1);
            relogio.avancar(Duration.ofMinutes(30));

            api.cadastrar("  FARÍNHA   de  trigo ", "secos", "50.5", 1, "ana")
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.id", is((int) id)))
                    .andExpect(jsonPath("$.nome", is("Farinha de Trigo")))
                    .andExpect(jsonPath("$.quantidade", closeTo(150.5, 0.0001)))
                    .andExpect(jsonPath("$.criadoEm", startsWith("2026-11-17T10:00:00")))
                    .andExpect(jsonPath("$.atualizadoEm", startsWith("2026-11-17T10:30:00")));

            assertThat(contar("ingrediente")).isEqualTo(1);
            assertThat(contar("movimentacao")).isEqualTo(2);
        }

        @Test
        @DisplayName("mesmo nome em outro compartimento cria outro ingrediente (201)")
        void mesmoNomeEmOutroCompartimento() throws Exception {
            long primeiro = api.cadastrar("Farinha", "SECO", "100", 1);

            api.cadastrar("Farinha", "SECO", "100", 2, "ana")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is((int) primeiro + 1)))
                    .andExpect(jsonPath("$.compartimentoCodigo", is("C2")));
        }

        @Test
        @DisplayName("nomes diferentes no mesmo compartimento e tipo criam ingredientes distintos")
        void nomesDiferentes() throws Exception {
            api.cadastrar("Farinha", "SECO", "100", 1, "ana").andExpect(status().isCreated());
            api.cadastrar("Açúcar", "SECO", "100", 1, "ana").andExpect(status().isCreated());

            assertThat(contar("ingrediente")).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT quantidade_atual FROM compartimento WHERE id = 1",
                    java.math.BigDecimal.class)).isEqualByComparingTo("200");
        }

        @Test
        @DisplayName("preencher exatamente 600 kg é aceito")
        void preencheExatamenteACapacidade() throws Exception {
            api.cadastrar("Farinha", "SECO", "600", 1, "ana").andExpect(status().isCreated());
            api.cadastrar("Leite", "LIQUIDO", "500", 2, "ana").andExpect(status().isCreated());
            api.cadastrar("Manteiga", "REFRIGERADO", "400", 3, "ana").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("600,001 kg excede a capacidade")
        void umMilesimoAcima() throws Exception {
            assertProblema(api.cadastrar("Farinha", "SECO", "600.001", 1, "ana"), 400, "CAPACIDADE_EXCEDIDA");
            assertThat(contar("ingrediente")).isZero();
            assertThat(contar("movimentacao")).isZero();
        }

        @Test
        @DisplayName("tipo diferente em compartimento ocupado: TIPO_INCOMPATIVEL")
        void tipoIncompativel() throws Exception {
            api.cadastrar("Farinha", "SECO", "100", 1, "ana").andExpect(status().isCreated());

            assertProblema(api.cadastrar("Leite", "LIQUIDO", "10", 1, "ana"), 400, "TIPO_INCOMPATIVEL")
                    .andExpect(jsonPath("$.detail", containsString("C1")));
            assertProblema(api.cadastrar("Farinha", "LIQUIDO", "10", 1, "ana"), 400, "TIPO_INCOMPATIVEL");
            assertThat(contar("ingrediente")).isEqualTo(1);
            assertThat(contar("movimentacao")).isEqualTo(1);
        }

        @Test
        @DisplayName("compartimentoId inexistente: 400 COMPARTIMENTO_INEXISTENTE listando os válidos")
        void compartimentoInexistente() throws Exception {
            assertProblema(api.cadastrar("Farinha", "SECO", "10", 99, "ana"), 400, "COMPARTIMENTO_INEXISTENTE")
                    .andExpect(jsonPath("$.detail", containsString("99")))
                    .andExpect(jsonPath("$.detail", containsString("C1")))
                    .andExpect(jsonPath("$.detail", containsString("C5")));
            assertThat(contar("ingrediente")).isZero();
        }

        @ParameterizedTest(name = "tipo \"{0}\" é aceito e devolvido como {1}")
        @CsvSource({"SECOS, SECO", "seco, SECO", "Líquidos, LIQUIDO", "liquido, LIQUIDO", "refrigerados, REFRIGERADO"})
        void leituraTolerante(String tipo, String canonico) throws Exception {
            api.cadastrar("Item", tipo, "1", 1, "ana")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipo", is(canonico)));
        }

        @Test
        @DisplayName("tipo com espaços nas pontas (\" refrigerados \") é aceito")
        void tipoComEspacos() throws Exception {
            api.cadastrar("Item", " refrigerados ", "1", 1, "ana")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipo", is("REFRIGERADO")))
                    .andExpect(jsonPath("$.unidade", is("kg")));
        }

        @Test
        @DisplayName("tipo LIQUIDO devolve unidade L")
        void unidadeDeLiquido() throws Exception {
            api.cadastrar("Leite", "Líquidos", "1", 1, "ana")
                    .andExpect(jsonPath("$.unidade", is("L")));
        }

        @Test
        @DisplayName("responsável é gravado sem espaços extras")
        void responsavelNormalizado() throws Exception {
            api.cadastrar("Farinha", "SECO", "1", 1, "  maria   clara ").andExpect(status().isCreated());

            assertThat(jdbc.queryForObject("SELECT responsavel FROM movimentacao", String.class))
                    .isEqualTo("maria clara");
        }

        @Test
        @DisplayName("responsável só com espaços: 400 VALIDACAO")
        void responsavelEmBranco() throws Exception {
            assertProblema(api.cadastrar("Farinha", "SECO", "1", 1, "   "), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[0].campo", is("responsavel")));
        }
    }

    @Nested
    @DisplayName("POST /ingredientes: validação do corpo")
    class Validacao {

        @Test
        @DisplayName("corpo vazio: VALIDACAO com todos os campos obrigatórios, ordenados por campo")
        void camposObrigatorios() throws Exception {
            assertProblema(api.postar("/ingredientes", "{}"), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.title", is("Dados inválidos")))
                    .andExpect(jsonPath("$.erros", hasSize(5)))
                    .andExpect(jsonPath("$.erros[*].campo",
                            org.hamcrest.Matchers.contains("compartimentoId", "nome", "quantidade", "responsavel",
                                    "tipo")))
                    .andExpect(jsonPath("$.erros[0].mensagem", is("é obrigatório")))
                    .andExpect(jsonPath("$.detail", containsString("nome: é obrigatório")));
            assertThat(contar("movimentacao")).isZero();
        }

        @Test
        @DisplayName("quantidade negativa: VALIDACAO no campo quantidade")
        void quantidadeNegativa() throws Exception {
            assertProblema(api.cadastrar("Farinha", "SECO", "-5", 1, "ana"), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros", hasSize(1)))
                    .andExpect(jsonPath("$.erros[0].campo", is("quantidade")))
                    .andExpect(jsonPath("$.erros[0].mensagem", is("deve ser maior que zero")));
        }

        @Test
        @DisplayName("quantidade zero: VALIDACAO")
        void quantidadeZero() throws Exception {
            assertProblema(api.cadastrar("Farinha", "SECO", "0", 1, "ana"), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[0].campo", is("quantidade")));
        }

        @Test
        @DisplayName("mais de 3 casas decimais: VALIDACAO")
        void maisDeTresCasas() throws Exception {
            assertProblema(api.cadastrar("Farinha", "SECO", "1.0001", 1, "ana"), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[0].campo", is("quantidade")))
                    .andExpect(jsonPath("$.erros[0].mensagem", containsString("3 casas decimais")));
            assertThat(contar("movimentacao")).isZero();
        }

        @Test
        @DisplayName("três casas decimais são aceitas")
        void tresCasas() throws Exception {
            api.cadastrar("Farinha", "SECO", "1.001", 1, "ana")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.quantidade", closeTo(1.001, 0.00001)));
        }

        @Test
        @DisplayName("nome e responsável longos demais: VALIDACAO")
        void tamanhoMaximo() throws Exception {
            String nomeLongo = "a".repeat(121);
            String responsavelLongo = "b".repeat(101);

            assertProblema(api.cadastrar(nomeLongo, "SECO", "1", 1, responsavelLongo), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[*].campo",
                            org.hamcrest.Matchers.contains("nome", "responsavel")));
        }

        @Test
        @DisplayName("erros de vários campos vêm ordenados por campo")
        void errosOrdenados() throws Exception {
            assertProblema(api.postar("/ingredientes", """
                    {"nome":"","quantidade":-1,"compartimentoId":1,"responsavel":"x","tipo":"SECO"}"""),
                    400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[*].campo",
                            org.hamcrest.Matchers.contains("nome", "quantidade")));
        }

        @ParameterizedTest(name = "JSON malformado: {0}")
        @ValueSource(strings = {"{", "nao e json", "{\"nome\": }", "[1,2"})
        void jsonMalformado(String corpo) throws Exception {
            assertProblema(api.postar("/ingredientes", corpo), 400, "REQUISICAO_INVALIDA");
            assertThat(contar("movimentacao")).isZero();
        }

        @Test
        @DisplayName("corpo ausente: REQUISICAO_INVALIDA")
        void corpoAusente() throws Exception {
            assertProblema(mockMvc.perform(post("/ingredientes").contentType(MediaType.APPLICATION_JSON)),
                    400, "REQUISICAO_INVALIDA");
        }

        @Test
        @DisplayName("tipo inválido no corpo: REQUISICAO_INVALIDA citando o campo")
        void tipoInvalido() throws Exception {
            assertProblema(api.cadastrar("Farinha", "GELADO", "1", 1, "ana"), 400, "REQUISICAO_INVALIDA")
                    .andExpect(jsonPath("$.detail", containsString("tipo")))
                    .andExpect(jsonPath("$.detail", containsString("GELADO")));
        }

        @Test
        @DisplayName("quantidade não numérica: REQUISICAO_INVALIDA")
        void quantidadeNaoNumerica() throws Exception {
            assertProblema(api.postar("/ingredientes", """
                    {"nome":"Farinha","tipo":"SECO","quantidade":"muito","compartimentoId":1,"responsavel":"a"}"""),
                    400, "REQUISICAO_INVALIDA");
        }

        @Test
        @DisplayName("Content-Type não suportado: 415 com problem+json e timestamp")
        void contentTypeNaoSuportado() throws Exception {
            mockMvc.perform(post("/ingredientes").contentType(MediaType.TEXT_PLAIN).content("x"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status", is(415)));
        }
    }

    @Nested
    @DisplayName("GET /ingredientes")
    class Consulta {

        @Test
        @DisplayName("lista vazia")
        void listaVazia() throws Exception {
            api.consultar("/ingredientes").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("filtros tipo, compartimentoId e comEstoque")
        void filtros() throws Exception {
            long farinha = api.cadastrar("Farinha", "SECO", "100", 1);
            long acucar = api.cadastrar("Açúcar", "SECO", "30", 1);
            long leite = api.cadastrar("Leite", "LIQUIDO", "50", 2);
            long manteiga = api.cadastrar("Manteiga", "REFRIGERADO", "20", 3);
            api.sair(leite, "50");

            assertThat(ClienteApi.ids(api.consultar("/ingredientes"), "$[*].id"))
                    .as("ordenado por compartimento e nome").containsExactly(acucar, farinha, leite, manteiga);
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?tipo=SECO"), "$[*].id"))
                    .containsExactly(acucar, farinha);
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?tipo=secos"), "$[*].id"))
                    .containsExactly(acucar, farinha);
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?tipo=Líquidos"), "$[*].id"))
                    .containsExactly(leite);
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?compartimentoId=1"), "$[*].id"))
                    .containsExactly(acucar, farinha);
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?compartimentoId=4"), "$[*].id")).isEmpty();
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?comEstoque=true"), "$[*].id"))
                    .as("o leite foi todo retirado").containsExactly(acucar, farinha, manteiga);
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?comEstoque=false"), "$[*].id"))
                    .containsExactly(acucar, farinha, leite, manteiga);
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?tipo=LIQUIDO&comEstoque=true"), "$[*].id"))
                    .isEmpty();
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?tipo=SECO&compartimentoId=2"), "$[*].id"))
                    .isEmpty();
            assertThat(ClienteApi.ids(api.consultar("/ingredientes?tipo=SECO&compartimentoId=1&comEstoque=true"),
                    "$[*].id")).containsExactly(acucar, farinha);
        }

        @Test
        @DisplayName("itens da lista têm todos os campos")
        void camposDoItem() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);

            api.consultar("/ingredientes")
                    .andExpect(jsonPath("$[0].id", is((int) id)))
                    .andExpect(jsonPath("$[0].nome", is("Farinha")))
                    .andExpect(jsonPath("$[0].tipo", is("SECO")))
                    .andExpect(jsonPath("$[0].unidade", is("kg")))
                    .andExpect(jsonPath("$[0].quantidade", closeTo(100.0, 0.0001)))
                    .andExpect(jsonPath("$[0].compartimentoId", is(1)))
                    .andExpect(jsonPath("$[0].compartimentoCodigo", is("C1")))
                    .andExpect(jsonPath("$[0].criadoEm").exists())
                    .andExpect(jsonPath("$[0].atualizadoEm").exists());
        }

        @Test
        @DisplayName("tipo inválido na query: 400 PARAMETRO_INVALIDO")
        void tipoInvalido() throws Exception {
            assertProblema(api.consultar("/ingredientes?tipo=gelado"), 400, "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", containsString("gelado")));
        }

        @Test
        @DisplayName("compartimentoId e comEstoque inválidos: 400 PARAMETRO_INVALIDO")
        void parametrosMalFormados() throws Exception {
            assertProblema(api.consultar("/ingredientes?compartimentoId=abc"), 400, "PARAMETRO_INVALIDO");
            assertProblema(api.consultar("/ingredientes?comEstoque=talvez"), 400, "PARAMETRO_INVALIDO");
        }

        @Test
        @DisplayName("GET por id devolve o ingrediente")
        void buscarPorId() throws Exception {
            long id = api.cadastrar("Leite Integral", "LIQUIDO", "75.5", 2);

            api.consultar("/ingredientes/" + id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is((int) id)))
                    .andExpect(jsonPath("$.nome", is("Leite Integral")))
                    .andExpect(jsonPath("$.tipo", is("LIQUIDO")))
                    .andExpect(jsonPath("$.unidade", is("L")))
                    .andExpect(jsonPath("$.quantidade", closeTo(75.5, 0.0001)))
                    .andExpect(jsonPath("$.compartimentoCodigo", is("C2")));
        }

        @Test
        @DisplayName("id inexistente: 404 INGREDIENTE_NAO_ENCONTRADO")
        void naoEncontrado() throws Exception {
            assertProblema(api.consultar("/ingredientes/9999"), 404, "INGREDIENTE_NAO_ENCONTRADO")
                    .andExpect(jsonPath("$.instance", is("/ingredientes/9999")))
                    .andExpect(jsonPath("$.detail", containsString("9999")));
        }

        @Test
        @DisplayName("id não numérico: 400 PARAMETRO_INVALIDO")
        void idNaoNumerico() throws Exception {
            assertProblema(api.consultar("/ingredientes/abc"), 400, "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", containsString("abc")));
        }

        @Test
        @DisplayName("rota inexistente: 404 com problem+json")
        void rotaInexistente() throws Exception {
            mockMvc.perform(get("/nao-existe"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Nested
    @DisplayName("POST /ingredientes/{id}/entradas e /saidas")
    class Movimentos {

        @Test
        @DisplayName("entrada soma ao ingrediente e responde 200")
        void entradaFeliz() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);
            relogio.avancar(Duration.ofHours(1));

            api.entrada(id, "50.25", "  ana   paula ")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is((int) id)))
                    .andExpect(jsonPath("$.quantidade", closeTo(150.25, 0.0001)))
                    .andExpect(jsonPath("$.atualizadoEm", startsWith("2026-11-17T11:00:00")));

            assertThat(jdbc.queryForObject("SELECT quantidade_atual FROM compartimento WHERE id = 1",
                    java.math.BigDecimal.class)).isEqualByComparingTo("150.25");
            assertThat(jdbc.queryForObject(
                    "SELECT responsavel FROM movimentacao WHERE tipo_operacao = 'ENTRADA' ORDER BY id DESC LIMIT 1",
                    String.class)).isEqualTo("ana paula");
            assertThat(contar("movimentacao")).isEqualTo(2);
        }

        @Test
        @DisplayName("saída subtrai do ingrediente e responde 200")
        void saidaFeliz() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);

            api.saida(id, "40", "joao")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantidade", closeTo(60.0, 0.0001)));

            assertThat(jdbc.queryForObject("SELECT quantidade_atual FROM compartimento WHERE id = 1",
                    java.math.BigDecimal.class)).isEqualByComparingTo("60");
            assertThat(contar("movimentacao")).isEqualTo(2);
        }

        @Test
        @DisplayName("retirar exatamente todo o estoque é permitido")
        void saidaTotal() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);

            api.saida(id, "100", "joao")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantidade", closeTo(0.0, 0.0001)));
        }

        @Test
        @DisplayName("entrada e saída em ingrediente inexistente: 404 INGREDIENTE_NAO_ENCONTRADO")
        void ingredienteInexistente() throws Exception {
            assertProblema(api.entrada(404, "1", "ana"), 404, "INGREDIENTE_NAO_ENCONTRADO")
                    .andExpect(jsonPath("$.instance", is("/ingredientes/404/entradas")));
            assertProblema(api.saida(404, "1", "ana"), 404, "INGREDIENTE_NAO_ENCONTRADO")
                    .andExpect(jsonPath("$.instance", is("/ingredientes/404/saidas")));
            assertThat(contar("movimentacao")).isZero();
        }

        @Test
        @DisplayName("saída acima do estoque do ingrediente: ESTOQUE_INSUFICIENTE, sem alterar nada")
        void estoqueInsuficiente() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);

            assertProblema(api.saida(id, "100.001", "ana"), 400, "ESTOQUE_INSUFICIENTE")
                    .andExpect(jsonPath("$.detail", containsString("Farinha")))
                    .andExpect(jsonPath("$.detail", containsString("100 kg")));

            api.consultar("/ingredientes/" + id).andExpect(jsonPath("$.quantidade", closeTo(100.0, 0.0001)));
            assertThat(contar("movimentacao")).isEqualTo(1);
        }

        @Test
        @DisplayName("o limite da saída é o estoque do ingrediente, mesmo que o compartimento tenha mais")
        void limitePorIngrediente() throws Exception {
            long a = api.cadastrar("Farinha", "SECO", "100", 1);
            api.cadastrar("Trigo", "SECO", "50", 1);

            assertProblema(api.saida(a, "120", "ana"), 400, "ESTOQUE_INSUFICIENTE");
            assertThat(jdbc.queryForObject("SELECT quantidade_atual FROM compartimento WHERE id = 1",
                    java.math.BigDecimal.class)).isEqualByComparingTo("150");
        }

        @Test
        @DisplayName("entrada acima da capacidade: CAPACIDADE_EXCEDIDA; exatamente até a capacidade é aceita")
        void capacidadeNaEntrada() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "500", 1);

            assertProblema(api.entrada(id, "100.001", "ana"), 400, "CAPACIDADE_EXCEDIDA");
            assertThat(contar("movimentacao")).isEqualTo(1);

            api.entrada(id, "100", "ana")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantidade", closeTo(600.0, 0.0001)));
            assertThat(contar("movimentacao")).isEqualTo(2);
        }

        @Test
        @DisplayName("entrada em compartimento esvaziado hoje do mesmo tipo continua permitida")
        void entradaAposEsvaziar() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);
            api.sair(id, "100");

            api.entrada(id, "30", "ana").andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantidade", closeTo(30.0, 0.0001)));
        }

        @Test
        @DisplayName("quantidade zero, negativa ou com mais de 3 casas: 400 VALIDACAO")
        void quantidadeInvalida() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);

            for (String quantidade : List.of("0", "-1", "1.0001")) {
                assertProblema(api.entrada(id, quantidade, "ana"), 400, "VALIDACAO")
                        .andExpect(jsonPath("$.erros[0].campo", is("quantidade")));
                assertProblema(api.saida(id, quantidade, "ana"), 400, "VALIDACAO")
                        .andExpect(jsonPath("$.erros[0].campo", is("quantidade")));
            }
            assertThat(contar("movimentacao")).isEqualTo(1);
        }

        @Test
        @DisplayName("corpo vazio: VALIDACAO com quantidade e responsável")
        void corpoVazio() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);

            assertProblema(api.postar("/ingredientes/%d/saidas".formatted(id), "{}"), 400, "VALIDACAO")
                    .andExpect(jsonPath("$.erros[*].campo",
                            org.hamcrest.Matchers.contains("quantidade", "responsavel")));
        }

        @Test
        @DisplayName("JSON malformado em saída: REQUISICAO_INVALIDA")
        void jsonMalformado() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);

            assertProblema(api.postar("/ingredientes/%d/entradas".formatted(id), "{"), 400,
                    "REQUISICAO_INVALIDA");
        }

        @Test
        @DisplayName("id não numérico no caminho: 400 PARAMETRO_INVALIDO")
        void idInvalido() throws Exception {
            assertProblema(api.postar("/ingredientes/abc/saidas", "{\"quantidade\":1,\"responsavel\":\"a\"}"),
                    400, "PARAMETRO_INVALIDO");
        }
    }

    @Nested
    @DisplayName("GET /ingredientes/volume")
    class Volume {

        @Test
        @DisplayName("sem estoque: 3 itens ordenados por tipo, todos com zero")
        void zeros() throws Exception {
            api.consultar("/ingredientes/volume")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].type", is("LIQUIDO")))
                    .andExpect(jsonPath("$[0].totalQuantity", closeTo(0.0, 0.0001)))
                    .andExpect(jsonPath("$[1].type", is("REFRIGERADO")))
                    .andExpect(jsonPath("$[1].totalQuantity", closeTo(0.0, 0.0001)))
                    .andExpect(jsonPath("$[2].type", is("SECO")))
                    .andExpect(jsonPath("$[2].totalQuantity", closeTo(0.0, 0.0001)));
        }

        @Test
        @DisplayName("soma por tipo considerando vários compartimentos, entradas e saídas")
        void somasPorTipo() throws Exception {
            long farinha = api.cadastrar("Farinha", "SECO", "400", 1);
            api.cadastrar("Açúcar", "SECO", "100.5", 2);
            api.cadastrar("Leite", "LIQUIDO", "250", 3);
            api.sair(farinha, "50");

            api.consultar("/ingredientes/volume")
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].type", is("LIQUIDO")))
                    .andExpect(jsonPath("$[0].totalQuantity", closeTo(250.0, 0.0001)))
                    .andExpect(jsonPath("$[1].type", is("REFRIGERADO")))
                    .andExpect(jsonPath("$[1].totalQuantity", closeTo(0.0, 0.0001)))
                    .andExpect(jsonPath("$[2].type", is("SECO")))
                    .andExpect(jsonPath("$[2].totalQuantity", closeTo(450.5, 0.0001)));
        }

        @Test
        @DisplayName("compartimento esvaziado não conta no volume do tipo")
        void esvaziadoConta0() throws Exception {
            long id = api.cadastrar("Leite", "LIQUIDO", "100", 1);
            api.sair(id, "100");

            api.consultar("/ingredientes/volume")
                    .andExpect(jsonPath("$[0].type", is("LIQUIDO")))
                    .andExpect(jsonPath("$[0].totalQuantity", closeTo(0.0, 0.0001)));
        }

        @Test
        @DisplayName("os itens só têm os campos type e totalQuantity")
        void formato() throws Exception {
            api.consultar("/ingredientes/volume")
                    .andExpect(jsonPath("$[0].length()", is(2)))
                    .andExpect(jsonPath("$[0].type").exists())
                    .andExpect(jsonPath("$[0].totalQuantity").exists());
        }
    }

    @Nested
    @DisplayName("Regra de troca de tipo no mesmo dia (ponta a ponta)")
    class TrocaDeTipo {

        @ParameterizedTest(name = "{0} -> {1}: bloqueado hoje, liberado amanhã")
        @CsvSource({
                "SECO, LIQUIDO", "SECO, REFRIGERADO",
                "LIQUIDO, SECO", "LIQUIDO, REFRIGERADO",
                "REFRIGERADO, SECO", "REFRIGERADO, LIQUIDO"})
        void trocaBloqueadaHojeELiberadaAmanha(String de, String para) throws Exception {
            long id = api.cadastrar("Item A", de, "100", 1);
            api.sair(id, "100");

            assertProblema(api.cadastrar("Item B", para, "10", 1, "ana"), 400, "TROCA_DE_TIPO_BLOQUEADA")
                    .andExpect(jsonPath("$.detail", containsString("C1")))
                    .andExpect(jsonPath("$.detail", containsString("17/11/2026")))
                    .andExpect(jsonPath("$.detail", containsString("18/11/2026")));
            assertThat(contar("ingrediente")).as("nenhum ingrediente criado na falha").isEqualTo(1);
            assertThat(contar("movimentacao")).isEqualTo(2);

            relogio.avancar(Duration.ofDays(1));

            api.cadastrar("Item B", para, "10", 1, "ana")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipo", is(para)));
        }

        @Test
        @DisplayName("fronteira da meia-noite: 23:59 ainda bloqueia, 00:01 do dia seguinte libera")
        void fronteiraDaMeiaNoite() throws Exception {
            relogio.ajustar(LocalDateTime.of(2026, 11, 17, 23, 58));
            long id = api.cadastrar("Farinha", "SECO", "100", 1);
            relogio.ajustar(LocalDateTime.of(2026, 11, 17, 23, 59));
            api.sair(id, "100");

            assertProblema(api.cadastrar("Leite", "LIQUIDO", "10", 1, "ana"), 400, "TROCA_DE_TIPO_BLOQUEADA");

            relogio.ajustar(LocalDateTime.of(2026, 11, 18, 0, 1));

            api.cadastrar("Leite", "LIQUIDO", "10", 1, "ana")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipo", is("LIQUIDO")))
                    .andExpect(jsonPath("$.criadoEm", startsWith("2026-11-18T00:01:00")));
        }

        @Test
        @DisplayName("a saída do dia seguinte conta como movimentação: bloqueia de novo até depois dela")
        void saidaDoDiaSeguinteReiniciaOBloqueio() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);
            relogio.avancar(Duration.ofDays(1));
            api.sair(id, "100");

            assertProblema(api.cadastrar("Leite", "LIQUIDO", "10", 1, "ana"), 400, "TROCA_DE_TIPO_BLOQUEADA");

            relogio.avancar(Duration.ofDays(1));
            api.cadastrar("Leite", "LIQUIDO", "10", 1, "ana").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("compartimento nunca usado aceita qualquer tipo no mesmo dia em que outro foi usado")
        void compartimentoNuncaUsado() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);
            api.sair(id, "100");

            api.cadastrar("Leite", "LIQUIDO", "10", 2, "ana").andExpect(status().isCreated());
            api.cadastrar("Manteiga", "REFRIGERADO", "10", 3, "ana").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("mesmo tipo volta no mesmo dia após esvaziar")
        void mesmoTipoNoMesmoDia() throws Exception {
            long id = api.cadastrar("Farinha", "SECO", "100", 1);
            api.sair(id, "100");

            api.cadastrar("Outra Farinha", "SECO", "10", 1, "ana").andExpect(status().isCreated());
        }
    }
}
