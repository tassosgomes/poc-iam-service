# Review — Task 18

## Status: ✅ APROVADO

## 1. Validação da definição da tarefa

- **RF-02 / task 18.0 atendido**
  - `permissions.yaml` existe em `src/main/resources` e declara exatamente as 4 permissões requeridas.
- **RF-04 / task 18.0 atendido**
  - `DiscoveryController` expõe `GET /.well-known/permissions`, lê o mesmo `permissions.yaml` do classpath e retorna JSON.
- **RF-14 / task 18.0 atendido no escopo da implementação**
  - o serviço sobe em runtime, integra com o SDK Java, expõe endpoints protegidos e registra o catálogo no bootstrap via `SelfRegistrationRunner`.

## 2. Conformidade com PRD, TechSpec e task

### 2.1 Requisitos estruturais

- `apps/demo-ms-java` existe como módulo Maven e está incluído no `pom.xml` raiz.
- `apps/demo-ms-java/pom.xml` depende de `sdk-java`.
- `OrdersController` implementa:
  - `GET /orders` → `@HasPermission("vendas.orders.view")`
  - `POST /orders` → `@HasPermission("vendas.orders.create")`
  - `DELETE /orders/{id}` → `@HasPermission("vendas.orders.cancel")`
- `DiscoveryController` lê `permissions.yaml` do classpath e retorna JSON.
- `SecurityConfig` configura Spring Security como OAuth2 Resource Server JWT e libera `/.well-known/permissions`.
- `Dockerfile` é multi-stage.
- `application.yml` contém `authz.module-id` e `authz.module-key`.
- `infra/docker/docker-compose.yml` contém o serviço `demo-ms-java`.
- `infra/docker/.env.example` contém `AUTHZ_MODULE_KEY_VENDAS`.

### 2.2 Critérios funcionais revisados

- `/.well-known/permissions` público: **OK**
- `/orders` sem JWT → `401`: **OK**
- `/orders` com JWT sem permissão → `403`: **OK**
- `/orders` com JWT e permissão → `200`: **OK**
- payload de discovery com 4 permissões: **OK**

## 3. Análise de regras / skills

- `java-production-readiness`: **OK**
  - startup validado
  - Actuator/readiness configurados
  - Dockerfile multi-stage presente
  - sem segredo hardcoded fora do placeholder padrão local
- `java-testing`: **OK**
  - suíte do app cobre `401`, `403`, `200`, endpoint público e validação `400`
- `java-code-quality`: **OK**
  - records usados para DTO/modelo simples
  - constructor/field injection não violado
  - logging com placeholders SLF4J
- `java-dependency-config`: **OK**
  - starters corretos para web, validation, actuator, security e resource server
- `common-restful-api`: **OK**
  - semântica HTTP consistente (`200/201/204/400/401/403/404`)
- `common-roles-naming`: **OK**
  - permissões seguem `<modulo>.<recurso>.<acao>`

## 4. Build, testes e validação executada

- `mvn -pl apps/demo-ms-java -am test package -DskipTests=false`: ✅
- Demo MS Java:
  - `OrdersControllerTest`: `2 + 3 + 2` cenários passando
  - `OrdersControllerAuthzTest`: `4` cenários passando
- SDK Java:
  - `35` testes passando
- Smoke test runtime:
  - `java -jar apps/demo-ms-java/target/demo-ms-java-0.1.0-SNAPSHOT.jar ...` iniciou com sucesso
  - log observado: `Started SalesApplication`
  - log observado: `authz.registration.attempt sequence=1 delay_ms=0`
- `docker compose config`: ✅ sintaxe válida

## 5. Problemas identificados e resoluções

- Problemas bloqueantes da revisão anterior foram resolvidos:
  1. falha de startup por configuração de readiness
  2. ausência de cobertura do cenário `403` no app consumidor

- **Problemas atuais encontrados nesta revisão:** nenhum defeito bloqueante ou médio/alto severidade.

## 6. Feedback e recomendações

1. Para validação end-to-end completa do critério com PAP/docker-compose, ainda é necessário ambiente com módulo `vendas` previamente criado e `AUTHZ_MODULE_KEY_VENDAS` real.
2. Como melhoria futura, vale evitar execução imediata duplicada entre startup e heartbeat se isso gerar ruído operacional no bootstrap.

## 7. Veredito final

**✅ APROVADO**

Justificativa:

1. Todos os artefatos pedidos pela task 18 estão presentes e coerentes com PRD + TechSpec.
2. Build e testes passam.
3. O serviço sobe em runtime.
4. Os cenários funcionais essenciais (`401`, `403`, `200`, discovery público) estão cobertos.
5. Não restam defeitos relevantes na implementação revisada.
