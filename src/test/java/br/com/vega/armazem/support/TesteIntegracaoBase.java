package br.com.vega.armazem.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes de integração: contexto Spring completo, H2 com migrações Flyway e relógio controlável.
 * O banco é limpo antes de cada teste. Não usamos {@code @Transactional} nos testes porque ele esconderia o
 * comportamento real de commit e lock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RelogioTestConfig.class)
public abstract class TesteIntegracaoBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RelogioAjustavel relogio;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void prepararAmbiente() {
        jdbc.update("DELETE FROM movimentacao");
        jdbc.update("DELETE FROM ingrediente");
        jdbc.update("UPDATE compartimento SET tipo_atual = NULL, quantidade_atual = 0, "
                + "ultima_movimentacao_em = NULL, versao = 0");
        relogio.ajustar(RelogioTestConfig.INICIO);
    }
}
