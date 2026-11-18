package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Compartimento do armazém (C1..C5). Concentra as regras de armazenamento:
 * <ul>
 *   <li>guarda um único tipo de ingrediente por vez;</li>
 *   <li>a capacidade máxima depende do tipo armazenado ({@link TipoIngrediente#getCapacidadeMaxima()});</li>
 *   <li>tipos diferentes não se misturam no mesmo período (dia). Um compartimento que armazenou um tipo
 *       hoje só aceita outro tipo amanhã, mesmo que tenha sido esvaziado.</li>
 * </ul>
 * Os métodos que alteram estoque são package-private. Só {@link Movimentacao} os chama, então toda alteração
 * de estoque gera um registro de histórico.
 */
@Entity
@Table(name = "compartimento")
public class Compartimento {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 5)
    private String codigo;

    /** Último tipo armazenado. Fica preenchido depois de esvaziar, para aplicar a regra de troca de tipo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_atual", length = 20)
    private TipoIngrediente tipoAtual;

    @Column(name = "quantidade_atual", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeAtual = Quantidades.ZERO;

    @Column(name = "ultima_movimentacao_em")
    private LocalDate ultimaMovimentacaoEm;

    @Version
    private long versao;

    protected Compartimento() {
        // JPA
    }

    public Compartimento(Long id, String codigo) {
        this.id = id;
        this.codigo = codigo;
    }

    // ------------------------------------------------------------------ regras de consulta

    public boolean estaVazio() {
        return quantidadeAtual.signum() == 0;
    }

    /** {@code true} se o compartimento pode receber qualquer tipo agora: vazio e sem uso de outro tipo hoje. */
    public boolean aceitaQualquerTipo(LocalDate hoje) {
        return estaVazio() && (tipoAtual == null || !movimentadoEm(hoje));
    }

    /** {@code true} se uma entrada de {@code quantidade} do {@code tipo} seria aceita agora. */
    public boolean podeReceber(TipoIngrediente tipo, BigDecimal quantidade, LocalDate hoje) {
        return violacaoDeEntrada(tipo, quantidade, hoje).isEmpty();
    }

    /** Espaço livre considerando o tipo atual, ou {@code null} se o compartimento nunca foi usado. */
    public BigDecimal getEspacoLivre() {
        return tipoAtual == null ? null : tipoAtual.getCapacidadeMaxima().subtract(quantidadeAtual);
    }

    /** Espaço livre para o {@code tipo} informado. É zero se o compartimento não pode receber esse tipo agora. */
    public BigDecimal espacoLivrePara(TipoIngrediente tipo, LocalDate hoje) {
        if (!aceitaTipo(tipo, hoje)) {
            return Quantidades.ZERO;
        }
        return tipo.getCapacidadeMaxima().subtract(quantidadeAtual);
    }

    /** Percentual de ocupação (0 a 100, 2 casas) em relação à capacidade do tipo atual. */
    public BigDecimal getPercentualOcupacao() {
        if (tipoAtual == null || estaVazio()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return quantidadeAtual.multiply(BigDecimal.valueOf(100))
                .divide(tipoAtual.getCapacidadeMaxima(), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ operações (via Movimentacao)

    void receber(TipoIngrediente tipo, BigDecimal quantidade, LocalDate hoje) {
        violacaoDeEntrada(tipo, quantidade, hoje).ifPresent(violacao -> {
            throw violacao;
        });
        this.tipoAtual = tipo;
        this.quantidadeAtual = quantidadeAtual.add(quantidade);
        this.ultimaMovimentacaoEm = hoje;
    }

    void retirar(BigDecimal quantidade, LocalDate hoje) {
        if (quantidade.compareTo(quantidadeAtual) > 0) {
            throw new RegraNegocioException(CodigoErro.ESTOQUE_INSUFICIENTE,
                    "O compartimento %s possui apenas %s; não é possível retirar %s.".formatted(
                            codigo, formatar(quantidadeAtual), formatar(quantidade)));
        }
        this.quantidadeAtual = quantidadeAtual.subtract(quantidade);
        this.ultimaMovimentacaoEm = hoje;
    }

    // ------------------------------------------------------------------ detalhes

    private Optional<RegraNegocioException> violacaoDeEntrada(TipoIngrediente tipo, BigDecimal quantidade,
                                                              LocalDate hoje) {
        if (quantidade == null || quantidade.signum() <= 0) {
            return Optional.of(new RegraNegocioException(CodigoErro.QUANTIDADE_INVALIDA,
                    "A quantidade deve ser maior que zero."));
        }
        if (tipoAtual != null && tipoAtual != tipo) {
            if (!estaVazio()) {
                return Optional.of(new RegraNegocioException(CodigoErro.TIPO_INCOMPATIVEL,
                        ("O compartimento %s está armazenando %s e não pode receber %s: não é permitido misturar "
                                + "tipos de ingredientes no mesmo compartimento.").formatted(codigo, tipoAtual, tipo)));
            }
            if (movimentadoEm(hoje)) {
                return Optional.of(new RegraNegocioException(CodigoErro.TROCA_DE_TIPO_BLOQUEADA,
                        ("O compartimento %s armazenou %s hoje (%s) e só poderá receber outro tipo de ingrediente "
                                + "a partir de amanhã (%s).").formatted(codigo, tipoAtual, hoje.format(DATA_BR),
                                hoje.plusDays(1).format(DATA_BR))));
            }
        }
        // Aqui o compartimento está vazio ou já contém o mesmo tipo.
        BigDecimal espacoLivre = tipo.getCapacidadeMaxima().subtract(quantidadeAtual);
        if (quantidade.compareTo(espacoLivre) > 0) {
            return Optional.of(new RegraNegocioException(CodigoErro.CAPACIDADE_EXCEDIDA,
                    ("Capacidade excedida no compartimento %s: a capacidade máxima para %s é %s, já há %s "
                            + "armazenados (espaço livre: %s) e foram solicitados %s.").formatted(
                            codigo, tipo, Quantidades.formatar(tipo.getCapacidadeMaxima(), tipo.getUnidade()),
                            Quantidades.formatar(quantidadeAtual, tipo.getUnidade()),
                            Quantidades.formatar(espacoLivre, tipo.getUnidade()),
                            Quantidades.formatar(quantidade, tipo.getUnidade()))));
        }
        return Optional.empty();
    }

    private boolean aceitaTipo(TipoIngrediente tipo, LocalDate hoje) {
        return tipoAtual == null || tipoAtual == tipo || aceitaQualquerTipo(hoje);
    }

    private boolean movimentadoEm(LocalDate dia) {
        return ultimaMovimentacaoEm != null && !ultimaMovimentacaoEm.isBefore(dia);
    }

    private String formatar(BigDecimal quantidade) {
        return Quantidades.formatar(quantidade, tipoAtual == null ? "" : tipoAtual.getUnidade()).strip();
    }

    // ------------------------------------------------------------------ getters

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public TipoIngrediente getTipoAtual() {
        return tipoAtual;
    }

    public BigDecimal getQuantidadeAtual() {
        return quantidadeAtual;
    }

    public LocalDate getUltimaMovimentacaoEm() {
        return ultimaMovimentacaoEm;
    }

    public BigDecimal getCapacidadeMaxima() {
        return tipoAtual == null ? null : tipoAtual.getCapacidadeMaxima();
    }

    @Override
    public String toString() {
        return "Compartimento[%s, tipo=%s, quantidade=%s]".formatted(codigo, tipoAtual, quantidadeAtual);
    }
}
