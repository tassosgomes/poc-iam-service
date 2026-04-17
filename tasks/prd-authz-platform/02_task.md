---
status: pending
parallelizable: true
blocked_by: []
---

<task_context>
<domain>infra/docker</domain>
<type>configuration</type>
<scope>configuration</scope>
<complexity>medium</complexity>
<dependencies>external_apis</dependencies>
<unblocks>"3.0,8.0,30.0"</unblocks>
</task_context>

# Tarefa 2.0: Docker Compose base + mock CyberArk + nginx gateway

## Relacionada às User Stories

- RF-01 (CyberArk via mock para dev local)
- Suporte a RF-14, RF-16 (ambiente para auto-registro funcionar)

## Visão Geral

Criar a stack completa de desenvolvimento local via `docker-compose`: Postgres, mock-oauth2-server (mocka CyberArk), nginx gateway que proxia o App Shell + MFEs + AuthZ Service. K8s está fora do escopo (TechSpec § E).

## Requisitos

- `docker-compose.yml` com serviços: `postgres`, `cyberark-mock`, `nginx-gateway`, e placeholders comentados para `authz-service`, `app-shell`, `pap-ui`, `demo-mfe`, `demo-ms-java`, `demo-ms-dotnet`
- Postgres 16 com volume persistente, porta 5432, init script criando database `authz` e user `authz`
- `mock-oauth2-server` (`ghcr.io/navikt/mock-oauth2-server`) com seed de usuários demo incluindo claim `module_membership`
- nginx gateway com routes: `/` → app-shell, `/pap` → pap-ui, `/demo` → demo-mfe, `/api` → authz-service, `/oidc` → cyberark-mock
- `.env.example` documentando variáveis: `AUTHZ_DB_URL`, `AUTHZ_DB_USER`, `AUTHZ_DB_PASS`, `CYBERARK_ISSUER`, `AUTHZ_MODULE_KEY_VENDAS`, `AUTHZ_MODULE_KEY_ESTOQUE`

## Arquivos Envolvidos

- **Criar:**
  - `infra/docker/docker-compose.yml`
  - `infra/docker/.env.example`
  - `infra/docker/postgres/init.sql`
  - `infra/docker/cyberark-mock/Dockerfile`
  - `infra/docker/cyberark-mock/users.json`
  - `infra/docker/nginx-gateway/nginx.conf`
  - `infra/docker/bootstrap/seed-modules.http` (REST Client script para criar módulos via admin API após subir tudo)
- **Modificar:**
  - `.gitignore` (adicionar `.env`)
- **Skills para consultar durante implementação:**
  - `java-production-readiness` — graceful shutdown e probes esperadas pelos serviços

## Subtarefas

- [ ] 2.1 Postgres com healthcheck e volume
- [ ] 2.2 mock-oauth2-server com config JSON de issuer + 5 usuários demo
- [ ] 2.3 nginx gateway com upstream para todos os serviços + SPA fallback
- [ ] 2.4 `.env.example` documentado
- [ ] 2.5 Script `seed-modules.http` (cria `vendas` e `estoque`, captura chaves)
- [ ] 2.6 Validar `docker-compose up postgres cyberark-mock nginx-gateway` sobe sem erro

## Sequenciamento

- Bloqueado por: Nenhum
- Desbloqueia: 3.0 (config local AuthZ), 8.0 (testes contra mock), 30.0 (E2E)
- Paralelizável: Sim (com 1.0)

## Rastreabilidade

- Esta tarefa cobre: ambiente local; suporte a RF-01, RF-14, RF-16
- Evidência esperada: `docker-compose up postgres cyberark-mock` saudável; `curl http://localhost:8080/oidc/.well-known/openid-configuration` retorna 200

## Detalhes de Implementação

**Usuários seed (cyberark-mock):**
```json
[
  { "sub": "user-admin", "email": "admin@demo", "module_membership": ["platform"] },
  { "sub": "user-vendas-mgr", "email": "vmgr@demo", "module_membership": ["vendas"] },
  { "sub": "user-vendas-op", "email": "vop@demo", "module_membership": ["vendas"] },
  { "sub": "user-estoque-mgr", "email": "emgr@demo", "module_membership": ["estoque"] },
  { "sub": "user-multi", "email": "multi@demo", "module_membership": ["vendas","estoque"] }
]
```

**Convenções da stack:**
- Healthchecks devem ser `depends_on: condition: service_healthy` (espera readiness)
- Usar imagens versionadas pinned (não `latest`)

## Critérios de Sucesso (Verificáveis)

- [ ] `docker-compose -f infra/docker/docker-compose.yml up -d postgres cyberark-mock nginx-gateway` sobe os 3 serviços
- [ ] `docker-compose ps` mostra todos com status `healthy`
- [ ] `curl http://localhost/oidc/.well-known/openid-configuration` retorna 200 com JSON válido
- [ ] `psql postgres://authz:authz@localhost:5432/authz -c '\l'` conecta com sucesso
