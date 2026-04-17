# Resumo de Tarefas de Implementação — AuthZ Platform

## Visão Geral

Plataforma greenfield de autorização para ecossistema MFE + MS, com:
- AuthZ Service centralizado (Java 21 + Spring Boot 3 + Postgres 16) em monolito modular
- Auto-registro de módulos no bootstrap via chave compartilhada (sem CI)
- 3 SDKs (React, Java, .NET) publicados em GitHub Packages
- App Shell + 2 MFEs (PAP UI + demo) com Module Federation/Vite
- 2 MSs demo (Java + .NET) exercitando o fluxo end-to-end
- Stack 100% containerizada via `docker-compose` (K8s fora do escopo do MVP)

Esta lista cobre 31 tarefas principais distribuídas em 6 fases. As fases 3 e 4 (SDK Java e SDK .NET) são paralelizáveis entre si após a Fase 2 estabelecer o contrato de sync.

## Skills de Stack Consultadas

| Skill | Caminho | Influência |
|-------|---------|------------|
| `java-architecture` | `~/.claude/skills/java-architecture` | Camadas Clean Arch, CQRS type-safe, organização por feature/módulo |
| `java-dependency-config` | `~/.claude/skills/java-dependency-config` | Spring Boot 3, JPA + HikariCP, Flyway, Resilience4j, Caffeine |
| `java-code-quality` | `~/.claude/skills/java-code-quality` | Records para DTOs, Optional, sealed classes, Bean Validation |
| `java-testing` | `~/.claude/skills/java-testing` | JUnit 5 + AssertJ + Mockito (AAA), Testcontainers Postgres |
| `java-observability` | `~/.claude/skills/java-observability` | OpenTelemetry, Micrometer, Actuator probes, logs JSON via logback |
| `java-production-readiness` | `~/.claude/skills/java-production-readiness` | Checklist pré-produção, segurança mínima, graceful shutdown |
| `csharp-dotnet-architecture` | `~/.claude/skills/csharp-dotnet-architecture` | CQRS nativo, ProblemDetails, Result Pattern (demo + SDK .NET) |
| `dotnet-dependency-config` | `~/.claude/skills/dotnet-dependency-config` | Pacotes base .NET 8, EF Core (não usado aqui), Polly |
| `dotnet-testing` | `~/.claude/skills/dotnet-testing` | xUnit + AwesomeAssertions + Moq, Testcontainers .NET |
| `dotnet-observability` | `~/.claude/skills/dotnet-observability` | Health Checks, OpenTelemetry .NET |
| `react-architecture` | `~/.claude/skills/react-architecture` | Feature-based folders, path aliases, kebab-case |
| `react-code-quality` | `~/.claude/skills/react-code-quality` | TypeScript strict, hooks tipados, naming |
| `react-testing` | `~/.claude/skills/react-testing` | Vitest + Testing Library + Playwright |
| `common-restful-api` | `~/.claude/skills/common-restful-api` | RFC 9457 ProblemDetails, versionamento via path, kebab-case |
| `common-roles-naming` | `~/.claude/skills/common-roles-naming` | SCREAMING_SNAKE_CASE com prefixo de módulo |
| `common-git-commit` | `~/.claude/skills/common-git-commit` | Padrão de mensagens de commit |
| `playwright-cli` | `~/.claude/skills/playwright-cli` | E2E coverage do RF-14 |

## Fases de Implementação

### Fase 1 — Foundation
Setup do monorepo e ambiente base (docker-compose + cyberark-mock + Postgres). Habilita o restante.

### Fase 2 — AuthZ Service
Backend completo com módulos `modules`, `catalog`, `iam`, `authz`, `audit`. Fim desta fase = API totalmente operável via cURL.

### Fase 3 — SDK Java + Demo MS Java
SDK Java + integração com demo de Vendas. Paralelo à Fase 4.

### Fase 4 — SDK .NET + Demo MS .NET
SDK .NET + integração com demo de Estoque. Paralelo à Fase 3.

### Fase 5 — Frontend (SDK React + App Shell + MFEs)
SDK React, App Shell host MF, PAP UI, Demo MFE.

### Fase 6 — Integração e Release
CI/CD GitHub Actions, publicação dos SDKs em GitHub Packages, suíte E2E Playwright, documentação operacional.

## Tarefas

