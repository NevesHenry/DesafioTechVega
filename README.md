# Armazém de Ingredientes — API REST

API RESTful para gerenciar o estoque de ingredientes de uma fábrica de alimentos, distribuídos em
5 compartimentos (**C1 a C5**). Inclui regras de capacidade por tipo, bloqueio de mistura de tipos,
histórico obrigatório de movimentações, tratamento de concorrência e um frontend simples.

| | |
|---|---|
| **Stack** | Java 21 · Spring Boot 4.1 (Spring MVC, Data JPA, Validation) · Hibernate 7 · H2 em memória · Flyway · springdoc-openapi (Swagger UI) |
| **Testes** | JUnit 6 · AssertJ · Mockito · MockMvc (unitários, integração e concorrência) |
| **Frontend** | HTML + CSS + JavaScript puro, servido pela própria aplicação (sem build) |

---

## 1. Como executar

### Pré-requisitos
- **JDK 21** (`java -version`).
- Não é preciso instalar Maven: o projeto inclui o Maven Wrapper (`mvnw` / `mvnw.cmd`).

### Rodando a aplicação

```bash
# Linux / macOS / WSL
./mvnw spring-boot:run

# Windows (PowerShell ou CMD)
mvnw.cmd spring-boot:run
```

Ou gerando o jar:

```bash
./mvnw clean package
java -jar target/armazem-ingredientes-1.0.0.jar
```

Ou com Docker:

```bash
docker build -t armazem-ingredientes .
docker run --rm -p 8080:8080 armazem-ingredientes
```

> A imagem sobe com o perfil Spring `docker` (`application-docker.yml`), que libera o Console H2 para
> acesso pelo navegador do host. Dentro do container a requisição não chega como `localhost`, e sem
> isso o console responderia "remote connections ('webAllowOthers') are disabled". Fora do Docker o
> console continua restrito a `localhost`.
>
> O formulário de login do console já vem preenchido com `jdbc:h2:mem:armazem` (via
> `docker/h2.server.properties`). Fora do Docker o H2 sugere `jdbc:h2:~/test`, que não é o banco da
> aplicação: troque a **JDBC URL** para `jdbc:h2:mem:armazem` (usuário `sa`, senha vazia).

### Endereços (porta padrão 8080)

| O quê | URL |
|---|---|
| Frontend | http://localhost:8080/ |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Console H2 | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:armazem`, usuário `sa`, senha vazia) |

> O banco é em memória: os dados são recriados a cada inicialização. Os compartimentos C1 a C5 são
> criados pela migração Flyway `V1__criar_estrutura_armazem.sql`.

### Rodando os testes

```bash
./mvnw test
```

---

## 2. Endpoints

| Método | Rota | Descrição | Sucesso |
|---|---|---|---|
| `POST` | `/ingredientes` | Cadastra um ingrediente num compartimento (gera **ENTRADA**) | `201` (novo) / `200` (somou a um existente) |
| `GET` | `/ingredientes` | Lista ingredientes. Filtros opcionais: `tipo`, `compartimentoId`, `comEstoque=true` | `200` |
| `GET` | `/ingredientes/{id}` | Busca por id | `200` |
| `POST` | `/ingredientes/{id}/entradas` | Reposição de um ingrediente existente (gera **ENTRADA**) | `200` |
| `POST` | `/ingredientes/{id}/saidas` | Retirada/venda (gera **SAIDA**) | `200` |
| `GET` | `/ingredientes/volume` | Volume total armazenado por tipo | `200` |
| `GET` | `/compartimentos` | Situação de C1 a C5 (tipo, ocupação, espaço livre) | `200` |
| `GET` | `/compartimentos/{id}` | Situação de um compartimento | `200` |
| `GET` | `/compartimentos/disponiveis?quantidade=&tipo=` | Compartimentos que comportam a quantidade do tipo agora | `200` |
| `GET` | `/compartimentos/disponiveis-para-venda?tipo=` | Compartimentos com saldo > 0 do tipo | `200` |
| `GET` | `/historico?sortBy=date\|compartimento&order=asc\|desc` | Histórico paginado (`page`, `size`) com filtros `tipoOperacao` e `compartimentoId` | `200` |

