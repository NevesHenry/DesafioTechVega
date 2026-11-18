package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.RegraNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Regras de representação de quantidades: sempre {@link BigDecimal} com 3 casas (gramas/mililitros). */
public final class Quantidades {

    public static final int ESCALA = 3;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(ESCALA);

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Quantidades() {
    }

    /** Valida que a quantidade é positiva e tem no máximo 3 casas decimais, devolvendo-a com escala 3. */
    public static BigDecimal positiva(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new RegraNegocioException(CodigoErro.QUANTIDADE_INVALIDA, "A quantidade deve ser maior que zero.");
        }
        if (quantidade.stripTrailingZeros().scale() > ESCALA) {
            throw new RegraNegocioException(CodigoErro.QUANTIDADE_INVALIDA,
                    "A quantidade aceita no máximo %d casas decimais.".formatted(ESCALA));
        }
        return quantidade.setScale(ESCALA, RoundingMode.UNNECESSARY);
    }

    /** Formata para mensagens: {@code 1234.500} vira {@code "1.234,5 kg"}. */
    public static String formatar(BigDecimal quantidade, String unidade) {
        DecimalFormat formato = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(PT_BR));
        return formato.format(quantidade) + " " + unidade;
    }
}
