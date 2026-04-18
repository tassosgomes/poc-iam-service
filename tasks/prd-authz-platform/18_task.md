---
status: completed
parallelizable: false
blocked_by: [16.0, 17.0]
---

<task_context>
<domain>apps/demo-ms-java</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>low</complexity>
<dependencies>http_server</dependencies>
<unblocks>"30.0"</unblocks>
</task_context>

# Tarefa 18.0: Demo MS Java (vendas) — endpoints protegidos + permissions.yaml

## Relacionada às User Stories

- RF-02 (yaml declarativo), RF-04 (discovery), RF-14 (demo end-to-end)

## Visão Geral

MS demo de Vendas em Java consumindo o SDK Java. Expõe endpoints protegidos por `@HasPermission`, `GET /.well-known/permissions` e usa o `SelfRegistrationRunner` no bootstrap. Roda no docker-compose.

## Requisitos

- Maven module `apps/demo-ms-java` dependendo de `sdk-java`
- `permissions.yaml` declarando: `vendas.orders.view`, `vendas.orders.create`, `vendas.orders.cancel`, `vendas.can_manage_users`
- `OrdersController`: `GET /orders` (`@HasPermission("vendas.orders.view")`), `POST /orders` (`@HasPermission("vendas.orders.create")`), `DELETE /orders/{id}` (`@HasPermission("vendas.orders.cancel")`)
- `DiscoveryController`: `GET /.well-known/permissions` lê o mesmo `permissions.yaml` e retorna JSON
- Spring Security configurado como Resource Server JWT (mesmo CyberArk mock)
- Dockerfile multi-stage
- `application.yml` com `authz.module-id`, `authz.module-key` (env var)

## Arquivos Envolvidos

- **Criar:**
  - `apps/demo-ms-java/pom.xml`
  - `apps/demo-ms-java/Dockerfile`
  - `apps/demo-ms-java/src/main/java/com/platform/demo/sales/SalesApplication.java`
  - `apps/demo-ms-java/src/main/java/com/platform/demo/sales/api/OrdersController.java`
  - `apps/demo-ms-java/src/main/java/com/platform/demo/sales/api/DiscoveryController.java`
  - `apps/demo-ms-java/src/main/java/com/platform/demo/sales/config/SecurityConfig.java`
  - `apps/demo-ms-java/src/main/resources/application.yml`
  - `apps/demo-ms-java/src/main/resources/permissions.yaml`
  - `apps/demo-ms-java/src/test/java/com/platform/demo/sales/api/OrdersControllerTest.java`
- **Modificar:**
  - `pom.xml` parent — adicionar como `<module>`
  - `infra/docker/docker-compose.yml` — descomentar serviço `demo-ms-java`
  - `infra/docker/.env.example` — `AUTHZ_MODULE_KEY_VENDAS`
- **Skills para consultar durante implementação:**
  - `java-architecture` — controller fino delegando ao SDK
  - `java-dependency-config` — Spring Boot resource server config
  - `java-testing` — `@WebMvcTest` para controller

## Subtarefas

- [x] 18.1 `pom.xml` + bootstrap
- [x] 18.2 `permissions.yaml`
- [x] 18.3 `DiscoveryController` (lê classpath, retorna JSON)
- [x] 18.4 `OrdersController` (mock in-memory de pedidos)
- [x] 18.5 `SecurityConfig` (resource server JWT)
- [x] 18.6 Dockerfile + integração docker-compose
- [x] 18.7 Testes controller (mock auth)

## Sequenciamento

- Bloqueado por: 16.0, 17.0
- Desbloqueia: 30.0
- Paralelizável: Não (depende dos blocos do SDK)

## Rastreabilidade

- Esta tarefa cobre: RF-02, RF-04, parte de RF-14
- Evidência esperada: docker-compose sobe demo-ms-java, ele se registra automaticamente; `GET /.well-known/permissions` retorna o YAML em JSON

## Detalhes de Implementação

**`OrdersController` — exemplo:**
```java
@RestController
@RequestMapping("/orders")
public class OrdersController {
    private final List<Order> orders = new CopyOnWriteArrayList<>();

    @GetMapping
    @HasPermission("vendas.orders.view")
    public List<Order> list() { return orders; }

    @PostMapping
    @HasPermission("vendas.orders.create")
    public Order create(@RequestBody CreateOrderRequest req) {
        var o = new Order(UUID.randomUUID(), req.customer(), req.amount(), Instant.now());
        orders.add(o);
        return o;
    }
}
```

**Convenções:**
- Endpoint `GET /.well-known/permissions` (sem auth, conforme RF-04)
- Logs INFO em criação de pedido sem PII

## Critérios de Sucesso (Verificáveis)

- [x] Testes passam
- [x] `docker-compose up demo-ms-java` sobe; logs mostram `authz.registration.attempt sequence=1 ... result=success`
- [x] `curl /.well-known/permissions` retorna 4 permissões
- [x] `curl /orders` sem JWT → 401
- [x] `curl /orders` com JWT user-vendas-op + role atribuída via PAP → 200
- [x] Sem permissão → 403

## Conclusão

- [x] 18.0 Demo MS Java (vendas) — endpoints protegidos + permissions.yaml ✅ CONCLUÍDA
  - [x] 18.1 Implementação completada
  - [x] 18.2 Definição da tarefa, PRD e tech spec validados
  - [x] 18.3 Análise de regras e conformidade verificadas
  - [x] 18.4 Revisão de código completada
  - [x] 18.5 Pronto para deploy
