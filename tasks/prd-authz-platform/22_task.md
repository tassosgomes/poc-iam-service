---
status: pending
parallelizable: false
blocked_by: [20.0, 21.0]
---

<task_context>
<domain>apps/demo-ms-dotnet</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>low</complexity>
<dependencies>http_server</dependencies>
<unblocks>"30.0"</unblocks>
</task_context>

# Tarefa 22.0: Demo MS .NET (estoque) — endpoints protegidos + permissions.yaml

## Relacionada às User Stories

- RF-02, RF-04, RF-14

## Visão Geral

MS demo de Estoque em .NET 8 consumindo o SDK .NET. Expõe endpoints protegidos com `[HasPermission]`, `GET /.well-known/permissions` e auto-registro via `AddAuthzSdk()`. Roda no docker-compose.

## Requisitos

- Projeto `apps/demo-ms-dotnet/DemoStock.Api` referenciando o SDK .NET (via project reference no solution; em CI o NuGet)
- `permissions.yaml`: `estoque.inventory.view`, `estoque.inventory.adjust`, `estoque.can_manage_users`
- `InventoryController`: `[HasPermission("estoque.inventory.view")]`, `[HasPermission("estoque.inventory.adjust")]`
- `DiscoveryController` `/.well-known/permissions`
- `Program.cs`: `AddAuthzSdk()`, `AddAuthentication().AddJwtBearer(...)`, mapear health checks
- Dockerfile multi-stage

## Arquivos Envolvidos

- **Criar:**
  - `apps/demo-ms-dotnet/DemoStock.Api/DemoStock.Api.csproj`
  - `apps/demo-ms-dotnet/DemoStock.Api/Dockerfile`
  - `apps/demo-ms-dotnet/DemoStock.Api/Program.cs`
  - `apps/demo-ms-dotnet/DemoStock.Api/Controllers/InventoryController.cs`
  - `apps/demo-ms-dotnet/DemoStock.Api/Controllers/DiscoveryController.cs`
  - `apps/demo-ms-dotnet/DemoStock.Api/Models/InventoryItem.cs`
  - `apps/demo-ms-dotnet/DemoStock.Api/appsettings.json`
  - `apps/demo-ms-dotnet/DemoStock.Api/permissions.yaml`
  - `apps/demo-ms-dotnet/DemoStock.Api.Tests/InventoryControllerTests.cs`
- **Modificar:**
  - `authz-stack.sln` — adicionar projetos
  - `infra/docker/docker-compose.yml` — descomentar `demo-ms-dotnet`
  - `infra/docker/.env.example` — `AUTHZ_MODULE_KEY_ESTOQUE`
- **Skills para consultar durante implementação:**
  - `csharp-dotnet-architecture` — Minimal API ou Controllers (escolher Controllers para alinhar com SDK Java)
  - `dotnet-observability` — health checks endpoints, logging
  - `dotnet-testing` — `WebApplicationFactory` para integration

## Subtarefas

- [ ] 22.1 csproj + bootstrap
- [ ] 22.2 `permissions.yaml` (Build Action = Content, CopyToOutput)
- [ ] 22.3 `Program.cs` com pipeline JWT + AuthzSdk + health endpoints
- [ ] 22.4 Controllers
- [ ] 22.5 Dockerfile + integração compose
- [ ] 22.6 Tests via `WebApplicationFactory`

## Sequenciamento

- Bloqueado por: 20.0, 21.0
- Desbloqueia: 30.0
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-02, RF-04, parte de RF-14
- Evidência esperada: docker-compose sobe o serviço; auto-registra; `/.well-known/permissions` retorna 3 permissões; endpoints respeitam `[HasPermission]`

## Detalhes de Implementação

**`Program.cs` skeleton:**
```csharp
var builder = WebApplication.CreateBuilder(args);
builder.Services.AddAuthentication("Bearer").AddJwtBearer(...);
builder.Services.AddAuthorization();
builder.Services.AddAuthzSdk(builder.Configuration);
builder.Services.AddControllers();
var app = builder.Build();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapHealthChecks("/health/ready", new() { Predicate = c => c.Tags.Contains("ready") });
app.MapHealthChecks("/health/live");
app.Run();
```

## Critérios de Sucesso (Verificáveis)

- [ ] `dotnet test` passa
- [ ] `docker-compose up demo-ms-dotnet` sobe; logs mostram registration sucesso
- [ ] `curl /.well-known/permissions` retorna 3 permissões
- [ ] `GET /inventory` sem JWT → 401
- [ ] Com JWT + permissão → 200
- [ ] Sem permissão → 403
