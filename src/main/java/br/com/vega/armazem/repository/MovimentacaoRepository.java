package br.com.vega.armazem.repository;

import br.com.vega.armazem.domain.model.Movimentacao;
import br.com.vega.armazem.domain.model.TipoOperacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    /** Histórico paginado com filtros opcionais. A ordenação vem do {@link Pageable}. */
    @Query(value = """
            select m from Movimentacao m
             where (:tipoOperacao is null or m.tipoOperacao = :tipoOperacao)
               and (:compartimentoId is null or m.compartimento.id = :compartimentoId)
            """,
            countQuery = """
            select count(m) from Movimentacao m
             where (:tipoOperacao is null or m.tipoOperacao = :tipoOperacao)
               and (:compartimentoId is null or m.compartimento.id = :compartimentoId)
            """)
    Page<Movimentacao> buscar(@Param("tipoOperacao") TipoOperacao tipoOperacao,
                              @Param("compartimentoId") Long compartimentoId,
                              Pageable pageable);

    long countByIngredienteId(Long ingredienteId);
}
