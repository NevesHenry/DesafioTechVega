package br.com.vega.armazem.service;

import br.com.vega.armazem.domain.exception.CodigoErro;
import br.com.vega.armazem.domain.exception.RecursoNaoEncontradoException;
import br.com.vega.armazem.domain.exception.RegraNegocioException;
import br.com.vega.armazem.domain.model.Compartimento;
import br.com.vega.armazem.domain.model.Ingrediente;
import br.com.vega.armazem.domain.model.Movimentacao;
import br.com.vega.armazem.domain.model.TipoIngrediente;
import br.com.vega.armazem.domain.model.TipoOperacao;
import br.com.vega.armazem.dto.CadastroIngredienteRequest;
import br.com.vega.armazem.dto.IngredienteResponse;
import br.com.vega.armazem.dto.MovimentacaoRequest;
import br.com.vega.armazem.repository.CompartimentoRepository;
import br.com.vega.armazem.repository.IngredienteRepository;
import br.com.vega.armazem.repository.MovimentacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstoqueService (unitário, repositórios simulados)")
class EstoqueServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final Clock RELOGIO = Clock.fixed(AGORA.atZone(ZONA).toInstant(), ZONA);

    @Mock
    private CompartimentoRepository compartimentos;
    @Mock
    private IngredienteRepository ingredientes;
    @Mock
    private MovimentacaoRepository movimentacoes;

    private EstoqueService servico;
    private Compartimento c1;

    private static BigDecimal q(String valor) {
        return new BigDecimal(valor);
    }

    private static CadastroIngredienteRequest cadastro(String nome, TipoIngrediente tipo, String quantidade,
                                                       long compartimentoId) {
        return new CadastroIngredienteRequest(nome, tipo, q(quantidade), compartimentoId, "pedro");
    }

    @BeforeEach
    void preparar() {
        servico = new EstoqueService(compartimentos, ingredientes, movimentacoes, RELOGIO);
        c1 = new Compartimento(1L, "C1");
    }

    @Nested
    @DisplayName("cadastrar")
    class Cadastrar {

        @Test
        @DisplayName("ingrediente novo: salva ingrediente e uma movimentação, criado=true")
        void novoIngrediente() {
            when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(c1));
            when(ingredientes.findByCompartimentoIdAndTipoAndNomeNormalizado(1L, TipoIngrediente.SECO,
                    "farinha de trigo")).thenReturn(Optional.empty());

            EstoqueService.ResultadoCadastro resultado = servico.cadastrar(
                    cadastro("  Farinha  de Trigo ", TipoIngrediente.SECO, "400", 1L));

            assertThat(resultado.criado()).isTrue();
            IngredienteResponse resposta = resultado.ingrediente();
            assertThat(resposta.nome()).isEqualTo("Farinha de Trigo");
            assertThat(resposta.tipo()).isEqualTo(TipoIngrediente.SECO);
            assertThat(resposta.quantidade()).isEqualByComparingTo("400");
            assertThat(resposta.compartimentoCodigo()).isEqualTo("C1");

            ArgumentCaptor<Ingrediente> ingrediente = ArgumentCaptor.forClass(Ingrediente.class);
            verify(ingredientes).save(ingrediente.capture());
            assertThat(ingrediente.getValue().getNomeNormalizado()).isEqualTo("farinha de trigo");
            assertThat(ingrediente.getValue().getCriadoEm()).isEqualTo(AGORA);

            ArgumentCaptor<Movimentacao> movimentacao = ArgumentCaptor.forClass(Movimentacao.class);
            verify(movimentacoes).save(movimentacao.capture());
            assertThat(movimentacao.getValue().getTipoOperacao()).isEqualTo(TipoOperacao.ENTRADA);
            assertThat(movimentacao.getValue().getQuantidade()).isEqualByComparingTo("400");
            assertThat(movimentacao.getValue().getResponsavel()).isEqualTo("pedro");
            assertThat(movimentacao.getValue().getDataHora()).isEqualTo(AGORA);
            assertThat(c1.getQuantidadeAtual()).isEqualByComparingTo("400");
        }

        @Test
        @DisplayName("ingrediente existente: não salva ingrediente, soma a quantidade, criado=false")
        void ingredienteExistente() {
            Ingrediente existente = Ingrediente.novo("Farinha de Trigo", TipoIngrediente.SECO, c1, AGORA.minusDays(1));
            Movimentacao.entrada(existente, q("100"), "ana", AGORA.minusDays(1));
            when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(c1));
            when(ingredientes.findByCompartimentoIdAndTipoAndNomeNormalizado(1L, TipoIngrediente.SECO,
                    "farinha de trigo")).thenReturn(Optional.of(existente));

            EstoqueService.ResultadoCadastro resultado = servico.cadastrar(
                    cadastro("FARÍNHA   de trigo", TipoIngrediente.SECO, "50", 1L));

            assertThat(resultado.criado()).isFalse();
            assertThat(resultado.ingrediente().quantidade()).isEqualByComparingTo("150");
            assertThat(existente.getQuantidade()).isEqualByComparingTo("150");
            assertThat(c1.getQuantidadeAtual()).isEqualByComparingTo("150");
            verify(ingredientes, never()).save(any());
            ArgumentCaptor<Movimentacao> movimentacao = ArgumentCaptor.forClass(Movimentacao.class);
            verify(movimentacoes).save(movimentacao.capture());
            assertThat(movimentacao.getValue().getSaldoCompartimento()).isEqualByComparingTo("150");
            assertThat(movimentacao.getValue().getNomeIngrediente()).isEqualTo("Farinha de Trigo");
        }

        @Test
        @DisplayName("compartimento inexistente: COMPARTIMENTO_INEXISTENTE listando os válidos, sem gravar nada")
        void compartimentoInexistente() {
            when(compartimentos.findByIdParaAtualizacao(99L)).thenReturn(Optional.empty());
            when(compartimentos.findAllByOrderByIdAsc()).thenReturn(List.of(c1, new Compartimento(2L, "C2")));

            assertThatThrownBy(() -> servico.cadastrar(cadastro("Farinha", TipoIngrediente.SECO, "1", 99L)))
                    .isInstanceOfSatisfying(RegraNegocioException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.COMPARTIMENTO_INEXISTENTE))
                    .hasMessageContaining("99")
                    .hasMessageContaining("1 (C1)")
                    .hasMessageContaining("2 (C2)");

            verifyNoInteractions(ingredientes, movimentacoes);
        }

        @Test
        @DisplayName("violação de regra (tipo incompatível): nada é salvo")
        void violacaoDeRegra() {
            Ingrediente leite = Ingrediente.novo("Leite", TipoIngrediente.LIQUIDO, c1, AGORA.minusHours(1));
            Movimentacao.entrada(leite, q("10"), "ana", AGORA.minusHours(1));
            when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(c1));
            when(ingredientes.findByCompartimentoIdAndTipoAndNomeNormalizado(anyLong(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico.cadastrar(cadastro("Farinha", TipoIngrediente.SECO, "5", 1L)))
                    .isInstanceOfSatisfying(RegraNegocioException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.TIPO_INCOMPATIVEL));

            verify(movimentacoes, never()).save(any());
            verify(ingredientes, never()).save(any());
            assertThat(c1.getQuantidadeAtual()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("capacidade excedida em ingrediente existente: movimentação nunca salva")
        void capacidadeExcedida() {
            Ingrediente existente = Ingrediente.novo("Farinha", TipoIngrediente.SECO, c1, AGORA);
            Movimentacao.entrada(existente, q("500"), "ana", AGORA);
            when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(c1));
            when(ingredientes.findByCompartimentoIdAndTipoAndNomeNormalizado(1L, TipoIngrediente.SECO, "farinha"))
                    .thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> servico.cadastrar(cadastro("Farinha", TipoIngrediente.SECO, "101", 1L)))
                    .isInstanceOfSatisfying(RegraNegocioException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.CAPACIDADE_EXCEDIDA));

            verifyNoInteractions(movimentacoes);
            assertThat(existente.getQuantidade()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("obtém o compartimento por findByIdParaAtualizacao, nunca por findById")
        void usaConsultaComLock() {
            when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(c1));
            when(ingredientes.findByCompartimentoIdAndTipoAndNomeNormalizado(anyLong(), any(), any()))
                    .thenReturn(Optional.empty());

            servico.cadastrar(cadastro("Farinha", TipoIngrediente.SECO, "1", 1L));

            verify(compartimentos).findByIdParaAtualizacao(1L);
            verify(compartimentos, never()).findById(any());
            verify(compartimentos, never()).getReferenceById(any());
        }
    }

    @Nested
    @DisplayName("registrarEntrada / registrarSaida")
    class EntradaESaida {

        private Ingrediente farinha;

        @BeforeEach
        void ingredienteExistente() {
            farinha = Ingrediente.novo("Farinha", TipoIngrediente.SECO, c1, AGORA.minusHours(2));
            Movimentacao.entrada(farinha, q("300"), "ana", AGORA.minusHours(2));
        }

        private void stubsDeBusca() {
            when(ingredientes.findCompartimentoIdById(7L)).thenReturn(Optional.of(1L));
            when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(c1));
            when(ingredientes.findById(7L)).thenReturn(Optional.of(farinha));
        }

        @Test
        @DisplayName("saída: trava o compartimento antes de ler o ingrediente e grava uma movimentação SAIDA")
        void saidaComLockAntesDaLeitura() {
            stubsDeBusca();

            IngredienteResponse resposta = servico.registrarSaida(7L, new MovimentacaoRequest(q("100"), "joao"));

            assertThat(resposta.quantidade()).isEqualByComparingTo("200");
            InOrder ordem = inOrder(ingredientes, compartimentos, movimentacoes);
            ordem.verify(ingredientes).findCompartimentoIdById(7L);
            ordem.verify(compartimentos).findByIdParaAtualizacao(1L);
            ordem.verify(ingredientes).findById(7L);
            ArgumentCaptor<Movimentacao> movimentacao = ArgumentCaptor.forClass(Movimentacao.class);
            ordem.verify(movimentacoes).save(movimentacao.capture());
            assertThat(movimentacao.getValue().getTipoOperacao()).isEqualTo(TipoOperacao.SAIDA);
            assertThat(movimentacao.getValue().getQuantidade()).isEqualByComparingTo("100");
            assertThat(movimentacao.getValue().getSaldoCompartimento()).isEqualByComparingTo("200");
            verify(compartimentos, never()).findById(any());
            verify(ingredientes, never()).save(any());
        }

        @Test
        @DisplayName("entrada: grava uma movimentação ENTRADA e soma no ingrediente")
        void entrada() {
            stubsDeBusca();

            IngredienteResponse resposta = servico.registrarEntrada(7L, new MovimentacaoRequest(q("50"), "ana"));

            assertThat(resposta.quantidade()).isEqualByComparingTo("350");
            ArgumentCaptor<Movimentacao> movimentacao = ArgumentCaptor.forClass(Movimentacao.class);
            verify(movimentacoes).save(movimentacao.capture());
            assertThat(movimentacao.getValue().getTipoOperacao()).isEqualTo(TipoOperacao.ENTRADA);
            verify(compartimentos, never()).findById(any());
        }

        @Test
        @DisplayName("saída de ingrediente desconhecido: RecursoNaoEncontradoException, sem lock nem gravação")
        void saidaIngredienteDesconhecido() {
            when(ingredientes.findCompartimentoIdById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico.registrarSaida(5L, new MovimentacaoRequest(q("1"), "ana")))
                    .isInstanceOfSatisfying(RecursoNaoEncontradoException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.INGREDIENTE_NAO_ENCONTRADO))
                    .hasMessageContaining("5");

            verifyNoInteractions(compartimentos, movimentacoes);
            verify(ingredientes, never()).findById(any());
        }

        @Test
        @DisplayName("entrada de ingrediente desconhecido: RecursoNaoEncontradoException")
        void entradaIngredienteDesconhecido() {
            when(ingredientes.findCompartimentoIdById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico.registrarEntrada(5L, new MovimentacaoRequest(q("1"), "ana")))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verifyNoInteractions(compartimentos, movimentacoes);
        }

        @Test
        @DisplayName("estoque insuficiente: movimentação nunca salva e saldos intactos")
        void estoqueInsuficiente() {
            stubsDeBusca();

            assertThatThrownBy(() -> servico.registrarSaida(7L, new MovimentacaoRequest(q("300.001"), "ana")))
                    .isInstanceOfSatisfying(RegraNegocioException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.ESTOQUE_INSUFICIENTE));

            verifyNoInteractions(movimentacoes);
            assertThat(farinha.getQuantidade()).isEqualByComparingTo("300");
            assertThat(c1.getQuantidadeAtual()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("capacidade excedida na entrada: movimentação nunca salva")
        void capacidadeExcedida() {
            stubsDeBusca();

            assertThatThrownBy(() -> servico.registrarEntrada(7L, new MovimentacaoRequest(q("300.001"), "ana")))
                    .isInstanceOfSatisfying(RegraNegocioException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.CAPACIDADE_EXCEDIDA));

            verifyNoInteractions(movimentacoes);
            assertThat(farinha.getQuantidade()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("compartimento do ingrediente sumiu entre as consultas: RecursoNaoEncontradoException")
        void compartimentoNaoEncontrado() {
            when(ingredientes.findCompartimentoIdById(7L)).thenReturn(Optional.of(1L));
            when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico.registrarSaida(7L, new MovimentacaoRequest(q("1"), "ana")))
                    .isInstanceOfSatisfying(RecursoNaoEncontradoException.class,
                            e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.COMPARTIMENTO_NAO_ENCONTRADO));

            verifyNoInteractions(movimentacoes);
            verify(ingredientes, never()).findById(any());
        }
    }

    @Test
    @DisplayName("usa o relógio injetado (truncado em milissegundos) como data da operação")
    void usaRelogioInjetado() {
        Instant instante = LocalDateTime.of(2030, 1, 2, 3, 4, 5, 123_456_789).atZone(ZONA).toInstant();
        EstoqueService outro = new EstoqueService(compartimentos, ingredientes, movimentacoes,
                Clock.fixed(instante, ZONA));
        when(compartimentos.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(c1));
        when(ingredientes.findByCompartimentoIdAndTipoAndNomeNormalizado(anyLong(), any(), any()))
                .thenReturn(Optional.empty());

        EstoqueService.ResultadoCadastro resultado = outro.cadastrar(
                cadastro("Farinha", TipoIngrediente.SECO, "1", 1L));

        assertThat(resultado.ingrediente().criadoEm()).isEqualTo(LocalDateTime.of(2030, 1, 2, 3, 4, 5, 123_000_000));
        assertThat(c1.getUltimaMovimentacaoEm()).isEqualTo(LocalDateTime.of(2030, 1, 2, 0, 0).toLocalDate());
    }
}
