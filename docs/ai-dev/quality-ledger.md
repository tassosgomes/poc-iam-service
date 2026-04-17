## [2026-04-16] | PRD: prd-authz-platform | Task: 1.0

Modelo utilizado:
(Preenchido pelo Orquestrador)

### Problemas Identificados

Zero Defects Identified
Iterações até estabilização: 1

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Nenhuma.
- Skill: Nenhuma.

## [2026-04-16] | PRD: prd-authz-platform | Task: 2.0

Modelo utilizado:
(Preenchido pelo Orquestrador)

### Problemas Identificados

1. Categoria Técnica: Erro de integração
	Severidade: Alta
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Sim
	Descrição: O issuer público documentado em `infra/docker/.env.example` não é compatível com o rewrite configurado no gateway OIDC, fazendo a variante `/oidc/default/.well-known/openid-configuration` produzir issuer incorreto.

2. Categoria Técnica: Falha de validação
	Severidade: Alta
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Sim
	Descrição: O mock CyberArk não possui seed funcional dos 5 usuários demo; o arquivo `users.json` apenas documenta dados para uso manual no debugger e não é aplicado pelo container.

3. Categoria Técnica: Violação de padrão arquitetural
	Severidade: Alta
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Sim
	Descrição: O gateway não implementa de fato as rotas `/`, `/pap`, `/demo` e `/api`; os blocos permanecem comentados e a raiz retorna texto estático, contrariando o contrato da tarefa.

4. Categoria Técnica: Erro de integração
	Severidade: Média
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Não
	Descrição: O script `infra/docker/bootstrap/seed-modules.http` monta URL incorreta para o health check ao combinar `@baseUrl = http://localhost/api/v1` com `/actuator/health/readiness`.

5. Categoria Técnica: Falha de validação
	Severidade: Média
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Não
	Descrição: O `.env.example` não segue exatamente o contrato da task, usando `AUTHZ_DB_PASSWORD` no lugar de `AUTHZ_DB_PASS`.

6. Categoria Técnica: Violação de padrão arquitetural
	Severidade: Média
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Não
	Descrição: A convenção declarada de `depends_on: condition: service_healthy` não foi aplicada no serviço ativo `nginx-gateway`.

### Resumo da Tarefa

Total de Problemas: 6
Categoria Técnica mais frequente: Erro de integração
Origem mais frequente: Limitação do modelo
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Reforçar explicitamente que rotas comentadas no gateway não satisfazem requisito funcional quando a task pede mapeamento de rotas.
- Skill: Incluir checagem explícita de coerência entre issuer público documentado e rewrites de gateway em tarefas de OIDC local.

## [2026-04-16] | PRD: prd-authz-platform | Task: 2.0

Modelo utilizado:
(Preenchido pelo Orquestrador)

### Problemas Identificados

1. Categoria Técnica: Erro de integração
	Severidade: Alta
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Não
	Descrição: O discovery público expõe issuer `http://localhost/default`, coerente com `infra/docker/.env.example`, mas o placeholder do App Shell em `infra/docker/docker-compose.yml` ainda aponta para `http://localhost/oidc/default`. O contrato OIDC continua inconsistente para o consumidor quando o serviço for habilitado.

2. Categoria Técnica: Violação de padrão arquitetural
	Severidade: Média
	Fase Detectada: Revisão
	Origem Provável: Limitação do modelo
	Necessitou Reimplementação Significativa? Não
	Descrição: O serviço ativo `nginx-gateway` mantém `depends_on` simples para `cyberark-mock`, sem `condition: service_healthy`, contrariando a convenção declarada na própria task 2.0.

### Resumo da Tarefa

Total de Problemas: 2
Categoria Técnica mais frequente: Erro de integração
Origem mais frequente: Limitação do modelo
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Explicitar que placeholders comentados também devem permanecer coerentes com o contrato público efetivo quando servem de referência para tarefas subsequentes.
- Skill: Incluir validação específica de consistência entre issuer OIDC discoverable, URL pública documentada e authority configurada em consumidores web.

## [2026-04-16] | PRD: prd-authz-platform | Task: 2.0

Modelo utilizado:
(Preenchido pelo Orquestrador)

### Problemas Identificados

