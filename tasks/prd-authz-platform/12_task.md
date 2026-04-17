---
status: pending
parallelizable: false
blocked_by: [11.0]
---

<task_context>
<domain>backend/authz-service/authz</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>low</complexity>
<dependencies>database</dependencies>
<unblocks>"13.0"</unblocks>
</task_context>

# Tarefa 12.0: Módulo `authz` — endpoint `POST /v1/authz/check`

## Relacionada às User Stories

- RF-11 — Endpoint de decisão pontual

## Visão Geral

Endpoint para casos sem bulk fetch (jobs, integrações). Recebe `{userId, permission}` e retorna boolean. Deve atender p95 < 50ms — reaproveita o cache de bulk fetch da 11.0.

## Requisitos

- `CheckController`: `POST /v1/authz/check`
- `CheckPermissionHandler` reutiliza `GetUserPermissionsHandler` (que já cacheia) e faz `Set.contains(permission)`
- DTO request: `{ userId, permission }`; response: `{ allowed: boolean, source: "active"|"deprecated"|"denied" }`
- Autorização: PLATFORM_ADMIN ou role interna `AUTHZ_CHECK` (para SAs); usuário não-admin verifica apenas a si mesmo
- Métrica: `authz_check_seconds` histogram

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/authz/api/CheckController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/authz/api/dto/CheckRequest.java`
  - `apps/authz-service/src/main/java/com/platform/authz/authz/api/dto/CheckResponse.java`
  - `apps/authz-service/src/main/java/com/platform/authz/authz/application/CheckPermissionHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/authz/application/CheckPermissionQuery.java`
  - `apps/authz-service/src/test/java/com/platform/authz/authz/application/CheckPermissionHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/authz/integration/CheckEndpointPerformanceTest.java`
- **Skills para consultar durante implementação:**
  - `java-architecture` — query handler
  - `java-testing` — teste de perf

## Subtarefas

- [ ] 12.1 Handler reaproveitando bulk fetch
- [ ] 12.2 Controller + DTOs
- [ ] 12.3 Métrica
- [ ] 12.4 Testes unit + perf (p95 < 50ms)

## Sequenciamento

- Bloqueado por: 11.0
- Desbloqueia: 13.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-11
- Evidência esperada: response em < 50ms; cache hit dominante após primeira chamada

## Detalhes de Implementação

```java
public CheckResponse handle(CheckPermissionQuery q) {
    var perms = getUserPermissions.handle(new GetUserPermissionsQuery(q.userId()));
    var allowed = perms.permissions().contains(q.permission());
    var source = allowed ? "active" : "denied"; // distinguir DEPRECATED via segunda consulta opcional
    return new CheckResponse(allowed, source);
}
```

**Convenções:**
- Mesmo padrão CQRS de 11.0
- Histograma com SLO buckets reduzidos (`0.001, 0.005, 0.01, 0.025, 0.05`)

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] cURL `POST /v1/authz/check` → 200 com `{allowed:true|false}`
- [ ] Teste perf `p95 < 50ms`
- [ ] Métrica `authz_check_seconds` exposta
