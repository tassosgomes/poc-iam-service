---
status: pending
parallelizable: false
blocked_by: [10.0]
---

<task_context>
<domain>backend/authz-service/iam</domain>
<type>implementation</type>
<scope>performance</scope>
<complexity>medium</complexity>
<dependencies>database</dependencies>
<unblocks>"12.0,23.0"</unblocks>
</task_context>

# Tarefa 11.0: Módulo `iam` — bulk fetch `/v1/users/{id}/permissions` com cache Caffeine

## Relacionada às User Stories

- RF-08 — Endpoint de bulk fetch de permissões do usuário

## Visão Geral

Endpoint de leitura quente: agrega todas as permissões `ACTIVE` ou `DEPRECATED` derivadas dos roles atribuídos a um usuário, deduplicado, com cache Caffeine TTL ≤ TTL do JWT (configurável, default 10min). Alvo: p95 < 100ms.

## Requisitos

- `GetUserPermissionsHandler` agrega via JOIN: `user_role` (não revogado) → `role` → `role_permission` → `permission` (status `ACTIVE` ou `DEPRECATED`)
- Cache Caffeine `userPermissions` com chave = `userId`, TTL configurável `authz.cache.user-permissions-ttl=10m`
- Cache invalidado proativamente em `AssignRoleHandler`/`RevokeRoleHandler` (modificar 10.0 para invalidar)
- `PermissionQueryController`: `GET /v1/users/{userId}/permissions`
- DTO: `UserPermissionsDto` `{ userId, permissions: [code], resolvedAt, ttlSeconds }`
- Autorização: usuário pode buscar próprias permissões; PLATFORM_ADMIN pode buscar de qualquer um
- Métrica: `authz_bulk_fetch_seconds` histogram + `authz_user_permissions_cache_hit_ratio`

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/GetUserPermissionsHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/GetUserPermissionsQuery.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/PermissionQueryController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/UserPermissionsDto.java`
  - `apps/authz-service/src/main/java/com/platform/authz/config/CacheConfig.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/application/GetUserPermissionsHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/integration/BulkFetchPerformanceTest.java`
- **Modificar:**
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/AssignRoleHandler.java` (invalidar cache após assignment)
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/RevokeRoleHandler.java` (idem)
  - `apps/authz-service/pom.xml` (caffeine + spring-boot-starter-cache)
  - `apps/authz-service/src/main/resources/application.yml` (TTL configurável)
- **Skills para consultar durante implementação:**
  - `java-architecture` — query handler
  - `java-dependency-config` — Caffeine + Spring Cache
  - `java-testing` — perf test com seed grande

## Subtarefas

- [ ] 11.1 `CacheConfig` (Caffeine, TTL via property)
- [ ] 11.2 Query JPQL agregando permissões
- [ ] 11.3 Handler com `@Cacheable("userPermissions")` + `@CacheEvict` em mutações
- [ ] 11.4 Controller + DTO
- [ ] 11.5 Métricas (Micrometer Caffeine integration: `CaffeineCacheMetrics.monitor`)
- [ ] 11.6 Teste unit cobrindo agregação, dedup, exclusão de revogados, exclusão de status REMOVED
- [ ] 11.7 Teste perf: seed 10k user_roles + 500 permissions, 1000 requests, asseverar p95 < 100ms

## Sequenciamento

- Bloqueado por: 10.0
- Desbloqueia: 12.0, 23.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-08
- Evidência esperada: bulk fetch retorna lista correta; perf test passa; cache hit visível em métrica

## Detalhes de Implementação

**Query (JPA/JPQL):**
```sql
SELECT DISTINCT p.code
FROM user_role ur
JOIN role r ON r.id = ur.role_id
JOIN role_permission rp ON rp.role_id = r.id
JOIN permission p ON p.id = rp.permission_id
WHERE ur.user_id = :userId
  AND ur.revoked_at IS NULL
  AND p.status IN ('ACTIVE','DEPRECATED')
```

**Convenções da stack:**
- Cache via Spring `@Cacheable` para legibilidade; Caffeine spec via property `spring.cache.caffeine.spec=expireAfterWrite=10m,maximumSize=10000` (`java-dependency-config`)
- Histograma com SLO buckets `0.005, 0.01, 0.025, 0.05, 0.1, 0.25` (`java-observability`)
- Teste perf isolado em profile `perf` (`java-testing`)

## Critérios de Sucesso (Verificáveis)

- [ ] Testes unitários passam
- [ ] Teste de performance passa (`BulkFetchPerformanceTest`): p95 < 100ms
- [ ] cURL: usuário próprio → 200; outro usuário sem PLATFORM_ADMIN → 403
- [ ] Métrica `authz_bulk_fetch_seconds` exposta em `/actuator/prometheus`
- [ ] Após `assign`, próximo bulk fetch retorna nova permissão (cache invalidado)