Zero Defects Identified
Iterações até estabilização: 1

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Nenhuma.
- Skill: Nenhuma.

## [2026-04-16] | PRD: prd-authz-platform | Task: 2.0

Modelo utilizado:
GPT-5.4

### Problemas Identificados

Zero Defects Identified
Iterações até estabilização: 1

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Nenhuma.
- Skill: Nenhuma.

## [2026-04-17] | PRD: prd-authz-platform | Task: 3.0

Modelo utilizado:
GPT-5.4

### Problemas Identificados

Zero Defects Identified
Iterações até estabilização: 1

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Nenhuma.
- Skill: Nenhuma.

## [2026-04-17] | PRD: prd-authz-platform | Task: 4.0

Modelo utilizado:
GPT-5.4

### Problemas Identificados

1. Categoria Técnica: Violação de padrão arquitetural
   Severidade: Baixa
   Fase Detectada: Revisão
   Origem Provável: Lacuna na TechSpec
   Necessitou Reimplementação Significativa? Não
   Descrição: A TechSpec define o sync de catálogo com idempotência baseada em `payload hash`, mas o § Modelos de Dados não prevê campo persistente explícito para armazenar esse hash por módulo. Isso não afeta a correção da task 4.0, porém tende a exigir ajuste estrutural antes da task 7. Trecho afetado: `tasks/prd-authz-platform/techspec.md` nas seções de interfaces de sync e modelos de dados.

### Resumo da Tarefa

Total de Problemas: 1
Categoria Técnica mais frequente: Violação de padrão arquitetural
Origem mais frequente: Lacuna na TechSpec
Indício de fragilidade estrutural? (Sim/Não) Sim
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Adicionar campo persistente para `payload hash`/versão de catálogo por módulo ou documentar explicitamente a estratégia stateless de idempotência.
- Template de Task: Incluir alerta para registrar dependências de dados implícitas quando o requisito funcional depender de persistência futura.
- Skill: Incluir checagem explícita de coerência entre contratos de idempotência e modelo de dados em revisões de schema.

## [2026-04-17] | PRD: prd-authz-platform | Task: 9.0

Modelo utilizado:
GPT-5.4

### Problemas Identificados

1. Categoria Técnica: Lógica incorreta
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Limitação do modelo
   Necessitou Reimplementação Significativa? Não
   Descrição: Os fluxos `CreateRoleHandler`, `UpdateRoleHandler` e `CloneRoleHandler` validam unicidade do nome apenas com `existsByModuleIdAndName(...)` antes do `save(...)`. Em concorrência, duas requisições podem passar na pré-validação e uma delas cair na constraint única `uq_role_module_name`, resultando em `DataIntegrityViolationException` tratada como 500 pelo `GlobalExceptionHandler` em vez de 409 `role-conflict`. Isso quebra o contrato esperado do CRUD de roles sob carga simultânea.

### Resumo da Tarefa

Total de Problemas: 1
Categoria Técnica mais frequente: Lógica incorreta
Origem mais frequente: Limitação do modelo
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Explicitar em tasks de CRUD que conflitos de unicidade devem ser tratados também no caminho de persistência, não só por pré-validação.
- Skill: Incluir checagem explícita de cenários TOCTOU/race condition quando o código depender de `exists...` + `save` com constraint única.

---

## 2026-04-17 | PRD: prd-authz-platform | Task: 05

### Problemas Identificados

1. Categoria Técnica: Teste inadequado
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Contexto insuficiente
   Necessitou Reimplementação Significativa? Não
   Descrição: `ModuleAdminIntegrationTest` possui dois testes que compartilham os mesmos dados de fixture (`name="Sales"`, `allowedPrefix="sales"`) sem isolamento de estado entre eles. Num Testcontainers com container estático, a execução do primeiro teste contamina o banco para o segundo, podendo causar falha não-determinística dependendo da ordem de execução do JUnit 5. Correção: usar fixtures com nomes únicos por teste ou adicionar limpeza `@BeforeEach` via JDBC.

2. Categoria Técnica: Overengineering
   Severidade: Baixa
   Fase Detectada: Revisão
   Origem Provável: Limitação do modelo
   Necessitou Reimplementação Significativa? Não
   Descrição: Método `generateSecret()` duplicado identicamente em `CreateModuleHandler` e `RotateKeyHandler`. Deveria ser extraído para um componente ou utilitário compartilhado, seguindo o princípio DRY.

