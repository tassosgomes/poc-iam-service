# Review — Task 17

## Status: ✅ APROVADO

## 1. Validação da definição da tarefa

- RF-16 / task 17.0: **atendido**
- `PermissionsYamlLoader` lê `permissions.yaml` do classpath, valida schema e normaliza campos obrigatórios:
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/PermissionsYamlLoader.java:48-69`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/PermissionsYamlLoader.java:98-151`
- O hash SHA-256 é calculado sobre payload canônico com ordenação determinística e normalização de whitespace:
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/PermissionsYamlLoader.java:72-95`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/SelfRegistrationRunner.java:119-127`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/SelfRegistrationRunner.java:212-219`
- O startup sync ocorre antes de liberar readiness, e o gate permanece `DOWN` até o primeiro sync com sucesso:
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/SelfRegistrationRunner.java:82-117`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/ReadinessGate.java:22-33`
- O heartbeat reexecuta sync em scheduler dedicado:
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/HeartbeatScheduler.java:9-25`

## 2. Conformidade com PRD, TechSpec e pontos solicitados

### 2.1 PRD / TechSpec

- RF-16 exige retry exponencial `1s, 2s, 4s, 8s, 16s, 32s` com janela total ~5min. A configuração programática em `SelfRegistrationRunner` atende esse contrato:
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/SelfRegistrationRunner.java:169-210`
- A TechSpec exige `401/403` como falha terminal, readiness `DOWN` até sucesso, e heartbeat a cada 15min. O fluxo implementado corresponde a isso:
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/SelfRegistrationRunner.java:130-167`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/HeartbeatScheduler.java:11-24`
- A auto-config foi ligada de forma condicional por `authz.registration.enabled` e registrada no auto-configuration imports:
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/config/AuthzAutoConfiguration.java:122-156`
  - `libs/sdk-java/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`

### 2.2 Verificações específicas solicitadas

- `PermissionsYamlLoader` validation: **OK**
  - valida root object, `schemaVersion`, `moduleId`, lista não vazia e `code`/`description` por item
- SHA-256 canonicalization: **OK**
  - usa `TreeMap`, ordenação de permissões por `code` e `normalizeWhitespace(...)`
- `SelfRegistrationRunner` retry: **OK**
  - backoff exponencial com teto de `32s` e cálculo de tentativas dentro da janela de `5min`
- `ReadinessGate` `HealthIndicator`: **OK**
  - `DOWN` com `reason=awaiting-first-sync` até sucesso; depois `UP`
- `HeartbeatScheduler`: **OK**
  - `@Scheduled(fixedRate = 15 min)` chama `syncOnHeartbeat()`
- `401/403 = terminal failure (DOWN + ERROR)`: **OK**
  - falha não é marcada como retryable e readiness não sobe
- `success = UP`: **OK**
  - `readinessGate.markReady()` é executado apenas após sync bem-sucedido
- `heartbeat failure = WARN only`: **OK**
  - falha em heartbeat usa `LOGGER.warn(...)` e não derruba readiness já aberta
- auto-config condicional: **OK**
  - beans de registration dependem de `authz.registration.enabled=true` (default)

## 3. Cobertura de testes

Os cenários críticos da task estão cobertos em `libs/sdk-java`:

- YAML válido + canonicalização determinística:
  - `PermissionsYamlLoaderTest.load_WithValidYaml_ShouldParseAndCanonicalize`
- schema inválido:
  - `PermissionsYamlLoaderTest.load_WithUnsupportedSchemaVersion_ShouldThrowException`
- payload inválido:
  - `PermissionsYamlLoaderTest.load_WithMissingPermissionDescription_ShouldThrowException`
  - `SelfRegistrationRunnerTest.run_WhenPermissionsPayloadIsInvalid_ShouldFailStartup`
- sucesso no primeiro try:
  - `SelfRegistrationRunnerTest.run_WhenSyncSucceedsOnFirstAttempt_ShouldMarkGateReady`
- retry com sucesso:
  - `SelfRegistrationRunnerTest.run_WhenSyncFailsTransiently_ShouldRetryAndSucceed`
- falha terminal 401:
  - `SelfRegistrationRunnerTest.run_WhenSyncFailsWithUnauthorized_ShouldKeepGateDown`
- readiness DOWN/UP:
  - `ReadinessGateTest.health_BeforeFirstSync_ShouldBeDown`
  - `ReadinessGateTest.health_AfterFirstSync_ShouldBeUp`

## 4. Análise de regras / skills

- `java-dependency-config`: ✅ uso apropriado de Resilience4j, Actuator e SnakeYAML
- `java-testing`: ✅ testes unitários com JUnit 5/AssertJ e cenários centrais da task
- `java-code-quality`: ✅ constructor injection, records, `Objects.requireNonNull`, sem `null` de retorno
- `java-observability` / `java-production-readiness`: ✅ readiness gate customizado e logs com placeholders SLF4J; sem exposição de segredo

## 5. Build e testes validados

- `mvn -pl libs/sdk-java -am test`: ✅ **35 passed, 0 failed, 0 skipped**
- `mvn -pl libs/sdk-java -am -DskipTests package`: ✅ **BUILD SUCCESS**
- Evidência fornecida pelo solicitante:
  - `apps/authz-service`: **121 passed, 0 failed, 0 skipped**

## 6. Feedback e recomendações

### Problemas bloqueantes

- **Nenhum bloqueador identificado.**

### Recomendações não bloqueantes

1. Adicionar um teste explícito para `403` terminal, espelhando o cenário já coberto para `401`.
2. Adicionar um teste de integração/scheduler para evidenciar o heartbeat periódico em contexto Spring Boot, já que hoje a lógica do agendamento é validada principalmente por inspeção de código.

## 7. Veredito final

**✅ APROVADO**

Resumo:

1. A implementação atende a Task 17, RF-16 e o contrato descrito na TechSpec.
2. Os pontos solicitados (`PermissionsYamlLoader`, canonicalização SHA-256, retry exponencial, readiness gate, heartbeat, falhas terminais 401/403, sucesso=UP, heartbeat=WARN, auto-config condicional) estão corretos.
3. Os testes de `libs/sdk-java` e o build do módulo passaram com sucesso.
4. A task está tecnicamente pronta, sem bloqueios para finalização.
