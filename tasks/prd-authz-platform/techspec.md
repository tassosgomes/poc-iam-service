# TechSpec — AuthZ Platform

**Referência PRD:** `tasks/prd-authz-platform/prd.md` (v1.1)
**Status:** Draft v1.0
**Autor:** Tasso (Tech)
**Data:** 2026-04-16

---

## Resumo Executivo

A plataforma AuthZ é um monolito modular em Java 21 + Spring Boot 3 sobre PostgreSQL 16, entregue junto com dois MSs demo (Java e .NET), um App Shell e dois MFEs (PAP UI e demo funcional) em React 18 + Vite com Module Federation, e três SDKs (React, Java, .NET) publicados em GitHub Packages. Toda a stack roda localmente via `docker-compose` — não há artefatos Kubernetes no escopo desta POC.

A decisão arquitetural central do MVP é que cada MS realiza **auto-registro no bootstrap** contra o AuthZ Service, autenticando com uma chave compartilhada por módulo (bearer token) cujo `allowed_prefix` é o controle de autoridade que impede cross-namespace squatting. Isso remove a dependência de CI/CD como caminho de sincronização de catálogo (RF-16). A autorização runtime segue padrão **bulk fetch + cache de sessão** (React) / **cache por requisição** (Java/.NET), com o AuthZ Service centralizando todas as decisões persistidas e os SDKs resolvendo `check` em memória após o primeiro fetch.

## Skills de Referência

| Skill | Caminho | Decisões Influenciadas |
|-------|---------|------------------------|
| `java-architecture` | `~/.claude/skills/java-architecture` | Clean Architecture do AuthZ Service, camadas api/application/domain/infra, CQRS type-safe |
| `java-dependency-config` | `~/.claude/skills/java-dependency-config` | Spring Boot 3, Spring Data JPA + HikariCP, Flyway, MapStruct, Resilience4j, Caffeine |
| `java-code-quality` | `~/.claude/skills/java-code-quality` | Records, sealed classes, Optional, null safety, DTOs |
| `java-testing` | `~/.claude/skills/java-testing` | JUnit 5 + AssertJ + Mockito (AAA), Testcontainers Postgres |
| `java-observability` | `~/.claude/skills/java-observability` | OpenTelemetry, Micrometer/Prometheus, Actuator probes, logs JSON estruturados |
| `dotnet-architecture` | `~/.claude/skills/csharp-dotnet-architecture` | CQRS nativo sem MediatR, ProblemDetails, Result Pattern (demo .NET + SDK) |
| `dotnet-dependency-config` | `~/.claude/skills/dotnet-dependency-config` | Dependências base .NET 8 (demo + SDK) |
| `react-architecture` | `~/.claude/skills/react-architecture` | Estrutura feature-based, path aliases, separação shared vs features |
| `react-code-quality` | `~/.claude/skills/react-code-quality` | TypeScript strict, hooks tipados, naming conventions |
| `react-testing` | `~/.claude/skills/react-testing` | Vitest + Testing Library + Playwright para E2E |
| `common-restful-api` | `~/.claude/skills/common-restful-api` | RFC 9457 ProblemDetails, versionamento via path, kebab-case |
| `common-roles-naming` | `~/.claude/skills/common-roles-naming` | Permissões em snake case dotted (`vendas.orders.create`), roles em SCREAMING_SNAKE_CASE |

## Arquitetura do Sistema

### Visão Geral dos Componentes

```
┌──────────────────────────────────────────────────────────────────┐
│                         Navegador                                │
│  ┌────────────┐   ┌───────────────┐   ┌──────────────────────┐  │
│  │ App Shell  │←─│ PAP UI (MFE)  │   │ Demo MFE (vendas)    │  │
│  │  (host MF) │→│ SDK React     │   │ SDK React            │  │
│  └─────┬──────┘   └───────┬───────┘   └────────┬─────────────┘  │
│        │  OIDC/JWT        │                    │                │
└────────┼──────────────────┼────────────────────┼────────────────┘
         │                  │ REST               │ REST
    ┌────▼──────┐      ┌────▼────────────────────▼───┐
    │ CyberArk  │      │      AuthZ Service         │
    │  (OIDC +  │─────→│  (Java/Spring, Postgres)   │
    │  user API)│      │                            │
    └───────────┘      │  catalog │ iam │ modules   │
                       │  authz   │ audit          │
                       └──┬─────────────────┬───────┘
            Bearer(mod)   │                 │ Bearer(mod)
                       ┌──▼──────────┐   ┌──▼──────────────┐
                       │ Demo MS Java│   │ Demo MS .NET    │
                       │ SDK Java    │   │ SDK .NET        │
                       │ permissions.│   │ permissions.    │
                       │   yaml      │   │   yaml          │
                       └─────────────┘   └─────────────────┘
```

**AuthZ Service (monolito modular, Java/Spring)** — centraliza catálogo de permissões, papéis, atribuições, decisões e auditoria. Divide-se em módulos internos por responsabilidade: `catalog`, `iam`, `modules`, `authz`, `audit`, `shared`.

**App Shell (React + Vite, host Module Federation)** — executa login OIDC contra CyberArk, expõe contexto de autenticação aos MFEs filhos e roteia entre eles. É o único lugar que lida com o JWT do usuário final.

**PAP UI (MFE React)** — interface de administração: criação de módulos/chaves, gestão de roles, atribuição a usuários, auditoria. Dogfooda o SDK React (RF-15).

**Demo MFE (MFE React)** — MFE de vendas que exercita UI guard via `<IfPermitted>` e ações que batem no Demo MS Java ou .NET.

**Demo MS Java / Demo MS .NET** — serviços que expõem endpoints protegidos por `@HasPermission` / `[HasPermission]`, expõem `GET /.well-known/permissions`, e fazem auto-registro no bootstrap.

**SDKs (React, Java, .NET)** — abstraem chamadas ao AuthZ Service. Os server-side também embutem o client de auto-registro (RF-16).

### Arquitetura interna do AuthZ Service (Clean Architecture por módulo)

