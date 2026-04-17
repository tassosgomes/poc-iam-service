---
status: pending
parallelizable: false
blocked_by: [9.0]
---

<task_context>
<domain>backend/authz-service/iam</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>database</dependencies>
<unblocks>"11.0,26.0"</unblocks>
</task_context>

# Tarefa 10.0: Módulo `iam` — atribuição/revogação de roles com escopo por módulo (RF-07)

## Relacionada às User Stories

- RF-06 (atribuição self-service) e RF-07 (delegação por módulo `can_manage_users`)

## Visão Geral

API para admins atribuírem/revogarem roles a usuários, com enforcement do escopo por módulo: um admin com role `<MODULE>_USER_MANAGER` só pode atribuir/revogar roles do mesmo módulo. Toda operação gera audit event (consumido pela 13.0).

## Requisitos

- Domain: `UserRoleAssignment`, `UserRoleRepository`
- Handlers: `AssignRoleHandler`, `RevokeRoleHandler`, `ListUserRolesQuery`
- `UserRoleController`: `POST /v1/users/{userId}/roles`, `DELETE /v1/users/{userId}/roles/{roleId}`, `GET /v1/users/{userId}/roles`
- Validações:
  - Role existe
  - Usuário existe (consulta CyberArk via cliente da 8.0)
  - Admin tem escopo no módulo da role (PLATFORM_ADMIN OU `<MODULE>_USER_MANAGER` do mesmo módulo)
- Cada operação publica `AuditEvent` (`ROLE_ASSIGNED` / `ROLE_REVOKED`) — handler de auditoria entra em 13.0; aqui usar interface stub
- `assigned_by`, `revoked_by` populados a partir do JWT (claim `sub`)
- Idempotência: re-atribuição da mesma role retorna 200 sem efeito

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/iam/domain/UserRoleAssignment.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/domain/UserRoleRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/infra/JpaUserRoleRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/infra/UserRoleJpaEntity.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/AssignRoleHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/RevokeRoleHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/ListUserRolesQuery.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/AdminScopeChecker.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/UserRoleController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/AssignRoleRequest.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/UserRoleDto.java`
  - `apps/authz-service/src/main/java/com/platform/authz/audit/application/AuditEventPublisher.java` (interface stub; impl em 13.0)
  - `apps/authz-service/src/test/java/com/platform/authz/iam/application/AssignRoleHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/application/AdminScopeCheckerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/integration/UserRoleAssignmentIntegrationTest.java`
- **Skills para consultar durante implementação:**
  - `java-architecture` — handler CQRS
  - `java-production-readiness` — auditoria de mutações
  - `common-roles-naming` — escopo via authority

## Subtarefas

- [ ] 10.1 Domain + repos
- [ ] 10.2 `AdminScopeChecker` (lógica central de RF-07)
- [ ] 10.3 Handlers `AssignRoleHandler`/`RevokeRoleHandler` com idempotência e audit publish
- [ ] 10.4 Controller + DTOs + `@PreAuthorize`
- [ ] 10.5 Testes unitários cobrindo: PLATFORM_ADMIN OK, scoped manager OK same module, scoped manager BLOQUEIA outro módulo, idempotência, role inexistente, user inexistente
- [ ] 10.6 Teste integração end-to-end

## Sequenciamento

- Bloqueado por: 9.0
- Desbloqueia: 11.0, 26.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-06, RF-07
- Evidência esperada: assignment funciona; cross-module bloqueado; audit event registrado (verificável após 13.0)

## Detalhes de Implementação

**`AdminScopeChecker`:**
```java
public void requireScope(Authentication auth, UUID roleModuleId) {
    if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"))) return;
    String moduleName = moduleRepository.findById(roleModuleId).orElseThrow().name();
    boolean canManage = auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + moduleName.toUpperCase() + "_USER_MANAGER"));
    if (!canManage) throw new AdminScopeViolationException(roleModuleId, moduleName);
}
```

**Convenções da stack:**
- Idempotência: query `existsByUserIdAndRoleIdAndRevokedAtIsNull` antes de inserir
- Logs: `INFO role_assigned actor={} target={} role={}`
- Métrica: `authz_role_assignment_total{action,module,result}`

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] PLATFORM_ADMIN atribui role de qualquer módulo → 201
- [ ] VENDAS_USER_MANAGER atribui role do módulo vendas → 201
- [ ] VENDAS_USER_MANAGER tenta atribuir role do módulo estoque → 403 ProblemDetail `admin_scope_violation`
- [ ] Re-atribuição idempotente → 200
- [ ] Revogação atualiza `revoked_at` e `revoked_by`
- [ ] `AuditEventPublisher.publish(...)` é invocado (verificável via mock)
