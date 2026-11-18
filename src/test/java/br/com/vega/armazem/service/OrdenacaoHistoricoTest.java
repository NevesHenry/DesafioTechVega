package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import br.com.vega.armazem.service.OrdenacaoHistorico.Campo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrdenacaoHistorico")
class OrdenacaoHistoricoTest {

    @ParameterizedTest(name = "sortBy=\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "date|DATA", "DATE|DATA", "data|DATA", "Data|DATA", "dataHora|DATA",
            "compartimento|COMPARTIMENTO", "COMPARTIMENTO|COMPARTIMENTO", "compartment|COMPARTIMENTO",
            "compartimentoId|COMPARTIMENTO", "' compartimento '|COMPARTIMENTO"})
    void interpretaSortBy(String sortBy, Campo esperado) {
        assertThat(OrdenacaoHistorico.de(sortBy, "asc").campo()).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "order=\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "asc|ASC", "ASC|ASC", "Asc|ASC", "desc|DESC", "DESC|DESC", "Desc|DESC", "' desc '|DESC"})
    void interpretaOrder(String order, Sort.Direction esperado) {
        assertThat(OrdenacaoHistorico.de("date", order).direcao()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("valores nulos usam os padrões: date desc")
    void padroes() {
        OrdenacaoHistorico padrao = OrdenacaoHistorico.de(null, null);

        assertThat(padrao.campo()).isEqualTo(Campo.DATA);
        assertThat(padrao.direcao()).isEqualTo(Sort.Direction.DESC);
    }

    @ParameterizedTest(name = "sortBy=\"{0}\" é inválido")
    @ValueSource(strings = {"", " ", "nome", "id", "dateX", "quantidade"})
    void sortByInvalido(String sortBy) {
        assertThatThrownBy(() -> OrdenacaoHistorico.de(sortBy, "asc"))
                .isInstanceOfSatisfying(ParametroInvalidoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
                .hasMessageContaining("sortBy")
                .hasMessageContaining("date, compartimento");
    }

    @ParameterizedTest(name = "order=\"{0}\" é inválido")
    @ValueSource(strings = {"", " ", "up", "ascending", "descendente", "1"})
    void orderInvalido(String order) {
        assertThatThrownBy(() -> OrdenacaoHistorico.de("date", order))
                .isInstanceOfSatisfying(ParametroInvalidoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
                .hasMessageContaining("order")
                .hasMessageContaining("asc, desc");
    }

    @ParameterizedTest(name = "date {0}: só id (ordem de gravação, imune a ajuste de relógio)")
    @ValueSource(strings = {"asc", "desc"})
    void toSortPorData(String order) {
        Sort.Direction direcao = Sort.Direction.fromString(order);

        Sort sort = OrdenacaoHistorico.de("date", order).toSort();

        assertThat(sort.toList()).containsExactly(new Sort.Order(direcao, "id"));
    }

    @ParameterizedTest(name = "compartimento {0}: compartimento.id, id")
    @ValueSource(strings = {"asc", "desc"})
    void toSortPorCompartimento(String order) {
        Sort.Direction direcao = Sort.Direction.fromString(order);

        Sort sort = OrdenacaoHistorico.de("compartimento", order).toSort();

        assertThat(sort.toList()).containsExactly(
                new Sort.Order(direcao, "compartimento.id"),
                new Sort.Order(direcao, "id"));
    }

    @Test
    @DisplayName("a ordenação padrão é id desc (gravação mais recente primeiro)")
    void toSortPadrao() {
        assertThat(OrdenacaoHistorico.de(null, null).toSort().toList()).containsExactly(Sort.Order.desc("id"));
    }
}
