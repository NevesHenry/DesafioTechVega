package br.com.vega.armazem.web;

import br.com.vega.armazem.support.ClienteApi;
import br.com.vega.armazem.support.TesteIntegracaoBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static br.com.vega.armazem.support.ClienteApi.assertProblema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Casos de borda da API: erros do protocolo HTTP, paginação extrema, números fracionários e textos exóticos. */
@DisplayName("Bordas da API")
class BordasDaApiTest extends TesteIntegracaoBase {

    private ClienteApi api;

    @BeforeEach
    void iniciarCliente() {
        api = new ClienteApi(mockMvc);
    }

    private int totalIngredientes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ingrediente", Integer.class);
    }

    @Nested
    @DisplayName("Erros gerados pelo Spring trazem codigo e timestamp")
    class ErrosDoProtocolo {

        @Test
        @DisplayName("404 de rota inexistente: mensagem sem placeholder e codigo RECURSO_NAO_ENCONTRADO")
        void rotaInexistente() throws Exception {
            assertProblema(api.consultar("/naoexiste"), 404, "RECURSO_NAO_ENCONTRADO")
                    .andExpect(jsonPath("$.detail", is("Nenhum recurso encontrado em '/naoexiste'.")));
        }

        @Test
        @DisplayName("405 traz METODO_NAO_SUPORTADO")
        void metodoNaoSuportado() throws Exception {
            assertProblema(mockMvc.perform(delete("/ingredientes/1")), 405, "METODO_NAO_SUPORTADO");
        }

        @Test
        @DisplayName("406 traz FORMATO_NAO_ACEITO")
        void formatoNaoAceito() throws Exception {
            assertProblema(mockMvc.perform(get("/compartimentos").accept(MediaType.APPLICATION_XML)), 406,
                    "FORMATO_NAO_ACEITO");
        }

        @Test
        @DisplayName("415 traz TIPO_DE_CONTEUDO_NAO_SUPORTADO")
        void tipoDeConteudoNaoSuportado() throws Exception {
            assertProblema(mockMvc.perform(post("/ingredientes").contentType(MediaType.TEXT_PLAIN).content("x")),
                    415, "TIPO_DE_CONTEUDO_NAO_SUPORTADO");
        }
    }

    @Nested
    @DisplayName("Paginação do histórico")
    class Paginacao {

        @Test
        @DisplayName("page x size acima de Integer.MAX_VALUE responde 400 PARAMETRO_INVALIDO, não 500")
        void offsetEstourado() throws Exception {
            assertProblema(api.consultar("/historico?page=10000000&size=500"), 400, "PARAMETRO_INVALIDO")
                    .andExpect(jsonPath("$.detail", containsString("page")))
                    .andExpect(jsonPath("$.detail", containsString("size")));
        }

        @Test
        @DisplayName("page=2147483647&size=1 continua válido e devolve página vazia")
        void ultimoOffsetValido() throws Exception {
            api.consultar("/historico?page=2147483647&size=1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("Números do corpo")
    class Numeros {

        @Test
        @DisplayName("compartimentoId fracionário é rejeitado (400) e nada é gravado")
        void compartimentoIdFracionario() throws Exception {
            String json = """
                    {"nome":"Farinha","tipo":"SECO","quantidade":10,"compartimentoId":3.9,"responsavel":"ana"}""";

            assertProblema(api.postar("/ingredientes", json), 400, "REQUISICAO_INVALIDA")
                    .andExpect(jsonPath("$.detail", containsString("compartimentoId")));

            assertThat(totalIngredientes()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao", Integer.class)).isZero();
        }

        @Test
        @DisplayName("quantidade com decimais continua aceita")
        void quantidadeDecimal() throws Exception {
            api.cadastrar("Farinha", "SECO", "10.5", 3, "ana").andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Textos exóticos")
    class Textos {

        @Test
        @DisplayName("nomes que diferem só por espaço não separável (NBSP) são o mesmo ingrediente")
        void espacoNaoSeparavel() throws Exception {
            api.cadastrar("Sal Grosso", "SECO", "10", 1, "ana").andExpect(status().isCreated());
            api.cadastrar("Sal Grosso", "SECO", "5", 1, "ana").andExpect(status().isOk());

            assertThat(totalIngredientes()).isEqualTo(1);
        }

        @Test
        @DisplayName("nome de 120 sílabas coreanas cabe na coluna normalizada")
        void nomeCoreano() throws Exception {
            api.cadastrar("가".repeat(120), "SECO", "10", 1, "ana").andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Mensagens de validação")
    class Mensagens {

        @Test
        @DisplayName("quantidade com dígitos inteiros demais cita os 9 dígitos inteiros")
        void digitosInteiros() throws Exception {
            assertProblema(api.consultar("/compartimentos/disponiveis?quantidade=12345678901&tipo=SECO"), 400,
                    "VALIDACAO")
                    .andExpect(jsonPath("$.detail", containsString("9 dígitos inteiros")));
        }
    }
}
