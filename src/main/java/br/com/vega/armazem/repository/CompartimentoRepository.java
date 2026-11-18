package br.com.vega.armazem.repository;

import br.com.vega.armazem.domain.model.Compartimento;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompartimentoRepository extends JpaRepository<Compartimento, Long> {

    /**
     * Carrega o compartimento com lock pessimista de escrita ({@code SELECT ... FOR UPDATE}).
     * Entradas e saídas no mesmo compartimento ficam serializadas e compartimentos diferentes seguem em paralelo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compartimento c where c.id = :id")
    Optional<Compartimento> findByIdParaAtualizacao(@Param("id") Long id);

    List<Compartimento> findAllByOrderByIdAsc();

    /**
     * Compartimentos que aceitam agora uma entrada do {@code tipo} sem exceder a capacidade. Valem os que já
     * guardam o tipo e têm ocupação até {@code ocupacaoMaxima} (= capacidade - quantidade pedida), e os vazios
     * que não foram usados por outro tipo hoje.
     *
     * <p>A ordenação é por melhor encaixe: primeiro os que ficariam com menos espaço sobrando (mais ocupados do
     * mesmo tipo), depois os vazios. Isso reduz a fragmentação e deixa compartimentos vazios livres para outros
     * tipos. É a mesma regra de {@link Compartimento#podeReceber}, aplicada em uma única consulta.</p>
     */
    @Query("""
            select c from Compartimento c
             where (c.tipoAtual = :tipo and c.quantidadeAtual <= :ocupacaoMaxima)
                or (c.quantidadeAtual = 0
                    and (c.tipoAtual is null or c.ultimaMovimentacaoEm is null or c.ultimaMovimentacaoEm < :hoje))
             order by c.quantidadeAtual desc, c.id asc
            """)
    List<Compartimento> findDisponiveisParaArmazenamento(@Param("tipo") TipoIngrediente tipo,
                                                         @Param("ocupacaoMaxima") BigDecimal ocupacaoMaxima,
                                                         @Param("hoje") LocalDate hoje);

    /** Compartimentos com saldo positivo do tipo informado, ou seja, de onde é possível retirar ou vender. */
    @Query("""
            select c from Compartimento c
             where c.tipoAtual = :tipo and c.quantidadeAtual > 0
             order by c.id asc
            """)
    List<Compartimento> findDisponiveisParaVenda(@Param("tipo") TipoIngrediente tipo);

    /** Total armazenado por tipo, somado a partir dos totais desnormalizados de cada compartimento (no máximo 5 linhas). */
    @Query("""
            select new br.com.vega.armazem.repository.VolumePorTipo(c.tipoAtual, sum(c.quantidadeAtual))
              from Compartimento c
             where c.tipoAtual is not null
             group by c.tipoAtual
            """)
    List<VolumePorTipo> somarVolumePorTipo();
}