### Exemplos (curl)

```bash
# Cadastro (payload do enunciado)
curl -i -X POST localhost:8080/ingredientes -H 'Content-Type: application/json' -d '{
  "nome": "Farinha de Trigo", "tipo": "SECOS", "quantidade": 400,
  "compartimentoId": 1, "responsavel": "pedro"}'

# Repetir o mesmo cadastro -> 400: capacidade excedida (400 + 400 > 600 kg)

# Saída / venda
curl -X POST localhost:8080/ingredientes/1/saidas -H 'Content-Type: application/json' \
  -d '{"quantidade": 150, "responsavel": "ana"}'

# Volume por tipo
curl localhost:8080/ingredientes/volume
# [{"type":"LIQUIDO","totalQuantity":0.000},{"type":"REFRIGERADO","totalQuantity":0.000},{"type":"SECO","totalQuantity":250.000}]

# Onde cabem 100 kg de secos?
curl 'localhost:8080/compartimentos/disponiveis?quantidade=100&tipo=SECO'

# De onde posso vender secos?
curl 'localhost:8080/compartimentos/disponiveis-para-venda?tipo=SECO'

# Histórico por compartimento, crescente
curl 'localhost:8080/historico?sortBy=compartimento&order=asc'
```

### Formato de erro (RFC 9457, `application/problem+json`)

```json
{
  "title": "Regra de negócio violada",
  "status": 400,
  "detail": "Capacidade excedida no compartimento C1: a capacidade máxima para SECO é 600 kg, já há 400 kg armazenados (espaço livre: 200 kg) e foram solicitados 400 kg.",
  "instance": "/ingredientes",
  "codigo": "CAPACIDADE_EXCEDIDA",
  "timestamp": "2026-11-17T14:34:43.410-03:00"
}
```

Erros de validação trazem também `"erros": [{"campo": "quantidade", "mensagem": "deve ser maior que zero"}]`.

| HTTP | `codigo` | Quando |
|---|---|---|
| 400 | `CAPACIDADE_EXCEDIDA` | A entrada passa da capacidade do tipo no compartimento |
| 400 | `TIPO_INCOMPATIVEL` | O compartimento contém outro tipo de ingrediente |
| 400 | `TROCA_DE_TIPO_BLOQUEADA` | O compartimento foi usado por outro tipo hoje (só aceita outro tipo amanhã) |
| 400 | `ESTOQUE_INSUFICIENTE` | Saída maior que o saldo |
| 400 | `COMPARTIMENTO_INEXISTENTE` | `compartimentoId` do corpo não existe |
| 400 | `VALIDACAO` / `REQUISICAO_INVALIDA` / `PARAMETRO_INVALIDO` / `QUANTIDADE_INVALIDA` | Campos inválidos, JSON malformado, enum ou parâmetro inválido, `page` × `size` acima do limite do histórico |
| 404 | `INGREDIENTE_NAO_ENCONTRADO` / `COMPARTIMENTO_NAO_ENCONTRADO` | Id da URL não existe |
| 404 | `RECURSO_NAO_ENCONTRADO` | Rota inexistente |
| 405 | `METODO_NAO_SUPORTADO` | Método HTTP não permitido no recurso |
| 406 | `FORMATO_NAO_ACEITO` | `Accept` pede um formato que a API não gera (só JSON) |
| 409 | `CONFLITO_DE_CONCORRENCIA` | Timeout de lock ou conflito de versão (o cliente pode repetir) |
| 409 | `VIOLACAO_DE_INTEGRIDADE` | Restrição do banco violada (ex.: registro duplicado), segunda barreira atrás das regras do domínio |
| 415 | `TIPO_DE_CONTEUDO_NAO_SUPORTADO` | `Content-Type` do corpo diferente de `application/json` |
| 500 | `ERRO_INTERNO` | Erro inesperado (detalhe só no log) |

