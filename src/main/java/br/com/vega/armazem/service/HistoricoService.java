package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.ParametroInvalidoException;
import br.com.vega.armazem.domain.model.TipoOperacao;
import br.com.vega.armazem.dto.MovimentacaoResponse;
import br.com.vega.armazem.repository.MovimentacaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta paginada do histórico de movimentações. */
@Service
@Transactional(readOnly = true)
public class HistoricoService {

    private final MovimentacaoRepository movimentacoes;

    public HistoricoService(MovimentacaoRepository movimentacoes) {
        this.movimentacoes = movimentacoes;
    }

    public Page<MovimentacaoResponse> listar(OrdenacaoHistorico ordenacao, TipoOperacao tipoOperacao,
                                             Long compartimentoId, int pagina, int tamanho) {
        if ((long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new ParametroInvalidoException("Paginação fora do limite: 'page' x 'size' não pode passar de "
                    + Integer.MAX_VALUE + " (recebido page=" + pagina + " e size=" + tamanho + ").");
        }
        PageRequest pageRequest = PageRequest.of(pagina, tamanho, ordenacao.toSort());
        return movimentacoes.buscar(tipoOperacao, compartimentoId, pageRequest)
                .map(MovimentacaoResponse::de);
    }
}
