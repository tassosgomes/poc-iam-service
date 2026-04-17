---
status: pending
parallelizable: true
blocked_by: [7.0]
---

<task_context>
<domain>libs/sdk-dotnet</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>medium</complexity>
<dependencies>http_server</dependencies>
<unblocks>"20.0,21.0,22.0,29.0"</unblocks>
</task_context>

# Tarefa 19.0: SDK .NET — IAuthzClient + RequestPermissionCache + DI extensions

## Relacionada às User Stories

- RF-10 — SDK .NET com bulk fetch e cache por requisição

## Visão Geral

Espelho .NET do SDK Java: `IAuthzClient` (HttpClient com Polly), `RequestPermissionCache` registrado como `AddScoped` (escopo de requisição em ASP.NET Core), `IServiceCollection` extensions para registro idiomático em `Program.cs`.

## Requisitos

- Projeto .NET classlib `libs/sdk-dotnet/AuthzSdk` target `net8.0`
- `IAuthzClient` + `AuthzClient` com `IHttpClientFactory` + Polly (retry exp + circuit breaker)
- `AuthzOptions` (`IOptions<AuthzOptions>`): `BaseUrl`, `ModuleId`, `ModuleKey`, `Timeout`
- `RequestPermissionCache` `AddScoped` cacheia por `userId` no escopo do request
- `ServiceCollectionExtensions.AddAuthzSdk(this IServiceCollection, IConfiguration)` registra tudo
- Polly: retry 3 exponential, circuit breaker 50%/10
- Publicação em GitHub Packages via `nuget push` (configurado em 29.0)

## Arquivos Envolvidos

- **Criar:**
  - `libs/sdk-dotnet/AuthzSdk/AuthzSdk.csproj`
  - `libs/sdk-dotnet/AuthzSdk/IAuthzClient.cs`
  - `libs/sdk-dotnet/AuthzSdk/AuthzClient.cs`
  - `libs/sdk-dotnet/AuthzSdk/AuthzOptions.cs`
  - `libs/sdk-dotnet/AuthzSdk/Caching/RequestPermissionCache.cs`
  - `libs/sdk-dotnet/AuthzSdk/Extensions/ServiceCollectionExtensions.cs`
  - `libs/sdk-dotnet/AuthzSdk/Models/SyncRequest.cs`
  - `libs/sdk-dotnet/AuthzSdk/Models/UserPermissions.cs`
  - `libs/sdk-dotnet/AuthzSdk/Exceptions/AuthzClientException.cs`
  - `libs/sdk-dotnet/AuthzSdk.Tests/AuthzSdk.Tests.csproj`
  - `libs/sdk-dotnet/AuthzSdk.Tests/AuthzClientTests.cs`
  - `libs/sdk-dotnet/AuthzSdk.Tests/RequestPermissionCacheTests.cs`
- **Modificar:**
  - `authz-stack.sln` — adicionar projetos
- **Skills para consultar durante implementação:**
  - `csharp-dotnet-architecture` — Result Pattern, organização Clean
  - `dotnet-dependency-config` — IHttpClientFactory + Polly
  - `dotnet-testing` — xUnit + AwesomeAssertions + Moq
  - `dotnet-code-quality` — async/await, CancellationToken obrigatório

## Subtarefas

- [ ] 19.1 csproj + dependências NuGet (Polly, Microsoft.Extensions.*)
- [ ] 19.2 `IAuthzClient` + impl com `IHttpClientFactory` + Polly policy
- [ ] 19.3 `RequestPermissionCache` scoped
- [ ] 19.4 `AddAuthzSdk` extension
- [ ] 19.5 Testes com `WireMock.Net` para HTTP isolation

## Sequenciamento

- Bloqueado por: 7.0
- Desbloqueia: 20.0, 21.0, 22.0, 29.0
- Paralelizável: Sim (paralelo a 15.0)

## Rastreabilidade

- Esta tarefa cobre: parte de RF-10
- Evidência esperada: app .NET consegue chamar `AddAuthzSdk()` em Program.cs e injetar `IAuthzClient`

## Detalhes de Implementação

**Padrão `AddAuthzSdk`:**
```csharp
public static IServiceCollection AddAuthzSdk(this IServiceCollection services, IConfiguration config)
{
    services.Configure<AuthzOptions>(config.GetSection("Authz"));
    services.AddHttpClient<IAuthzClient, AuthzClient>()
        .AddPolicyHandler(GetRetryPolicy())
        .AddPolicyHandler(GetCircuitBreakerPolicy());
    services.AddScoped<RequestPermissionCache>();
    return services;
}
```

**Convenções da stack:**
- Async/await com `CancellationToken` (`dotnet-code-quality`)
- DI scoped para cache (`csharp-dotnet-architecture`)
- ProblemDetails para erros (`dotnet-architecture`)
- Sem logar `ModuleKey` (`dotnet-production-readiness`)

## Critérios de Sucesso (Verificáveis)

- [ ] `dotnet test libs/sdk-dotnet/AuthzSdk.Tests` passa
- [ ] `dotnet pack libs/sdk-dotnet/AuthzSdk` produz `.nupkg`
- [ ] Registro via `AddAuthzSdk` funciona em Program.cs de teste
- [ ] Cache scoped: 2 chamadas no mesmo request → 1 HTTP call (validável via WireMock)
