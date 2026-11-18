package br.com.vega.armazem.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Atalhos para os testes de API: chamadas frequentes e verificação do formato de erro (problem+json). */
public final class ClienteApi {

    private final MockMvc mvc;

    public ClienteApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    public static String cadastroJson(String nome, String tipo, String quantidade, long compartimentoId,
                                      String responsavel) {
        return """
                {"nome":"%s","tipo":"%s","quantidade":%s,"compartimentoId":%d,"responsavel":"%s"}"""
                .formatted(nome, tipo, quantidade, compartimentoId, responsavel);
    }

    public static String movimentoJson(String quantidade, String responsavel) {
        return """
                {"quantidade":%s,"responsavel":"%s"}""".formatted(quantidade, responsavel);
    }

    public ResultActions postar(String uri, String json) throws Exception {
        return mvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions cadastrar(String nome, String tipo, String quantidade, long compartimentoId,
                                   String responsavel) throws Exception {
        return postar("/ingredientes", cadastroJson(nome, tipo, quantidade, compartimentoId, responsavel));
    }

    /** Cadastra esperando sucesso (200 ou 201) e devolve o id do ingrediente. */
    public long cadastrar(String nome, String tipo, String quantidade, long compartimentoId) throws Exception {
        MvcResult resultado = cadastrar(nome, tipo, quantidade, compartimentoId, "teste").andReturn();
        int status = resultado.getResponse().getStatus();
        if (status != 200 && status != 201) {
            throw new AssertionError("Cadastro de %s falhou com status %d: %s".formatted(nome, status,
                    resultado.getResponse().getContentAsString()));
        }
        return id(resultado);
    }

    public ResultActions entrada(long ingredienteId, String quantidade, String responsavel) throws Exception {
        return postar("/ingredientes/%d/entradas".formatted(ingredienteId), movimentoJson(quantidade, responsavel));
    }

    public ResultActions saida(long ingredienteId, String quantidade, String responsavel) throws Exception {
        return postar("/ingredientes/%d/saidas".formatted(ingredienteId), movimentoJson(quantidade, responsavel));
    }

    /** Entrada esperando sucesso. */
    public void entrar(long ingredienteId, String quantidade) throws Exception {
        entrada(ingredienteId, quantidade, "teste").andExpect(status().isOk());
    }

    /** Saída esperando sucesso. */
    public void sair(long ingredienteId, String quantidade) throws Exception {
        saida(ingredienteId, quantidade, "teste").andExpect(status().isOk());
    }

    public ResultActions consultar(String uri) throws Exception {
        return mvc.perform(get(uri));
    }

    public static long id(MvcResult resultado) throws Exception {
        Number id = JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    /** Lê uma lista de ids do corpo JSON, por exemplo {@code "$.content[*].id"} ou {@code "$[*].id"}. */
    public static List<Long> ids(ResultActions resposta, String caminho) throws Exception {
        List<Number> numeros = JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), caminho);
        return numeros.stream().map(Number::longValue).toList();
    }

    /**
     * Verifica o formato padrão de erro: status HTTP, {@code application/problem+json} e os campos
     * {@code status, title, detail, instance, codigo, timestamp}.
     */
    public static ResultActions assertProblema(ResultActions resposta, int status, String codigo) throws Exception {
        return resposta
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(status)))
                .andExpect(jsonPath("$.title", notNullValue()))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.instance", notNullValue()))
                .andExpect(jsonPath("$.codigo", is(codigo)))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$", hasKey("codigo")));
    }
}
