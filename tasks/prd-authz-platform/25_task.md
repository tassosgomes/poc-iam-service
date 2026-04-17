---
status: pending
parallelizable: true
blocked_by: [24.0, 5.0, 9.0, 14.0]
---

<task_context>
<domain>apps/pap-ui</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>high</complexity>
<dependencies>http_server</dependencies>
<unblocks>"30.0"</unblocks>
</task_context>

# Tarefa 25.0: PAP UI MFE — features de módulos + roles

## Relacionada às User Stories

- RF-05 (gestão de roles), RF-15 (PAP como MFE), RF-17 (gestão de chaves)

## Visão Geral

Primeira metade do PAP UI: features de módulos (criar, listar, rotacionar chave com alerta de idade > 180d) e roles (CRUD + clone + editor com permissões com descrição natural). Empacotado como remote MFE consumido pelo App Shell. Dogfooda o SDK React.

## Requisitos

- Vite + Module Federation `remote` expondo `./PapApp`
- React Router próprio interno: `/modules`, `/modules/:id`, `/roles`, `/roles/new`, `/roles/:id`
- `ModulesListPage`: lista módulos com idade da chave, status heartbeat, ação rotacionar
- `CreateModuleDialog`: form `name`, `allowedPrefix`, `description`; após criar, exibe secret em texto claro com botão copiar
- `RotateKeyDialog`: confirmação + exibe nova secret + lembrete dos 24h de grace
- `RolesListPage`: filtros por módulo + busca + paginação
- `RoleEditor`: form de role + multi-select de permissões agrupadas por recurso, com descrição
- Toda chamada protegida por `<IfPermitted permission="platform.admin.all">` ou similar conforme escopo
- Toasts/alertas para sucesso/erro

## Arquivos Envolvidos

- **Criar:**
  - `apps/pap-ui/package.json`
  - `apps/pap-ui/vite.config.ts`
  - `apps/pap-ui/tsconfig.json`
  - `apps/pap-ui/Dockerfile`
  - `apps/pap-ui/nginx.conf`
  - `apps/pap-ui/index.html`
  - `apps/pap-ui/src/bootstrap.tsx`
  - `apps/pap-ui/src/PapApp.tsx`
  - `apps/pap-ui/src/api/adminClient.ts`
  - `apps/pap-ui/src/api/types.ts`
  - `apps/pap-ui/src/features/modules/ModulesListPage.tsx`
  - `apps/pap-ui/src/features/modules/CreateModuleDialog.tsx`
  - `apps/pap-ui/src/features/modules/RotateKeyDialog.tsx`
  - `apps/pap-ui/src/features/modules/SecretRevealCard.tsx`
  - `apps/pap-ui/src/features/roles/RolesListPage.tsx`
  - `apps/pap-ui/src/features/roles/RoleEditor.tsx`
  - `apps/pap-ui/src/features/roles/PermissionPicker.tsx`
  - `apps/pap-ui/src/components/Toast.tsx`
  - `apps/pap-ui/src/__tests__/CreateModuleDialog.test.tsx`
  - `apps/pap-ui/src/__tests__/RoleEditor.test.tsx`
- **Modificar:**
  - `apps/app-shell/vite.config.ts` (confirmar remote URL)
  - `infra/docker/docker-compose.yml` (descomentar `pap-ui`)
- **Skills para consultar durante implementação:**
  - `react-architecture` — feature folders
  - `react-code-quality` — props tipadas
  - `react-testing` — Testing Library

## Subtarefas

- [ ] 25.1 Setup MFE Vite + bootstrap deferido
- [ ] 25.2 `adminClient` (fetch tipado para endpoints admin do AuthZ)
- [ ] 25.3 ModulesListPage + CreateModuleDialog + RotateKeyDialog + SecretRevealCard
- [ ] 25.4 RolesListPage + RoleEditor + PermissionPicker
- [ ] 25.5 Alerta visual quando idade da chave > 180 dias
- [ ] 25.6 Testes (renderiza form, valida campos, exibe secret após criar)

## Sequenciamento

- Bloqueado por: 24.0 (host), 5.0 (admin de módulos), 9.0 (roles), 14.0 (gauges/alertas de idade)
- Desbloqueia: 30.0
- Paralelizável: Sim (paralelo a 26.0 e 27.0)

## Rastreabilidade

- Esta tarefa cobre: parte de RF-05, RF-15, RF-17
- Evidência esperada: pelo PAP, criar módulo gera chave; rotação exibe nova chave; criar role com permissões funciona

## Detalhes de Implementação

**Componente `SecretRevealCard`:** alerta vermelho "Esta é sua única chance de copiar este secret. Após fechar, ele não poderá ser recuperado." + botão copiar (`navigator.clipboard.writeText`). Após confirmação, fecha.

**Convenções da stack:**
- Componentes PascalCase, pastas kebab-case (`react-architecture`)
- TS strict, sem `any` (`react-code-quality`)
- Toda mutação UX-confirmada (rotacionar, deletar)

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] Manual: criar módulo via PAP → secret aparece, é copiável, fecha sem volta
- [ ] Rotacionar → novo secret aparece + aviso de grace 24h
- [ ] Criar role com nome inválido → mensagem clara
- [ ] Sem permissão → seção não aparece (IfPermitted)
