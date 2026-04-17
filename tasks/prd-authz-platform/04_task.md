---
status: completed
parallelizable: false
blocked_by: [3.0]
---

<task_context>
<domain>backend/authz-service</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>database</dependencies>
<unblocks>"5.0"</unblocks>
</task_context>

# Tarefa 4.0: AuthZ Service — schema Postgres e migrations Flyway ✅ CONCLUÍDA

## Relacionada às User Stories

- Suporte a RF-02, RF-03, RF-05, RF-06, RF-07, RF-08, RF-12, RF-13, RF-17

## Visão Geral

Criar todas as tabelas do AuthZ Service via migration Flyway `V1__init_schema.sql`, conforme TechSpec § Modelos de Dados. Adicionar `V2__seed_platform_admin.sql` plantando o módulo `platform` + role `PLATFORM_ADMIN` + atribuição inicial ao usuário `user-admin` (do mock CyberArk).

## Requisitos

- Migration `V1` com tabelas: `module`, `module_key`, `permission`, `role`, `role_permission`, `user_role`, `audit_event`
- Índices: `idx_module_key_module_active`, `idx_user_role_user_active`, `idx_audit_event_type_time`, `idx_audit_event_target_time`
- `V2` seed: módulo `platform` com `allowed_prefix='platform'`, role `PLATFORM_ADMIN` com permissão `platform.admin.all`, atribuição a `user-admin`
- Habilitar Flyway no `application.yml`

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/resources/db/migration/V1__init_schema.sql`
  - `apps/authz-service/src/main/resources/db/migration/V2__seed_platform_admin.sql`
- **Referência:**
  - `tasks/prd-authz-platform/techspec.md` § Modelos de Dados
- **Skills para consultar durante implementação:**
  - `java-architecture` — naming snake_case nas tabelas, FK obrigatórias

## Subtarefas

- [x] 4.1 Escrever V1 com todas as tabelas + FKs + checks de status
- [x] 4.2 Escrever V2 seed (UUIDs fixos para `platform` e `user-admin`)
- [x] 4.3 Subir `authz-service` e validar Flyway aplica migrations sem erro
- [x] 4.4 Validar via `psql` que as tabelas + dados de seed existem

## Sequenciamento

- Bloqueado por: 3.0
- Desbloqueia: 5.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: schema completo do MVP
- Evidência esperada: `flyway info` mostra V1 e V2 aplicadas

## Detalhes de Implementação

**Status enums (CHECK constraints):**
- `module_key.status IN ('ACTIVE','SUPERSEDED','REVOKED')`
- `permission.status IN ('ACTIVE','DEPRECATED','STALE','REMOVED')`

**UUID fixos para seed (facilita testes):**
- `platform` module: `00000000-0000-0000-0000-000000000001`
- `PLATFORM_ADMIN` role: `00000000-0000-0000-0000-000000000002`
- `platform.admin.all` permission: `00000000-0000-0000-0000-000000000003`

**Convenções da stack:**
- Naming snake_case em tabelas e colunas (`java-architecture`)
- TIMESTAMPTZ para todos os timestamps
- `gen_random_uuid()` (extensão `pgcrypto`) — habilitar no início da V1 com `CREATE EXTENSION IF NOT EXISTS pgcrypto;`

## Critérios de Sucesso (Verificáveis)

- [x] `mvn -pl apps/authz-service spring-boot:run` aplica V1 e V2 (verificar via logs Flyway)
- [x] `psql -c "SELECT version FROM flyway_schema_history"` mostra `1` e `2`
- [x] `psql -c "SELECT name FROM module WHERE name='platform'"` retorna 1 linha
- [x] `psql -c "SELECT user_id FROM user_role WHERE user_id='user-admin'"` retorna 1 linha

## Conclusão

- [x] 4.0 AuthZ Service — schema Postgres e migrations Flyway ✅ CONCLUÍDA
  - [x] 4.1 Implementação completada
  - [x] 4.2 Definição da tarefa, PRD e tech spec validados
  - [x] 4.3 Análise de regras e conformidade verificadas
  - [x] 4.4 Revisão de código completada
  - [x] 4.5 Pronto para deploy