### Resumo da Tarefa

Total de Problemas: 2
Categoria Técnica mais frequente: Teste inadequado
Origem mais frequente: Contexto insuficiente
Indício de fragilidade estrutural? Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Incluir padrão explícito de isolamento de estado para testes de integração com Testcontainers (ex.: fixtures com nomes gerados dinamicamente ou cleanup `@BeforeEach`).
- Template de Task: Adicionar critério explícito de isolamento de testes de integração nas subtarefas de teste (5.8).

## [2026-04-17] | PRD: prd-authz-platform | Task: 05

Modelo utilizado:
GPT-5.4

### Problemas Identificados

1. Categoria Técnica: Erro de integração
   Severidade: Alta
   Fase Detectada: Revisão
   Origem Provável: Task mal fragmentada
   Necessitou Reimplementação Significativa? Não
   Descrição: A implementação da task 05 não registra evento de auditoria na criação e na rotação de chave, embora o PRD (`tasks/prd-authz-platform/prd.md`, RF-13/RF-17) e a TechSpec (`tasks/prd-authz-platform/techspec.md`, seção `RecordAuditEvent`) exijam esse comportamento. O código atual em `CreateModuleHandler` e `RotateKeyHandler` registra apenas logs INFO.

2. Categoria Técnica: Teste inadequado
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Contexto insuficiente
   Necessitou Reimplementação Significativa? Não
   Descrição: `ModuleAdminIntegrationTest` não cobre cenários negativos de acesso administrativo (`401` sem autenticação e `403` sem role `PLATFORM_ADMIN`), deixando sem proteção de regressão um requisito explícito de autorização placeholder das rotas admin.

### Resumo da Tarefa

Total de Problemas: 2
Categoria Técnica mais frequente: Erro de integração
Origem mais frequente: Task mal fragmentada
Indício de fragilidade estrutural? (Sim/Não) Sim
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Explicitar dependências obrigatórias com auditoria quando o requisito funcional do PRD já exigir emissão de evento na mesma entrega.
- Skill: Incluir checagem explícita de rastreabilidade entre requisitos de auditoria do PRD/TechSpec e implementações de handlers de escrita.

## [2026-04-17] | PRD: prd-authz-platform | Task: 05

Modelo utilizado:
GPT-5.4

### Problemas Identificados

1. Categoria Técnica: Teste inadequado
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Contexto insuficiente
   Necessitou Reimplementação Significativa? (Sim/Não) Não
   Descrição: `ModuleAdminIntegrationTest` continua compartilhando fixtures (`Sales/sales` e `Support/support`) entre múltiplos testes sobre o mesmo banco do container estático, sem cleanup ou dados únicos por caso. Isso mantém a suíte dependente da ordem de execução e potencialmente flakey quando Docker/Testcontainers estiver funcional.

### Resumo da Tarefa

Total de Problemas: 1
Categoria Técnica mais frequente: Teste inadequado
Origem mais frequente: Contexto insuficiente
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Incluir orientação explícita de isolamento de dados em testes de integração com banco compartilhado.
- Template de Task: Reforçar critério de isolamento/cleanup para suites com Testcontainers e container estático.
- Skill: Incluir checagem explícita de reuso de fixtures entre testes de integração stateful.

## [2026-04-17] | PRD: prd-authz-platform | Task: 05 (revisão final)

Modelo utilizado:
Claude Sonnet 4.5

### Problemas Identificados

Zero Defects Identified

Iterações até estabilização: 3
Todas as issues das revisões anteriores foram resolvidas:
- Auditoria (MODULE_CREATED / KEY_ROTATED) integrada nos dois handlers de escrita
- Cenários 401/403 cobertos em ModuleAdminIntegrationTest
- Isolamento de estado resolvido com uniqueSuffix() baseado em UUID

Observação não-bloqueante registrada:
- generateSecret() e hashPayload() duplicados entre CreateModuleHandler e RotateKeyHandler (candidatos a extração futura, baixa severidade)

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Incluir orientação explícita de isolamento de dados em testes de integração com Testcontainers (apontado na iteração 2 — confirmar se já incorporado).
- Template de Task: Subtarefa 5.8 poderia incluir critério explícito de "dados únicos por caso" para suites com container estático.

