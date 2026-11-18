package br.com.vega.armazem.repository;

import br.com.vega.armazem.domain.model.Compartimento;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.dto.CadastroIngredienteRequest;
import br.com.vega.armazem.dto.CompartimentoResponse;
import br.com.vega.armazem.dto.MovimentacaoRequest;
import br.com.vega.armazem.service.CompartimentoService;
import br.com.vega.armazem.service.EstoqueService;
import br.com.vega.armazem.support.TesteIntegracaoBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que a consulta JPQL de disponibilidade ({@code findDisponiveisParaArmazenamento}) e a regra do domínio
 * ({@link Compartimento#podeReceber}) nunca divergem, para vários estados do armazém, tipos e quantidades.
 */
@DisplayName("Consistência entre a consulta de disponibilidade e a regra do domínio")
class DisponibilidadeConsistenciaTest extends TesteIntegracaoBase {

    private static final List<String> QUANTIDADES = List.of("0.001", "1", "100", "250", "400", "500", "600");

    private static final LocalDateTime DIA_ANTERIOR = LocalDateTime.of(2026, 11, 16, 15, 0);

    enum Cenario {
        VAZIO,
        TIPOS_MISTOS_E_PREENCHIMENTOS_PARCIAIS,
        QUASE_CHEIOS_E_CHEIOS,
        ESVAZIADOS_HOJE,
        ESVAZIADOS_ONTEM_E_REUSADOS_HOJE,
        ESVAZIADO_PARCIALMENTE_HOJE,
        MISTURA_GERAL
    }

    @Autowired
    private EstoqueService estoque;

    @Autowired
    private CompartimentoService compartimentoService;

    @Autowired
    private CompartimentoRepository compartimentos;

    private long entrar(String nome, TipoIngrediente tipo, String quantidade, long compartimentoId) {
        return estoque.cadastrar(new CadastroIngredienteRequest(nome, tipo, new BigDecimal(quantidade),
                compartimentoId, "teste")).ingrediente().id();
    }

    private void sair(long ingredienteId, String quantidade) {
        estoque.registrarSaida(ingredienteId, new MovimentacaoRequest(new BigDecimal(quantidade), "teste"));
    }

    private void montar(Cenario cenario) {
        switch (cenario) {
            case VAZIO -> {
            }
            case TIPOS_MISTOS_E_PREENCHIMENTOS_PARCIAIS -> {
                entrar("Farinha", TipoIngrediente.SECO, "100", 1);
                entrar("Leite", TipoIngrediente.LIQUIDO, "250", 2);
                entrar("Manteiga", TipoIngrediente.REFRIGERADO, "399.999", 3);
                entrar("Trigo", TipoIngrediente.SECO, "300", 4);
            }
            case QUASE_CHEIOS_E_CHEIOS -> {
                entrar("Farinha", TipoIngrediente.SECO, "599.999", 1);
                entrar("Leite", TipoIngrediente.LIQUIDO, "500", 2);
                entrar("Manteiga", TipoIngrediente.REFRIGERADO, "400", 3);
                entrar("Trigo", TipoIngrediente.SECO, "600", 4);
                entrar("Suco", TipoIngrediente.LIQUIDO, "499.999", 5);
            }
            case ESVAZIADOS_HOJE -> {
                long farinha = entrar("Farinha", TipoIngrediente.SECO, "100", 1);
                sair(farinha, "100");
                long leite = entrar("Leite", TipoIngrediente.LIQUIDO, "300", 2);
                sair(leite, "300");
                long manteiga = entrar("Manteiga", TipoIngrediente.REFRIGERADO, "400", 3);
                sair(manteiga, "400");
            }
            case ESVAZIADOS_ONTEM_E_REUSADOS_HOJE -> {
                relogio.ajustar(DIA_ANTERIOR);
                long farinha = entrar("Farinha", TipoIngrediente.SECO, "100", 1);
                sair(farinha, "100");
                long leite = entrar("Leite", TipoIngrediente.LIQUIDO, "300", 2);
                sair(leite, "300");
                entrar("Manteiga", TipoIngrediente.REFRIGERADO, "50", 3);
                relogio.ajustar(DIA_ANTERIOR.plusDays(1));
                entrar("Suco", TipoIngrediente.LIQUIDO, "200", 1);
                entrar("Arroz", TipoIngrediente.SECO, "10", 2);
            }
            case ESVAZIADO_PARCIALMENTE_HOJE -> {
                long farinha = entrar("Farinha", TipoIngrediente.SECO, "100", 1);
                sair(farinha, "99.999");
                long leite = entrar("Leite", TipoIngrediente.LIQUIDO, "100", 2);
                sair(leite, "40");
            }
            case MISTURA_GERAL -> {
                relogio.ajustar(DIA_ANTERIOR);
                long farinha = entrar("Farinha", TipoIngrediente.SECO, "250", 1);
                sair(farinha, "250");
                long leite = entrar("Leite", TipoIngrediente.LIQUIDO, "500", 2);
                sair(leite, "100");
                relogio.ajustar(DIA_ANTERIOR.plusDays(1));
                entrar("Manteiga", TipoIngrediente.REFRIGERADO, "123", 1);
                long suco = entrar("Suco", TipoIngrediente.LIQUIDO, "50", 3);
                sair(suco, "50");
                entrar("Trigo", TipoIngrediente.SECO, "600", 4);
            }
        }
    }

    private void conferirTodasAsCombinacoes(String momento) {
        LocalDate hoje = LocalDate.now(relogio);
        List<Compartimento> todos = compartimentos.findAll();
        for (TipoIngrediente tipo : TipoIngrediente.values()) {
            for (String texto : QUANTIDADES) {
                BigDecimal quantidade = new BigDecimal(texto);
                List<Long> esperados = todos.stream()
                        .filter(c -> c.podeReceber(tipo, quantidade, hoje))
                        .sorted(Comparator.comparing(Compartimento::getQuantidadeAtual).reversed()
                                .thenComparing(Compartimento::getId))
                        .map(Compartimento::getId)
                        .toList();

                List<CompartimentoResponse> obtidos = compartimentoService
                        .disponiveisParaArmazenamento(tipo, quantidade);

                assertThat(obtidos.stream().map(CompartimentoResponse::id).toList())
                        .as("%s: ids disponíveis para %s %s (estado: %s)", momento, texto, tipo, todos)
                        .containsExactlyElementsOf(esperados);
                assertThat(obtidos).as("%s: %s %s", momento, texto, tipo).allSatisfy(c -> {
                    assertThat(c.capacidadeMaxima()).isEqualByComparingTo(tipo.getCapacidadeMaxima());
                    assertThat(c.espacoLivre()).as("espaço livre de %s", c.codigo())
                            .isGreaterThanOrEqualTo(quantidade);
                });
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Cenario.class)
    @DisplayName("a consulta e Compartimento.podeReceber concordam (hoje, amanhã e daqui a uma semana)")
    void consultaEDominioConcordam(Cenario cenario) {
        montar(cenario);

        conferirTodasAsCombinacoes("hoje");
        relogio.avancar(Duration.ofDays(1));
        conferirTodasAsCombinacoes("um dia depois");
        relogio.avancar(Duration.ofDays(6));
        conferirTodasAsCombinacoes("uma semana depois");
    }

    @Test
    @DisplayName("sanidade: o cenário de esvaziados hoje muda de resultado quando o dia vira")
    void esvaziadosHojeLiberamAmanha() {
        montar(Cenario.ESVAZIADOS_HOJE);

        List<Long> hoje = compartimentoService.disponiveisParaArmazenamento(TipoIngrediente.SECO,
                new BigDecimal("10")).stream().map(CompartimentoResponse::id).toList();
        relogio.avancar(Duration.ofDays(1));
        List<Long> amanha = compartimentoService.disponiveisParaArmazenamento(TipoIngrediente.SECO,
                new BigDecimal("10")).stream().map(CompartimentoResponse::id).toList();

        assertThat(hoje).as("C2 e C3 foram esvaziados hoje com outros tipos").containsExactly(1L, 4L, 5L);
        assertThat(amanha).containsExactly(1L, 2L, 3L, 4L, 5L);
    }
}