---

## 3. Regras de negócio e como foram interpretadas

| Regra do enunciado | Implementação |
|---|---|
| 5 compartimentos C1 a C5 | Criados pela migração Flyway (ids 1 a 5). |
| Capacidade: 600 kg secos, 500 L líquidos, 400 kg refrigerados | A capacidade vem do **tipo armazenado** (`TipoIngrediente.capacidadeMaxima`). O compartimento é genérico e assume o tipo na primeira entrada. |
| Um tipo por compartimento, sem misturar no mesmo período | Entrada de outro tipo em compartimento não vazio → `TIPO_INCOMPATIVEL`. |
| "Armazenou secos hoje → outro tipo só amanhã" | Se o compartimento **foi movimentado hoje** com o tipo X, ele só aceita outro tipo no **dia seguinte**, mesmo que esteja vazio (`TROCA_DE_TIPO_BLOQUEADA`). Ver decisão D1. |
| Só armazenar se houver espaço suficiente | `saldo + quantidade <= capacidade` → senão `CAPACIDADE_EXCEDIDA`. |
| Histórico com data/hora, operação, quantidade, tipo e responsável | Entidade `Movimentacao` imutável, que também guarda ingrediente, compartimento e saldo do compartimento após a operação. |
| Não existe entrada/saída sem histórico | Garantido pelo domínio e pela transação (seção 5.2). |
| Violação de tipo ou capacidade → 400 com mensagem clara | `RegraNegocioException` → `400` + `detail` explicativo + `codigo` estável. |

### Decisões sobre pontos ambíguos

