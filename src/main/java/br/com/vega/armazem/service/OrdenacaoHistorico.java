package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/**
 * Ordenação do histórico.
 *
 * <p>{@code date} ordena pela <b>ordem de gravação</b> (o id da movimentação, que só cresce) e não pelo horário
 * gravado. O relógio do sistema pode ser ajustado para trás (NTP, VM, WSL) e inverter a ordem de operações
 * que aconteceram em sequência. O horário continua sendo gravado e exibido, mas não decide quem é o mais
 * recente. {@code compartimento} ordena por compartimento e, dentro dele, pela ordem de gravação.</p>
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
        Sort gravacao = Sort.by(direcao, "id");
        return campo == Campo.DATA ? gravacao : Sort.by(direcao, "compartimento.id").and(gravacao);
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
