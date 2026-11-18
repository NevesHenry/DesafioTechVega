package br.com.vega.armazem.domain.model;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro imutável do histórico de entradas e saídas.
 *
 * <p>As fábricas {@link #entrada} e {@link #saida} são o único caminho público para alterar estoque. Elas
 * validam as regras, atualizam ingrediente e compartimento e devolvem a movimentação que o serviço grava
 * na mesma transação. Assim não existe entrada ou saída sem histórico.</p>
 */
@Entity
@Immutable
@Table(name = "movimentacao")
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", nullable = false, updatable = false, length = 10)
    private TipoOperacao tipoOperacao;

    @Column(nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ingrediente", nullable = false, updatable = false, length = 20)
    private TipoIngrediente tipoIngrediente;

    @Column(nullable = false, updatable = false, length = 100)
    private String responsavel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingrediente_id", nullable = false, updatable = false)
    private Ingrediente ingrediente;

    @Column(name = "nome_ingrediente", nullable = false, updatable = false, length = 120)
    private String nomeIngrediente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compartimento_id", nullable = false, updatable = false)
    private Compartimento compartimento;

    @Column(name = "compartimento_codigo", nullable = false, updatable = false, length = 5)
    private String compartimentoCodigo;

    /** Quantidade no compartimento logo após a operação. */
    @Column(name = "saldo_compartimento", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal saldoCompartimento;

    protected Movimentacao() {
        // JPA
    }

    private Movimentacao(TipoOperacao tipoOperacao, Ingrediente ingrediente, BigDecimal quantidade,
                         String responsavel, LocalDateTime agora) {
        Compartimento destino = ingrediente.getCompartimento();
        this.dataHora = agora;
        this.tipoOperacao = tipoOperacao;
        this.quantidade = quantidade;
        this.tipoIngrediente = ingrediente.getTipo();
        this.responsavel = responsavel;
        this.ingrediente = ingrediente;
        this.nomeIngrediente = ingrediente.getNome();
        this.compartimento = destino;
        this.compartimentoCodigo = destino.getCodigo();
        this.saldoCompartimento = destino.getQuantidadeAtual();
    }

    /** Valida e aplica a entrada no compartimento e no ingrediente e devolve o registro de histórico. */
    public static Movimentacao entrada(Ingrediente ingrediente, BigDecimal quantidade, String responsavel,
                                       LocalDateTime agora) {
        BigDecimal valor = Quantidades.positiva(quantidade);
        String quem = validarResponsavel(responsavel);
        ingrediente.getCompartimento().receber(ingrediente.getTipo(), valor, agora.toLocalDate());
        ingrediente.adicionar(valor, agora);
        return new Movimentacao(TipoOperacao.ENTRADA, ingrediente, valor, quem, agora);
    }

    /** Valida e aplica a saída no ingrediente e no compartimento e devolve o registro de histórico. */
    public static Movimentacao saida(Ingrediente ingrediente, BigDecimal quantidade, String responsavel,
                                     LocalDateTime agora) {
        BigDecimal valor = Quantidades.positiva(quantidade);
        String quem = validarResponsavel(responsavel);
        ingrediente.retirar(valor, agora);
        ingrediente.getCompartimento().retirar(valor, agora.toLocalDate());
        return new Movimentacao(TipoOperacao.SAIDA, ingrediente, valor, quem, agora);
    }

    private static String validarResponsavel(String responsavel) {
        if (responsavel == null || responsavel.isBlank()) {
            throw new RegraNegocioException(CodigoErro.VALIDACAO, "O responsável pela movimentação é obrigatório.");
        }
        return Textos.limpar(responsavel);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public TipoOperacao getTipoOperacao() {
        return tipoOperacao;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public TipoIngrediente getTipoIngrediente() {
        return tipoIngrediente;
    }

    public String getResponsavel() {
        return responsavel;
    }

    public Ingrediente getIngrediente() {
        return ingrediente;
    }

    public String getNomeIngrediente() {
        return nomeIngrediente;
    }

    public Compartimento getCompartimento() {
        return compartimento;
    }

    public String getCompartimentoCodigo() {
        return compartimentoCodigo;
    }

    public BigDecimal getSaldoCompartimento() {
        return saldoCompartimento;
    }
}
