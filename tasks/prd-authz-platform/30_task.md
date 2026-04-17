---
status: pending
parallelizable: false
blocked_by: [18.0, 22.0, 25.0, 26.0, 27.0]
---

<task_context>
<domain>tests/e2e</domain>
<type>testing</type>
<scope>core_feature</scope>
<complexity>high</complexity>
<dependencies>http_server,external_apis,database</dependencies>
<unblocks>"31.0"</unblocks>
</task_context>

# Tarefa 30.0: Suíte E2E Playwright cobrindo RF-14

## Relacionada às User Stories

- RF-14 — Demos showcase end-to-end

## Visão Geral

Suíte Playwright que sobe o `docker-compose` completo e exercita o fluxo da seção 6/RF-14 do PRD: criação de módulo → role → atribuição → usuário enxerga → execução → revogação → usuário deixa de enxergar.

## Requisitos

- Setup Playwright em `tests/e2e` (pnpm package)
- `playwright.config.ts` apontando para `http://localhost` (gateway nginx)
- Helpers de auth (login programático no mock-oauth2-server) e helpers de admin (cria módulo via API)
- Cenários:
  - "Onboarding completo de Vendas": admin cria módulo `vendas`, copia chave, configura demo MS, atribui role `VENDAS_OPERADOR` a `user-vendas-op`, faz login, vê botão Novo Pedido, cria pedido
  - "Revogação reflete no UI": admin revoga role, usuário refresha, botão desaparece
  - "Cross-module bloqueado": admin de Vendas tenta atribuir role de Estoque, recebe erro
  - "Auditoria reflete ação": após cada ação, audit log mostra evento
- Reports HTML + screenshots em failure
- Roda em CI (workflow separado ou job dentro de `ci.yml`)

## Arquivos Envolvidos

- **Criar:**
  - `tests/e2e/package.json`
  - `tests/e2e/playwright.config.ts`
  - `tests/e2e/tests/onboarding.spec.ts`
  - `tests/e2e/tests/revogacao.spec.ts`
  - `tests/e2e/tests/cross-module.spec.ts`
  - `tests/e2e/tests/audit.spec.ts`
  - `tests/e2e/helpers/auth.ts`
  - `tests/e2e/helpers/admin.ts`
  - `tests/e2e/helpers/dockerStack.ts` (sobe stack via `docker compose up -d --wait`)
  - `tests/e2e/.env.example`
- **Modificar:**
  - `pnpm-workspace.yaml` (adicionar `tests/e2e`)
  - `.github/workflows/ci.yml` (adicionar job e2e)
- **Skills para consultar durante implementação:**
  - `playwright-cli` — patterns Playwright

## Subtarefas

- [ ] 30.1 Setup Playwright + helpers
- [ ] 30.2 Cenário onboarding (completo)
- [ ] 30.3 Cenário revogação
- [ ] 30.4 Cenário cross-module
- [ ] 30.5 Cenário auditoria
- [ ] 30.6 Workflow CI rodando E2E em headless

## Sequenciamento

- Bloqueado por: 18.0, 22.0, 25.0, 26.0, 27.0
- Desbloqueia: 31.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-14 (validação executável)
- Evidência esperada: 4 specs verdes, report HTML disponível

## Detalhes de Implementação

**`dockerStack.ts` exemplo:**
```ts
import { execSync } from 'child_process';
export async function up() {
  execSync('docker compose -f infra/docker/docker-compose.yml up -d --wait', { stdio: 'inherit' });
}
export async function down() {
  execSync('docker compose -f infra/docker/docker-compose.yml down -v', { stdio: 'inherit' });
}
```

**Convenções:**
- Specs idempotentes: cada spec cria seus próprios módulos com sufixo random
- `globalSetup` sobe a stack uma vez por suíte
- Screenshots+video em falha apenas (`use.video: 'retain-on-failure'`)

## Critérios de Sucesso (Verificáveis)

- [ ] `pnpm --filter e2e test` roda 4 specs com sucesso
- [ ] CI mostra E2E job verde
- [ ] Falha gera report com screenshots