- [X] 1.0 Setup do monorepo (pnpm + Turborepo + Maven parent + .NET solution)
- [ ] 2.0 Docker Compose base + mock CyberArk + nginx gateway
- [ ] 3.0 AuthZ Service: bootstrap Spring Boot e configurações base
- [ ] 4.0 AuthZ Service: schema Postgres e migrations Flyway
- [ ] 5.0 Módulo `modules`: domain, repositórios e API admin (criar/rotacionar chave)
- [ ] 6.0 Filtro de autenticação por bearer de módulo + GlobalExceptionHandler ProblemDetails
- [ ] 7.0 Módulo `catalog`: endpoint `POST /v1/catalog/sync` idempotente com prefix binding
- [ ] 8.0 Integração CyberArk: validação de JWT e proxy de busca de usuários
- [ ] 9.0 Módulo `iam`: domain Role/UserRole + API CRUD de roles
- [ ] 10.0 Módulo `iam`: atribuição/revogação de roles com escopo por módulo (RF-07)
- [ ] 11.0 Módulo `iam`: bulk fetch `/v1/users/{id}/permissions` com cache Caffeine
- [ ] 12.0 Módulo `authz`: endpoint `POST /v1/authz/check`
- [ ] 13.0 Módulo `audit`: persistência, integração @Async em handlers e endpoint de consulta
- [ ] 14.0 Lifecycle: StaleModuleDetector e deprecation de permissões (RF-12 + RF-16 stale)
- [ ] 15.0 SDK Java: AuthzClient + cache por requisição + auto-config
- [ ] 16.0 SDK Java: anotação `@HasPermission` + Aspect AOP
- [ ] 17.0 SDK Java: SelfRegistrationRunner + ReadinessGate + HeartbeatScheduler
- [ ] 18.0 Demo MS Java (vendas): endpoints protegidos + permissions.yaml
- [ ] 19.0 SDK .NET: IAuthzClient + RequestPermissionCache + DI extensions
- [ ] 20.0 SDK .NET: `[HasPermission]` attribute + AuthorizationHandler
- [ ] 21.0 SDK .NET: SelfRegistrationHostedService + HeartbeatHostedService
- [ ] 22.0 Demo MS .NET (estoque): endpoints protegidos + permissions.yaml
- [ ] 23.0 SDK React: AuthzProvider + hooks `usePermission` + `<IfPermitted>`
- [ ] 24.0 App Shell: Vite host Module Federation + OIDC client + roteamento
- [ ] 25.0 PAP UI MFE: features de módulos + roles
- [ ] 26.0 PAP UI MFE: features de usuários + auditoria
- [ ] 27.0 Demo MFE (vendas): SalesDashboard com guards via SDK React
- [ ] 28.0 CI GitHub Actions: build e teste de todos os apps/libs
- [ ] 29.0 CI GitHub Actions: publish dos 3 SDKs em GitHub Packages
- [ ] 30.0 Suíte E2E Playwright cobrindo RF-14 (fluxo completo)
- [ ] 31.0 README e documentação operacional (rodar local, regenerar chaves, troubleshooting)

## Rastreabilidade RF → Tasks

> O PRD usa identificadores RF-XX (Requisitos Funcionais) — tratados aqui como user stories.

| RF | Tasks Relacionadas | Tipo de Cobertura |
|---|---|---|
| RF-01 — OIDC CyberArk | 8.0, 24.0 | Direta |
| RF-02 — `permissions.yaml` declarativo | 18.0, 22.0 | Direta |
| RF-03 — `POST /catalog/sync` | 7.0 | Direta |
| RF-04 — `/.well-known/permissions` | 18.0, 22.0 | Direta |
| RF-05 — Gestão de roles via PAP | 9.0, 25.0 | Direta |
| RF-06 — Atribuição self-service | 10.0, 26.0 | Direta |
| RF-07 — Delegação por módulo (`can_manage_users`) | 10.0 | Direta |
| RF-08 — Bulk fetch permissões | 11.0 | Direta |
| RF-09 — SDK React bulk fetch + cache sessão | 23.0 | Direta |
| RF-10 — SDK Java/.NET cache por requisição | 15.0, 19.0 | Direta |
| RF-11 — `POST /check` decisão pontual | 12.0 | Direta |
| RF-12 — Lifecycle active/deprecated/removed | 14.0 | Direta |
| RF-13 — Log de auditoria | 13.0 | Direta |
| RF-14 — Demos showcase (1 MFE + 2 MS) | 18.0, 22.0, 27.0, 30.0 | Direta |
| RF-15 — PAP UI como MFE (dogfooding) | 25.0, 26.0 | Direta |
| RF-16 — Auto-registro no bootstrap | 17.0, 21.0 | Direta |
| RF-17 — Gestão de chaves compartilhadas | 5.0, 25.0 | Direta |

## Validação de Cobertura

### Requisitos Funcionais