---

## [2026-04-17] | PRD: prd-authz-platform | Task: 6.0

Modelo utilizado:
(Preenchido pelo Orquestrador)

### Problemas Identificados

1. Categoria Técnica: Teste inadequado
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Task mal fragmentada
   Necessitou Reimplementação Significativa? Não
   Descrição: `ValidateModuleKeyServiceTest` não cobre o branch em que `candidateKeys` é não-vazio mas nenhum hash bate com o segredo, resultando em `UnauthorizedModuleKeyException("invalid")`. O caso "secret incorreto para módulo válido" não foi listado explicitamente na subtarefa 6.7.

2. Categoria Técnica: Violação de padrão arquitetural
   Severidade: Baixa
   Fase Detectada: Revisão
   Origem Provável: Lacuna na TechSpec
   Necessitou Reimplementação Significativa? Não
   Descrição: A TechSpec define a métrica como `authz_module_key_invalid_total{module}`, mas a task 6.6 e o código implementado usam `authz_module_key_invalid_total{reason}`. Trecho afetado: linha da TechSpec que descreve a métrica customizada.

3. Categoria Técnica: Teste inadequado
   Severidade: Baixa
   Fase Detectada: Revisão
   Origem Provável: Task mal fragmentada
   Necessitou Reimplementação Significativa? Não
   Descrição: `PermissionPrefixValidator` foi criado sem cobertura de teste unitário. Possui edge cases (prefix vazio, código exato igual ao prefix sem ponto). Deve ser coberto na Task 7.0.

### Resumo da Tarefa

Total de Problemas: 3
Categoria Técnica mais frequente: Teste inadequado
Origem mais frequente: Task mal fragmentada
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Corrigir o label da métrica de `{module}` para `{reason}` no trecho que define `authz_module_key_invalid_total`.
- Template de Task: Ao listar cenários de teste em subtarefas de autenticação, incluir explicitamente o caso "credencial inválida para entidade existente" (wrong secret, known entity), que é o cenário de ataque primário e costuma ser omitido quando a atenção vai para casos de "não encontrado".
- Skill: Nenhuma.

## [2026-04-17] | PRD: prd-authz-platform | Task: 7.0

Modelo utilizado:
(Preenchido pelo Orquestrador)

### Problemas Identificados

1. Categoria Técnica: Erro de integração
   Severidade: Alta
   Fase Detectada: Revisão
   Origem Provável: Task mal fragmentada
   Necessitou Reimplementação Significativa? Não
   Descrição: Há inconsistência contratual entre as Tasks 5.0 e 7.0. `CreateModuleRequest` e `Module` aceitam `allowedPrefix` com hífen (`^[a-z][a-z0-9-]{1,30}$`), mas `PermissionDeclaration.code` exige primeiro segmento sem hífen (`^[a-z][a-z0-9_]{0,30}(\\.[a-z][a-z0-9_]{0,30}){2,}$`). Assim, módulos válidos como `sales-abc12345` não conseguem sincronizar permissões como `sales-abc12345.orders.create`, pois a requisição falha com 422 antes do `PermissionPrefixValidator`. Trechos afetados: `apps/authz-service/src/main/java/com/platform/authz/modules/api/dto/CreateModuleRequest.java`, `apps/authz-service/src/main/java/com/platform/authz/modules/domain/Module.java`, `apps/authz-service/src/main/java/com/platform/authz/catalog/api/dto/PermissionDeclaration.java` e os testes de integração que usam prefixos com hífen e ficaram mascarados por skip de Testcontainers.

### Resumo da Tarefa

Total de Problemas: 1
Categoria Técnica mais frequente: Erro de integração
Origem mais frequente: Task mal fragmentada
Indício de fragilidade estrutural? (Sim/Não) Sim
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Explicitar de forma única se `allowedPrefix` pode ou não conter hífen e alinhar a regex de permissões ao contrato escolhido.
- Template de Task: Adicionar checagem explícita de compatibilidade entre regex de criação do módulo e regex do catálogo quando houver binding por prefixo.
- Skill: Incluir validação explícita de consistência entre convenções de naming e regex cruzadas em revisões Java/REST.

