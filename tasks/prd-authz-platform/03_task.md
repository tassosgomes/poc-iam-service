---
status: completed
parallelizable: false
blocked_by: [1.0]
---

<task_context>
<domain>backend/authz-service</domain>
<type>implementation</type>
<scope>configuration</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"4.0"</unblocks>
</task_context>

# Tarefa 3.0: AuthZ Service — bootstrap Spring Boot e configurações base

## Relacionada às User Stories

- Suporte a todas as RFs server-side

## Visão Geral

Subir o esqueleto Spring Boot 3 do AuthZ Service com Clean Architecture, configurações de observabilidade (OpenTelemetry, Micrometer, logback JSON), Actuator probes, OpenAPI 3 (springdoc) e perfis dev/prod. Sem regras de negócio nesta tarefa — apenas o "vazio funcional" pronto para receber os módulos.

## Requisitos

- `pom.xml` declarando dependências base (Spring Boot 3.x web + actuator + validation, Spring Data JPA, Postgres driver, Flyway, MapStruct, Resilience4j, Micrometer + OTel, Caffeine, Argon2 jvm-libsodium ou equivalente, Bean Validation)
- `application.yml` base + `application-dev.yml` apontando para Postgres do docker-compose
- `logback-spring.xml` com encoder JSON (LogstashEncoder) e MDC `trace_id`/`span_id`
- `Dockerfile` multi-stage (build com Maven + runtime `eclipse-temurin:21-jre`)
- `OpenApiConfig` configurando springdoc com security schemes (bearer module, JWT)
- `ObservabilityConfig` registrando tags globais Micrometer
- Estrutura de pacotes: `com.platform.authz.{catalog,iam,modules,authz,audit,shared}.{api,application,domain,infra}`

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/pom.xml`
  - `apps/authz-service/Dockerfile`
  - `apps/authz-service/src/main/java/com/platform/authz/AuthzServiceApplication.java`
  - `apps/authz-service/src/main/java/com/platform/authz/config/OpenApiConfig.java`
  - `apps/authz-service/src/main/java/com/platform/authz/config/ObservabilityConfig.java`
  - `apps/authz-service/src/main/resources/application.yml`
  - `apps/authz-service/src/main/resources/application-dev.yml`
  - `apps/authz-service/src/main/resources/logback-spring.xml`
  - `.gitkeep` em cada pacote esperado
- **Modificar:**
  - `pom.xml` (parent) — adicionar `apps/authz-service` como `<module>`
  - `infra/docker/docker-compose.yml` — descomentar serviço `authz-service`
- **Skills para consultar durante implementação:**
  - `java-architecture` — estrutura de pacotes e camadas
  - `java-dependency-config` — versões e libs canônicas
  - `java-observability` — logback JSON + OTel + Actuator
  - `common-restful-api` — OpenAPI/path versioning

## Subtarefas

- [x] 3.1 `pom.xml` com BOMs e dependências
- [x] 3.2 `AuthzServiceApplication.java` com `@SpringBootApplication`
- [x] 3.3 `application.yml` (server, datasource, JPA, management endpoints)
- [x] 3.4 `logback-spring.xml` com JSON encoder
- [x] 3.5 `OpenApiConfig` com bearer + JWT schemes
- [x] 3.6 `Dockerfile` multi-stage
- [x] 3.7 Smoke test: `mvn spring-boot:run` com perfil `dev` sobe e `/actuator/health` retorna `UP`

- [x] 3.0 AuthZ Service — bootstrap Spring Boot e configurações base ✅ CONCLUÍDA
  - [x] 3.1 Implementação completada
  - [x] 3.2 Definição da tarefa, PRD e tech spec validados
  - [x] 3.3 Análise de regras e conformidade verificadas
  - [x] 3.4 Revisão de código completada
  - [x] 3.5 Pronto para deploy

## Sequenciamento

- Bloqueado por: 1.0
- Desbloqueia: 4.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: setup técnico, observabilidade base
- Evidência esperada: serviço sobe, `/actuator/health` 200, `/v3/api-docs` retorna OpenAPI JSON

## Detalhes de Implementação

**`application.yml` (essencial):**
```yaml
spring:
  application:
    name: authz-service
  datasource:
    url: ${AUTHZ_DB_URL}
    username: ${AUTHZ_DB_USER}
    password: ${AUTHZ_DB_PASS}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
```

**Convenções da stack:**
- Logs JSON via Logback + LogstashEncoder, com MDC propagado por OTel (`java-observability`)
- Actuator probes liveness/readiness sempre habilitadas (`java-production-readiness`)
- Spring Boot 3 + Java 21 records nos DTOs (`java-code-quality`)

## Critérios de Sucesso (Verificáveis)

- [x] `mvn -pl apps/authz-service compile` ok
- [x] `mvn -pl apps/authz-service test` ok (sem testes ainda, mas plumbing funciona)
- [x] `docker build apps/authz-service` produz imagem
- [x] Sobe via `docker-compose up authz-service` e `/actuator/health` retorna `{"status":"UP"}`
- [x] `/v3/api-docs` retorna JSON OpenAPI válido
