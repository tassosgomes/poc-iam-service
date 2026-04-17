---
status: pending
parallelizable: false
blocked_by: [5.0]
---

<task_context>
<domain>backend/authz-service/shared</domain>
<type>implementation</type>
<scope>middleware</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"7.0"</unblocks>
</task_context>

# Tarefa 6.0: Filtro de autenticação por bearer de módulo + GlobalExceptionHandler

## Relacionada às User Stories

- RF-03 (auth do sync), RF-17 (validação de chave)

## Visão Geral

Implementar a infra de segurança M2M: `ModuleBearerAuthenticationFilter` (Spring Security filter) que valida o header `Authorization: Bearer <secret>` contra hashes em `module_key`, considerando `ACTIVE` e `SUPERSEDED` em grace period. Erros padronizados via `GlobalExceptionHandler` (RFC 9457 ProblemDetail). Também o `PermissionPrefixValidator` (usado pelo handler de sync na 7.0).

## Requisitos

- `ModuleBearerAuthenticationFilter` aplicado apenas em rotas `/v1/catalog/**`
- `ValidateModuleKeyService` faz timing-safe compare via Argon2 verify; aceita ACTIVE ou SUPERSEDED com `grace_expires_at > now()`
- Resultado da autenticação populado em `SecurityContext` como `ModuleContext` (moduleId, allowedPrefix)
- `GlobalExceptionHandler` cobre: `ConstraintViolationException`, `MethodArgumentNotValidException`, `EntityNotFoundException`, `UnauthorizedModuleKeyException`, `PrefixViolationException`, fallback `Exception`
- Todas as respostas de erro seguem RFC 9457 (`application/problem+json`) com `type`, `title`, `status`, `detail`, `instance`, `traceId`
- `PermissionPrefixValidator`: utility que valida `permission.code.startsWith(allowedPrefix + ".")`

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/shared/security/ModuleBearerAuthenticationFilter.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/security/ModuleAuthenticationToken.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/security/ModuleContext.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/application/ValidateModuleKeyService.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/security/PermissionPrefixValidator.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/api/GlobalExceptionHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/api/ProblemDetailFactory.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/exception/UnauthorizedModuleKeyException.java`
  - `apps/authz-service/src/main/java/com/platform/authz/shared/exception/PrefixViolationException.java`
  - `apps/authz-service/src/main/java/com/platform/authz/config/SecurityConfig.java` (apenas a parte de bearer; JWT vem em 8.0)
  - `apps/authz-service/src/test/java/com/platform/authz/shared/security/ModuleBearerAuthenticationFilterTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/modules/application/ValidateModuleKeyServiceTest.java`
- **Skills para consultar durante implementação:**
  - `java-architecture` — separação domain/infra para validação
  - `common-restful-api` — RFC 9457
  - `java-production-readiness` — timing-safe compare, sem leak de informação no erro

## Subtarefas

- [ ] 6.1 `ValidateModuleKeyService` com Argon2 verify + lookup por hash candidato (filtra `ACTIVE` + `SUPERSEDED` no grace, varre cada um e tenta verify)
- [ ] 6.2 `ModuleBearerAuthenticationFilter` extrai header, chama service, popula SecurityContext
- [ ] 6.3 `SecurityConfig` registra filter na chain
- [ ] 6.4 `GlobalExceptionHandler` + `ProblemDetailFactory`
- [ ] 6.5 Logs de segurança: `WARN key_auth_failed` com `source_ip`, sem revelar qual módulo
- [ ] 6.6 Métrica `authz_module_key_invalid_total{reason}` (reasons: `not_found`, `expired_grace`, `revoked`)
- [ ] 6.7 Testes unitários cobrindo: chave válida ACTIVE, válida em grace, expirada, inexistente, revogada, header malformado
- [ ] 6.8 Teste de integração end-to-end (auth + reject)

## Sequenciamento

- Bloqueado por: 5.0
- Desbloqueia: 7.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: parte de RF-03 (auth do endpoint), RF-17 (uso da chave)
- Evidência esperada: requests com chave inválida retornam 401 com ProblemDetail; chave válida popula contexto

## Detalhes de Implementação

**Estratégia de lookup (eficiente o bastante para MVP):**

Para evitar fazer Argon2 verify contra todas as chaves do banco, exigir que o cliente envie também `X-Module-Id` como hint. Filter extrai os dois headers, busca apenas as chaves daquele módulo, e tenta verify. Se hint ausente, retorna 401.

```java
public ModuleContext validate(String moduleIdHint, String secret) {
    var keys = repository.findActiveOrInGrace(UUID.fromString(moduleIdHint));
    if (keys.isEmpty()) throw new UnauthorizedModuleKeyException("not_found");
    for (var key : keys) {
        if (hasher.verify(key.keyHash(), secret)) {
            return new ModuleContext(moduleIdHint, key.module().allowedPrefix(), key.createdAt());
        }
    }
    throw new UnauthorizedModuleKeyException("invalid");
}
```

**ProblemDetail exemplo (auth fail):**
```json
{
  "type": "https://authz.platform/errors/unauthorized-module-key",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Module key invalid or expired",
  "instance": "/v1/catalog/sync",
  "traceId": "abc123..."
}
```

**Convenções da stack:**
- Filter na chain antes do `UsernamePasswordAuthenticationFilter`
- Logs em formato JSON; eventos de segurança em WARN
- Sem leak de qual chave/módulo no detail do erro

## Critérios de Sucesso (Verificáveis)

- [ ] Testes unitários passam
- [ ] `curl -H 'Authorization: Bearer wrong' -H 'X-Module-Id: <id>' .../v1/catalog/sync` retorna 401 application/problem+json
- [ ] `curl` com chave válida ACTIVE → 200 (ou erro de payload, mas autenticação OK)
- [ ] `curl` com chave SUPERSEDED dentro do grace → 200; após grace → 401
- [ ] Métrica `authz_module_key_invalid_total` incrementa