## [2026-04-17] | PRD: prd-authz-platform | Task: 7.0

Modelo utilizado:
GPT-5.4

### Problemas Identificados

Zero Defects Identified
Iterações até estabilização: 1

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Nenhuma.
- Skill: Nenhuma.

## [2026-04-17] | PRD: prd-authz-platform | Task: 8.0

Modelo utilizado:
GPT-5.4

### Problemas Identificados

1. Categoria Técnica: Teste inadequado
   Severidade: Crítica
   Fase Detectada: Revisão
   Origem Provável: Limitação do modelo
   Necessitou Reimplementação Significativa? Não
   Descrição: `UserSearchIntegrationTest` contém uma asserção inconsistente no cenário `searchUsers_WithScopedManagerToken_ShouldFilterModulesByScope`. O teste espera 2 usuários, mas a lógica implementada mantém também `user-multi` com `modules=["vendas"]`, totalizando 3 resultados.

2. Categoria Técnica: Violação de padrão arquitetural
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Contexto insuficiente
   Necessitou Reimplementação Significativa? Não
   Descrição: `UserSearchService` depende diretamente da classe concreta `CyberArkUserSearchClient` na camada `infra`, contrariando a diretriz de port/adapter e a dependência da camada `application` apenas de abstrações internas.

3. Categoria Técnica: Violação de padrão arquitetural
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Contexto insuficiente
   Necessitou Reimplementação Significativa? Não
   Descrição: `UserSearchService` usa e retorna `UserSummaryDto`, tipo definido na camada `api`, invertendo a direção de dependência entre `api` e `application`.

4. Categoria Técnica: Violação de padrão arquitetural
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Contexto insuficiente
   Necessitou Reimplementação Significativa? Não
   Descrição: `CyberArkUnavailableException` modela falha de integração externa como `DomainException`, misturando semânticas de domínio e infraestrutura.

5. Categoria Técnica: Teste inadequado
   Severidade: Média
   Fase Detectada: Revisão
   Origem Provável: Task mal fragmentada
   Necessitou Reimplementação Significativa? Não
   Descrição: A task não resultou em testes unitários dedicados para `UserSearchService` e `ModuleScopeExtractor`, deixando sem proteção de regressão a lógica central de filtragem por escopo.

### Resumo da Tarefa

Total de Problemas: 5
Categoria Técnica mais frequente: Violação de padrão arquitetural
Origem mais frequente: Contexto insuficiente
Indício de fragilidade estrutural? (Sim/Não) Sim
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Explicitar com exemplo que integrações externas no módulo `iam` devem usar portas/interfaces na camada de aplicação e que DTOs de API não devem atravessar para `application`.
- Template de Task: Adicionar checklist obrigatório para cobertura unitária da regra de negócio quando houver filtragem/autorização além do teste de integração.
- Skill: Reforçar exemplos de adapter externo em Java retornando modelos internos da aplicação e não DTOs da API.

---

## [Re-revisão] 2025 | PRD: authz-platform | Task: 8

### Problemas Identificados

Zero Defects Identified

Todos os 5 problemas apontados na revisão anterior foram corrigidos pelo implementador antes desta re-revisão:
- Teste de integração corrigido (3 usuários, `user-multi` validado explicitamente)
- `UserSearchPort` + `UserSummary` introduzidos (violação de arquitetura resolvida)
- `CyberArkUnavailableException` desacoplada de `DomainException`
- 11 testes unitários adicionados em `UserSearchServiceTest`
- 11 testes unitários adicionados em `ModuleScopeExtractorTest`

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? Não
Sugestão de melhoria: Nenhuma — ciclo de correção eficiente; todos os bloqueadores resolvidos em uma iteração.

## [2026-04-17] | PRD: prd-authz-platform | Task: 8.0

Modelo utilizado:
(Preenchido pelo Orquestrador)

### Problemas Identificados

Zero Defects Identified
Iterações até estabilização: 1

### Resumo da Tarefa

Total de Problemas: 0
Categoria Técnica mais frequente: N/A
Origem mais frequente: N/A
Indício de fragilidade estrutural? (Sim/Não) Não
Sugestão de melhoria no:
- PRD: Nenhuma.
- TechSpec: Nenhuma.
- Template de Task: Nenhuma.
- Skill: Nenhuma.
