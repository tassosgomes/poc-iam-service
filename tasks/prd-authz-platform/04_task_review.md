# Review da Task 4.0

## Status

**APROVADA**

## Escopo validado

- Task: `tasks/prd-authz-platform/04_task.md`
- PRD: `tasks/prd-authz-platform/prd.md`
- TechSpec: `tasks/prd-authz-platform/techspec.md`

## Validação de conformidade

### Requisitos da task

- `V1__init_schema.sql` criada com as tabelas `module`, `module_key`, `permission`, `role`, `role_permission`, `user_role` e `audit_event`.
- Índices exigidos presentes:
  - `idx_module_key_module_active`
  - `idx_user_role_user_active`
  - `idx_audit_event_type_time`
  - `idx_audit_event_target_time`
- CHECK constraints exigidas presentes:
  - `module_key.status IN ('ACTIVE','SUPERSEDED','REVOKED')`
  - `permission.status IN ('ACTIVE','DEPRECATED','STALE','REMOVED')`
- `CREATE EXTENSION IF NOT EXISTS pgcrypto;` presente.
- Todos os timestamps usam `TIMESTAMPTZ`.
- `V2__seed_platform_admin.sql` criada com:
  - módulo `platform`
  - role `PLATFORM_ADMIN`
  - permissão `platform.admin.all`
  - atribuição inicial para `user-admin`
- `application.yml` com Flyway habilitado em `classpath:db/migration`.

### Conformidade com a TechSpec

- Estrutura das tabelas e FKs aderente ao § **Modelos de Dados**.
- Naming em `snake_case` aderente à convenção da stack.
- Role seedada em `SCREAMING_SNAKE_CASE`, aderente à skill `roles-naming`.
- Configuração de banco e Flyway compatível com a skill `java-production-readiness` (`ddl-auto: validate`, `open-in-view: false`, migrations versionadas).

## Evidências executadas

### Build

- `mvn -pl apps/authz-service clean compile` ✅

### Validação runtime das migrations

Executado bootstrap do serviço com Postgres e mock OIDC ativos.

Evidências observadas:

- Log Flyway: `Successfully applied 2 migrations to schema "public", now at version v2`
- Log Spring Boot: `Started AuthzServiceApplication`

### Validação via banco

- `SELECT version, description, success FROM flyway_schema_history` → versões `1` e `2` com `success = true`
- `SELECT name, allowed_prefix FROM module WHERE name='platform'` → 1 linha
- `SELECT user_id, revoked_at FROM user_role WHERE user_id='user-admin'` → 1 linha ativa

## Achados relevantes

### Observação estrutural

- A implementação da task 4 está correta.
- Existe uma lacuna na TechSpec para tarefas futuras: o contrato de idempotência do sync de catálogo menciona `payload hash`, mas o modelo de dados atual não reserva campo persistente explícito para esse hash. Isso **não reprova** a task 4, mas merece ajuste antes da implementação da task 7.
- Trechos afetados:
  - `techspec.md` — interfaces de sync de catálogo
  - `techspec.md` — § Modelos de Dados

## Conclusão

A task 4.0 foi revisada e validada com sucesso. As migrations estão corretas, aplicam sem erro e seedam os dados esperados. A review está **APROVADA** e a entrega está **pronta para deploy**.
