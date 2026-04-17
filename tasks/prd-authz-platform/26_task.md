---
status: pending
parallelizable: true
blocked_by: [24.0, 8.0, 10.0, 13.0]
---

<task_context>
<domain>apps/pap-ui</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"30.0"</unblocks>
</task_context>

# Tarefa 26.0: PAP UI MFE — features de usuários + auditoria

## Relacionada às User Stories

- RF-06 (atribuição), RF-07 (escopo), RF-13 (auditoria), RF-15 (PAP MFE)

## Visão Geral

Segunda metade do PAP UI: busca de usuários (consome `/v1/users/search` proxy CyberArk), atribuição/revogação de roles, e tela de auditoria com filtros.

## Requisitos

- `UserSearchPage`: busca por nome/email com debounce, filtra por módulo conforme escopo
- `AssignRolesDialog`: seleciona usuário + roles disponíveis (multi-select); confirmação
- `UserRoleListPage` (ou seção): lista atribuições atuais com botão revogar
- `AuditLogPage`: filtros (`eventType`, `moduleId`, `actorId`, `from`, `to`), paginação, expansão de payload JSON
- Roteamento interno: `/users`, `/users/:userId`, `/audit`
- Toda chamada com `<IfPermitted>` apropriado

## Arquivos Envolvidos

- **Criar:**
  - `apps/pap-ui/src/features/users/UserSearchPage.tsx`
  - `apps/pap-ui/src/features/users/UserDetailsPage.tsx`
  - `apps/pap-ui/src/features/users/AssignRolesDialog.tsx`
  - `apps/pap-ui/src/features/users/UserRolesList.tsx`
  - `apps/pap-ui/src/features/audit/AuditLogPage.tsx`
  - `apps/pap-ui/src/features/audit/AuditEventDetailsDrawer.tsx`
  - `apps/pap-ui/src/hooks/useDebounce.ts`
  - `apps/pap-ui/src/__tests__/UserSearchPage.test.tsx`
  - `apps/pap-ui/src/__tests__/AuditLogPage.test.tsx`
- **Modificar:**
  - `apps/pap-ui/src/PapApp.tsx` (adicionar rotas)
  - `apps/pap-ui/src/api/adminClient.ts` (endpoints users + audit)
- **Skills para consultar durante implementação:**
  - `react-architecture`, `react-code-quality`, `react-testing`

## Subtarefas

- [ ] 26.1 `useDebounce` + `UserSearchPage`
- [ ] 26.2 `AssignRolesDialog` + `UserRolesList`
- [ ] 26.3 `AuditLogPage` com filtros e paginação
- [ ] 26.4 `AuditEventDetailsDrawer` (json viewer)
- [ ] 26.5 Testes

## Sequenciamento

- Bloqueado por: 24.0, 8.0, 10.0, 13.0
- Desbloqueia: 30.0
- Paralelizável: Sim (paralelo a 25.0 e 27.0)

## Rastreabilidade

- Esta tarefa cobre: parte de RF-06, RF-07, RF-13, RF-15
- Evidência esperada: buscar usuário, atribuir role, ver auditoria reflete a ação

## Detalhes de Implementação

**Convenções:**
- Debounce 300ms na busca (`react-code-quality`)
- Paginação server-side com cursor opcional (servidor já retorna `Page`)
- Tabela de auditoria com timezone do navegador

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] Buscar `vmgr` retorna usuário; atribuir role aparece na lista; revogar atualiza
- [ ] AuditLogPage filtra `eventType=ROLE_ASSIGNED` e mostra eventos
- [ ] Admin de Vendas só vê usuários do módulo Vendas (RF-07 enforcement no backend, refletido na UI)
