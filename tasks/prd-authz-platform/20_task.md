---
status: pending
parallelizable: true
blocked_by: [19.0]
---

<task_context>
<domain>libs/sdk-dotnet</domain>
<type>implementation</type>
<scope>middleware</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"22.0"</unblocks>
</task_context>

# Tarefa 20.0: SDK .NET — `[HasPermission]` attribute + AuthorizationHandler

## Relacionada às User Stories

- RF-10 (uso ergonômico)

## Visão Geral

Guard declarativo idiomático ASP.NET Core: `[HasPermission("estoque.inventory.view")]` em controllers/actions. Implementado via `IAuthorizationRequirement` + `AuthorizationHandler` consultando `RequestPermissionCache`. Espelho semântico do `@HasPermission` Java.

## Requisitos

- `HasPermissionAttribute : AuthorizeAttribute` define policy dinamicamente
- `HasPermissionRequirement : IAuthorizationRequirement` carrega o `Permission` requerido
- `HasPermissionHandler : AuthorizationHandler<HasPermissionRequirement>` consulta `RequestPermissionCache` e `IAuthzClient`
- `PermissionPolicyProvider : IAuthorizationPolicyProvider` cria policies on-the-fly para qualquer permissão
- Suporte a wildcard `estoque.*`
- Registrado via `AddAuthzSdk` (extension de 19.0)

## Arquivos Envolvidos

- **Criar:**
  - `libs/sdk-dotnet/AuthzSdk/Authorization/HasPermissionAttribute.cs`
  - `libs/sdk-dotnet/AuthzSdk/Authorization/HasPermissionRequirement.cs`
  - `libs/sdk-dotnet/AuthzSdk/Authorization/HasPermissionHandler.cs`
  - `libs/sdk-dotnet/AuthzSdk/Authorization/PermissionPolicyProvider.cs`
  - `libs/sdk-dotnet/AuthzSdk/Authorization/PermissionMatcher.cs`
  - `libs/sdk-dotnet/AuthzSdk.Tests/Authorization/HasPermissionHandlerTests.cs`
  - `libs/sdk-dotnet/AuthzSdk.Tests/Authorization/PermissionMatcherTests.cs`
- **Modificar:**
  - `libs/sdk-dotnet/AuthzSdk/Extensions/ServiceCollectionExtensions.cs` (registrar policy provider e handler)
- **Skills para consultar durante implementação:**
  - `csharp-dotnet-architecture` — separation of concerns
  - `dotnet-testing` — testar AuthorizationHandler

## Subtarefas

- [ ] 20.1 Attribute + Requirement
- [ ] 20.2 PolicyProvider dinâmico
- [ ] 20.3 Handler usando cache scoped
- [ ] 20.4 PermissionMatcher (mesma lógica wildcard do Java)
- [ ] 20.5 Testes

## Sequenciamento

- Bloqueado por: 19.0
- Desbloqueia: 22.0
- Paralelizável: Sim (paralelo a 16.0 e 21.0)

## Rastreabilidade

- Esta tarefa cobre: parte de RF-10
- Evidência esperada: action anotada em Demo MS .NET nega/permite conforme cache

## Detalhes de Implementação

**Uso esperado em controller:**
```csharp
[ApiController]
[Route("inventory")]
public class InventoryController : ControllerBase
{
    [HttpGet]
    [HasPermission("estoque.inventory.view")]
    public IActionResult List() => Ok(items);
}
```

**Convenções:**
- Deny default no handler (não chamar `context.Succeed` se não validar)
- Logs em DEBUG sem listar permissões
- Async com CancellationToken

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] Action sem permissão → 403
- [ ] Wildcard `estoque.*` casa `estoque.inventory.view`
- [ ] Sem auth → 401
