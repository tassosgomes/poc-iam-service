# Task 14 Review — Lifecycle

**Status:** APROVADO
**Data:** 2026-04-17
**Branch:** feat/task-14-lifecycle

## Escopo revisado

- Task: `tasks/prd-authz-platform/14_task.md`
- PRD: `tasks/prd-authz-platform/prd.md`
- TechSpec: `tasks/prd-authz-platform/techspec.md`

## Validação contra requisitos

### RF-12 — lifecycle active/deprecated/removed

- `PermissionSunsetJob` remove permissões `DEPRECATED` com `sunset_at < now`
- `Permission` possui transições para `STALE` e `REMOVED`
- `SyncCatalogHandler` reativa permissões `DEPRECATED` e `STALE` quando reaparecem no sync

### RF-16 — stale após 7 dias sem heartbeat

- `StaleModuleDetector` busca módulos com `lastHeartbeatAt < now - staleAfter`
- permissões `ACTIVE` do módulo são marcadas como `STALE`
- evento de auditoria `MODULE_WENT_STALE` é publicado apenas quando houve mudança real
- scheduling e properties foram externalizados em `authz.lifecycle.*`

## Checagens explícitas solicitadas

### 1. Clock injetado

Conforme esperado.

- `StaleModuleDetector`: usa `Instant.now(clock)`
- `PermissionSunsetJob`: usa `Instant.now(clock)`
- `PlatformGauges`: usa `Instant.now(clock)`
- `TimeConfig` fornece `Clock.systemUTC()`
- testes usam `Clock.fixed(...)`

Não encontrei uso de `Instant.now()` direto nos arquivos da implementação da task.

### 2. Idempotência / audit

Conforme esperado.

- `StaleModuleDetector` consulta apenas permissões `ACTIVE`
- `Permission.markStale(...)` retorna `false` se já estiver `STALE` ou `REMOVED`
- audit só é publicado quando `moduleMarkedPermissions > 0`
- existe teste cobrindo o cenário sem mudança e sem publish de audit

### 3. Gauges Micrometer

Conforme esperado.

- `authz_stale_modules_count`
- `authz_module_key_age_max_days`
- `authz_permissions_deprecated_count`

Registrados em `PlatformGauges`.

## Revisão de código e padrões

Skills revisadas:

- `java-production-readiness`
- `java-architecture`
- `java-observability`
- `java-testing`
- `java-code-quality`

Resultado:

- separação de camadas está adequada
- constructor injection + `Objects.requireNonNull(...)`
- logging com placeholders SLF4J
- jobs ficam na camada `application`
- scheduling habilitado em config dedicada
- testes seguem padrão AAA e naming aceitável

## Build e testes

Executado localmente:

- `mvn -pl apps/authz-service -DskipTests compile` ✅
- `mvn -pl apps/authz-service test` ✅

Resultado:

- **121 testes passaram**
- **0 falhas**
- **BUILD SUCCESS**

## Observações não bloqueantes

1. `PlatformGauges.countStaleModules()` faz consulta carregando entidades para contar; uma query de contagem seria mais eficiente.
2. `PlatformGauges.maxModuleKeyAgeDays()` percorre todos os módulos/chaves em memória; pode virar gargalo futuro com escala maior.
3. `Permission.markStale()` ainda aceitaria `DEPRECATED -> STALE` no domínio, embora o serviço filtre apenas `ACTIVE`.

Nenhuma observação acima bloqueia a aprovação desta task.

## Veredito final

**APROVADO**

A implementação atende à Task 14, está alinhada com RF-12 e RF-16, respeita o uso de `Clock`, preserva idempotência do audit e registra os gauges Micrometer solicitados.
