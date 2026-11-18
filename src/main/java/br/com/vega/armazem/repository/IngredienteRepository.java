package br.com.vega.armazem.repository;

import br.com.vega.armazem.domain.model.Ingrediente;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredienteRepository extends JpaRepository<Ingrediente, Long> {

    Optional<Ingrediente> findByCompartimentoIdAndTipoAndNomeNormalizado(Long compartimentoId, TipoIngrediente tipo,
                                                                         String nomeNormalizado);

    /**
     * Devolve só o id do compartimento, sem carregar o ingrediente no contexto de persistência. Assim o
     * ingrediente é lido depois de adquirido o lock do compartimento e nunca fica desatualizado.
     */
    @Query("select i.compartimento.id from Ingrediente i where i.id = :id")
    Optional<Long> findCompartimentoIdById(@Param("id") Long id);

    @Query("""
            select i from Ingrediente i join fetch i.compartimento c
             where (:tipo is null or i.tipo = :tipo)
               and (:compartimentoId is null or c.id = :compartimentoId)
               and (:somenteComEstoque = false or i.quantidade > 0)
             order by c.id asc, i.nome asc
            """)
    List<Ingrediente> buscar(@Param("tipo") TipoIngrediente tipo,
                             @Param("compartimentoId") Long compartimentoId,
                             @Param("somenteComEstoque") boolean somenteComEstoque);

    @Query("select i from Ingrediente i join fetch i.compartimento where i.id = :id")
    Optional<Ingrediente> findComCompartimentoById(@Param("id") Long id);
}
