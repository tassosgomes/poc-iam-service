---
status: pending
parallelizable: false
blocked_by: [13.0]
---

<task_context>
<domain>backend/authz-service/modules</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>database,temporal</dependencies>
<unblocks>"25.0"</unblocks>
</task_context>

# Tarefa 14.0: Lifecycle — StaleModuleDetector e deprecation de permissões

## Relacionada às User Stories

- RF-12 (lifecycle active/deprecated/removed) e RF-16 (stale após 7 dias sem heartbeat)

## Visão Geral

Implementar dois jobs scheduled: (a) `StaleModuleDetector` que marca permissões como `STALE` quando o módulo não emite heartbeat por > 7 dias e gera audit event + alerta; (b) `PermissionSunsetJob` que marca permissões `DEPRECATED` cujo `sunset_at` passou para `REMOVED`. Também o gauge `authz_stale_modules_count` e `authz_module_key_age_days{module}`.

## Requisitos

- `StaleModuleDetector` `@Scheduled(fixedRate=1h)`:
  - Encontra módulos com `last_heartbeat_at < now()-7d`
  - Marca permissões do módulo como `STALE`
  - Emite `AuditEvent` `MODULE_WENT_STALE`
- `PermissionSunsetJob` `@Scheduled(cron="0 0 3 * * *")` (diário 3h):
  - Marca permissões `DEPRECATED` com `sunset_at < now()` como `REMOVED`
- Gauges Micrometer: `authz_stale_modules_count`, `authz_module_key_age_max_days`, `authz_permissions_deprecated_count`
- `@EnableScheduling` em config

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/modules/application/StaleModuleDetector.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/application/PermissionSunsetJob.java`
  - `apps/authz-service/src/main/java/com/platform/authz/config/SchedulingConfig.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/observability/PlatformGauges.java`
  - `apps/authz-service/src/test/java/com/platform/authz/modules/application/StaleModuleDetectorTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/catalog/application/PermissionSunsetJobTest.java`
- **Modificar:**
  - `application.yml` (configurar cron e fixedRate via property)
- **Skills para consultar durante implementação:**
  - `java-architecture` — scheduled jobs em application layer
  - `java-observability` — gauges, métricas custom

## Subtarefas

- [ ] 14.1 `StaleModuleDetector` lógica + audit event
- [ ] 14.2 `PermissionSunsetJob` lógica
- [ ] 14.3 `SchedulingConfig` + `@EnableScheduling`
- [ ] 14.4 `PlatformGauges` registrando gauges
- [ ] 14.5 Testes com clock injetado (`Clock.fixed`)

## Sequenciamento

- Bloqueado por: 13.0 (precisa do publisher real para audit)
- Desbloqueia: 25.0 (PAP UI consome `last_heartbeat_at` e idade da chave para alertas)
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-12, parte de RF-16 (stale detection)
- Evidência esperada: simulando módulo > 7d sem heartbeat, permissões viram STALE; permissões `DEPRECATED` com sunset passado viram REMOVED

## Detalhes de Implementação

**Property defaults:**
```yaml
authz:
  lifecycle:
    stale-after: 7d
    sunset-cron: "0 0 3 * * *"
    detector-rate: 1h
```

**Convenções:**
- Usar `Clock` injetado para testabilidade (`java-architecture`)
- Logs INFO com totais (`detector.run found_stale_modules=N marked_permissions=M`)
- Não disparar audit event se nada mudou (idempotente)

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam (com clock manipulado)
- [ ] Inserir módulo com `last_heartbeat_at = now()-8d` + permissões `ACTIVE` → após detector rodar, status `STALE`
- [ ] Inserir permissão `DEPRECATED` com `sunset_at = now()-1d` → após sunset job, status `REMOVED`
- [ ] `/actuator/prometheus` mostra `authz_stale_modules_count`
