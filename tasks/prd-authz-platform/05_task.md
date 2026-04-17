---
status: pending
parallelizable: false
blocked_by: [4.0]
---

<task_context>
<domain>backend/authz-service/modules</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>high</complexity>
<dependencies>database</dependencies>
<unblocks>"6.0,7.0,25.0"</unblocks>
</task_context>

# Tarefa 5.0: Módulo `modules` — domain, repositórios e API admin

## Relacionada às User Stories

- RF-17 — Gestão de chaves compartilhadas por módulo

## Visão Geral

Implementar o módulo `modules` do AuthZ Service: domain entities (`Module`, `ModuleKey`), repos JPA, hash Argon2id e API administrativa para criar módulos e rotacionar chaves com grace period de 24h. **Crítico**: a chave em texto claro é exibida apenas uma vez na resposta de criação/rotação; backend só persiste o hash.

## Requisitos

- Domain: `Module`, `ModuleKey` (records ou classes imutáveis), enums `ModuleKeyStatus`
- Repository interfaces no `domain` + impl JPA no `infra`
- `Argon2KeyHasher` (usa `de.mkammerer.argon2:argon2-jvm` ou similar)
- Handlers: `CreateModuleHandler`, `RotateKeyHandler`
- Controllers: `ModuleController` (`POST /v1/modules`, `GET /v1/modules`, `GET /v1/modules/{id}`), `KeyController` (`POST /v1/modules/{id}/keys/rotate`)
- DTOs: `CreateModuleRequest`, `CreateModuleResponse` (inclui `secret` em texto claro), `RotateKeyResponse`, `ModuleSummaryDto` (inclui idade da chave + status heartbeat)
- Validação: `allowed_prefix` único, lowercase, sem ponto, regex `^[a-z][a-z0-9-]{1,30}$`
- Acesso: rotas exigem role `PLATFORM_ADMIN` (validação placeholder por enquanto; integração Spring Security completa em 6.0/8.0)

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/modules/domain/Module.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/domain/ModuleKey.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/domain/ModuleKeyStatus.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/domain/ModuleRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/domain/ModuleKeyRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/infra/JpaModuleRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/infra/JpaModuleKeyRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/infra/Argon2KeyHasher.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/application/CreateModuleHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/application/RotateKeyHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/api/ModuleController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/api/KeyController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/modules/api/dto/*.java`
  - `apps/authz-service/src/test/java/com/platform/authz/modules/application/CreateModuleHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/modules/application/RotateKeyHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/modules/integration/ModuleAdminIntegrationTest.java`
- **Skills para consultar durante implementação:**
  - `java-architecture` — Clean Arch, CQRS handlers, ProblemDetail
  - `java-code-quality` — records para DTOs, Bean Validation
  - `java-testing` — AAA, Testcontainers Postgres
  - `common-restful-api` — `/v1/modules`, ProblemDetails
  - `java-production-readiness` — não logar chave em texto claro

## Subtarefas

- [ ] 5.1 Domain entities + value objects (UUID + Instant)
- [ ] 5.2 Repos + JPA mappings (MapStruct opcional)
- [ ] 5.3 `Argon2KeyHasher` (parâmetros: 3 iterations, 64MB, 4 threads, salt 16B)
- [ ] 5.4 `CreateModuleHandler`: gera secret 32 bytes (SecureRandom), persiste hash, retorna texto claro uma vez
- [ ] 5.5 `RotateKeyHandler`: cria nova `ACTIVE`, marca anterior `SUPERSEDED` com `grace_expires_at = now()+24h`
- [ ] 5.6 Controllers + DTOs + Bean Validation
- [ ] 5.7 Testes unitários (geração de secret, hash determinístico de salt, grace period)
- [ ] 5.8 Teste de integração com Testcontainers cobrindo create + rotate

## Sequenciamento

- Bloqueado por: 4.0
- Desbloqueia: 6.0 (filter precisa de `ModuleKeyRepository`), 7.0 (catalog usa ctx do módulo), 25.0 (PAP UI)
- Paralelizável: Não (espinha do backend)

## Rastreabilidade

- Esta tarefa cobre: RF-17
- Evidência esperada: criar módulo via cURL retorna 201 + secret; rotacionar retorna nova chave + grace; secret nunca aparece em log

## Detalhes de Implementação

**Geração de secret:**
```java
byte[] raw = new byte[32];
SecureRandom.getInstanceStrong().nextBytes(raw);
String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
```

**Resposta de criação (DTO):**
```java
public record CreateModuleResponse(
    UUID moduleId,
    String name,
    String allowedPrefix,
    String secret,            // texto claro - ÚNICA exibição
    Instant createdAt
) {}
```

**Convenções da stack:**
- Records para DTOs e value objects (`java-code-quality`)
- Repos no domain como interface, impl em infra (`java-architecture`)
- Testes nomeados `methodName_Condition_ExpectedBehavior` (`java-testing`)
- Logs de criação: `INFO module.created moduleId={} name={}` — **nunca** logar `secret`

## Critérios de Sucesso (Verificáveis)

- [ ] `mvn -pl apps/authz-service test -Dtest='CreateModuleHandlerTest,RotateKeyHandlerTest'` passa
- [ ] `mvn -pl apps/authz-service test -Dtest='ModuleAdminIntegrationTest'` passa
- [ ] `curl -XPOST .../v1/modules -d '{"name":"vendas","allowedPrefix":"vendas","description":"..."}' ` retorna 201 com `secret`
- [ ] `curl -XPOST .../v1/modules/{id}/keys/rotate` retorna 200 com nova `secret`
- [ ] Grep no log não encontra a string do secret
- [ ] `allowedPrefix` duplicado retorna 409 ProblemDetail
