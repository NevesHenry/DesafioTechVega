'use strict';

/* ==========================================================================
   Armazém de Ingredientes - frontend (vanilla JS, sem dependências)
   Organização: constantes/utilitários -> cliente de API -> toasts ->
   renderização por seção -> carregadores -> ações -> ligação de eventos.
   Todo conteúdo vindo do usuário/servidor é inserido via textContent.
   ========================================================================== */

/* ---------- Constantes e estado ---------- */

const TIPOS = {
  SECO: { rotulo: 'Seco', plural: 'Secos', unidade: 'kg' },
  LIQUIDO: { rotulo: 'Líquido', plural: 'Líquidos', unidade: 'L' },
  REFRIGERADO: { rotulo: 'Refrigerado', plural: 'Refrigerados', unidade: 'kg' }
};
const TIPOS_ORDEM = ['SECO', 'LIQUIDO', 'REFRIGERADO'];
const COMPARTIMENTOS = [1, 2, 3, 4, 5].map((id) => ({ id: id, codigo: 'C' + id }));
const HIST_PAGE_SIZE = 20;
const LS_RESPONSAVEL = 'armazem.responsavel';

const state = {
  compFiltro: null, // id do compartimento filtrado pelo card (ou null)
  histPagina: 0,
  histTotalPaginas: 1,
  dialog: null // { id, op: 'entradas' | 'saidas', nome, unidade }
};

const $ = (id) => document.getElementById(id);

/* ---------- Formatação ---------- */

const nf = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: 3 });

function fmtNum(valor) {
  const n = Number(valor);
  return Number.isFinite(n) ? nf.format(n) : '—';
}

function fmtQtd(valor, unidade) {
  return fmtNum(valor) + (unidade ? ' ' + unidade : '');
}

// "2026-11-17" -> "17/11/2026"
function fmtData(iso) {
  if (!iso) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? m[3] + '/' + m[2] + '/' + m[1] : String(iso);
}

// "2026-11-17T14:30:00.123" -> "17/11/2026 14:30:00" (sem conversão de fuso: horário local)
function fmtDataHora(iso) {
  if (!iso) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(iso);
  if (!m) return String(iso);
  return m[3] + '/' + m[2] + '/' + m[1] + ' ' + m[4] + ':' + m[5] + ':' + (m[6] || '00');
}

function rotuloTipo(tipo) {
  return TIPOS[tipo] ? TIPOS[tipo].rotulo : (tipo || '—');
}

function unidadeDoTipo(tipo) {
  return TIPOS[tipo] ? TIPOS[tipo].unidade : '';
}

/* ---------- Helpers de DOM (sempre textContent) ---------- */

function el(tag, opcoes, filhos) {
  const node = document.createElement(tag);
  const o = opcoes || {};
  if (o.className) node.className = o.className;
  if (o.text !== undefined && o.text !== null) node.textContent = o.text;
  if (o.attrs) {
    Object.keys(o.attrs).forEach((k) => node.setAttribute(k, o.attrs[k]));
  }
  (filhos || []).forEach((f) => { if (f) node.appendChild(f); });
  return node;
}

