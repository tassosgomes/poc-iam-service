---
status: pending
parallelizable: true
blocked_by: [11.0]
---

<task_context>
<domain>libs/sdk-react</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"24.0,25.0,27.0,29.0"</unblocks>
</task_context>

# Tarefa 23.0: SDK React — AuthzProvider + hooks `usePermission` + `<IfPermitted>`

## Relacionada às User Stories

- RF-09 — SDK React com bulk fetch e cache de sessão

## Visão Geral

SDK React publicado em GitHub Packages. Padrão: `<AuthzProvider>` no topo do MFE consome `useAuth()` do App Shell, faz **uma** chamada ao `GET /v1/users/{id}/permissions` por sessão e expõe via Context. Hooks `usePermission(code)` e componente `<IfPermitted permission=...>` resolvem síncronos a partir do Context. Cache expira junto com o JWT.

## Requisitos

- Package `@platform/sdk-react` em `libs/sdk-react`, build via Rollup → ESM + CJS + d.ts
- `AuthzProvider`: aceita `userId`, `token`, `baseUrl`, opcional `onTokenExpired`
- Faz fetch único, mantém em estado React; refetch quando `userId`/`token` mudar
- `usePermission(code: string): boolean` (síncrono em memória)
- `useAllPermissions(): { permissions: string[], loading, error }`
- `<IfPermitted permission="vendas.orders.create" fallback={null}>{...}</IfPermitted>`
- Suporte wildcard `vendas.*`
- TypeScript strict, sem `any`
- `package.json` com `publishConfig.registry` apontando para GitHub Packages

## Arquivos Envolvidos

- **Criar:**
  - `libs/sdk-react/package.json`
  - `libs/sdk-react/rollup.config.mjs`
  - `libs/sdk-react/tsconfig.json`
  - `libs/sdk-react/src/index.ts`
  - `libs/sdk-react/src/AuthzProvider.tsx`
  - `libs/sdk-react/src/AuthzContext.ts`
  - `libs/sdk-react/src/hooks/usePermission.ts`
  - `libs/sdk-react/src/hooks/useAllPermissions.ts`
  - `libs/sdk-react/src/components/IfPermitted.tsx`
  - `libs/sdk-react/src/api/authzHttpClient.ts`
  - `libs/sdk-react/src/utils/permissionMatcher.ts`
  - `libs/sdk-react/src/types.ts`
  - `libs/sdk-react/src/__tests__/AuthzProvider.test.tsx`
  - `libs/sdk-react/src/__tests__/usePermission.test.tsx`
  - `libs/sdk-react/src/__tests__/IfPermitted.test.tsx`
  - `libs/sdk-react/src/__tests__/permissionMatcher.test.ts`
- **Modificar:**
  - `pnpm-workspace.yaml` (já cobre `libs/*`, confirmar)
- **Skills para consultar durante implementação:**
  - `react-architecture` — public API via index.ts
  - `react-code-quality` — TS strict, hooks tipados, naming
  - `react-testing` — Vitest + Testing Library

## Subtarefas

- [ ] 23.1 Configurar Rollup para ESM/CJS/d.ts
- [ ] 23.2 `AuthzContext` + `AuthzProvider` (useEffect, loading state)
- [ ] 23.3 `permissionMatcher` com wildcard
- [ ] 23.4 `usePermission`, `useAllPermissions`
- [ ] 23.5 `<IfPermitted>` com fallback
- [ ] 23.6 Testes Vitest cobrindo: mount, fetch único, hook, wildcard, expiração

## Sequenciamento

- Bloqueado por: 11.0
- Desbloqueia: 24.0, 25.0, 27.0, 29.0
- Paralelizável: Sim (paralelo a 18.0/22.0)

## Rastreabilidade

- Esta tarefa cobre: RF-09
- Evidência esperada: 1 fetch por sessão; `usePermission` retorna boolean síncrono; expira com JWT

## Detalhes de Implementação

**API pública (`index.ts`):**
```ts
export { AuthzProvider } from './AuthzProvider';
export { usePermission } from './hooks/usePermission';
export { useAllPermissions } from './hooks/useAllPermissions';
export { IfPermitted } from './components/IfPermitted';
export type { AuthzConfig, UserPermissions } from './types';
```

**`<IfPermitted>` exemplo:**
```tsx
<IfPermitted permission="vendas.orders.create" fallback={<Disabled />}>
  <NewOrderButton />
</IfPermitted>
```

**Convenções da stack:**
- TS strict, sem `any`, props tipadas com interface (`react-code-quality`)
- Componentes em PascalCase, pastas em kebab-case (`react-architecture`)
- Vitest + Testing Library (`react-testing`)
- Não persistir token em `localStorage` aqui; provider recebe via prop

## Critérios de Sucesso (Verificáveis)

- [ ] `pnpm --filter @platform/sdk-react test` passa
- [ ] `pnpm --filter @platform/sdk-react build` produz `dist/index.{esm,cjs}.js` + `.d.ts`
- [ ] Validável manualmente: `<AuthzProvider>` faz exatamente 1 fetch para um par `userId,token`
- [ ] `usePermission('vendas.orders.create')` retorna `true|false` síncrono