Cada módulo interno segue:
- `api/` — controllers REST, DTOs, exception handlers específicos
- `application/` — handlers de comandos e queries (CQRS type-safe sem MediatR-like framework; dispatcher via interface `Handler<C, R>`)
- `domain/` — entidades, value objects, repositories (interfaces)
- `infra/` — implementações JPA dos repositories, adapters externos

Separação inter-módulos se dá via pacotes + visibilidade de classes; chamadas cross-module passam por interfaces públicas de `application`. Evolução para Maven multi-module é deixada para quando houver pressão real (pós-MVP).

## Design de Implementação

### Interfaces Principais

```java
// catalog/application
public interface CatalogSyncHandler {
    SyncResult handle(SyncCatalogCommand cmd, ModuleContext ctx);
}

public record SyncCatalogCommand(String moduleId, List<PermissionDeclaration> permissions, String payloadHash) {}
public record SyncResult(int added, int updated, int deprecated, String catalogVersion) {}

// modules/application
public interface ValidateModuleKey {
    ModuleContext validate(String bearerToken); // lança UnauthorizedException se inválido
}

public record ModuleContext(String moduleId, String allowedPrefix, Instant keyIssuedAt) {}

// iam/application
public interface GetUserPermissions {
    UserPermissions handle(GetUserPermissionsQuery query);
}

public record UserPermissions(String userId, Set<String> permissions, Instant resolvedAt, Duration ttl) {}

// authz/application
public interface CheckPermission {
    boolean handle(CheckPermissionQuery query);
}

// audit/application
public interface RecordAuditEvent {
    void record(AuditEvent event); // assíncrono via @Async + transactional outbox
}
```

### Modelos de Dados

**Tabelas Postgres (migration Flyway `V1__init_schema.sql`):**

```sql
CREATE TABLE module (
    id UUID PRIMARY KEY,
    name VARCHAR(64) UNIQUE NOT NULL,          -- ex: "vendas"
    allowed_prefix VARCHAR(64) UNIQUE NOT NULL, -- ex: "vendas"
    description TEXT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ              -- alimenta detecção de stale (RF-16)
);

CREATE TABLE module_key (
    id UUID PRIMARY KEY,
    module_id UUID NOT NULL REFERENCES module(id),
    key_hash TEXT NOT NULL,                    -- Argon2id
    status VARCHAR(16) NOT NULL,               -- ACTIVE | SUPERSEDED | REVOKED
    rotated_at TIMESTAMPTZ,
    grace_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_module_key_module_active ON module_key(module_id) WHERE status = 'ACTIVE';

CREATE TABLE permission (
    id UUID PRIMARY KEY,
    module_id UUID NOT NULL REFERENCES module(id),
    code VARCHAR(128) NOT NULL,                -- ex: "vendas.orders.create"
    description TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,               -- ACTIVE | DEPRECATED | STALE | REMOVED
    sunset_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(module_id, code)
);

CREATE TABLE role (
    id UUID PRIMARY KEY,
    module_id UUID NOT NULL REFERENCES module(id),
    name VARCHAR(64) NOT NULL,                 -- SCREAMING_SNAKE_CASE
    description TEXT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(module_id, name)
);

CREATE TABLE role_permission (
    role_id UUID NOT NULL REFERENCES role(id),
    permission_id UUID NOT NULL REFERENCES permission(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role (
    id UUID PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,             -- cyberark subject
    role_id UUID NOT NULL REFERENCES role(id),
    assigned_by VARCHAR(128) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(128)
);
CREATE INDEX idx_user_role_user_active ON user_role(user_id) WHERE revoked_at IS NULL;

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,           -- ROLE_ASSIGNED | ROLE_REVOKED | CATALOG_SYNC | MODULE_CREATED | KEY_ROTATED | KEY_AUTH_FAILED
    actor_id VARCHAR(128),                     -- NULL para eventos de sistema
    target VARCHAR(256),                       -- user_id, module_id, role_id, conforme evento
    payload JSONB NOT NULL,
    source_ip VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_event_type_time ON audit_event(event_type, occurred_at DESC);
CREATE INDEX idx_audit_event_target_time ON audit_event(target, occurred_at DESC);
```

**Mapeamento de status de permissão:**
- `ACTIVE` — disponível para atribuição
- `DEPRECATED` — existe `sunset_at`, ainda funciona, alerta na PAP (RF-12)
- `STALE` — módulo parou de emitir heartbeat por > 7 dias (RF-16); permissão não é removida
- `REMOVED` — terminal, após sunset

### Endpoints de API

Versionamento via path (`/v1`), RFC 9457 ProblemDetails para erros, OpenAPI 3 gerado por springdoc.

**Catalog & Modules (auth: bearer de módulo)**
- `POST /v1/catalog/sync` — reconcilia permissões do módulo (RF-03)

**Module admin (auth: JWT de usuário com role `PLATFORM_ADMIN`)**
- `POST /v1/modules` — cria módulo, retorna chave em texto claro uma única vez (RF-17)
- `POST /v1/modules/{id}/keys/rotate` — gera nova chave com grace period
- `GET /v1/modules` — lista módulos + idade das chaves + status de heartbeat
- `GET /v1/modules/{id}/permissions` — lista permissões do módulo para admin

**IAM (auth: JWT de usuário, escopo verificado por `module_id` + `can_manage_users`)**
- `GET /v1/roles?moduleId=` — lista roles (RF-05)
- `POST /v1/roles` — cria role
- `PUT /v1/roles/{id}` — edita role
- `POST /v1/roles/{id}/clone` — clona
- `DELETE /v1/roles/{id}`
- `POST /v1/users/{userId}/roles` — atribui role (RF-06)
- `DELETE /v1/users/{userId}/roles/{roleId}` — revoga
- `GET /v1/users/search?q=&moduleId=` — proxy CyberArk (filtrado por RF-07)

**AuthZ (auth: JWT de usuário)**
- `GET /v1/users/{userId}/permissions` — bulk fetch, < 100ms p95 (RF-08)
- `POST /v1/authz/check` — decisão pontual, < 50ms p95 (RF-11)

