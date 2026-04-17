---
status: pending
parallelizable: false
blocked_by: [23.0]
---

<task_context>
<domain>apps/app-shell</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>high</complexity>
<dependencies>http_server,external_apis</dependencies>
<unblocks>"25.0,26.0,27.0"</unblocks>
</task_context>

# Tarefa 24.0: App Shell — Vite host Module Federation + OIDC client + roteamento

## Relacionada às User Stories

- RF-01 (login OIDC), suporte a RF-15 (PAP como MFE) e RF-14 (Demo MFE como MFE)

## Visão Geral

Aplicação host React+Vite que carrega os MFEs remotos (PAP UI e Demo MFE) via Module Federation, integra OIDC com CyberArk (mock em dev), expõe contexto de auth para os filhos, e roteia entre eles. Layout básico (Header, SideNav).

## Requisitos

- Vite + `@originjs/vite-plugin-federation` em modo `host`, declarando remotes `papUi` e `demoMfe`
- `oidc-client-ts` para Authorization Code + PKCE contra mock-oauth2-server (em dev) / CyberArk
- `OidcProvider` armazena tokens em memória (não localStorage) e expõe `useAuth()` (`{ user, token, login, logout }`)
- Wrap raiz com `<AuthzProvider>` do SDK React (sessão única)
- React Router v6 com rotas: `/` (home), `/pap/*` (lazy MFE), `/demo/*` (lazy MFE), `/login`, `/callback`
- Layout: Header com avatar/logout, SideNav com links condicionados via `<IfPermitted>`
- Dockerfile com Nginx servindo build estático; `nginx.conf` com SPA fallback + proxy `/api` → `authz-service`

## Arquivos Envolvidos

- **Criar:**
  - `apps/app-shell/package.json`
  - `apps/app-shell/vite.config.ts`
  - `apps/app-shell/tsconfig.json`
  - `apps/app-shell/index.html`
  - `apps/app-shell/Dockerfile`
  - `apps/app-shell/nginx.conf`
  - `apps/app-shell/src/main.tsx`
  - `apps/app-shell/src/bootstrap.tsx`
  - `apps/app-shell/src/App.tsx`
  - `apps/app-shell/src/auth/OidcProvider.tsx`
  - `apps/app-shell/src/auth/useAuth.ts`
  - `apps/app-shell/src/auth/oidcConfig.ts`
  - `apps/app-shell/src/router/routes.tsx`
  - `apps/app-shell/src/router/MfeLoader.tsx`
  - `apps/app-shell/src/layout/Shell.tsx`
  - `apps/app-shell/src/layout/Header.tsx`
  - `apps/app-shell/src/layout/SideNav.tsx`
  - `apps/app-shell/src/pages/HomePage.tsx`
  - `apps/app-shell/src/pages/LoginPage.tsx`
  - `apps/app-shell/src/pages/CallbackPage.tsx`
  - `apps/app-shell/src/__tests__/router.test.tsx`
- **Modificar:**
  - `infra/docker/docker-compose.yml` (descomentar `app-shell`)
  - `infra/docker/nginx-gateway/nginx.conf` (rota `/` → app-shell)
- **Skills para consultar durante implementação:**
  - `react-architecture` — feature folders, aliases
  - `react-code-quality` — TS strict
  - `react-testing` — testing-library para router
  - `react-production-readiness` — separação de configs por ambiente

## Subtarefas

- [ ] 24.1 Vite config com Module Federation host
- [ ] 24.2 OIDC integration + callback handling
- [ ] 24.3 Wrap com AuthzProvider
- [ ] 24.4 Router + lazy loaders dos MFEs
- [ ] 24.5 Layout (Shell, Header, SideNav)
- [ ] 24.6 Dockerfile + nginx.conf
- [ ] 24.7 Smoke tests (renderiza, redireciona quando não autenticado)

## Sequenciamento

- Bloqueado por: 23.0
- Desbloqueia: 25.0, 26.0, 27.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-01, suporte a RF-14, RF-15
- Evidência esperada: `docker-compose up app-shell` + login no mock CyberArk → user autenticado, MFEs carregam

## Detalhes de Implementação

**`vite.config.ts` host:**
```ts
import federation from '@originjs/vite-plugin-federation';

export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'shell',
      remotes: {
        papUi:   'http://localhost/pap/assets/remoteEntry.js',
        demoMfe: 'http://localhost/demo/assets/remoteEntry.js',
      },
      shared: ['react', 'react-dom', '@platform/sdk-react']
    })
  ]
});
```

**Convenções da stack:**
- Token em memória; não usar `localStorage` para JWT (`react-production-readiness`)
- Path alias `@/` (`react-architecture`)
- Estados `loading | authenticated | unauthenticated` explícitos

## Critérios de Sucesso (Verificáveis)

- [ ] `pnpm --filter app-shell build` produz `dist/`
- [ ] `docker build apps/app-shell` sucesso
- [ ] Acessar `http://localhost/` sem auth redireciona para `/login`
- [ ] Após login no mock → home renderiza com user no header
- [ ] `/pap` carrega o remote (mesmo que vazio nesta task)
