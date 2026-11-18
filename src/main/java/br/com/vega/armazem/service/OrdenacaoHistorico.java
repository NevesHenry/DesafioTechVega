package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/**
 * Ordenação do histórico. O critério principal vem de {@code sortBy}. O outro critério vira desempate, e o id
 * garante ordem estável entre movimentações do mesmo instante.
 */
public record OrdenacaoHistorico(Campo campo, Sort.Direction direcao) {

    public enum Campo {
        DATA(Set.of("date", "data", "datahora")),
        COMPARTIMENTO(Set.of("compartimento", "compartment", "compartimentoid"));

        private final Set<String> apelidos;

        Campo(Set<String> apelidos) {
            this.apelidos = apelidos;
        }
    }

    public static OrdenacaoHistorico de(String sortBy, String order) {
        return new OrdenacaoHistorico(campo(sortBy), direcao(order));
    }

    public Sort toSort() {
        Sort data = Sort.by(direcao, "dataHora");
        Sort compartimento = Sort.by(direcao, "compartimento.id");
        Sort principal = campo == Campo.DATA ? data.and(compartimento) : compartimento.and(data);
        return principal.and(Sort.by(direcao, "id"));
    }

    private static Campo campo(String sortBy) {
        String chave = sortBy == null ? "date" : sortBy.strip().toLowerCase(Locale.ROOT);
        for (Campo campo : Campo.values()) {
            if (campo.apelidos.contains(chave)) {
                return campo;
            }
        }
        throw new ParametroInvalidoException(
                "Parâmetro 'sortBy' inválido: '%s'. Valores aceitos: date, compartimento.".formatted(sortBy));
    }

    private static Sort.Direction direcao(String order) {
        String chave = order == null ? "desc" : order.strip().toLowerCase(Locale.ROOT);
        return switch (chave) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ParametroInvalidoException(
                    "Parâmetro 'order' inválido: '%s'. Valores aceitos: asc, desc.".formatted(order));
        };
    }
}
