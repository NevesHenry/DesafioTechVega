package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Movimentacao: entradas e saídas")
class MovimentacaoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 17, 10, 30, 15);

    private Compartimento compartimento;
    private Ingrediente farinha;

    private static BigDecimal q(String valor) {
        return new BigDecimal(valor);
    }

    private static void assertCodigo(Runnable acao, CodigoErro esperado) {
        assertThatThrownBy(acao::run)
                .isInstanceOfSatisfying(RegraNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(esperado));
    }

    @BeforeEach
    void preparar() {
        compartimento = new Compartimento(1L, "C1");
        farinha = Ingrediente.novo("  Farinha   de Trigo ", TipoIngrediente.SECO, compartimento, AGORA);
    }

    @Nested
    @DisplayName("Entrada")
    class Entrada {

        @Test
        @DisplayName("atualiza ingrediente e compartimento e devolve o registro de histórico")
        void entradaCompleta() {
            LocalDateTime depois = AGORA.plusMinutes(5);

            Movimentacao m = Movimentacao.entrada(farinha, q("400"), "  pedro   silva ", depois);

            assertThat(farinha.getQuantidade()).isEqualByComparingTo("400");
            assertThat(farinha.getAtualizadoEm()).isEqualTo(depois);
            assertThat(farinha.getCriadoEm()).isEqualTo(AGORA);
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("400");
            assertThat(compartimento.getTipoAtual()).isEqualTo(TipoIngrediente.SECO);
            assertThat(compartimento.getUltimaMovimentacaoEm()).isEqualTo(depois.toLocalDate());

            assertThat(m.getTipoOperacao()).isEqualTo(TipoOperacao.ENTRADA);
            assertThat(m.getQuantidade()).isEqualTo(new BigDecimal("400.000"));
            assertThat(m.getQuantidade().scale()).isEqualTo(3);
            assertThat(m.getTipoIngrediente()).isEqualTo(TipoIngrediente.SECO);
            assertThat(m.getResponsavel()).isEqualTo("pedro silva");
            assertThat(m.getNomeIngrediente()).isEqualTo("Farinha de Trigo");
            assertThat(m.getCompartimentoCodigo()).isEqualTo("C1");
            assertThat(m.getCompartimento()).isSameAs(compartimento);
            assertThat(m.getIngrediente()).isSameAs(farinha);
            assertThat(m.getDataHora()).isEqualTo(depois);
            assertThat(m.getSaldoCompartimento()).isEqualByComparingTo("400");
        }

        @Test
        @DisplayName("saldoCompartimento é o saldo após a operação, incluindo outros ingredientes")
        void saldoAposAEntrada() {
            Ingrediente trigo = Ingrediente.novo("Trigo", TipoIngrediente.SECO, compartimento, AGORA);
            Movimentacao primeira = Movimentacao.entrada(farinha, q("100"), "ana", AGORA);
            Movimentacao segunda = Movimentacao.entrada(trigo, q("50.5"), "ana", AGORA);
            Movimentacao terceira = Movimentacao.entrada(farinha, q("0.25"), "ana", AGORA);

            assertThat(primeira.getSaldoCompartimento()).isEqualByComparingTo("100");
            assertThat(segunda.getSaldoCompartimento()).isEqualByComparingTo("150.5");
            assertThat(terceira.getSaldoCompartimento()).isEqualByComparingTo("150.75");
            assertThat(farinha.getQuantidade()).isEqualByComparingTo("100.25");
            assertThat(trigo.getQuantidade()).isEqualByComparingTo("50.5");
        }

        @Test
        @DisplayName("quantidade com zeros à direita além da 3ª casa é aceita e normalizada para escala 3")
        void zerosADireita() {
            Movimentacao m = Movimentacao.entrada(farinha, q("1.5000"), "ana", AGORA);

            assertThat(m.getQuantidade()).isEqualTo(new BigDecimal("1.500"));
        }

        @Test
        @DisplayName("capacidade excedida não altera ingrediente nem compartimento")
        void capacidadeExcedidaNaoMuda() {
            Movimentacao.entrada(farinha, q("500"), "ana", AGORA);
            LocalDateTime depois = AGORA.plusHours(1);

            assertCodigo(() -> Movimentacao.entrada(farinha, q("100.001"), "ana", depois),
                    CodigoErro.CAPACIDADE_EXCEDIDA);

            assertThat(farinha.getQuantidade()).isEqualByComparingTo("500");
            assertThat(farinha.getAtualizadoEm()).isEqualTo(AGORA);
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("tipo incompatível não altera nada")
        void tipoIncompativelNaoMuda() {
            Movimentacao.entrada(farinha, q("10"), "ana", AGORA);
            Ingrediente leite = Ingrediente.novo("Leite", TipoIngrediente.LIQUIDO, compartimento, AGORA);

            assertCodigo(() -> Movimentacao.entrada(leite, q("10"), "ana", AGORA), CodigoErro.TIPO_INCOMPATIVEL);

            assertThat(leite.getQuantidade()).isEqualByComparingTo("0");
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("10");
            assertThat(compartimento.getTipoAtual()).isEqualTo(TipoIngrediente.SECO);
        }

        @Test
        @DisplayName("troca de tipo bloqueada não altera nada")
        void trocaBloqueadaNaoMuda() {
            Movimentacao.entrada(farinha, q("10"), "ana", AGORA);
            Movimentacao.saida(farinha, q("10"), "ana", AGORA);
            Ingrediente leite = Ingrediente.novo("Leite", TipoIngrediente.LIQUIDO, compartimento, AGORA);

            assertCodigo(() -> Movimentacao.entrada(leite, q("10"), "ana", AGORA.plusHours(2)),
                    CodigoErro.TROCA_DE_TIPO_BLOQUEADA);

            assertThat(leite.getQuantidade()).isEqualByComparingTo("0");
            assertThat(compartimento.getTipoAtual()).isEqualTo(TipoIngrediente.SECO);
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Saída")
    class Saida {

        @BeforeEach
        void estoqueInicial() {
            Movimentacao.entrada(farinha, q("300"), "ana", AGORA);
        }

        @Test
        @DisplayName("atualiza ingrediente e compartimento e devolve o registro de histórico")
        void saidaCompleta() {
            LocalDateTime depois = AGORA.plusMinutes(10);

            Movimentacao m = Movimentacao.saida(farinha, q("120.5"), " joao ", depois);

            assertThat(farinha.getQuantidade()).isEqualByComparingTo("179.5");
            assertThat(farinha.getAtualizadoEm()).isEqualTo(depois);
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("179.5");
            assertThat(m.getTipoOperacao()).isEqualTo(TipoOperacao.SAIDA);
            assertThat(m.getQuantidade()).isEqualTo(new BigDecimal("120.500"));
            assertThat(m.getTipoIngrediente()).isEqualTo(TipoIngrediente.SECO);
            assertThat(m.getResponsavel()).isEqualTo("joao");
            assertThat(m.getNomeIngrediente()).isEqualTo("Farinha de Trigo");
            assertThat(m.getCompartimentoCodigo()).isEqualTo("C1");
            assertThat(m.getDataHora()).isEqualTo(depois);
            assertThat(m.getSaldoCompartimento()).isEqualByComparingTo("179.5");
        }

        @Test
        @DisplayName("retirar exatamente o estoque do ingrediente zera ingrediente e compartimento")
        void saidaTotal() {
            Movimentacao m = Movimentacao.saida(farinha, q("300"), "ana", AGORA);

            assertThat(farinha.getQuantidade()).isEqualByComparingTo("0");
            assertThat(compartimento.estaVazio()).isTrue();
            assertThat(m.getSaldoCompartimento()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("estoque insuficiente não altera ingrediente nem compartimento")
        void estoqueInsuficienteNaoMuda() {
            LocalDateTime depois = AGORA.plusHours(3);

            assertCodigo(() -> Movimentacao.saida(farinha, q("300.001"), "ana", depois),
                    CodigoErro.ESTOQUE_INSUFICIENTE);

            assertThat(farinha.getQuantidade()).isEqualByComparingTo("300");
            assertThat(farinha.getAtualizadoEm()).isEqualTo(AGORA);
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("300");
            assertThat(compartimento.getUltimaMovimentacaoEm()).isEqualTo(AGORA.toLocalDate());
        }

        @Test
        @DisplayName("limite é o estoque do ingrediente, não o saldo do compartimento")
        void limiteEPorIngrediente() {
            Ingrediente trigo = Ingrediente.novo("Trigo", TipoIngrediente.SECO, compartimento, AGORA);
            Movimentacao.entrada(trigo, q("50"), "ana", AGORA);

            assertCodigo(() -> Movimentacao.saida(trigo, q("60"), "ana", AGORA), CodigoErro.ESTOQUE_INSUFICIENTE);

            assertThat(trigo.getQuantidade()).isEqualByComparingTo("50");
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("350");
        }
    }

    @Nested
    @DisplayName("Validações de entrada de dados")
    class Validacoes {

        @ParameterizedTest(name = "quantidade \"{0}\" é inválida")
        @ValueSource(strings = {"0", "0.000", "-1", "-0.001", "1.0001", "0.0005", "10.12345"})
        void quantidadeInvalida(String valor) {
            assertCodigo(() -> Movimentacao.entrada(farinha, q(valor), "ana", AGORA),
                    CodigoErro.QUANTIDADE_INVALIDA);
            assertCodigo(() -> Movimentacao.saida(farinha, q(valor), "ana", AGORA),
                    CodigoErro.QUANTIDADE_INVALIDA);

            assertThat(farinha.getQuantidade()).isEqualByComparingTo("0");
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("quantidade nula é inválida")
        void quantidadeNula() {
            assertCodigo(() -> Movimentacao.entrada(farinha, null, "ana", AGORA), CodigoErro.QUANTIDADE_INVALIDA);
            assertCodigo(() -> Movimentacao.saida(farinha, null, "ana", AGORA), CodigoErro.QUANTIDADE_INVALIDA);
        }

        @ParameterizedTest(name = "responsável \"{0}\" é rejeitado")
        @ValueSource(strings = {"", " ", "   \t "})
        @NullSource
        void responsavelEmBranco(String responsavel) {
            assertCodigo(() -> Movimentacao.entrada(farinha, q("1"), responsavel, AGORA), CodigoErro.VALIDACAO);
            assertThat(farinha.getQuantidade()).isEqualByComparingTo("0");
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("0");

            Movimentacao.entrada(farinha, q("5"), "ana", AGORA);
            assertCodigo(() -> Movimentacao.saida(farinha, q("1"), responsavel, AGORA), CodigoErro.VALIDACAO);
            assertThat(farinha.getQuantidade()).isEqualByComparingTo("5");
            assertThat(compartimento.getQuantidadeAtual()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("Ingrediente.novo com nome em branco: VALIDACAO")
        void nomeEmBranco() {
            assertCodigo(() -> Ingrediente.novo("  ", TipoIngrediente.SECO, compartimento, AGORA),
                    CodigoErro.VALIDACAO);
            assertCodigo(() -> Ingrediente.novo(null, TipoIngrediente.SECO, compartimento, AGORA),
                    CodigoErro.VALIDACAO);
        }
    }
}
