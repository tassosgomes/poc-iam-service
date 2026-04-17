---
status: pending
parallelizable: false
blocked_by: [8.0]
---

<task_context>
<domain>backend/authz-service/iam</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>database</dependencies>
<unblocks>"10.0,25.0"</unblocks>
</task_context>

# Tarefa 9.0: Módulo `iam` — domain Role/UserRole + API CRUD de roles

## Relacionada às User Stories

- RF-05 — Gestão de papéis (roles) via PAP UI

## Visão Geral

Implementar o domínio de roles e a API CRUD: criar, listar, editar, clonar, remover. Cada role é vinculada a um `module_id` e composta por permissões `ACTIVE` ou `DEPRECATED`. Visualização inclui descrições naturais das permissões (vindas do `catalog`).

## Requisitos

- Domain: `Role`, `RolePermission` (associação), repos
- Handlers CQRS: `CreateRoleHandler`, `UpdateRoleHandler`, `CloneRoleHandler`, `DeleteRoleHandler`
- Query handler: `ListRolesQuery` (filtros: `moduleId`, `query`, paginação)
- `RoleController`: `POST /v1/roles`, `GET /v1/roles?moduleId=&q=`, `GET /v1/roles/{id}`, `PUT /v1/roles/{id}`, `POST /v1/roles/{id}/clone`, `DELETE /v1/roles/{id}`
- DTOs: `RoleDto` (com `permissions: [{code, description, status}]`), `CreateRoleRequest`, `UpdateRoleRequest`, `CloneRoleRequest`
- Autorização: `PLATFORM_ADMIN` ou `<MODULE>_USER_MANAGER` do mesmo `moduleId`
- Validação: nome SCREAMING_SNAKE_CASE com prefixo do módulo, único por módulo, permissões devem pertencer ao módulo
- Não permite excluir role com `user_role` ativos (retorna 409)

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/iam/domain/Role.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/domain/RoleRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/infra/JpaRoleRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/infra/RoleJpaEntity.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/CreateRoleHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/UpdateRoleHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/CloneRoleHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/DeleteRoleHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/application/ListRolesQuery.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/RoleController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/RoleDto.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/CreateRoleRequest.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/UpdateRoleRequest.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/CloneRoleRequest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/application/CreateRoleHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/application/CloneRoleHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/integration/RoleCrudIntegrationTest.java`
- **Skills para consultar durante implementação:**
  - `java-architecture` — handler CQRS
  - `java-code-quality` — Bean Validation
  - `common-restful-api` — paginação, RFC 9457
  - `common-roles-naming` — SCREAMING_SNAKE_CASE com prefixo

## Subtarefas

- [ ] 9.1 Domain + repos
- [ ] 9.2 Validação de nome (regex `^[A-Z]+(?:_[A-Z0-9]+)+$`, prefixo do módulo)
- [ ] 9.3 Handlers CRUD + Clone (clone gera novo nome com sufixo `_COPY`)
- [ ] 9.4 Controller + DTOs com authorities `PLATFORM_ADMIN` OR `MODULE_<X>_USER_MANAGER`
- [ ] 9.5 Bloqueio de delete se `user_role` ativo
- [ ] 9.6 Testes unitários e integração

## Sequenciamento

- Bloqueado por: 8.0
- Desbloqueia: 10.0, 25.0 (PAP UI)
- Paralelizável: Não (espinha)

## Rastreabilidade

- Esta tarefa cobre: RF-05
- Evidência esperada: cURL CRUD funcional; nome inválido → 422; permissão fora do módulo → 422

## Detalhes de Implementação

**Validação cross-domain:** ao criar/editar role com `permissionIds`, verificar que todas as permissões pertencem ao mesmo `moduleId` da role. Se não, 422 com `permission_module_mismatch`.

**Convenções da stack:**
- Records DTOs (`java-code-quality`)
- Naming `<MODULE>_<ROLE>` ex `VENDAS_GERENTE` (`common-roles-naming`)
- Spring Method Security `@PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN') or hasAuthority('ROLE_' + #request.module + '_USER_MANAGER')")`

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] `POST /v1/roles` com payload válido → 201
- [ ] Nome sem prefixo de módulo → 422
- [ ] Permissão de módulo errado → 422 `permission_module_mismatch`
- [ ] `DELETE` com user_role ativo → 409
- [ ] Admin de outro módulo → 403
