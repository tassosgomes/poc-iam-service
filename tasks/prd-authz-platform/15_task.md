---
status: pending
parallelizable: true
blocked_by: [7.0]
---

<task_context>
<domain>libs/sdk-java</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"16.0,17.0,18.0,29.0"</unblocks>
</task_context>

# Tarefa 15.0: SDK Java — AuthzClient + cache por requisição + auto-config

## Relacionada às User Stories

- RF-10 — SDK Java com bulk fetch e cache por requisição

## Visão Geral

Construir a base do SDK Java publicado em GitHub Packages: cliente HTTP tipado (WebClient + Resilience4j), cache em escopo de requisição (`@RequestScope`), `AuthzAutoConfiguration` para integração transparente em apps Spring Boot consumidores.

## Requisitos

- Maven module `libs/sdk-java` com `pom.xml` declarando publishing em GitHub Packages
- `AuthzClient` interface + `AuthzClientImpl` chamando `GET /v1/users/{id}/permissions` e `POST /v1/authz/check`
- `RequestScopedPermissionCache` (bean `@RequestScope`): primeira chamada faz bulk fetch, subsequentes vão em memória
- `AuthzProperties` com `authz.base-url`, `authz.module-key`, `authz.module-id`, `authz.timeout`
- `AuthzAutoConfiguration` registra beans condicionalmente (`@ConditionalOnProperty(prefix="authz", name="enabled", matchIfMissing=true)`)
- Spring Boot 3 auto-config registry: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Resilience4j: retry 3x exp backoff, circuit breaker `authz` (50% failure rate, 10 calls window)
- Não inclui ainda: `@HasPermission` (16.0), self-registration (17.0)

## Arquivos Envolvidos

- **Criar:**
  - `libs/sdk-java/pom.xml`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/AuthzClient.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/AuthzClientImpl.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/AuthzProperties.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/cache/RequestScopedPermissionCache.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/exception/AuthzClientException.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/config/AuthzAutoConfiguration.java`
  - `libs/sdk-java/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - `libs/sdk-java/src/test/java/com/platform/authz/sdk/AuthzClientImplTest.java`
  - `libs/sdk-java/src/test/java/com/platform/authz/sdk/cache/RequestScopedPermissionCacheTest.java`
- **Modificar:**
  - `pom.xml` (parent) — registrar `libs/sdk-java` como `<module>`
- **Skills para consultar durante implementação:**
  - `java-dependency-config` — WebClient + Resilience4j
  - `java-architecture` — separação interface/impl
  - `java-testing` — MockWebServer (OkHttp) para testes do client

## Subtarefas

- [ ] 15.1 `pom.xml` com `distributionManagement` apontando GitHub Packages
- [ ] 15.2 `AuthzClient` interface + impl (WebClient + Resilience4j annotations)
- [ ] 15.3 `RequestScopedPermissionCache`
- [ ] 15.4 `AuthzAutoConfiguration` + properties
- [ ] 15.5 Testes com MockWebServer cobrindo: 200 ok, 401, 503 + retry, timeout

## Sequenciamento

- Bloqueado por: 7.0 (precisa do contrato sync; bulk fetch é definido em 11.0 mas aqui só consumimos)
- Desbloqueia: 16.0, 17.0, 18.0, 29.0
- Paralelizável: Sim (paralelo a 19.0)

## Rastreabilidade

- Esta tarefa cobre: parte de RF-10
- Evidência esperada: app consumidor importa via Maven, injeta `AuthzClient`, faz chamada, recebe permissões cacheadas

## Detalhes de Implementação

**`AuthzClient` API mínima:**
```java
public interface AuthzClient {
    Set<String> fetchUserPermissions(String userId);
    boolean check(String userId, String permission);
    SyncResult sync(SyncRequest request); // usado em 17.0
}
```

**Convenções da stack:**
- WebClient com `responseTimeout(Duration.ofSeconds(2))` (`java-dependency-config`)
- Resilience4j `@Retry(name="authz")` + `@CircuitBreaker(name="authz")` (`java-dependency-config`)
- DTOs como records (`java-code-quality`)
- Testes: MockWebServer para HTTP isolation (`java-testing`)
- Logs sem expor `module-key` (`java-production-readiness`)

## Critérios de Sucesso (Verificáveis)

- [ ] `mvn -pl libs/sdk-java test` passa
- [ ] `mvn -pl libs/sdk-java install` instala localmente
- [ ] App de teste consegue `@Autowired AuthzClient` e fazer chamada (via MockWebServer)
- [ ] Cache request-scoped: 2 chamadas no mesmo request → 1 HTTP call
