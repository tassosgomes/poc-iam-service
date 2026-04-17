---
status: pending
parallelizable: true
blocked_by: [7.0, 15.0]
---

<task_context>
<domain>libs/sdk-java</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>high</complexity>
<dependencies>http_server,temporal</dependencies>
<unblocks>"18.0"</unblocks>
</task_context>

# Tarefa 17.0: SDK Java — SelfRegistrationRunner + ReadinessGate + HeartbeatScheduler

## Relacionada às User Stories

- RF-16 — Auto-registro de módulos no bootstrap

## Visão Geral

Implementar o coração do auto-registro: no startup do app consumidor, ler `permissions.yaml` do classpath, calcular SHA-256, chamar `POST /v1/catalog/sync` com retry exponencial. Bloquear readiness probe via `HealthIndicator` até primeiro sync OK. Heartbeat re-sync a cada 15 minutos para detectar drift.

## Requisitos

- `PermissionsYamlLoader` lê `classpath:permissions.yaml`, valida shape, retorna `List<PermissionDeclaration>`
- `SelfRegistrationRunner` (`ApplicationRunner`):
  - Calcula `payloadHash`
  - Chama `authzClient.sync(...)` com Resilience4j retry exp (1s, 2s, 4s, 8s, 16s, 32s; capped 5min total)
  - Em sucesso: marca `ReadinessGate` UP
  - Em falha terminal (401/403): permanece DOWN + log ERROR
- `ReadinessGate` implementa `HealthIndicator` retornando `DOWN` até sucesso
- `HeartbeatScheduler` `@Scheduled(fixedRate=15min)` re-dispara sync (idempotente do lado AuthZ)
- Configuração: `authz.registration.enabled` (default true), `authz.registration.permissions-file` (default `permissions.yaml`)

## Arquivos Envolvidos

- **Criar:**
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/PermissionsYamlLoader.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/SelfRegistrationRunner.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/ReadinessGate.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/HeartbeatScheduler.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/registration/RegistrationProperties.java`
  - `libs/sdk-java/src/test/java/com/platform/authz/sdk/registration/PermissionsYamlLoaderTest.java`
  - `libs/sdk-java/src/test/java/com/platform/authz/sdk/registration/SelfRegistrationRunnerTest.java`
  - `libs/sdk-java/src/test/java/com/platform/authz/sdk/registration/ReadinessGateTest.java`
- **Modificar:**
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/config/AuthzAutoConfiguration.java` (registrar runners + gate condicional)
  - `libs/sdk-java/pom.xml` (snakeyaml)
- **Skills para consultar durante implementação:**
  - `java-dependency-config` — Resilience4j retry config programmatic
  - `java-production-readiness` — readiness probe correto
  - `java-observability` — log estruturado de cada tentativa

## Subtarefas

- [ ] 17.1 `PermissionsYamlLoader` com SnakeYAML + validação de schema
- [ ] 17.2 `ReadinessGate` `HealthIndicator`
- [ ] 17.3 `SelfRegistrationRunner` com retry programático + log estruturado
- [ ] 17.4 `HeartbeatScheduler` (não bloqueia, falha silenciosa com log WARN)
- [ ] 17.5 Auto-config wire-up
- [ ] 17.6 Testes com MockWebServer cobrindo: sucesso primeiro try, retry com sucesso, falha terminal 401, payload inválido

## Sequenciamento

- Bloqueado por: 7.0 (contrato sync), 15.0 (AuthzClient)
- Desbloqueia: 18.0 (Demo MS Java consome)
- Paralelizável: Sim (paralelo a 16.0 e 21.0)

## Rastreabilidade

- Esta tarefa cobre: RF-16 (lado Java)
- Evidência esperada: app de teste sobe, faz sync, readiness vai UP; sem AuthZ, readiness fica DOWN

## Detalhes de Implementação

**Schema `permissions.yaml`:**
```yaml
schemaVersion: "1.0"
moduleId: "vendas"
permissions:
  - code: "vendas.orders.create"
    description: "Criar pedidos de venda"
  - code: "vendas.orders.cancel"
    description: "Cancelar pedido em rascunho"
```

**Loader valida**: `schemaVersion` reconhecido, `moduleId` não vazio, cada permission com `code` e `description` não vazios.

**Convenções:**
- Calcular hash em `loader.canonicalize(...)` (sort keys, normalize whitespace) antes de SHA-256
- Logs JSON: `INFO authz.registration.attempt sequence=N delay_ms=...`
- ReadinessGate retorna `Health.down().withDetail("reason","awaiting-first-sync")` enquanto não OK

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] App teste com permissions.yaml válido + AuthZ rodando → readiness UP em poucos segundos
- [ ] App teste com AuthZ down → readiness permanece DOWN e retry visível em log
- [ ] Heartbeat re-dispara sync a cada 15min (configurar 5s no test e validar)
- [ ] payload yaml inválido → falha startup com mensagem clara
