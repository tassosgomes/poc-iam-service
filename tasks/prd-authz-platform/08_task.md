---
status: pending
parallelizable: true
blocked_by: [3.0, 2.0]
---

<task_context>
<domain>backend/authz-service/shared</domain>
<type>integration</type>
<scope>middleware</scope>
<complexity>medium</complexity>
<dependencies>external_apis,http_server</dependencies>
<unblocks>"9.0"</unblocks>
</task_context>

# Tarefa 8.0: Integração CyberArk — validação de JWT e proxy de busca de usuários

## Relacionada às User Stories

- RF-01 (autenticação OIDC), suporte a RF-05/06/07 (PAP precisa buscar usuários)

## Visão Geral

Configurar Spring Security como Resource Server validando JWTs do CyberArk (mock-oauth2-server em dev) via JWKS. Mapear claims (`sub`, `module_membership`, roles) para `Authentication`. Implementar proxy `GET /v1/users/search` chamando a API do CyberArk com Resilience4j (timeout 2s, circuit breaker). Em dev, o mock cobre os dois papéis (OIDC + busca).

## Requisitos

- `SecurityConfig` completa: chain JWT para `/v1/**` (exceto `/v1/catalog/**` que usa filter de módulo)
- `JwtAuthorizationConverter` extrai authorities a partir das claims (`platform_admin`, `auditor`, `<module>_user_manager`)
- `ModuleScopeExtractor`: utility para extrair lista de módulos onde o usuário tem `can_manage_users` (consumido em 10.0)
- `CyberArkUserSearchClient` (WebClient + Resilience4j): `searchUsers(query, moduleFilter)` retorna lista de `{ userId, displayName, email, modules }`
- `UserSearchController` `GET /v1/users/search?q=&moduleId=`: aplica `ModuleScopeExtractor` para filtrar resultados conforme escopo do admin (RF-07)
- Configuração: `cyberark.issuer-uri`, `cyberark.user-api-base-url` em `application.yml`

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/config/SecurityConfig.java` (completar com JWT chain — placeholder de 6.0)
  - `apps/authz-service/src/main/java/com/platform/authz/shared/security/JwtAuthorizationConverter.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/security/ModuleScopeExtractor.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/UserSearchController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/api/dto/UserSummaryDto.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/infra/CyberArkUserSearchClient.java`
  - `apps/authz-service/src/main/java/com/platform/authz/iam/infra/CyberArkProperties.java`
  - `apps/authz-service/src/test/java/com/platform/authz/shared/security/JwtAuthorizationConverterTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/iam/integration/UserSearchIntegrationTest.java`
- **Modificar:**
  - `apps/authz-service/pom.xml` (adicionar `spring-boot-starter-oauth2-resource-server` se não estiver)
  - `apps/authz-service/src/main/resources/application.yml` (`spring.security.oauth2.resourceserver.jwt.issuer-uri`)
- **Skills para consultar durante implementação:**
  - `java-dependency-config` — Resilience4j, WebClient
  - `java-production-readiness` — sanitização de logs (não logar JWT)
  - `common-restful-api` — paginação na busca

## Subtarefas

- [ ] 8.1 `SecurityConfig` com matchers separados (`/v1/catalog/**` filter; `/v1/**` JWT)
- [ ] 8.2 `JwtAuthorizationConverter` mapeando claims customizadas
- [ ] 8.3 `CyberArkUserSearchClient` com WebClient + Resilience4j (timeout 2s, retry 1, circuit breaker)
- [ ] 8.4 `UserSearchController` com filtragem por escopo
- [ ] 8.5 Mock-oauth2-server integration test (Testcontainers para mock-oauth2-server)
- [ ] 8.6 Fallback: se CyberArk indisponível → 503 ProblemDetail

## Sequenciamento

- Bloqueado por: 3.0, 2.0
- Desbloqueia: 9.0
- Paralelizável: Sim (paralelo a 7.0)

## Rastreabilidade

- Esta tarefa cobre: RF-01, suporte a RF-05/06/07
- Evidência esperada: request sem JWT → 401; com JWT válido → 200; busca de usuário filtra por escopo

## Detalhes de Implementação

**Mapeamento de claims (espera-se do mock):**
```json
{
  "sub": "user-vendas-mgr",
  "email": "vmgr@demo",
  "module_membership": ["vendas"],
  "platform_roles": ["VENDAS_USER_MANAGER"]
}
```

→ Authorities: `MODULE_vendas`, `ROLE_VENDAS_USER_MANAGER`

**Convenções da stack:**
- WebClient com `responseTimeout(2s)` (`java-dependency-config`)
- Resilience4j `@CircuitBreaker(name="cyberark", fallbackMethod=...)` (`java-dependency-config`)
- Não logar conteúdo do JWT, só `sub` (`java-production-readiness`)
- ProblemDetail para 503 fallback (`common-restful-api`)

## Critérios de Sucesso (Verificáveis)

- [ ] `mvn test -Dtest='JwtAuthorizationConverterTest,UserSearchIntegrationTest'` passa
- [ ] `curl .../v1/users/search?q=foo` sem header → 401
- [ ] Com JWT válido + role correta → 200 com lista
- [ ] Admin de Vendas buscando user de Estoque → vazio (filtrado)
- [ ] Mock CyberArk fora do ar → endpoint retorna 503 com ProblemDetail (não 500)
