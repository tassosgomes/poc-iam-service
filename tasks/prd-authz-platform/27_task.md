---
status: pending
parallelizable: true
blocked_by: [24.0, 18.0]
---

<task_context>
<domain>apps/demo-mfe</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>low</complexity>
<dependencies>http_server</dependencies>
<unblocks>"30.0"</unblocks>
</task_context>

# Tarefa 27.0: Demo MFE (vendas) — SalesDashboard com guards via SDK React

## Relacionada às User Stories

- RF-09 (uso do SDK React), RF-14 (demo end-to-end), RF-15 (MFE pattern)

## Visão Geral

MFE demo de Vendas que renderiza dashboard com guards `<IfPermitted>` em botões e seções. Chama o Demo MS Java via `salesClient`. Exemplifica o fluxo "usuário recebe role → vê novos itens → pode executar ações".

## Requisitos

- Vite + Module Federation `remote` expondo `./DemoApp`
- `SalesDashboard`: tabela de pedidos + ações condicionadas
- `NewOrderButton`: visível apenas com `vendas.orders.create`
- `OrderListPage`: cada linha tem ação Cancelar visível com `vendas.orders.cancel`
- `salesClient`: chama `GET/POST/DELETE /orders` no Demo MS Java
- Tratamento de erro: 401 → reauth via App Shell, 403 → toast informativo

## Arquivos Envolvidos

- **Criar:**
  - `apps/demo-mfe/package.json`
  - `apps/demo-mfe/vite.config.ts`
  - `apps/demo-mfe/tsconfig.json`
  - `apps/demo-mfe/Dockerfile`
  - `apps/demo-mfe/nginx.conf`
  - `apps/demo-mfe/index.html`
  - `apps/demo-mfe/src/bootstrap.tsx`
  - `apps/demo-mfe/src/DemoApp.tsx`
  - `apps/demo-mfe/src/features/sales/SalesDashboard.tsx`
  - `apps/demo-mfe/src/features/sales/NewOrderButton.tsx`
  - `apps/demo-mfe/src/features/sales/OrderListPage.tsx`
  - `apps/demo-mfe/src/api/salesClient.ts`
  - `apps/demo-mfe/src/__tests__/SalesDashboard.test.tsx`
- **Modificar:**
  - `infra/docker/docker-compose.yml` (descomentar `demo-mfe`)
- **Skills para consultar durante implementação:**
  - `react-architecture`, `react-code-quality`, `react-testing`

## Subtarefas

- [ ] 27.1 Setup MFE
- [ ] 27.2 SalesDashboard + NewOrderButton + OrderListPage
- [ ] 27.3 salesClient com tratamento 401/403
- [ ] 27.4 Testes (renderiza com/sem permissões)

## Sequenciamento

- Bloqueado por: 24.0 (host), 18.0 (Demo MS Java alvo)
- Desbloqueia: 30.0
- Paralelizável: Sim (paralelo a 25.0 e 26.0)

## Rastreabilidade

- Esta tarefa cobre: RF-09 (consumo), RF-14 (frontend), RF-15
- Evidência esperada: usuário com role correta vê botão Novo Pedido; sem ele, botão some

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] Manual: login como `user-vendas-op` sem role → botão Novo Pedido invisível
- [ ] Atribui role com `vendas.orders.create` via PAP → após refresh, botão aparece
- [ ] Click cria pedido (POST /orders MS Java) → 200, lista atualiza
- [ ] Sem `vendas.orders.cancel` → botão Cancelar invisível na linha