| Requisito | Task(s) | Status |
|-----------|---------|--------|
| RF-01 | 8.0, 24.0 | ✅ Coberto |
| RF-02 | 18.0, 22.0 | ✅ Coberto |
| RF-03 | 7.0 | ✅ Coberto |
| RF-04 | 18.0, 22.0 | ✅ Coberto |
| RF-05 | 9.0, 25.0 | ✅ Coberto |
| RF-06 | 10.0, 26.0 | ✅ Coberto |
| RF-07 | 10.0 | ✅ Coberto |
| RF-08 | 11.0 | ✅ Coberto |
| RF-09 | 23.0 | ✅ Coberto |
| RF-10 | 15.0, 19.0 | ✅ Coberto |
| RF-11 | 12.0 | ✅ Coberto |
| RF-12 | 14.0 | ✅ Coberto |
| RF-13 | 13.0 | ✅ Coberto |
| RF-14 | 18.0, 22.0, 27.0, 30.0 | ✅ Coberto |
| RF-15 | 25.0, 26.0 | ✅ Coberto |
| RF-16 | 17.0, 21.0 | ✅ Coberto |
| RF-17 | 5.0, 25.0 | ✅ Coberto |

### Artefatos da TechSpec (amostragem por componente)

| Artefato | Task | Status |
|---|---|---|
| `package.json`, `pnpm-workspace.yaml`, `turbo.json`, `pom.xml` raiz, `authz-stack.sln` | 1.0 | ✅ |
| `infra/docker/docker-compose.yml`, cyberark-mock, nginx-gateway | 2.0 | ✅ |
| `apps/authz-service/pom.xml`, `application.yml`, `Dockerfile`, `AuthzServiceApplication.java`, configs | 3.0 | ✅ |
| `db/migration/V1__init_schema.sql`, `V2__seed_platform_admin.sql` | 4.0 | ✅ |
| `modules/**` (domain, infra, controllers, handlers, Argon2KeyHasher) | 5.0 | ✅ |
| `shared/security/ModuleBearerAuthenticationFilter.java`, `GlobalExceptionHandler.java`, `PermissionPrefixValidator.java` | 6.0 | ✅ |
| `catalog/**` (CatalogController, SyncCatalogHandler, Permission, repos) | 7.0 | ✅ |
| `SecurityConfig.java` JWT + `CyberArkUserSearchClient.java` + `UserSearchController.java` | 8.0 | ✅ |
| `iam/api/RoleController.java`, `iam/application/Create/Update/CloneRoleHandler.java`, `iam/domain/Role.java`, repos | 9.0 | ✅ |
| `iam/api/UserRoleController.java`, `iam/application/Assign/RevokeRoleHandler.java`, `UserRoleAssignment.java`, `UserRoleRepository.java` | 10.0 | ✅ |
| `iam/application/GetUserPermissionsHandler.java` + cache Caffeine config | 11.0 | ✅ |
| `authz/api/CheckController.java`, `authz/application/CheckPermissionHandler.java` | 12.0 | ✅ |
| `audit/**` (controller, handler @Async, AuditEvent, repo) | 13.0 | ✅ |
| `modules/application/StaleModuleDetector.java` + lifecycle de permission | 14.0 | ✅ |
| `libs/sdk-java/**` AuthzClient, AuthzProperties, RequestScopedPermissionCache, AuthzAutoConfiguration | 15.0 | ✅ |
| `libs/sdk-java/**` HasPermission annotation + HasPermissionAspect | 16.0 | ✅ |
| `libs/sdk-java/**` SelfRegistrationRunner, ReadinessGate, HeartbeatScheduler, PermissionsYamlLoader | 17.0 | ✅ |
| `apps/demo-ms-java/**` SalesApplication, OrdersController, DiscoveryController, permissions.yaml | 18.0 | ✅ |
| `libs/sdk-dotnet/AuthzSdk/**` IAuthzClient, AuthzClient, AuthzOptions, RequestPermissionCache, ServiceCollectionExtensions | 19.0 | ✅ |
| `libs/sdk-dotnet/AuthzSdk/Authorization/**` HasPermissionAttribute, HasPermissionHandler | 20.0 | ✅ |
| `libs/sdk-dotnet/AuthzSdk/Registration/**` SelfRegistrationHostedService, HeartbeatHostedService, PermissionsYamlLoader | 21.0 | ✅ |
| `apps/demo-ms-dotnet/**` Program.cs, InventoryController, DiscoveryController, permissions.yaml | 22.0 | ✅ |
| `libs/sdk-react/**` AuthzProvider, usePermission, useAllPermissions, IfPermitted, authzHttpClient | 23.0 | ✅ |
| `apps/app-shell/**` vite.config.ts MF, OidcProvider, useAuth, layout, router | 24.0 | ✅ |
| `apps/pap-ui/**` ModulesListPage, CreateModuleDialog, RotateKeyDialog, RolesListPage, RoleEditor | 25.0 | ✅ |
| `apps/pap-ui/**` UserSearchPage, AssignRolesDialog, AuditLogPage | 26.0 | ✅ |
| `apps/demo-mfe/**` SalesDashboard, NewOrderButton, OrderListPage | 27.0 | ✅ |
| `.github/workflows/ci.yml` | 28.0 | ✅ |
| `.github/workflows/publish-sdks.yml` | 29.0 | ✅ |
| `tests/e2e/**` (Playwright) | 30.0 | ✅ |
| `README.md` + docs operacionais | 31.0 | ✅ |

