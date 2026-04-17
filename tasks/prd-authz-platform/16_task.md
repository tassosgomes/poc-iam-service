---
status: pending
parallelizable: true
blocked_by: [15.0]
---

<task_context>
<domain>libs/sdk-java</domain>
<type>implementation</type>
<scope>middleware</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"18.0"</unblocks>
</task_context>

# Tarefa 16.0: SDK Java — anotação `@HasPermission` + Aspect AOP

## Relacionada às User Stories

- RF-10 (uso ergonômico do SDK)

## Visão Geral

Adicionar guard declarativo para métodos: anotação `@HasPermission("vendas.orders.create")` interceptada por Aspect que consulta o `RequestScopedPermissionCache` e bloqueia chamadas não autorizadas com `AccessDeniedException` (mapeada pelo Spring Security para 403).

## Requisitos

- `@HasPermission` annotation runtime, target `METHOD` + `TYPE`
- `HasPermissionAspect` com `@Around` que:
  - Extrai userId do `SecurityContextHolder` (assume JWT já validado pelo MS consumidor)
  - Carrega permissões via `RequestScopedPermissionCache` (que delega ao `AuthzClient`)
  - Verifica match (suporta wildcard `vendas.*`)
  - Lança `AccessDeniedException` se negado
- Suporte a SpEL no value (`@HasPermission("'vendas.orders.' + #action")`) — opcional, MVP
- Habilitar via `@EnableAuthzGuard` ou auto-config (escolher uma — recomendado auto)

## Arquivos Envolvidos

- **Criar:**
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/annotation/HasPermission.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/aop/HasPermissionAspect.java`
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/aop/PermissionMatcher.java`
  - `libs/sdk-java/src/test/java/com/platform/authz/sdk/aop/HasPermissionAspectTest.java`
  - `libs/sdk-java/src/test/java/com/platform/authz/sdk/aop/PermissionMatcherTest.java`
- **Modificar:**
  - `libs/sdk-java/src/main/java/com/platform/authz/sdk/config/AuthzAutoConfiguration.java` (registrar Aspect + `@EnableAspectJAutoProxy` se necessário)
  - `libs/sdk-java/pom.xml` (`spring-boot-starter-aop`)
- **Skills para consultar durante implementação:**
  - `java-architecture` — separação concern (aspect em SDK, regras em handler do app)
  - `java-testing` — testes com `@SpringBootTest` minimal

## Subtarefas

- [ ] 16.1 Anotação
- [ ] 16.2 `PermissionMatcher` com suporte wildcard `*` e match exato
- [ ] 16.3 Aspect com extração de userId
- [ ] 16.4 Auto-config wire-up
- [ ] 16.5 Testes: allow, deny, wildcard, sem auth (deny por default)

## Sequenciamento

- Bloqueado por: 15.0
- Desbloqueia: 18.0
- Paralelizável: Sim (paralelo a 17.0 e 20.0)

## Rastreabilidade

- Esta tarefa cobre: parte de RF-10
- Evidência esperada: método anotado bloqueia/permite conforme cache; teste demonstra deny default

## Detalhes de Implementação

**Match com wildcard:**
```java
public boolean matches(Set<String> userPerms, String required) {
    if (userPerms.contains(required)) return true;
    if (required.endsWith(".*")) {
        String prefix = required.substring(0, required.length() - 2);
        return userPerms.stream().anyMatch(p -> p.startsWith(prefix + "."));
    }
    return userPerms.stream().anyMatch(p -> {
        if (p.endsWith(".*")) {
            String prefix = p.substring(0, p.length() - 2);
            return required.startsWith(prefix + ".");
        }
        return false;
    });
}
```

**Convenções:**
- Sem fallback de allow: deny default (`java-production-readiness`)
- Logs em DEBUG: `permission_check user={} required={} result=allow|deny` (não logar lista completa de perms)

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] Método anotado com permissão presente → executa
- [ ] Método anotado sem permissão → `AccessDeniedException`
- [ ] Wildcard `vendas.*` casa `vendas.orders.create`
- [ ] Sem `Authentication` no contexto → deny