**Audit (auth: JWT com role `AUDITOR` ou `PLATFORM_ADMIN`)**
- `GET /v1/audit/events?eventType=&moduleId=&from=&to=&page=&size=`

**Actuator (não exposto externamente; consumido pelo docker-compose healthcheck)**
- `/actuator/health/liveness`
- `/actuator/health/readiness` — fica DOWN até primeiro sync de catálogo do próprio AuthZ (catálogo vazio é válido, mas banco+migrations precisam estar up)
- `/actuator/prometheus`

### Auto-registro no bootstrap (RF-16) — contrato SDK Java/.NET

1. No startup, SDK lê o conteúdo do arquivo `permissions.yaml` embarcado no artefato.
2. SDK calcula `sha256` do payload.
3. SDK registra `ApplicationReadinessIndicator` em estado DOWN.
4. SDK dispara `POST /v1/catalog/sync` com retry exponencial (1s, 2s, 4s, 8s, 16s, 32s, capped em 5min), header `Authorization: Bearer ${AUTHZ_MODULE_KEY}`.
5. Em sucesso (2xx), readiness vira UP. Em falha terminal (401/403), readiness permanece DOWN e processo loga erro crítico.
6. Scheduler dispara re-sync a cada 15 min; se payload hash idêntico, AuthZ retorna 200 com `{changed: false}` (RF-03 idempotência).

### Convenções de nomenclatura

- Permissões: `<modulo>.<recurso>.<acao>` em `snake_case_with_dots` — ex: `vendas.orders.create`.
- Roles: `SCREAMING_SNAKE_CASE` com prefixo do módulo — ex: `VENDAS_GERENTE`, `VENDAS_OPERADOR`.
- Roles administrativas especiais: `PLATFORM_ADMIN`, `AUDITOR`, `<MODULO>_USER_MANAGER` (este último concede `can_manage_users` sobre o módulo — RF-07).

## Inventário de Artefatos

### Arquivos a Criar

#### Raiz do monorepo

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `package.json` | Config | Raiz pnpm workspace, scripts Turborepo |
| `pnpm-workspace.yaml` | Config | Declara `apps/*` e `libs/*` JS/TS |
| `turbo.json` | Config | Pipeline de build (typecheck, lint, test, build) |
| `pom.xml` | Config | Parent POM para `authz-service`, `demo-ms-java`, `sdk-java` |
| `authz-stack.sln` | Config | Solution .NET agrupando `demo-ms-dotnet` e `sdk-dotnet` |
| `.github/workflows/ci.yml` | CI | Build + test de todos os apps/libs por mudança detectada |
| `.github/workflows/publish-sdks.yml` | CI | Publica SDKs em GitHub Packages por tag |
| `.gitignore` | Config | Ignora build outputs Java/.NET/Node |
| `README.md` | Docs | Atualizar com instruções de `docker-compose up` |
| `tasks/prd-authz-platform/techspec.md` | Docs | Este documento |

