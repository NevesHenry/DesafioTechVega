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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Estoque de um ingrediente dentro de um compartimento. O mesmo nome (sem diferenciar caixa e acentos) e o
 * mesmo tipo no mesmo compartimento formam um único registro: novas entradas somam na quantidade.
 */
@Entity
@Table(name = "ingrediente")
public class Ingrediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = 120)
    private String nomeNormalizado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoIngrediente tipo;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade = Quantidades.ZERO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compartimento_id", nullable = false)
    private Compartimento compartimento;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    private long versao;

    protected Ingrediente() {
        // JPA
    }

    private Ingrediente(String nome, TipoIngrediente tipo, Compartimento compartimento, LocalDateTime agora) {
        this.nome = Textos.limpar(Objects.requireNonNull(nome, "nome"));
        this.nomeNormalizado = normalizarNome(nome);
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.compartimento = Objects.requireNonNull(compartimento, "compartimento");
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /**
     * Cria um ingrediente ainda sem estoque. A quantidade inicial só entra por
     * {@link Movimentacao#entrada(Ingrediente, BigDecimal, String, LocalDateTime)}, que valida as regras do
     * compartimento e gera o histórico.
     */
    public static Ingrediente novo(String nome, TipoIngrediente tipo, Compartimento compartimento,
                                   LocalDateTime agora) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException(CodigoErro.VALIDACAO, "O nome do ingrediente é obrigatório.");
        }
        return new Ingrediente(nome, tipo, compartimento, agora);
    }

    /** Chave usada para identificar o mesmo ingrediente: {@code "Açúcar  Refinado"} vira {@code "acucar refinado"}. */
    public static String normalizarNome(String nome) {
        return Textos.normalizar(nome);
    }

    void adicionar(BigDecimal quantidade, LocalDateTime agora) {
        this.quantidade = this.quantidade.add(quantidade);
        this.atualizadoEm = agora;
    }

    void retirar(BigDecimal quantidade, LocalDateTime agora) {
        if (quantidade.compareTo(this.quantidade) > 0) {
            throw new RegraNegocioException(CodigoErro.ESTOQUE_INSUFICIENTE,
                    "Estoque insuficiente de '%s' no compartimento %s: disponível %s, solicitado %s.".formatted(
                            nome, compartimento.getCodigo(),
                            Quantidades.formatar(this.quantidade, tipo.getUnidade()),
                            Quantidades.formatar(quantidade, tipo.getUnidade())));
        }
        this.quantidade = this.quantidade.subtract(quantidade);
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeNormalizado() {
        return nomeNormalizado;
    }

    public TipoIngrediente getTipo() {
        return tipo;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public Compartimento getCompartimento() {
        return compartimento;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    @Override
    public String toString() {
        return "Ingrediente[id=%d, nome=%s, tipo=%s, quantidade=%s]".formatted(id, nome, tipo, quantidade);
    }
}
