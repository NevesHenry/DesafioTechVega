package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Tipos (ingrediente e operação) e normalização de nomes")
class TipoIngredienteTest {

    @Nested
    @DisplayName("TipoIngrediente")
    class Ingredientes {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
                "SECO|SECO", "SECOS|SECO", "seco|SECO", "secos|SECO", "Seco|SECO", "' secos '|SECO",
                "LIQUIDO|LIQUIDO", "LIQUIDOS|LIQUIDO", "Líquidos|LIQUIDO", "líquido|LIQUIDO", "LÍQUIDO|LIQUIDO",
                "liquidos|LIQUIDO",
                "REFRIGERADO|REFRIGERADO", "REFRIGERADOS|REFRIGERADO", "' refrigerados '|REFRIGERADO",
                "Refrigerado|REFRIGERADO"})
        void leituraTolerante(String entrada, TipoIngrediente esperado) {
            assertThat(TipoIngrediente.of(entrada)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "\"{0}\" é inválido")
        @ValueSource(strings = {"", " ", "xyz", "SECOSS", "seco s", "sec", "gelado", "SECO,LIQUIDO", "1"})
        @NullSource
        void valoresInvalidos(String entrada) {
            assertThatThrownBy(() -> TipoIngrediente.of(entrada))
                    .isInstanceOfSatisfying(ParametroInvalidoException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
                    .hasMessageContaining("SECO")
                    .hasMessageContaining("LIQUIDO")
                    .hasMessageContaining("REFRIGERADO");
        }

        @Test
        @DisplayName("mensagem de inválido cita o valor recebido")
        void mensagemCitaValor() {
            assertThatThrownBy(() -> TipoIngrediente.of("gelado")).hasMessageContaining("'gelado'");
        }

        @ParameterizedTest(name = "{0}: capacidade {1}, unidade {2}")
        @CsvSource({"SECO, 600, kg", "LIQUIDO, 500, L", "REFRIGERADO, 400, kg"})
        void capacidadesEUnidades(TipoIngrediente tipo, String capacidade, String unidade) {
            assertThat(tipo.getCapacidadeMaxima()).isEqualByComparingTo(capacidade);
            assertThat(tipo.getUnidade()).isEqualTo(unidade);
        }

        @Test
        @DisplayName("descrições legíveis e exatamente 3 tipos")
        void descricoes() {
            assertThat(TipoIngrediente.values()).containsExactly(TipoIngrediente.SECO, TipoIngrediente.LIQUIDO,
                    TipoIngrediente.REFRIGERADO);
            assertThat(TipoIngrediente.SECO.getDescricao()).isEqualTo("Secos");
            assertThat(TipoIngrediente.LIQUIDO.getDescricao()).isEqualTo("Líquidos");
            assertThat(TipoIngrediente.REFRIGERADO.getDescricao()).isEqualTo("Refrigerados");
        }
    }

    @Nested
    @DisplayName("TipoOperacao")
    class Operacoes {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
                "ENTRADA|ENTRADA", "entrada|ENTRADA", "' Entrada '|ENTRADA",
                "SAIDA|SAIDA", "saida|SAIDA", "SAÍDA|SAIDA", "Saída|SAIDA", "' saída '|SAIDA"})
        void leituraTolerante(String entrada, TipoOperacao esperado) {
            assertThat(TipoOperacao.of(entrada)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "\"{0}\" é inválido")
        @ValueSource(strings = {"", " ", "xyz", "SAIDAS", "ENTRADAS", "saída s"})
        @NullSource
        void valoresInvalidos(String entrada) {
            assertThatThrownBy(() -> TipoOperacao.of(entrada))
                    .isInstanceOfSatisfying(ParametroInvalidoException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
                    .hasMessageContaining("ENTRADA")
                    .hasMessageContaining("SAIDA");
        }
    }

    @Nested
    @DisplayName("Normalização de nomes")
    class Nomes {

        @Test
        @DisplayName("Ingrediente.normalizarNome remove acentos, espaços extras e caixa")
        void normalizarNome() {
            assertThat(Ingrediente.normalizarNome("  Açúcar   Refinado ")).isEqualTo("acucar refinado");
        }

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', value = {
                "Farinha de Trigo|farinha de trigo", "FARINHA   DE\tTRIGO|farinha de trigo",
                "Farínha de Trígo|farinha de trigo", "Pão  de  Queijo|pao de queijo", "ÁÉÍÓÚ âêô ãõ ç|aeiou aeo ao c"})
        void variacoesDoMesmoNome(String entrada, String esperado) {
            assertThat(Ingrediente.normalizarNome(entrada)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("Textos.limpar preserva caixa e acentos, colapsando espaços")
        void limpar() {
            assertThat(Textos.limpar("  Açúcar   Refinado ")).isEqualTo("Açúcar Refinado");
        }
    }

    @Test
    @DisplayName("Quantidades.formatar usa formato pt-BR")
    void formatarQuantidades() {
        assertThat(Quantidades.formatar(new java.math.BigDecimal("1234.500"), "kg")).isEqualTo("1.234,5 kg");
        assertThat(Quantidades.formatar(new java.math.BigDecimal("600.000"), "kg")).isEqualTo("600 kg");
    }
}