#### apps/authz-service (Java 21 + Spring Boot 3)

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `apps/authz-service/pom.xml` | Config | Dependências Spring Boot, JPA, Flyway, Resilience4j, springdoc, Micrometer, OpenTelemetry |
| `apps/authz-service/Dockerfile` | Infra | Multi-stage: maven build → eclipse-temurin runtime |
| `apps/authz-service/src/main/resources/application.yml` | Config | Config base (server, datasource, management) |
| `apps/authz-service/src/main/resources/application-dev.yml` | Config | Overrides para `docker-compose` |
| `apps/authz-service/src/main/resources/logback-spring.xml` | Config | Logs JSON com MDC trace_id/span_id |
| `apps/authz-service/src/main/resources/db/migration/V1__init_schema.sql` | Migration | Schema completo (tabelas acima) |
| `apps/authz-service/src/main/resources/db/migration/V2__seed_platform_admin.sql` | Migration | Seed do `PLATFORM_ADMIN` bootstrap |
| `apps/authz-service/src/main/java/com/platform/authz/AuthzServiceApplication.java` | Bootstrap | `@SpringBootApplication` |
| `apps/authz-service/src/main/java/com/platform/authz/config/SecurityConfig.java` | Config | Spring Security, filtros JWT + module bearer |
| `apps/authz-service/src/main/java/com/platform/authz/config/JpaConfig.java` | Config | HikariCP, transaction manager |
| `apps/authz-service/src/main/java/com/platform/authz/config/OpenApiConfig.java` | Config | springdoc + security schemes |
| `apps/authz-service/src/main/java/com/platform/authz/config/ObservabilityConfig.java` | Config | Micrometer tags globais, OTLP exporter |
| `apps/authz-service/src/main/java/com/platform/authz/shared/api/GlobalExceptionHandler.java` | API | `@RestControllerAdvice` → ProblemDetail |
| `apps/authz-service/src/main/java/com/platform/authz/shared/security/ModuleBearerAuthenticationFilter.java` | Security | Filtro para rotas de sync |
| `apps/authz-service/src/main/java/com/platform/authz/shared/security/JwtAuthorizationConverter.java` | Security | Mapeia claims CyberArk → authorities |
| `apps/authz-service/src/main/java/com/platform/authz/shared/security/PermissionPrefixValidator.java` | Security | Valida payload vs `allowed_prefix` |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/api/CatalogController.java` | API | `POST /v1/catalog/sync` |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/api/dto/SyncRequest.java` | DTO | Payload de sync |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/api/dto/SyncResponse.java` | DTO | Resposta |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/application/SyncCatalogHandler.java` | Application | Handler idempotente |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/Permission.java` | Domain | Entidade de domínio |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/PermissionStatus.java` | Domain | Enum lifecycle |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/PermissionRepository.java` | Domain | Interface |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/infra/JpaPermissionRepository.java` | Infra | Impl Spring Data JPA |
| `apps/authz-service/src/main/java/com/platform/authz/catalog/infra/PermissionJpaEntity.java` | Infra | Entity JPA + MapStruct mapper |
| `apps/authz-service/src/main/java/com/platform/authz/modules/api/ModuleController.java` | API | CRUD de módulos |
| `apps/authz-service/src/main/java/com/platform/authz/modules/api/KeyController.java` | API | Rotação de chave |
| `apps/authz-service/src/main/java/com/platform/authz/modules/application/CreateModuleHandler.java` | Application | Cria módulo + chave inicial |
| `apps/authz-service/src/main/java/com/platform/authz/modules/application/RotateKeyHandler.java` | Application | Rotação com grace period |
| `apps/authz-service/src/main/java/com/platform/authz/modules/application/ValidateModuleKeyService.java` | Application | Valida bearer no filtro |
| `apps/authz-service/src/main/java/com/platform/authz/modules/application/StaleModuleDetector.java` | Application | Scheduled: marca stale após 7d sem heartbeat |
| `apps/authz-service/src/main/java/com/platform/authz/modules/domain/Module.java` | Domain | Entidade |
| `apps/authz-service/src/main/java/com/platform/authz/modules/domain/ModuleKey.java` | Domain | Entidade |
| `apps/authz-service/src/main/java/com/platform/authz/modules/domain/ModuleRepository.java` | Domain | Interface |
| `apps/authz-service/src/main/java/com/platform/authz/modules/domain/ModuleKeyRepository.java` | Domain | Interface |
| `apps/authz-service/src/main/java/com/platform/authz/modules/infra/JpaModuleRepository.java` | Infra | Impl |
| `apps/authz-service/src/main/java/com/platform/authz/modules/infra/JpaModuleKeyRepository.java` | Infra | Impl |
| `apps/authz-service/src/main/java/com/platform/authz/modules/infra/Argon2KeyHasher.java` | Infra | Hash e verificação de chave |
| `apps/authz-service/src/main/java/com/platform/authz/iam/api/RoleController.java` | API | Endpoints de roles |
| `apps/authz-service/src/main/java/com/platform/authz/iam/api/UserRoleController.java` | API | Atribuição de roles |
| `apps/authz-service/src/main/java/com/platform/authz/iam/api/UserSearchController.java` | API | Proxy CyberArk |
| `apps/authz-service/src/main/java/com/platform/authz/iam/application/CreateRoleHandler.java` | Application | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/application/UpdateRoleHandler.java` | Application | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/application/CloneRoleHandler.java` | Application | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/application/AssignRoleHandler.java` | Application | Inclui verificação de escopo do admin (RF-07) |
| `apps/authz-service/src/main/java/com/platform/authz/iam/application/RevokeRoleHandler.java` | Application | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/application/GetUserPermissionsHandler.java` | Application | Bulk fetch com cache Caffeine (TTL < JWT) |
| `apps/authz-service/src/main/java/com/platform/authz/iam/domain/Role.java` | Domain | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/domain/RoleRepository.java` | Domain | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/domain/UserRoleAssignment.java` | Domain | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/domain/UserRoleRepository.java` | Domain | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/infra/JpaRoleRepository.java` | Infra | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/infra/JpaUserRoleRepository.java` | Infra | — |
| `apps/authz-service/src/main/java/com/platform/authz/iam/infra/CyberArkUserSearchClient.java` | Infra | WebClient + Resilience4j |
| `apps/authz-service/src/main/java/com/platform/authz/authz/api/CheckController.java` | API | `POST /v1/authz/check` |
| `apps/authz-service/src/main/java/com/platform/authz/authz/application/CheckPermissionHandler.java` | Application | Decisão pontual |
| `apps/authz-service/src/main/java/com/platform/authz/audit/api/AuditController.java` | API | Consulta paginada |
| `apps/authz-service/src/main/java/com/platform/authz/audit/application/RecordAuditEventHandler.java` | Application | `@Async` + outbox simples |
| `apps/authz-service/src/main/java/com/platform/authz/audit/domain/AuditEvent.java` | Domain | — |
| `apps/authz-service/src/main/java/com/platform/authz/audit/domain/AuditEventRepository.java` | Domain | — |
| `apps/authz-service/src/main/java/com/platform/authz/audit/infra/JpaAuditEventRepository.java` | Infra | — |
| `apps/authz-service/src/test/java/com/platform/authz/catalog/application/SyncCatalogHandlerTest.java` | Teste unit | Casos idempotência, deprecação, rejeição de prefixo |
| `apps/authz-service/src/test/java/com/platform/authz/modules/application/RotateKeyHandlerTest.java` | Teste unit | Grace period, auditoria |
| `apps/authz-service/src/test/java/com/platform/authz/modules/application/ValidateModuleKeyServiceTest.java` | Teste unit | Argon2, grace, chave revogada |
| `apps/authz-service/src/test/java/com/platform/authz/iam/application/AssignRoleHandlerTest.java` | Teste unit | Escopo de admin por módulo |
| `apps/authz-service/src/test/java/com/platform/authz/iam/application/GetUserPermissionsHandlerTest.java` | Teste unit | Agregação e cache |
| `apps/authz-service/src/test/java/com/platform/authz/authz/application/CheckPermissionHandlerTest.java` | Teste unit | Hit/miss, perf |
| `apps/authz-service/src/test/java/com/platform/authz/integration/CatalogSyncIntegrationTest.java` | Teste int | Testcontainers Postgres, fluxo completo |
| `apps/authz-service/src/test/java/com/platform/authz/integration/ModuleKeyLifecycleIntegrationTest.java` | Teste int | Criação, rotação, grace, revogação |
| `apps/authz-service/src/test/java/com/platform/authz/integration/BulkFetchPerformanceTest.java` | Teste int | Valida p95 < 100ms |
| `apps/authz-service/src/test/java/com/platform/authz/integration/AuditTrailIntegrationTest.java` | Teste int | Persistência e consulta de audit |

#### apps/app-shell (React 18 + Vite + Module Federation host)

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `apps/app-shell/package.json` | Config | — |
| `apps/app-shell/vite.config.ts` | Config | `@originjs/vite-plugin-federation` em modo host |
| `apps/app-shell/tsconfig.json` | Config | — |
| `apps/app-shell/index.html` | UI | — |
| `apps/app-shell/Dockerfile` | Infra | Nginx servindo build estático |
| `apps/app-shell/nginx.conf` | Infra | SPA fallback + proxy `/api` para authz-service |
| `apps/app-shell/src/main.tsx` | UI | Entry point |
| `apps/app-shell/src/bootstrap.tsx` | UI | Bootstrap defer para MF |
| `apps/app-shell/src/App.tsx` | UI | Shell root |
| `apps/app-shell/src/auth/OidcProvider.tsx` | UI | `oidc-client-ts` wrapper |
| `apps/app-shell/src/auth/useAuth.ts` | UI | Hook exposto via SDK para MFEs |
| `apps/app-shell/src/auth/tokenStore.ts` | UI | Gestão de JWT |
| `apps/app-shell/src/router/routes.tsx` | UI | React Router v6 + lazy MFE loader |
| `apps/app-shell/src/layout/Shell.tsx` | UI | Layout principal |
| `apps/app-shell/src/layout/Header.tsx` | UI | — |
| `apps/app-shell/src/layout/SideNav.tsx` | UI | — |

#### apps/pap-ui (React MFE)

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `apps/pap-ui/package.json` | Config | Declara `@platform/sdk-react` |
| `apps/pap-ui/vite.config.ts` | Config | MF remote |
| `apps/pap-ui/Dockerfile` | Infra | Nginx |
| `apps/pap-ui/src/bootstrap.tsx` | UI | Entry |
| `apps/pap-ui/src/PapApp.tsx` | UI | Root do MFE |
| `apps/pap-ui/src/features/modules/ModulesListPage.tsx` | UI | Lista + alerta idade chave |
| `apps/pap-ui/src/features/modules/CreateModuleDialog.tsx` | UI | Cria módulo, exibe chave |
| `apps/pap-ui/src/features/modules/RotateKeyDialog.tsx` | UI | Confirma rotação |
| `apps/pap-ui/src/features/roles/RolesListPage.tsx` | UI | — |
| `apps/pap-ui/src/features/roles/RoleEditor.tsx` | UI | Seleciona permissões com descrição natural |
| `apps/pap-ui/src/features/users/UserSearchPage.tsx` | UI | Busca CyberArk |
| `apps/pap-ui/src/features/users/AssignRolesDialog.tsx` | UI | — |
| `apps/pap-ui/src/features/audit/AuditLogPage.tsx` | UI | Consulta paginada |
| `apps/pap-ui/src/api/adminClient.ts` | UI | Extensão do SDK para rotas admin |

#### apps/demo-mfe (React MFE)

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `apps/demo-mfe/package.json` | Config | — |
| `apps/demo-mfe/vite.config.ts` | Config | MF remote |
| `apps/demo-mfe/Dockerfile` | Infra | — |
| `apps/demo-mfe/src/bootstrap.tsx` | UI | — |
| `apps/demo-mfe/src/DemoApp.tsx` | UI | — |
| `apps/demo-mfe/src/features/sales/SalesDashboard.tsx` | UI | Itens condicionais via `<IfPermitted>` |
| `apps/demo-mfe/src/features/sales/NewOrderButton.tsx` | UI | Guarded por `vendas.orders.create` |
| `apps/demo-mfe/src/features/sales/OrderListPage.tsx` | UI | Ações por linha protegidas |
| `apps/demo-mfe/src/api/salesClient.ts` | UI | Chama demo MS |

#### apps/demo-ms-java (Java 21 + Spring Boot 3)

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `apps/demo-ms-java/pom.xml` | Config | Depende de `sdk-java` |
| `apps/demo-ms-java/Dockerfile` | Infra | — |
| `apps/demo-ms-java/src/main/java/com/platform/demo/sales/SalesApplication.java` | Bootstrap | — |
| `apps/demo-ms-java/src/main/java/com/platform/demo/sales/api/OrdersController.java` | API | `@HasPermission("vendas.orders.*")` |
| `apps/demo-ms-java/src/main/java/com/platform/demo/sales/api/DiscoveryController.java` | API | `GET /.well-known/permissions` |
| `apps/demo-ms-java/src/main/resources/application.yml` | Config | `authz.module-key: ${AUTHZ_MODULE_KEY}` |
| `apps/demo-ms-java/src/main/resources/permissions.yaml` | Data | Permissões do módulo vendas |

#### apps/demo-ms-dotnet (.NET 8)

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `apps/demo-ms-dotnet/DemoStock.Api/DemoStock.Api.csproj` | Config | Depende de `sdk-dotnet` |
| `apps/demo-ms-dotnet/DemoStock.Api/Dockerfile` | Infra | — |
| `apps/demo-ms-dotnet/DemoStock.Api/Program.cs` | Bootstrap | Registra `AddAuthzSdk()` + `[HasPermission]` handler |
| `apps/demo-ms-dotnet/DemoStock.Api/Controllers/InventoryController.cs` | API | `[HasPermission("estoque.inventory.view")]` |
| `apps/demo-ms-dotnet/DemoStock.Api/Controllers/DiscoveryController.cs` | API | `/.well-known/permissions` |
| `apps/demo-ms-dotnet/DemoStock.Api/appsettings.json` | Config | — |
| `apps/demo-ms-dotnet/DemoStock.Api/permissions.yaml` | Data | Permissões do módulo estoque |

#### libs/sdk-react

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `libs/sdk-react/package.json` | Config | Publica em GitHub Packages |
| `libs/sdk-react/rollup.config.mjs` | Config | ESM + CJS + d.ts |
| `libs/sdk-react/tsconfig.json` | Config | — |
| `libs/sdk-react/src/index.ts` | Lib | Public API |
| `libs/sdk-react/src/AuthzProvider.tsx` | Lib | Faz bulk fetch 1× por sessão (RF-09) |
| `libs/sdk-react/src/hooks/usePermission.ts` | Lib | Sincrona, em memória |
| `libs/sdk-react/src/hooks/useAllPermissions.ts` | Lib | — |
| `libs/sdk-react/src/components/IfPermitted.tsx` | Lib | Render conditional |
| `libs/sdk-react/src/api/authzHttpClient.ts` | Lib | Fetch tipado |
| `libs/sdk-react/src/types.ts` | Lib | — |
| `libs/sdk-react/src/__tests__/AuthzProvider.test.tsx` | Teste | Vitest + Testing Library |
| `libs/sdk-react/src/__tests__/usePermission.test.tsx` | Teste | — |

#### libs/sdk-java

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `libs/sdk-java/pom.xml` | Config | — |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/AuthzClient.java` | Lib | WebClient + Resilience4j |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/AuthzProperties.java` | Lib | `@ConfigurationProperties` |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/annotation/HasPermission.java` | Lib | Annotation |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/aop/HasPermissionAspect.java` | Lib | AOP guard |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/cache/RequestScopedPermissionCache.java` | Lib | Cache por requisição (RF-10) |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/SelfRegistrationRunner.java` | Lib | `ApplicationRunner` bootstrap (RF-16) |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/PermissionsYamlLoader.java` | Lib | Lê `permissions.yaml` do classpath |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/ReadinessGate.java` | Lib | Controla `HealthIndicator` de readiness |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/HeartbeatScheduler.java` | Lib | Re-sync 15 min |
| `libs/sdk-java/src/main/java/com/platform/authz/sdk/config/AuthzAutoConfiguration.java` | Lib | Spring Boot auto-config |
| `libs/sdk-java/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Config | Registro auto-config |
| `libs/sdk-java/src/test/java/com/platform/authz/sdk/aop/HasPermissionAspectTest.java` | Teste | — |
| `libs/sdk-java/src/test/java/com/platform/authz/sdk/registration/SelfRegistrationRunnerTest.java` | Teste | Retry, readiness gate |

#### libs/sdk-dotnet

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `libs/sdk-dotnet/AuthzSdk/AuthzSdk.csproj` | Config | Publica em GitHub Packages |
| `libs/sdk-dotnet/AuthzSdk/IAuthzClient.cs` | Lib | — |
| `libs/sdk-dotnet/AuthzSdk/AuthzClient.cs` | Lib | HttpClient + Polly |
| `libs/sdk-dotnet/AuthzSdk/AuthzOptions.cs` | Lib | `IOptions<AuthzOptions>` |
| `libs/sdk-dotnet/AuthzSdk/Authorization/HasPermissionAttribute.cs` | Lib | Auth policy requirement |
| `libs/sdk-dotnet/AuthzSdk/Authorization/HasPermissionHandler.cs` | Lib | `AuthorizationHandler` |
| `libs/sdk-dotnet/AuthzSdk/Caching/RequestPermissionCache.cs` | Lib | Scoped service |
| `libs/sdk-dotnet/AuthzSdk/Registration/SelfRegistrationHostedService.cs` | Lib | `IHostedService` bootstrap |
| `libs/sdk-dotnet/AuthzSdk/Registration/PermissionsYamlLoader.cs` | Lib | — |
| `libs/sdk-dotnet/AuthzSdk/Registration/HeartbeatHostedService.cs` | Lib | — |
| `libs/sdk-dotnet/AuthzSdk/Extensions/ServiceCollectionExtensions.cs` | Lib | `AddAuthzSdk()` |
| `libs/sdk-dotnet/AuthzSdk.Tests/AuthzSdk.Tests.csproj` | Config | — |
| `libs/sdk-dotnet/AuthzSdk.Tests/HasPermissionHandlerTests.cs` | Teste | — |
| `libs/sdk-dotnet/AuthzSdk.Tests/SelfRegistrationHostedServiceTests.cs` | Teste | — |

#### infra/docker (somente ambiente local — K8s fora do escopo)

| Caminho | Tipo | Descrição |
|---------|------|-----------|
| `infra/docker/docker-compose.yml` | Infra | Sobe postgres, authz-service, cyberark-mock, app-shell, pap-ui, demo-mfe, demo-ms-java, demo-ms-dotnet, nginx-gateway |
| `infra/docker/.env.example` | Config | `AUTHZ_MODULE_KEY_VENDAS`, `AUTHZ_MODULE_KEY_ESTOQUE`, `CYBERARK_ISSUER`, `CYBERARK_CLIENT_ID`, etc. |
| `infra/docker/postgres/init.sql` | Infra | Cria database + usuário |
| `infra/docker/cyberark-mock/Dockerfile` | Infra | Imagem baseada em MockOAuth2Server (ex: `ghcr.io/navikt/mock-oauth2-server`) |
| `infra/docker/cyberark-mock/users.json` | Infra | Seed de usuários demo com `module_membership` |
| `infra/docker/nginx-gateway/nginx.conf` | Infra | Reverse proxy: `/` → app-shell, `/pap` → pap-ui, `/demo` → demo-mfe, `/api` → authz-service |
| `infra/docker/bootstrap/seed-modules.http` | Doc | Script HTTP (JetBrains/REST Client) documentando criação inicial de módulos via admin API; chaves geradas são copiadas para `.env` |

### Arquivos a Modificar

| Caminho | Alteração |
|---------|-----------|
| `README.md` | Adicionar seção de arquitetura, como rodar com `docker-compose`, como regenerar chaves de módulo |
| `.gitignore` | Adicionar `target/`, `bin/`, `obj/`, `node_modules/`, `dist/`, `*.env` |

### Arquivos de Referência (não alterar)

| Caminho | Motivo |
|---------|--------|
| `tasks/prd-authz-platform/prd.md` | Fonte de requisitos funcionais |
| `~/.claude/skills/java-architecture/` | Padrões de camadas e CQRS |
| `~/.claude/skills/java-observability/` | Estrutura de logs e métricas |
| `~/.claude/skills/react-architecture/` | Estrutura de pastas e path aliases |
| `~/.claude/skills/common-restful-api/` | ProblemDetails, versionamento |
| `~/.claude/skills/common-roles-naming/` | Nomenclatura de roles/permissões |

## Pontos de Integração

**CyberArk (OIDC + User API)**
- Login: protocolo OIDC Authorization Code + PKCE no App Shell via `oidc-client-ts`.
- Validação de JWT no AuthZ Service via `spring-boot-starter-oauth2-resource-server` apontando para o JWKS do issuer.
- Busca de usuários: endpoint `GET /v1/users/search` no AuthZ Service proxia para a API do CyberArk usando WebClient com timeout 2s e circuit breaker Resilience4j (fallback: erro 503 com ProblemDetail).
- Para desenvolvimento local, `cyberark-mock` no docker-compose (mock-oauth2-server) simula o fluxo.

**GitHub Packages**
- Publicação dos 3 SDKs por workflow `publish-sdks.yml` disparado por tag `sdk-{react,java,dotnet}-vX.Y.Z`.
- Consumo autenticado via `GITHUB_TOKEN` no CI dos demo apps; devs locais usam PAT documentado no README.

## Análise de Impacto

Por ser projeto greenfield, não há componentes existentes a impactar. Pontos de atenção interna entre componentes novos:

| Componente Afetado | Tipo de Impacto | Descrição & Nível de Risco | Ação Requerida |
|---|---|---|---|
| Contrato `SyncRequest` | Compartilhado entre 3 SDKs + AuthZ | Schema versionado; quebra exige bump major. Risco médio. | Versionamento via `X-Schema-Version` header; testes de contrato |
| Contrato `UserPermissions` | Bulk fetch → 3 SDKs | Campo adicional é retrocompatível; remoção quebra SDKs antigos. Risco médio. | Adicionar campos como opcionais; deprecar antes de remover |
| Formato `permissions.yaml` | Lido por cada MS | Mudança no schema força migração coordenada. Risco baixo (escopo MVP pequeno). | Schema versionado + validador no AuthZ |
| Cache de permissões (SDK Java/.NET) | Scope de requisição | Vazamento entre threads = elevação de privilégio. Risco alto. | Garantir scope correto (`@RequestScope` / `AddScoped`); testes dedicados |
| Chave compartilhada em env var | Exposta a todo processo do MS | Se vazada, permite sync malicioso no prefixo do módulo. Risco médio. | Prefix binding + auditoria + rotação manual + alerta aos 180d |

## Abordagem de Testes

### Testes Unitários

Padrão AAA, JUnit 5 + AssertJ + Mockito no Java; Vitest + Testing Library no React; xUnit + AwesomeAssertions + Moq no .NET.

Cobertura mínima alvo 70% em lógica de domínio. Casos críticos a cobrir explicitamente:
- `SyncCatalogHandler`: idempotência (mesmo hash → no-op), adição, deprecação com sunset, rejeição de prefixo fora do `allowed_prefix`, concorrência de réplicas (bloqueio otimista).
- `ValidateModuleKeyService`: chave válida (ACTIVE), chave em grace period (SUPERSEDED + grace vigente), chave expirada (SUPERSEDED + grace vencido), chave revogada, chave inexistente, timing-safe compare.
- `AssignRoleHandler`: admin global assume qualquer módulo; admin com `can_manage_users` do módulo X assina role de X; tentativa cross-module é bloqueada.
- `GetUserPermissionsHandler`: agregação de múltiplos roles, deduplicação, cache hit/miss.
- `HasPermissionAspect` / `HasPermissionHandler`: deny por default, cache hit, usuário não autenticado.
- `usePermission`: retorna boolean síncrono, reflete cache atualizado.

### Testes de Integração

Localização: `src/test/java/.../integration/` (Java) e `test/integration/` (JS/TS).

- **Testcontainers Postgres** para todos os testes de integração do AuthZ Service. Banco sobe uma vez por suíte, migrations aplicadas via Flyway, dados resetados entre testes via `@Sql`.
- **Contract tests dos SDKs**: fixture compartilhada em `infra/docker/bootstrap/fixtures/sync-request.v1.json` consumida pelos 3 SDKs para garantir paridade de comportamento.
- **Fluxo de auto-registro**: teste integration do SDK Java/.NET sobe o AuthZ real (via Testcontainers) e valida ciclo completo de bootstrap → readiness UP.

### Testes E2E (Playwright)

Cobrem os fluxos de RF-14 (demo end-to-end):
1. Admin faz login, cria módulo "vendas", copia chave.
2. Admin cria role `VENDAS_OPERADOR` com permissão `vendas.orders.create`.
3. Admin atribui role a um usuário.
4. Usuário loga no App Shell, vê o botão "Novo Pedido" no Demo MFE.
5. Usuário cria pedido, chamada bate no Demo MS Java e passa pelo `@HasPermission`.
6. Admin revoga role; após refresh (< 5 min no MVP), botão desaparece.

Suíte E2E roda contra o `docker-compose up` completo; em CI, Playwright container acessa o gateway nginx.

## Sequenciamento de Desenvolvimento

### Ordem de Construção

1. **Schema do banco + AuthZ Service esqueleto** (módulos `modules` + `catalog` básico). Sem isso, nada mais sobe.
2. **Endpoints de admin de módulo** (criação + rotação de chave) + seeding `PLATFORM_ADMIN`. Necessário para provisionar chaves usadas pelos MSs demo.
3. **Endpoint `POST /v1/catalog/sync`** completo (com Argon2 + prefix binding + idempotência).
4. **SDK Java — componente de auto-registro** (`SelfRegistrationRunner` + `ReadinessGate` + `HeartbeatScheduler`). Valida RF-16 isoladamente.
5. **Demo MS Java** consumindo SDK Java (end-to-end de registro funcional).
6. **Módulo `iam` do AuthZ** (roles, atribuições, bulk fetch) + módulo `authz` (`/check`).
7. **SDK React** + App Shell + PAP UI (dogfooding RF-15).
8. **SDK .NET** espelhando SDK Java.
9. **Demo MS .NET** + Demo MFE.
10. **Módulo `audit`** consolidado (eventos já são emitidos por handlers anteriores, aqui entra a consulta).
11. **StaleModuleDetector** + alertas na PAP UI.
12. **Suíte E2E Playwright** + hardening.

### Dependências Técnicas

- `docker-compose` com Postgres + mock-oauth2-server disponível desde o início.
- Acesso ao GitHub Packages configurado antes do primeiro publish de SDK.
- Conta CyberArk real não é bloqueante do MVP — mock cobre o fluxo.

## Monitoramento e Observabilidade

OpenTelemetry como fachada unificada (traces + metrics + logs), exporter OTLP. Para dev local, subir um Grafana + Tempo + Loki + Prometheus stack é **opcional** (não incluso no escopo MVP); por padrão o docker-compose expõe `/actuator/prometheus` para scraping ad-hoc.

**Métricas custom (Micrometer):**
- `authz_catalog_sync_total{module,result}` — sucesso/falha/idempotent
- `authz_bulk_fetch_seconds` histogram — alvo p95 < 100ms
- `authz_check_seconds` histogram — alvo p95 < 50ms
- `authz_module_key_invalid_total{module}` — detectar tentativas de sync com chave inválida
- `authz_stale_modules_count` gauge — módulos sem heartbeat > 7d
- `authz_cache_hit_ratio{cache="user_permissions"}` — saúde do cache Caffeine

**Logs estruturados (JSON via logback-spring):**
- Todo request inclui `trace_id`, `span_id`, `user_id` (via MDC).
- Eventos de segurança (`key_auth_failed`, `prefix_violation`, `admin_scope_violation`) em nível WARN.
- Payloads de sync são logados sem o conteúdo cru, apenas `payload_hash` e contagens.

**Healthchecks:**
- `/actuator/health/liveness` — sempre UP enquanto JVM viva.
- `/actuator/health/readiness` — depende de `DataSourceHealthIndicator` + `FlywayHealthIndicator`.
- Nos MSs demo, readiness adicional é controlada pelo `ReadinessGate` do SDK até o primeiro sync OK.

## Considerações Técnicas

### Decisões Principais

**Monolito modular vs microsserviços** — monolito escolhido. Catálogo, roles e atribuições têm integridade referencial forte (foreign keys, transações). Microsserviços introduziriam sagas e eventual consistency sem pagar com ganho real na escala da POC. Quebrar depois é trivial se a cada módulo já tem pacote e interfaces estabelecidos.

**CQRS type-safe sem framework (MediatR-like)** — handlers são interfaces simples `Handler<C, R>`, resolvidos via Spring DI. Evita dependência em lib opaca e mantém stacktraces limpas.

**Cache Caffeine em-processo no AuthZ Service** — bulk fetch tem TTL curto (< JWT TTL, ex: 10min) para evitar stale e reduz carga no Postgres. Invalidação ativa não está no MVP (RF-06 aceita até 5min de staleness).

**Argon2id para hash de chaves de módulo** — mesmo padrão que seria usado para senhas. BCrypt seria aceitável; Argon2id é o recomendado atual.

**Bearer simples em vez de HMAC** — aceito o trade-off de não ter replay protection na rede interna. Upgrade para HMAC é mudança isolada no filtro `ModuleBearerAuthenticationFilter` e na função de sign no SDK.

**Env var para chave do módulo** — permite dev local via `docker-compose`. Produção real plugaria K8s Secret/Vault sem mudança de código.

**Module Federation via Vite plugin** — `@originjs/vite-plugin-federation` é mais jovem que o Webpack MF, mas alinha com a stack Vite. Trade-off aceito por consistência; revisitar se bugs bloquearem.

### Riscos Conhecidos

| Risco | Mitigação |
|---|---|
| Vazamento de chave de módulo em log/repo | Nunca logar a chave em texto claro; armazenar apenas hash; exibir em texto claro apenas uma vez na criação |
| Cache de permissões mantém dado stale após revogação | TTL curto (≤5 min) + documentação clara do trade-off; endpoint manual de força-logout na PAP |
| Race condition em sync concorrente (múltiplas réplicas) | Lock otimista via `updated_at` + `ON CONFLICT DO UPDATE` nas migrations; teste de concorrência |
| `@originjs/vite-plugin-federation` instável | Plano B: trocar host por webpack Module Federation (migração contida ao `vite.config.ts` do shell) |
| Desvio entre os 3 SDKs | Suíte de contract tests com fixture compartilhada; versionamento coordenado |
| Bootstrap loop: AuthZ fora do ar bloqueia todos os MSs | Backoff exponencial + alerta; sync manual via endpoint admin documentado como fallback de emergência |

### Requisitos Especiais

- **Performance**: `GET /v1/users/{userId}/permissions` p95 < 100ms (RF-08); `POST /v1/authz/check` p95 < 50ms (RF-11). Ambos validados em teste de integração com seed representativo (50 roles, 500 permissões, 10k users).
- **Segurança**: prefix binding é controle crítico — tem teste dedicado em múltiplas camadas (handler, aspect, filter). Timing-safe comparison na validação de chave.
- **Disponibilidade**: AuthZ Service é SPOF do bootstrap dos MSs. Mitigação: readiness gate permite retry sem intervenção humana; deploy do AuthZ exige janela coordenada (aceito pela seção 10 do PRD).

### Conformidade com Padrões

- **Arquitetura Java** (`java-architecture`): Clean Architecture por módulo, CQRS type-safe, ProblemDetail RFC 7807, exception handler global.
- **Qualidade Java** (`java-code-quality`): records para DTOs, sealed classes onde couber, Optional para retornos nulos, sem `null` implícito.
- **Testes Java** (`java-testing`): AAA, Testcontainers, naming `methodName_Condition_ExpectedBehavior`, cobertura > 70% em domínio.
- **Observabilidade Java** (`java-observability`): logs JSON via logback, MDC com trace/span, Micrometer, Actuator probes.
- **Arquitetura React** (`react-architecture`): feature-based folders, path aliases `@/`, PascalCase em componentes, kebab-case em pastas.
- **API REST** (`common-restful-api`): versionamento via path `/v1`, ProblemDetails RFC 9457, kebab-case em paths, plural nos recursos.
- **Nomenclatura de roles** (`common-roles-naming`): SCREAMING_SNAKE_CASE com prefixo de módulo.

Desvios conscientes:
- Não há Maven multi-module no AuthZ Service (pacotes apenas). Justificativa: tamanho do MVP. Upgrade path claro.
- Não há Vault/K8s Secret na distribuição de chaves. Justificativa: escopo limitado ao dev local (docker-compose).