function limpar(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

function badgeTipo(tipo) {
  if (!tipo) return el('span', { className: 'badge', text: 'Vazio' });
  return el('span', { className: 'badge t-' + tipo, text: rotuloTipo(tipo) });
}

function badgeOperacao(op) {
  const texto = op === 'SAIDA' ? 'SAÍDA' : (op === 'ENTRADA' ? 'ENTRADA' : String(op || '—'));
  return el('span', { className: 'badge op-' + op, text: texto });
}

function td(conteudo, className) {
  const cell = el('td', { className: className || '' });
  if (conteudo instanceof Node) cell.appendChild(conteudo);
  else cell.textContent = conteudo === null || conteudo === undefined ? '—' : String(conteudo);
  return cell;
}

function linhaVazia(colunas, mensagem) {
  const tr = document.createElement('tr');
  const cell = el('td', { className: 'empty', text: mensagem });
  cell.colSpan = colunas;
  tr.appendChild(cell);
  return tr;
}

function preencherSelectCompartimentos(select, comTodos) {
  limpar(select);
  if (comTodos) select.appendChild(el('option', { text: 'Todos', attrs: { value: '' } }));
  COMPARTIMENTOS.forEach((c) => {
    select.appendChild(el('option', { text: c.codigo, attrs: { value: String(c.id) } }));
  });
}

/* ---------- localStorage seguro ---------- */

function lsGet(chave) {
  try { return window.localStorage.getItem(chave); } catch (e) { return null; }
}

function lsSet(chave, valor) {
  try { window.localStorage.setItem(chave, valor); } catch (e) { /* ignorar */ }
}

/* ---------- Toasts ---------- */

function toast(mensagem, tipo) {
  const container = $('toasts');
  const linhas = String(mensagem).split('\n');
  const node = el('div', { className: 'toast ' + (tipo || '') }, linhas.map((l) => el('div', { text: l })));
  const remover = () => { if (node.parentNode) node.parentNode.removeChild(node); };
  node.addEventListener('click', remover);
  container.appendChild(node);
  setTimeout(remover, tipo === 'error' ? 9000 : 4000);
}

const toastOk = (m) => toast(m, 'success');
const toastErro = (m) => toast(m, 'error');

function mensagemDe(err) {
  return err && err.message ? err.message : 'Erro inesperado.';
}

/* ---------- Cliente de API ---------- */

// Monta a query string ignorando valores vazios.
function querystring(params) {
  const usp = new URLSearchParams();
  Object.keys(params || {}).forEach((k) => {
    const v = params[k];
    if (v !== undefined && v !== null && v !== '') usp.append(k, String(v));
  });
  const s = usp.toString();
  return s ? '?' + s : '';
}

// Converte uma resposta de erro (ProblemDetail ou outra) em texto legível.
function mensagemDeProblema(status, corpo) {
  const linhas = [];
  if (corpo && typeof corpo === 'object') {
    linhas.push(corpo.detail || corpo.title || ('Erro HTTP ' + status));
    if (Array.isArray(corpo.erros)) {
      corpo.erros.forEach((e) => {
        if (e && (e.campo || e.mensagem)) linhas.push((e.campo || '') + ': ' + (e.mensagem || ''));
      });
    }
  } else {
    linhas.push('Erro HTTP ' + status + (typeof corpo === 'string' && corpo ? ' - ' + corpo.slice(0, 200) : ''));
  }
  return linhas.join('\n');
}

/**
 * fetch com tratamento de erro. Retorna { status, data }.
 * Lança Error com mensagem legível (ProblemDetail ou falha de rede).
 */
async function api(path, opcoes) {
  const o = opcoes || {};
  const init = { method: o.method || 'GET', headers: { Accept: 'application/json, application/problem+json' } };
  if (o.body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(o.body);
  }

  let resp;
  try {
    resp = await fetch(path + querystring(o.params), init);
  } catch (e) {
    throw new Error('Falha de rede: não foi possível contactar o servidor. Verifique sua conexão e tente novamente.');
  }

  const texto = await resp.text();
  let corpo = null;
  if (texto) {
    try { corpo = JSON.parse(texto); } catch (e) { corpo = texto; }
  }

  if (!resp.ok) {
    const erro = new Error(mensagemDeProblema(resp.status, corpo));
    erro.status = resp.status;
    erro.problema = corpo;
    throw erro;
  }
  return { status: resp.status, data: corpo };
}

const getJson = async (path, params) => (await api(path, { params: params })).data;

/* ---------- Seção: dashboard de compartimentos ---------- */

function percentual(comp) {
  const cap = Number(comp.capacidadeMaxima);
  const atual = Number(comp.quantidadeAtual);
  if (!cap || !Number.isFinite(cap) || !Number.isFinite(atual)) return 0;
  return Math.max(0, Math.min(100, (atual / cap) * 100));
}

function statusCompartimento(comp) {
  if (!comp.tipoAtual || (comp.vazio && comp.aceitaQualquerTipo)) return 'Aceita qualquer tipo';
  if (comp.vazio) return 'Vazio — só aceita ' + rotuloTipo(comp.tipoAtual) + ' até amanhã';
  return 'Em uso: ' + rotuloTipo(comp.tipoAtual);
}

function renderCard(comp) {
  const mostraTipo = comp.tipoAtual && !(comp.vazio && comp.aceitaQualquerTipo);
  const tipoClasse = mostraTipo ? comp.tipoAtual : '';
  const pct = percentual(comp);

  const fill = el('span', { className: 'bar-fill' + (tipoClasse ? ' t-' + tipoClasse : '') });
  fill.style.width = pct.toFixed(1) + '%';
  const bar = el('span', {
    className: 'bar',
    attrs: { role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(Math.round(pct)) }
  }, [fill]);

  const temCap = comp.capacidadeMaxima !== null && comp.capacidadeMaxima !== undefined;
  const textoQtd = temCap
    ? fmtNum(comp.quantidadeAtual) + ' / ' + fmtNum(comp.capacidadeMaxima) + ' ' + (comp.unidade || '')
    : fmtNum(comp.quantidadeAtual) + ' (sem uso ainda)';
  const textoLivre = temCap ? 'Livre: ' + fmtQtd(comp.espacoLivre, comp.unidade) : 'Livre: —';

  const botao = el('button', {
    className: 'card' + (tipoClasse ? ' t-' + tipoClasse : ''),
    attrs: {
      type: 'button',
      'aria-pressed': state.compFiltro === comp.id ? 'true' : 'false',
      title: 'Filtrar ingredientes do compartimento ' + comp.codigo
    }
  }, [
    el('span', { className: 'card-top' }, [
      el('span', { className: 'card-code', text: comp.codigo }),
      mostraTipo ? badgeTipo(comp.tipoAtual) : badgeTipo(null)
    ]),
    bar,
    el('span', { className: 'card-qty', text: textoQtd }),
    el('span', { className: 'card-line', text: textoLivre }),
    el('span', { className: 'card-line', text: 'Última movimentação: ' + fmtData(comp.ultimaMovimentacaoEm) }),
    el('span', { className: 'card-status', text: statusCompartimento(comp) })
  ]);

  botao.addEventListener('click', () => {
    state.compFiltro = state.compFiltro === comp.id ? null : comp.id;
    $('i-compartimento').value = state.compFiltro === null ? '' : String(state.compFiltro);
    marcarCardSelecionado();
    carregarIngredientes();
  });
  botao.dataset.compId = String(comp.id);
  return botao;
}

function renderCompartimentos(lista) {
  const container = $('compartimentos');
  limpar(container);
  lista.forEach((c) => container.appendChild(renderCard(c)));
}

function marcarCardSelecionado() {
  document.querySelectorAll('#compartimentos .card').forEach((card) => {
    card.setAttribute('aria-pressed', card.dataset.compId === String(state.compFiltro) ? 'true' : 'false');
  });
}

/* ---------- Seção: volume por tipo ---------- */

function renderVolume(lista) {
  const container = $('volume');
  limpar(container);
  const porTipo = {};
  (lista || []).forEach((v) => { porTipo[v.type] = v.totalQuantity; });
  TIPOS_ORDEM.forEach((tipo) => {
    const unidade = unidadeDoTipo(tipo);
    container.appendChild(el('div', { className: 'tile t-' + tipo }, [
      el('div', { className: 'tile-label', text: TIPOS[tipo].plural }),
      el('div', { className: 'tile-value' }, [
        document.createTextNode(fmtNum(porTipo[tipo] || 0)),
        el('span', { className: 'tile-unit', text: unidade })
      ])
    ]));
  });
}

/* ---------- Seção: sugestões de compartimento ---------- */

function renderSugestoes(lista) {
  const container = $('sugestoes');
  limpar(container);
  if (!lista.length) {
    container.appendChild(el('span', { className: 'msg', text: 'Nenhum compartimento disponível' }));
    return;
  }
  container.appendChild(el('span', { className: 'msg', text: 'Sugestões (melhor ajuste primeiro):' }));
  lista.forEach((c) => {
    const chip = el('button', {
      className: 'chip',
      text: c.codigo + ' — livre ' + fmtQtd(c.espacoLivre, c.unidade),
      attrs: { type: 'button', 'aria-pressed': 'false' }
    });
    chip.addEventListener('click', () => {
      $('f-compartimento').value = String(c.id);
      container.querySelectorAll('.chip').forEach((x) => x.setAttribute('aria-pressed', 'false'));
      chip.setAttribute('aria-pressed', 'true');
    });
    container.appendChild(chip);
  });
}

/* ---------- Seção: tabela de ingredientes ---------- */

function renderIngredientes(lista) {
  const tbody = $('tbody-ingredientes');
  limpar(tbody);
  if (!lista.length) {
    tbody.appendChild(linhaVazia(6, 'Nenhum ingrediente encontrado.'));
    return;
  }
  lista.forEach((ing) => {
    const unidade = ing.unidade || unidadeDoTipo(ing.tipo);
    const btnEntrada = el('button', { className: 'btn btn-sm btn-entrada', text: 'Entrada', attrs: { type: 'button' } });
    const btnSaida = el('button', { className: 'btn btn-sm btn-saida', text: 'Saída', attrs: { type: 'button' } });
    btnEntrada.addEventListener('click', () => abrirDialogo(ing, 'entradas', unidade));
    btnSaida.addEventListener('click', () => abrirDialogo(ing, 'saidas', unidade));

    const tr = document.createElement('tr');
    tr.appendChild(td(ing.nome));
    tr.appendChild(td(badgeTipo(ing.tipo)));
    tr.appendChild(td(fmtQtd(ing.quantidade, unidade), 'num'));
    tr.appendChild(td(ing.compartimentoCodigo));
    tr.appendChild(td(fmtDataHora(ing.atualizadoEm)));
    tr.appendChild(td(el('div', { className: 'row-actions' }, [btnEntrada, btnSaida])));
    tbody.appendChild(tr);
  });
}

/* ---------- Seção: disponíveis para retirada/venda ---------- */

function renderVenda(lista, tipo) {
  const tbody = $('tbody-venda');
  limpar(tbody);
  if (!tipo) {
    tbody.appendChild(linhaVazia(3, 'Selecione um tipo para consultar.'));
    return;
  }
  if (!lista.length) {
    tbody.appendChild(linhaVazia(3, 'Nenhum compartimento com estoque deste tipo.'));
    return;
  }
  lista.forEach((c) => {
    const unidade = c.unidade || unidadeDoTipo(tipo);
    const tr = document.createElement('tr');
    tr.appendChild(td(c.codigo));
    tr.appendChild(td(badgeTipo(c.tipoAtual || tipo)));
    tr.appendChild(td(fmtQtd(c.quantidadeAtual, unidade), 'num'));
    tbody.appendChild(tr);
  });
}

/* ---------- Seção: histórico ---------- */

function renderHistorico(pagina) {
  const tbody = $('tbody-historico');
  limpar(tbody);
  const conteudo = (pagina && pagina.content) || [];
  const info = (pagina && pagina.page) || {};
  state.histPagina = Number.isFinite(info.number) ? info.number : 0;
  state.histTotalPaginas = Math.max(1, Number(info.totalPages) || 1);

  if (!conteudo.length) {
    tbody.appendChild(linhaVazia(8, 'Nenhuma movimentação encontrada.'));
  }
  conteudo.forEach((m) => {
    const unidade = m.unidade || unidadeDoTipo(m.tipoIngrediente);
    const tr = document.createElement('tr');
    tr.appendChild(td(fmtDataHora(m.dataHora)));
    tr.appendChild(td(badgeOperacao(m.tipoOperacao)));
    tr.appendChild(td(m.nomeIngrediente));
    tr.appendChild(td(badgeTipo(m.tipoIngrediente)));
    tr.appendChild(td(fmtQtd(m.quantidade, unidade), 'num'));
    tr.appendChild(td(m.compartimentoCodigo));
    tr.appendChild(td(fmtQtd(m.saldoCompartimento, unidade), 'num'));
    tr.appendChild(td(m.responsavel));
    tbody.appendChild(tr);
  });

  $('h-pagina').textContent = 'Página ' + (state.histPagina + 1) + ' de ' + state.histTotalPaginas;
  $('h-prev').disabled = state.histPagina <= 0;
  $('h-next').disabled = state.histPagina + 1 >= state.histTotalPaginas;
}

/* ---------- Carregadores (chamam a API e renderizam) ---------- */

async function carregarCompartimentos() {
  try {
    renderCompartimentos(await getJson('/compartimentos'));
  } catch (e) { toastErro(mensagemDe(e)); }
}

async function carregarVolume() {
  try {
    renderVolume(await getJson('/ingredientes/volume'));
  } catch (e) { toastErro(mensagemDe(e)); }
}

async function carregarIngredientes() {
  try {
    const lista = await getJson('/ingredientes', {
      tipo: $('i-tipo').value,
      compartimentoId: $('i-compartimento').value,
      comEstoque: $('i-estoque').checked ? 'true' : 'false'
    });
    renderIngredientes(lista);
  } catch (e) { toastErro(mensagemDe(e)); }
}

async function carregarVenda() {
  const tipo = $('v-tipo').value;
  if (!tipo) { renderVenda([], ''); return; }
  try {
    renderVenda(await getJson('/compartimentos/disponiveis-para-venda', { tipo: tipo }), tipo);
  } catch (e) { toastErro(mensagemDe(e)); }
}

async function carregarHistorico() {
  try {
    const pagina = await getJson('/historico', {
      sortBy: $('h-sortBy').value,
      order: $('h-order').value,
      page: state.histPagina,
      size: HIST_PAGE_SIZE,
      tipoOperacao: $('h-operacao').value,
      compartimentoId: $('h-compartimento').value
    });
    renderHistorico(pagina);
  } catch (e) { toastErro(mensagemDe(e)); }
}

// Após qualquer escrita: atualiza todos os painéis.
function atualizarTudo() {
  return Promise.all([
    carregarCompartimentos(),
    carregarVolume(),
    carregarIngredientes(),
    carregarHistorico(),
    carregarVenda()
  ]);
}

/* ---------- Ações ---------- */

// Desabilita o botão enquanto a requisição está em andamento.
async function comBotaoOcupado(botao, fn) {
  botao.disabled = true;
  try {
    return await fn();
  } finally {
    botao.disabled = false;
  }
}

function lembrarResponsavel(valor) {
  if (valor) lsSet(LS_RESPONSAVEL, valor);
}

function validarQuantidade(valor) {
  const n = Number(valor);
  return valor !== '' && Number.isFinite(n) && n > 0 ? n : null;
}

async function sugerirCompartimentos() {
  const quantidade = validarQuantidade($('f-quantidade').value);
  if (quantidade === null) {
    toastErro('Informe uma quantidade maior que 0 para sugerir compartimentos.');
    $('f-quantidade').focus();
    return;
  }
  await comBotaoOcupado($('btn-sugerir'), async () => {
    try {
      const lista = await getJson('/compartimentos/disponiveis', {
        quantidade: quantidade,
        tipo: $('f-tipo').value
      });
      renderSugestoes(lista);
    } catch (e) { toastErro(mensagemDe(e)); }
  });
}

async function cadastrarIngrediente(evento) {
  evento.preventDefault();
  const nome = $('f-nome').value.trim();
  const quantidade = validarQuantidade($('f-quantidade').value);
  const responsavel = $('f-responsavel').value.trim();
  const compartimentoId = Number($('f-compartimento').value);

  if (!nome || !responsavel) {
    toastErro('Preencha nome e responsável.');
    return;
  }
  if (quantidade === null) {
    toastErro('Informe uma quantidade maior que 0.');
    return;
  }

  await comBotaoOcupado($('btn-cadastrar'), async () => {
    try {
      const resp = await api('/ingredientes', {
        method: 'POST',
        body: {
          nome: nome,
          tipo: $('f-tipo').value,
          quantidade: quantidade,
          compartimentoId: compartimentoId,
          responsavel: responsavel
        }
      });
      lembrarResponsavel(responsavel);
      toastOk(resp.status === 201 ? 'Ingrediente cadastrado' : 'Entrada registrada em ingrediente existente');
      $('f-nome').value = '';
      $('f-quantidade').value = '';
      limpar($('sugestoes'));
      await atualizarTudo();
    } catch (e) { toastErro(mensagemDe(e)); }
  });
}

/* ---------- Diálogo de entrada/saída ---------- */

function abrirDialogo(ing, op, unidade) {
  state.dialog = { id: ing.id, op: op, nome: ing.nome, unidade: unidade };
  $('dlg-titulo').textContent = (op === 'entradas' ? 'Registrar entrada' : 'Registrar saída') + ' — ' + ing.nome;
  $('dlg-info').textContent = 'Saldo atual: ' + fmtQtd(ing.quantidade, unidade) + ' (' + (ing.compartimentoCodigo || '—') + ')';
  $('dlg-qtd-label').textContent = 'Quantidade' + (unidade ? ' (' + unidade + ')' : '');
  $('m-quantidade').value = '';
  $('m-responsavel').value = lsGet(LS_RESPONSAVEL) || '';
  const erro = $('dlg-erro');
  erro.hidden = true;
  erro.textContent = '';
  $('dlg-mov').showModal();
  $('m-quantidade').focus();
}

function erroNoDialogo(mensagem) {
  const erro = $('dlg-erro');
  erro.textContent = mensagem;
  erro.hidden = false;
  toastErro(mensagem);
}

async function confirmarMovimentacao(evento) {
  evento.preventDefault();
  if (!state.dialog) return;
  const quantidade = validarQuantidade($('m-quantidade').value);
  const responsavel = $('m-responsavel').value.trim();
  if (quantidade === null) { erroNoDialogo('Informe uma quantidade maior que 0.'); return; }
  if (!responsavel) { erroNoDialogo('Informe o responsável.'); return; }

  const d = state.dialog;
  await comBotaoOcupado($('m-confirmar'), async () => {
    try {
      await api('/ingredientes/' + encodeURIComponent(d.id) + '/' + d.op, {
        method: 'POST',
        body: { quantidade: quantidade, responsavel: responsavel }
      });
      lembrarResponsavel(responsavel);
      $('dlg-mov').close();
      toastOk(d.op === 'entradas' ? 'Entrada registrada' : 'Saída registrada');
      await atualizarTudo();
    } catch (e) { erroNoDialogo(mensagemDe(e)); }
  });
}

/* ---------- Ligação de eventos e inicialização ---------- */

function resetarHistoricoEBuscar() {
  state.histPagina = 0;
  carregarHistorico();
}

function ligarEventos() {
  $('btn-sugerir').addEventListener('click', sugerirCompartimentos);
  $('form-ingrediente').addEventListener('submit', cadastrarIngrediente);
  // Ao mudar tipo/quantidade as sugestões antigas deixam de valer.
  $('f-tipo').addEventListener('change', () => limpar($('sugestoes')));
  $('f-quantidade').addEventListener('input', () => limpar($('sugestoes')));

  $('i-tipo').addEventListener('change', carregarIngredientes);
  $('i-estoque').addEventListener('change', carregarIngredientes);
  $('i-compartimento').addEventListener('change', () => {
    const v = $('i-compartimento').value;
    state.compFiltro = v === '' ? null : Number(v);
    marcarCardSelecionado();
    carregarIngredientes();
  });

  $('v-tipo').addEventListener('change', carregarVenda);

  ['h-sortBy', 'h-order', 'h-operacao', 'h-compartimento'].forEach((id) => {
    $(id).addEventListener('change', resetarHistoricoEBuscar);
  });
  $('h-prev').addEventListener('click', () => {
    if (state.histPagina > 0) { state.histPagina -= 1; carregarHistorico(); }
  });
  $('h-next').addEventListener('click', () => {
    if (state.histPagina + 1 < state.histTotalPaginas) { state.histPagina += 1; carregarHistorico(); }
  });

  $('form-mov').addEventListener('submit', confirmarMovimentacao);
  $('m-cancelar').addEventListener('click', () => $('dlg-mov').close());
  $('dlg-mov').addEventListener('close', () => { state.dialog = null; });
}

function iniciar() {
  preencherSelectCompartimentos($('f-compartimento'), false);
  preencherSelectCompartimentos($('i-compartimento'), true);
  preencherSelectCompartimentos($('h-compartimento'), true);
  $('f-responsavel').value = lsGet(LS_RESPONSAVEL) || '';
  renderVenda([], '');
  ligarEventos();
  atualizarTudo();
}

iniciar();