### Categorias Obrigatórias

| # | Categoria | Task(s) / N/A | Skill Relacionada | Status |
|---|---|---|---|---|
| 1 | Setup / Configuração | 1.0, 2.0, 3.0 | `java-dependency-config`, `dotnet-dependency-config` | ✅ |
| 2 | Modelos de Dados | 4.0, 5.0, 7.0, 9.0, 10.0, 13.0 | `java-architecture` | ✅ |
| 3 | Lógica de Negócio | 5.0, 7.0, 9.0, 10.0, 11.0, 12.0, 14.0 | `java-architecture` | ✅ |
| 4 | Endpoints / Interfaces | 5.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0 | `common-restful-api` | ✅ |
| 5 | Integrações Externas | 8.0 (CyberArk), 17.0/21.0 (auto-registro contra AuthZ), 29.0 (GH Packages) | `java-dependency-config` | ✅ |
| 6 | Validações e Erros | 6.0 (ProblemDetails global), subtarefas em 5.0/7.0/10.0 | `java-code-quality`, `common-restful-api` | ✅ |
| 7 | Testes | Subtarefas em todas (15.x, 17.x etc.) + 30.0 (E2E) | `java-testing`, `dotnet-testing`, `react-testing`, `playwright-cli` | ✅ |
| 8 | Observabilidade | 3.0 (config), distribuído nos handlers, 14.0 (gauges) | `java-observability`, `dotnet-observability` | ✅ |
| 9 | Documentação | 31.0 + OpenAPI gerado em 3.0 | — | ✅ |
| 10 | Segurança | 5.0 (Argon2), 6.0 (filter), 8.0 (JWT), 10.0 (escopo), 14.0 (key alerts) | `java-production-readiness` | ✅ |

## Análise de Paralelização

### Lanes de Execução Paralela

| Lane | Tarefas | Descrição |
|------|---------|-----------|
| Lane A — Foundation | 1.0, 2.0 | Podem rodar em paralelo após 1.0 ter `pnpm-workspace.yaml` definido |
| Lane B — Backend (espinha) | 3.0 → 4.0 → 5.0 → 6.0 → 7.0 → 8.0 → 9.0 → 10.0 → 11.0 → 12.0 → 13.0 → 14.0 | Sequencial; 13.0 e 14.0 podem ser parcialmente antecipados |
| Lane C — SDK Java + Demo Java | 15.0 → 16.0 → 17.0 → 18.0 | Inicia após 7.0 (contrato sync pronto) |
| Lane D — SDK .NET + Demo .NET | 19.0 → 20.0 → 21.0 → 22.0 | Paralelo a Lane C, mesma dependência (após 7.0) |
| Lane E — Frontend | 23.0 → 24.0 → 25.0/26.0/27.0 | Inicia após 11.0 (bulk fetch pronto); 25.0/26.0/27.0 paralelos entre si |
| Lane F — CI/Release | 28.0, 29.0, 30.0, 31.0 | 28.0 pode iniciar após 1.0; 29.0 após primeiro SDK pronto; 30.0 e 31.0 ao final |

### Caminho Crítico

`1.0 → 3.0 → 4.0 → 5.0 → 6.0 → 7.0 → 8.0 → 9.0 → 10.0 → 11.0 → 23.0 → 24.0 → 25.0 → 30.0 → 31.0`

Tudo o que é não-crítico (lanes C, D, parte de F) pode ser executado em paralelo por agentes distintos.

### Diagrama de Dependências

```
1.0 ──┬─► 3.0 ──► 4.0 ──► 5.0 ──► 6.0 ──► 7.0 ──┬─► 8.0 ──► 9.0 ──► 10.0 ──► 11.0 ──► 12.0 ──► 13.0 ──► 14.0
      │                                           │
2.0 ──┘                                           ├─► 15.0 ──► 16.0 ──► 17.0 ──► 18.0  (Lane C)
                                                  │
                                                  └─► 19.0 ──► 20.0 ──► 21.0 ──► 22.0  (Lane D)

11.0 ──► 23.0 ──► 24.0 ──┬─► 25.0 ─┐
                          ├─► 26.0 ─┼─► 30.0 ──► 31.0
                          └─► 27.0 ─┘
                                    ▲
              18.0, 22.0 ───────────┘  (E2E precisa dos demos)

1.0 ──► 28.0
15.0, 19.0, 23.0 ──► 29.0
```
