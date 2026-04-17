---
status: pending
parallelizable: false
blocked_by: [12.0]
---

<task_context>
<domain>backend/authz-service/audit</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>database</dependencies>
<unblocks>"14.0,26.0"</unblocks>
</task_context>

# Tarefa 13.0: Módulo `audit` — persistência, integração @Async em handlers e endpoint

## Relacionada às User Stories

- RF-13 — Log de auditoria de atribuições e eventos de catálogo

## Visão Geral

Implementar a infra de auditoria: tabela `audit_event` (já criada em 4.0), publisher `@Async`, eventos disparados pelos handlers existentes (CreateModule, RotateKey, Sync, AssignRole, RevokeRole, KeyAuthFailed) e endpoint admin para consulta paginada com filtros.

## Requisitos

- Domain: `AuditEvent`, `AuditEventRepository`
- `AuditEventPublisher` (impl real da interface stub criada em 10.0): persiste em tabela via `@Async` + `@Transactional(propagation=REQUIRES_NEW)`
- Integração nos handlers:
  - 5.0 `CreateModuleHandler` → `MODULE_CREATED`
  - 5.0 `RotateKeyHandler` → `KEY_ROTATED`
  - 7.0 `SyncCatalogHandler` → `CATALOG_SYNC` (apenas se `changed:true`)
  - 10.0 `AssignRoleHandler` → `ROLE_ASSIGNED`
  - 10.0 `RevokeRoleHandler` → `ROLE_REVOKED`
  - 6.0 `ModuleBearerAuthenticationFilter` → `KEY_AUTH_FAILED` (em falhas)
- `AuditController`: `GET /v1/audit/events?eventType=&moduleId=&actorId=&from=&to=&page=&size=`
- Autorização: `PLATFORM_ADMIN` ou `AUDITOR`
- Configuração `@EnableAsync` + `TaskExecutor` dedicado (`audit-executor`)

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/audit/domain/AuditEvent.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/domain/AuditEventRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/infra/JpaAuditEventRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/infra/AuditEventJpaEntity.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/application/AuditEventPublisherImpl.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/application/RecordAuditEventHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/api/AuditController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/api/dto/AuditEventDto.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/api/dto/AuditEventQueryParams.java`
  - `apps/authz-service/src/main/java/com/platform/authz/config/AsyncConfig.java`
  - `apps/authz-service/src/test/java/com/platform/authz/audit/application/RecordAuditEventHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/audit/integration/AuditTrailIntegrationTest.java`
- **Modificar:**
  - Handlers de 5.0, 7.0, 10.0 para invocar `AuditEventPublisher.publish(...)`
  - `ModuleBearerAuthenticationFilter` (6.0) idem
- **Skills para consultar durante implementação:**
  - `java-architecture` — Outbox/Async pattern
  - `java-production-readiness` — auditoria imutável
  - `common-restful-api` — paginação

## Subtarefas

- [ ] 13.1 Domain + repo + entity
- [ ] 13.2 `AuditEventPublisherImpl` `@Async` REQUIRES_NEW
- [ ] 13.3 `AsyncConfig` com TaskExecutor dedicado
- [ ] 13.4 Wire-up nos handlers existentes (modificações)
- [ ] 13.5 Controller + paginação (Spring Data `Page`)
- [ ] 13.6 Testes integração end-to-end (cria módulo → audit event registrado)

## Sequenciamento

- Bloqueado por: 12.0 (formalmente — para fechar a Lane B sequencial; pode iniciar antes em paralelo se preferir)
- Desbloqueia: 14.0, 26.0 (PAP UI consome auditoria)
- Paralelizável: Não (fim da Lane B)

## Rastreabilidade

- Esta tarefa cobre: RF-13
- Evidência esperada: cada operação registrada gera linha em `audit_event`; consulta filtrada funciona

## Detalhes de Implementação

**Payload JSONB exemplo:**
```json
{
  "event_type": "ROLE_ASSIGNED",
  "actor_id": "user-vendas-mgr",
  "target": "user-vendas-op",
  "payload": { "role_id": "...", "role_name": "VENDAS_OPERADOR", "module": "vendas" },
  "source_ip": "10.0.1.5"
}
```

**Convenções:**
- Imutável: nunca UPDATE/DELETE — só INSERT
- TaskExecutor com queue capacity 1000, fallback log de erro
- Logs JSON estruturados ecoam mesmo conteúdo do evento

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] Criar módulo via cURL → linha aparece em `audit_event` com `event_type=MODULE_CREATED`
- [ ] Atribuir role → linha `ROLE_ASSIGNED` aparece
- [ ] Sync com chave inválida → linha `KEY_AUTH_FAILED`
- [ ] `GET /v1/audit/events?eventType=ROLE_ASSIGNED` retorna paginado
- [ ] Não-admin → 403
