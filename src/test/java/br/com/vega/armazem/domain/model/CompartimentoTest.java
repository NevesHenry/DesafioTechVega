package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Compartimento: regras de armazenamento")
class CompartimentoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 17);
    private static final LocalDate AMANHA = HOJE.plusDays(1);

    private static BigDecimal q(String valor) {
        return new BigDecimal(valor);
    }

    private static Compartimento novo() {
        return new Compartimento(1L, "C1");
    }

    private static void assertCodigo(Runnable acao, CodigoErro esperado) {
        assertThatThrownBy(acao::run)
                .isInstanceOfSatisfying(RegraNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(esperado));
    }

    @Nested
    @DisplayName("Capacidade")
    class Capacidade {

        @ParameterizedTest(name = "{0}: preencher exatamente a capacidade é permitido")
        @EnumSource(TipoIngrediente.class)
        void aceitaPreencherExatamenteACapacidade(TipoIngrediente tipo) {
            Compartimento c = novo();

            c.receber(tipo, tipo.getCapacidadeMaxima(), HOJE);

            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo(tipo.getCapacidadeMaxima());
            assertThat(c.getEspacoLivre()).isEqualByComparingTo("0");
        }

        @ParameterizedTest(name = "{0}: capacidade + 0,001 excede")
        @EnumSource(TipoIngrediente.class)
        void rejeitaUmMilesimoAcimaDaCapacidade(TipoIngrediente tipo) {
            Compartimento c = novo();
            BigDecimal acima = tipo.getCapacidadeMaxima().add(q("0.001"));

            assertCodigo(() -> c.receber(tipo, acima, HOJE), CodigoErro.CAPACIDADE_EXCEDIDA);

            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("0");
            assertThat(c.getTipoAtual()).as("falha não define o tipo").isNull();
            assertThat(c.getUltimaMovimentacaoEm()).isNull();
        }

        @ParameterizedTest(name = "{0}: soma de entradas respeita o limite")
        @EnumSource(TipoIngrediente.class)
        void somaDeEntradasNaoPodePassarDaCapacidade(TipoIngrediente tipo) {
            Compartimento c = novo();
            BigDecimal metade = tipo.getCapacidadeMaxima().divide(q("2"));
            c.receber(tipo, metade, HOJE);
            c.receber(tipo, metade, HOJE);

            assertCodigo(() -> c.receber(tipo, q("0.001"), HOJE), CodigoErro.CAPACIDADE_EXCEDIDA);
            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo(tipo.getCapacidadeMaxima());
        }

        @Test
        @DisplayName("600 kg de SECO são aceitos e 600,001 kg não")
        void fronteiraDoSeco() {
            Compartimento ok = novo();
            ok.receber(TipoIngrediente.SECO, q("600"), HOJE);
            assertThat(ok.getQuantidadeAtual()).isEqualByComparingTo("600");

            assertCodigo(() -> novo().receber(TipoIngrediente.SECO, q("600.001"), HOJE),
                    CodigoErro.CAPACIDADE_EXCEDIDA);
        }

        @Test
        @DisplayName("mensagem de capacidade excedida cita compartimento, capacidade e espaço livre")
        void mensagemClara() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("400"), HOJE);

            assertThatThrownBy(() -> c.receber(TipoIngrediente.SECO, q("400"), HOJE))
                    .hasMessageContaining("C1")
                    .hasMessageContaining("600 kg")
                    .hasMessageContaining("200 kg");
        }
    }

    @Nested
    @DisplayName("Mistura e troca de tipo")
    class Tipos {

        @Test
        @DisplayName("tipo diferente em compartimento não vazio: TIPO_INCOMPATIVEL")
        void tipoIncompativel() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("10"), HOJE);

            assertCodigo(() -> c.receber(TipoIngrediente.LIQUIDO, q("10"), HOJE), CodigoErro.TIPO_INCOMPATIVEL);
            assertCodigo(() -> c.receber(TipoIngrediente.LIQUIDO, q("10"), AMANHA), CodigoErro.TIPO_INCOMPATIVEL);
            assertThat(c.getTipoAtual()).isEqualTo(TipoIngrediente.SECO);
            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("10");
        }

        @ParameterizedTest(name = "{0} -> {1} no mesmo dia após esvaziar é bloqueado")
        @CsvSource({
                "SECO, LIQUIDO", "SECO, REFRIGERADO",
                "LIQUIDO, SECO", "LIQUIDO, REFRIGERADO",
                "REFRIGERADO, SECO", "REFRIGERADO, LIQUIDO"})
        void trocaNoMesmoDiaBloqueada(TipoIngrediente de, TipoIngrediente para) {
            Compartimento c = novo();
            c.receber(de, q("100"), HOJE);
            c.retirar(q("100"), HOJE);

            assertThat(c.estaVazio()).isTrue();
            assertCodigo(() -> c.receber(para, q("1"), HOJE), CodigoErro.TROCA_DE_TIPO_BLOQUEADA);
            assertThat(c.getTipoAtual()).isEqualTo(de);
            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("0");
        }

        @ParameterizedTest(name = "{0} -> {1} no dia seguinte é permitido")
        @CsvSource({
                "SECO, LIQUIDO", "LIQUIDO, SECO", "REFRIGERADO, SECO", "SECO, REFRIGERADO"})
        void trocaNoDiaSeguintePermitida(TipoIngrediente de, TipoIngrediente para) {
            Compartimento c = novo();
            c.receber(de, q("100"), HOJE);
            c.retirar(q("100"), HOJE);

            c.receber(para, q("50"), AMANHA);

            assertThat(c.getTipoAtual()).isEqualTo(para);
            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("50");
            assertThat(c.getUltimaMovimentacaoEm()).isEqualTo(AMANHA);
        }

        @Test
        @DisplayName("a retirada também conta como movimentação do dia")
        void retiradaContaComoMovimentacao() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("100"), HOJE);
            c.retirar(q("100"), AMANHA);

            assertCodigo(() -> c.receber(TipoIngrediente.LIQUIDO, q("1"), AMANHA),
                    CodigoErro.TROCA_DE_TIPO_BLOQUEADA);
            c.receber(TipoIngrediente.LIQUIDO, q("1"), AMANHA.plusDays(1));
            assertThat(c.getTipoAtual()).isEqualTo(TipoIngrediente.LIQUIDO);
        }

        @Test
        @DisplayName("mesmo tipo pode voltar a ser armazenado no mesmo dia após esvaziar")
        void mesmoTipoNoMesmoDia() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.LIQUIDO, q("100"), HOJE);
            c.retirar(q("100"), HOJE);

            c.receber(TipoIngrediente.LIQUIDO, q("30"), HOJE);

            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("30");
        }

        @ParameterizedTest(name = "compartimento nunca usado aceita {0}")
        @EnumSource(TipoIngrediente.class)
        void nuncaUsadoAceitaQualquerTipo(TipoIngrediente tipo) {
            Compartimento c = novo();

            assertThat(c.podeReceber(tipo, q("1"), HOJE)).isTrue();
            c.receber(tipo, q("1"), HOJE);
            assertThat(c.getTipoAtual()).isEqualTo(tipo);
        }

        @Test
        @DisplayName("troca bloqueada tem mensagem com a data de liberação")
        void mensagemDaTrocaBloqueada() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("5"), HOJE);
            c.retirar(q("5"), HOJE);

            assertThatThrownBy(() -> c.receber(TipoIngrediente.LIQUIDO, q("1"), HOJE))
                    .hasMessageContaining("C1")
                    .hasMessageContaining("17/11/2026")
                    .hasMessageContaining("18/11/2026");
        }
    }

    @Nested
    @DisplayName("Consultas")
    class Consultas {

        @Test
        @DisplayName("podeReceber reflete as regras sem alterar o estado")
        void podeReceber() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("500"), HOJE);

            assertThat(c.podeReceber(TipoIngrediente.SECO, q("100"), HOJE)).isTrue();
            assertThat(c.podeReceber(TipoIngrediente.SECO, q("100.001"), HOJE)).isFalse();
            assertThat(c.podeReceber(TipoIngrediente.LIQUIDO, q("1"), HOJE)).isFalse();
            assertThat(c.podeReceber(TipoIngrediente.SECO, q("0"), HOJE)).isFalse();
            assertThat(c.podeReceber(TipoIngrediente.SECO, q("-1"), HOJE)).isFalse();
            assertThat(c.podeReceber(TipoIngrediente.SECO, null, HOJE)).isFalse();
            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("podeReceber de compartimento esvaziado hoje: só o mesmo tipo")
        void podeReceberAposEsvaziar() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.REFRIGERADO, q("40"), HOJE);
            c.retirar(q("40"), HOJE);

            assertThat(c.podeReceber(TipoIngrediente.REFRIGERADO, q("400"), HOJE)).isTrue();
            assertThat(c.podeReceber(TipoIngrediente.SECO, q("1"), HOJE)).isFalse();
            assertThat(c.podeReceber(TipoIngrediente.SECO, q("1"), AMANHA)).isTrue();
        }

        @Test
        @DisplayName("aceitaQualquerTipo: vazio e sem uso hoje")
        void aceitaQualquerTipo() {
            Compartimento c = novo();
            assertThat(c.aceitaQualquerTipo(HOJE)).as("nunca usado").isTrue();

            c.receber(TipoIngrediente.SECO, q("10"), HOJE);
            assertThat(c.aceitaQualquerTipo(HOJE)).as("com estoque").isFalse();
            assertThat(c.aceitaQualquerTipo(AMANHA)).as("com estoque, mesmo amanhã").isFalse();

            c.retirar(q("10"), HOJE);
            assertThat(c.aceitaQualquerTipo(HOJE)).as("esvaziado hoje").isFalse();
            assertThat(c.aceitaQualquerTipo(AMANHA)).as("esvaziado ontem").isTrue();
        }

        @Test
        @DisplayName("espacoLivrePara considera tipo, estoque e bloqueio do dia")
        void espacoLivrePara() {
            Compartimento c = novo();
            assertThat(c.espacoLivrePara(TipoIngrediente.LIQUIDO, HOJE)).isEqualByComparingTo("500");

            c.receber(TipoIngrediente.SECO, q("250"), HOJE);
            assertThat(c.espacoLivrePara(TipoIngrediente.SECO, HOJE)).isEqualByComparingTo("350");
            assertThat(c.espacoLivrePara(TipoIngrediente.LIQUIDO, HOJE)).isEqualByComparingTo("0");
            assertThat(c.espacoLivrePara(TipoIngrediente.LIQUIDO, AMANHA)).isEqualByComparingTo("0");

            c.retirar(q("250"), HOJE);
            assertThat(c.espacoLivrePara(TipoIngrediente.SECO, HOJE)).isEqualByComparingTo("600");
            assertThat(c.espacoLivrePara(TipoIngrediente.LIQUIDO, HOJE)).isEqualByComparingTo("0");
            assertThat(c.espacoLivrePara(TipoIngrediente.LIQUIDO, AMANHA)).isEqualByComparingTo("500");
            assertThat(c.espacoLivrePara(TipoIngrediente.REFRIGERADO, AMANHA)).isEqualByComparingTo("400");
        }

        @Test
        @DisplayName("getEspacoLivre e getCapacidadeMaxima são null se nunca usado")
        void espacoLivreNuncaUsado() {
            Compartimento c = novo();

            assertThat(c.getEspacoLivre()).isNull();
            assertThat(c.getCapacidadeMaxima()).isNull();
            assertThat(c.getTipoAtual()).isNull();
            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("0");
            assertThat(c.estaVazio()).isTrue();
        }

        @Test
        @DisplayName("getEspacoLivre usa a capacidade do tipo atual, mesmo vazio")
        void espacoLivreComTipo() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.LIQUIDO, q("125.5"), HOJE);
            assertThat(c.getEspacoLivre()).isEqualByComparingTo("374.5");
            assertThat(c.getCapacidadeMaxima()).isEqualByComparingTo("500");

            c.retirar(q("125.5"), HOJE);
            assertThat(c.getEspacoLivre()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("getPercentualOcupacao: 0,00 sem tipo ou vazio, proporcional à capacidade do tipo")
        void percentualOcupacao() {
            Compartimento c = novo();
            assertThat(c.getPercentualOcupacao()).isEqualTo(new BigDecimal("0.00"));

            c.receber(TipoIngrediente.SECO, q("300"), HOJE);
            assertThat(c.getPercentualOcupacao()).isEqualTo(new BigDecimal("50.00"));

            c.receber(TipoIngrediente.SECO, q("300"), HOJE);
            assertThat(c.getPercentualOcupacao()).isEqualTo(new BigDecimal("100.00"));

            c.retirar(q("600"), HOJE);
            assertThat(c.getPercentualOcupacao()).isEqualTo(new BigDecimal("0.00"));

            c.receber(TipoIngrediente.REFRIGERADO, q("100"), AMANHA);
            assertThat(c.getPercentualOcupacao()).isEqualTo(new BigDecimal("25.00"));
        }

        @Test
        @DisplayName("percentual arredonda em 2 casas")
        void percentualArredondado() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("100"), HOJE);

            assertThat(c.getPercentualOcupacao()).isEqualTo(new BigDecimal("16.67"));
        }
    }

    @Nested
    @DisplayName("Retirada")
    class Retirada {

        @Test
        @DisplayName("retirar acima do estoque: ESTOQUE_INSUFICIENTE sem alterar o saldo")
        void retiradaAcimaDoEstoque() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("50"), HOJE);

            assertCodigo(() -> c.retirar(q("50.001"), AMANHA), CodigoErro.ESTOQUE_INSUFICIENTE);

            assertThat(c.getQuantidadeAtual()).isEqualByComparingTo("50");
            assertThat(c.getUltimaMovimentacaoEm()).as("falha não marca movimentação").isEqualTo(HOJE);
        }

        @Test
        @DisplayName("retirar exatamente o saldo esvazia o compartimento e mantém o tipo")
        void retiradaExata() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.LIQUIDO, q("50"), HOJE);

            c.retirar(q("50"), HOJE);

            assertThat(c.estaVazio()).isTrue();
            assertThat(c.getTipoAtual()).isEqualTo(TipoIngrediente.LIQUIDO);
        }

        @Test
        @DisplayName("mensagem de estoque insuficiente cita compartimento e quantidades")
        void mensagem() {
            Compartimento c = novo();
            c.receber(TipoIngrediente.SECO, q("50"), HOJE);

            assertThatThrownBy(() -> c.retirar(q("80"), HOJE))
                    .hasMessageContaining("C1")
                    .hasMessageContaining("50 kg")
                    .hasMessageContaining("80 kg");
        }
    }

    @Test
    @DisplayName("quantidade nula, zero ou negativa em receber: QUANTIDADE_INVALIDA")
    void quantidadeInvalida() {
        Compartimento c = novo();

        assertCodigo(() -> c.receber(TipoIngrediente.SECO, null, HOJE), CodigoErro.QUANTIDADE_INVALIDA);
        assertCodigo(() -> c.receber(TipoIngrediente.SECO, q("0"), HOJE), CodigoErro.QUANTIDADE_INVALIDA);
        assertCodigo(() -> c.receber(TipoIngrediente.SECO, q("-1"), HOJE), CodigoErro.QUANTIDADE_INVALIDA);
    }
}