- **D1. O que é "mesmo período"?** Interpretei como **dia civil** no fuso `America/Sao_Paulo`
  (configurável em `armazem.fuso-horario`), seguindo o exemplo do enunciado ("hoje" / "amanhã"). Apliquei a
  regra a **todos os tipos**, não só aos secos, porque a regra geral ("não pode misturar tipos diferentes no
  mesmo período") vale para qualquer tipo; o caso dos secos é o exemplo dado. "Armazenou hoje" significa
  que o compartimento **conteve** aquele tipo em algum momento do dia, então uma **saída** também conta:
  se C1 guardava secos desde ontem e foi esvaziado hoje, ele só aceita outro tipo amanhã. O mesmo tipo
  continua aceito a qualquer momento. O "hoje" vem de um `Clock` injetado, e os testes simulam a virada do
  dia (23:59 → 00:01).
- **D2. `"SECOS"` x `"SECO"`.** O enunciado usa `SECOS` no cadastro e `SECO` na resposta de volume. A API
  **aceita singular ou plural, com ou sem acento, em qualquer caixa** (`SECOS`, `seco`, `Líquidos`...) e
  **responde sempre no singular canônico** (`SECO`, `LIQUIDO`, `REFRIGERADO`), igual ao exemplo de volume.
  Vale para o JSON e para query params.
- **D3. O que é um "ingrediente"?** É o estoque de um *nome + tipo* dentro de um compartimento.
  Repetir o `POST /ingredientes` com o mesmo nome (sem diferenciar caixa e acentos) no mesmo compartimento
  **soma a quantidade** ao registro existente e responde `200`. Um ingrediente novo responde `201` + `Location`.
  Os dois casos geram uma ENTRADA no histórico.
- **D4. Endpoint de saída.** O enunciado pede "movimentação" e "compartimentos disponíveis para
  retirada/venda", mas não define a rota da saída. Criei `POST /ingredientes/{id}/saidas` e, por simetria,
  `POST /ingredientes/{id}/entradas` para reposição. Só as operações que alteram estoque usam `POST`; as
  consultas são `GET`. A saída é `POST`, e não `DELETE`, por quatro motivos:
  - **Saída não apaga, diminui.** `DELETE` significa "este recurso deixa de existir". Uma saída de 10 kg num
    estoque de 100 kg só baixa o saldo para 90 kg. Com saldo zero o ingrediente continua cadastrado (por isso
    existe o filtro `comEstoque=true`).
  - **Cada saída cria um registro novo:** uma movimentação `SAIDA` no histórico. Criar recurso é papel do
    `POST`; a rota se lê como "crie uma nova saída para o ingrediente {id}". É como um saque no banco: não
    fecha a conta, vira uma nova linha no extrato.
  - **`DELETE` é idempotente; a saída não.** Repetir um `DELETE` tem o mesmo efeito de enviá-lo uma vez. Duas
    saídas de 10 kg tiram 20 kg. O `POST` é o método que não promete idempotência.
  - **A saída precisa de corpo** (`quantidade`, `responsavel`), e o HTTP não define semântica para corpo em
    `DELETE`; proxies e clientes podem descartá-lo.

  Também descartei `PUT`/`PATCH` com o novo saldo (`{"quantidade": 90}`): o cliente teria que calcular o
  saldo, e duas vendas simultâneas sobrescreveriam uma à outra. Com o `POST`, a subtração acontece no
  servidor, com o compartimento travado (seção 5.1). Além disso, "agora é 90" não diz se foi uma saída ou uma
  correção, e o histórico precisa dessa informação. Um `DELETE /ingredientes/{id}` só faria sentido para
  remover um cadastro feito por engano, o que o enunciado não pede. Como esses `POST` não são idempotentes, uma
  retentativa do cliente pode duplicar a operação; a seção 8 traz a chave de idempotência como evolução.
- **D5. `compartimentoId` inexistente no corpo** responde `400`, não `404`: a URL (`/ingredientes`) existe;
  o problema é um dado inválido na requisição. `404` fica para ids na própria URL.
- **D6. Histórico.** `date` ordena pela **ordem de gravação** (o `id` da movimentação), não pelo horário
  gravado: o relógio do sistema pode voltar (NTP, VM, WSL) e inverter operações que aconteceram em
  sequência, deixando uma saída "mais antiga" que a entrada que a precede. `compartimento` ordena por
  compartimento e, dentro dele, pela ordem de gravação. `order` vale para ambos. Padrão: `date desc`
  (mais recentes primeiro). Como o histórico só cresce, a resposta é **paginada** (`page`, `size`, máx. 500)
  no formato padrão do Spring Data (`content` + `page`). Se `page` × `size` passa de 2.147.483.647, a
  resposta é `400` (`PARAMETRO_INVALIDO`).
- **D7. Quantidades** são `BigDecimal` com 3 casas (gramas/mililitros), sem erro de ponto flutuante. Mais de
  3 casas decimais é rejeitado.
- **D8. Volume por tipo** mantém exatamente o contrato do enunciado (`type`, `totalQuantity`) e sempre
  devolve os 3 tipos, com zero quando não há estoque.

---

## 4. Arquitetura

```
br.com.vega.armazem
├── config/              Clock (fuso), conversores de query param, OpenAPI, propriedades
├── domain/
│   ├── model/           Entidades com regras: Compartimento, Ingrediente, Movimentacao, enums
│   └── exception/       CodigoErro + exceções de negócio (400 / 404)
├── repository/          Spring Data JPA (inclui a query com lock pessimista)
├── service/             EstoqueService (único ponto de escrita) + serviços de consulta
├── dto/                 Records de request/response (contrato da API)
└── web/
    ├── controller/      @RestController: Ingredientes, Compartimentos, Histórico
    └── error/           @RestControllerAdvice → ProblemDetail (RFC 9457)
src/main/resources
├── db/migration/        Flyway (schema + seed C1 a C5, com CHECK constraints)
├── static/              Frontend (index.html, css, js)
└── messages.properties  Tradução das mensagens de erro geradas pelo Spring
```

**Responsabilidades**

- **Domínio rico.** As regras ficam nas entidades (`Compartimento.receber/retirar`, `Ingrediente.retirar`),
  que não dependem de Spring. Os métodos que alteram estoque são *package-private*. O único caminho público
  é `Movimentacao.entrada(...)` / `Movimentacao.saida(...)`, que valida, aplica e **devolve o registro de
  histórico**.
- **Serviços.** `EstoqueService` (`@Transactional`) é o único ponto de escrita: trava o compartimento,
  chama o domínio e grava estoque e histórico juntos. Os serviços de consulta são
  `@Transactional(readOnly = true)` e devolvem DTOs, sem expor entidades nem depender de *open-in-view*
  (desligado).
- **Controllers** ficam finos: validação (`@Valid`, Bean Validation), status HTTP, `Location` e
  documentação OpenAPI.
- **Erros** ficam centralizados num `@RestControllerAdvice` que estende `ResponseEntityExceptionHandler`.
  O formato é padronizado (ProblemDetail), com `codigo` estável para máquinas e `detail` claro para pessoas.
- **Schema** fica no Flyway (`ddl-auto=validate`), com `CHECK` constraints no banco (quantidade ≥ 0,
  tipos válidos) como segunda barreira.

---

## 5. Concorrência, consistência e desempenho

### 5.1 Transações simultâneas de entrada/saída

- Cada operação começa com `SELECT ... FOR UPDATE` no **compartimento**
  (`@Lock(PESSIMISTIC_WRITE)` em `CompartimentoRepository.findByIdParaAtualizacao`).
  - Operações no **mesmo** compartimento ficam serializadas. Assim, duas saídas simultâneas nunca deixam
    saldo negativo, duas entradas nunca estouram a capacidade e dois tipos diferentes nunca entram juntos
    num compartimento vazio.
  - Operações em compartimentos **diferentes** seguem em paralelo, porque o lock é por linha e não global.
- Ordem de leitura para evitar dado desatualizado: primeiro busca só o id do compartimento (sem carregar o
  ingrediente), depois adquire o lock e só então lê o ingrediente.
- `@Version` (lock otimista) em `Compartimento` e `Ingrediente` é uma **segunda barreira**. Se o lock
  pessimista fosse removido, um conflito viraria erro (`409`), nunca uma atualização perdida.
- Timeout de lock (`LOCK_TIMEOUT` do H2) e conflitos de versão → `409 CONFLITO_DE_CONCORRENCIA`.

O teste `ConcorrenciaEstoqueTest` dispara de 20 a 90 operações ao mesmo tempo (pool de até 32 threads,
liberadas juntas por um `CountDownLatch`) e confere:
- **20 saídas** de 10 kg num estoque de 100 kg → exatamente 10 sucessos, saldo 0.
- **20 entradas** de 50 kg em C1 → exatamente 12 sucessos (600 kg) e um único registro de ingrediente.
- **10 threads de SECO + 10 de LIQUIDO** num compartimento vazio → só um tipo vence.
- **Carga mista** em 3 compartimentos → para cada um, `saldo = Σ ingredientes = Σ entradas − Σ saídas`,
  com `0 ≤ saldo ≤ capacidade` e um registro de histórico por operação bem-sucedida.

Fiz uma validação negativa: **sem o `@Lock`, os 4 testes falham**. Ou seja, eles detectam de fato a ausência
do controle de concorrência.

### 5.2 "Não existe entrada/saída sem histórico"

1. **Pelo domínio:** só `Movimentacao.entrada/saida` alteram estoque, e elas sempre devolvem a movimentação.
2. **Pela transação:** estoque e histórico são gravados na mesma transação. Se gravar o histórico falhar,
   tudo é desfeito. Há um teste que força a falha do `save` do histórico e confere que o estoque não mudou.

### 5.3 Cálculo eficiente de espaço disponível

- Cada compartimento mantém seu **saldo total desnormalizado** (`quantidade_atual`), atualizado na mesma
  transação. O espaço livre sai em **O(1)** por compartimento, sem somar ingredientes ou histórico.
- `GET /compartimentos/disponiveis` é **uma única consulta** que aplica a regra no banco e já ordena por
  **melhor encaixe**: primeiro os compartimentos do mesmo tipo que ficariam mais cheios, depois os vazios.
  Isso reduz a fragmentação e preserva compartimentos vazios para outros tipos. Se a quantidade passa da
  capacidade do tipo, a resposta é imediata, sem ir ao banco.
- Um teste de consistência garante que essa consulta e a regra do domínio (`Compartimento.podeReceber`)
  dão sempre o mesmo resultado.
- `GET /ingredientes/volume` agrega os 5 saldos dos compartimentos, não a tabela de ingredientes.
- O histórico é paginado e ordenado pela chave primária `id` (decisão D6); o filtro por compartimento usa o
  índice `compartimento_id + data_hora`. Listagens usam `join fetch` ou a FK direta, sem N+1.

---

## 6. Testes

```bash
./mvnw test
```

| Tipo | Classe(s) | O que cobre |
|---|---|---|
| Unitário (domínio) | `CompartimentoTest`, `MovimentacaoTest`, `TipoIngredienteTest` | Capacidade por tipo (limites exatos), mistura de tipos, troca de tipo no dia seguinte, estoque insuficiente, ausência de alteração parcial, leitura tolerante de enums |
| Unitário (serviço) | `EstoqueServiceTest`, `OrdenacaoHistoricoTest` | Fluxo de cadastro/entrada/saída com mocks, uso obrigatório do lock, ordenação do histórico |
| Integração (API) | `IngredienteApiTest`, `CompartimentoApiTest`, `HistoricoApiTest` | Todos os endpoints via MockMvc: status, corpo, `Location`, erros RFC 9457, virada do dia, relógio que volta no tempo |
| Integração (bordas) | `BordasDaApiTest` | Erros do próprio Spring (404 de rota, 405, 406, 415) com `codigo`, paginação extrema, id fracionário, espaço não separável e nomes não latinos |
| Integração (garantias) | `HistoricoObrigatorioTest`, `DisponibilidadeConsistenciaTest` | Rollback quando o histórico falha; consulta de disponibilidade = regra do domínio |
| Concorrência | `ConcorrenciaEstoqueTest` | Seção 5.1 |

São **332 testes** e a suíte completa roda em cerca de 11 segundos. Os testes de integração usam o contexto Spring completo, H2 com as migrações Flyway e um relógio
controlável (`RelogioAjustavel`). Eles **não** usam `@Transactional`, para exercitar commit e lock reais.

---

## 7. Frontend

Disponível em http://localhost:8080/, sem dependências externas:
- painel dos compartimentos (tipo, barra de ocupação, espaço livre, se aceita outro tipo hoje); clicar
  num card filtra os ingredientes;
- volume por tipo;
- cadastro com botão **"Sugerir compartimentos"** (usa `/compartimentos/disponiveis`);
- tabela de ingredientes com ações de **Entrada** e **Saída**;
- consulta de disponíveis para venda;
- histórico com ordenação, filtros e paginação.

Os erros da API aparecem com a mensagem do `detail`. Textos digitados pelo usuário são inseridos só via
`textContent`, o que evita XSS.

---

## 8. Possíveis evoluções

- Autenticação e autorização (ex.: Spring Security + JWT). Hoje o `responsavel` é informado no corpo, e o
  ideal seria obtê-lo do usuário autenticado.
- Lotes com data de validade e saída FIFO/FEFO.
- Perfil para MySQL/PostgreSQL com Testcontainers. O lock pessimista e as migrações já são portáveis.
- Chave de idempotência nos `POST` de movimentação, para retentativas seguras do cliente após um `409`.
- Observabilidade com Actuator, métricas de movimentação e tracing.
