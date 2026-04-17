---
status: pending
parallelizable: true
blocked_by: [7.0, 19.0]
---

<task_context>
<domain>libs/sdk-dotnet</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>high</complexity>
<dependencies>http_server,temporal</dependencies>
<unblocks>"22.0"</unblocks>
</task_context>

# Tarefa 21.0: SDK .NET — SelfRegistrationHostedService + HeartbeatHostedService

## Relacionada às User Stories

- RF-16 — Auto-registro de módulos no bootstrap (lado .NET)

## Visão Geral

Espelho .NET do registration runner Java: `IHostedService` que executa no startup, lê `permissions.yaml`, faz sync com retry exp, controla `IHealthCheck` de readiness via flag até primeiro sync OK. Heartbeat re-sync a cada 15min.

## Requisitos

- `PermissionsYamlLoader` lê arquivo YAML embutido (`Build Action = Content`)
- `SelfRegistrationHostedService : BackgroundService`:
  - Calcula hash, dispara sync com Polly (retry exp)
  - Em sucesso: `RegistrationStateHolder.MarkReady()`
  - Em falha terminal: log Critical, mantém DOWN
- `RegistrationStateHolder` singleton thread-safe
- `RegistrationHealthCheck : IHealthCheck` consulta state holder
- `HeartbeatHostedService : BackgroundService` `Task.Delay(15min)` loop
- Registro via `AddAuthzSdk` (extension de 19.0)

## Arquivos Envolvidos

- **Criar:**
  - `libs/sdk-dotnet/AuthzSdk/Registration/PermissionsYamlLoader.cs`
  - `libs/sdk-dotnet/AuthzSdk/Registration/SelfRegistrationHostedService.cs`
  - `libs/sdk-dotnet/AuthzSdk/Registration/HeartbeatHostedService.cs`
  - `libs/sdk-dotnet/AuthzSdk/Registration/RegistrationStateHolder.cs`
  - `libs/sdk-dotnet/AuthzSdk/Registration/RegistrationHealthCheck.cs`
  - `libs/sdk-dotnet/AuthzSdk/Registration/RegistrationOptions.cs`
  - `libs/sdk-dotnet/AuthzSdk.Tests/Registration/SelfRegistrationHostedServiceTests.cs`
  - `libs/sdk-dotnet/AuthzSdk.Tests/Registration/RegistrationStateHolderTests.cs`
- **Modificar:**
  - `libs/sdk-dotnet/AuthzSdk/Extensions/ServiceCollectionExtensions.cs` (registrar hosted services + health check com tag `ready`)
  - `libs/sdk-dotnet/AuthzSdk/AuthzSdk.csproj` (`YamlDotNet`)
- **Skills para consultar durante implementação:**
  - `dotnet-observability` — Health Checks tag `ready` para K8s readiness probe
  - `csharp-dotnet-architecture` — Hosted services pattern
  - `dotnet-testing` — testar hosted service com WireMock

## Subtarefas

- [ ] 21.1 `PermissionsYamlLoader` (YamlDotNet)
- [ ] 21.2 `RegistrationStateHolder`
- [ ] 21.3 `SelfRegistrationHostedService` com Polly retry policy programática
- [ ] 21.4 `RegistrationHealthCheck`
- [ ] 21.5 `HeartbeatHostedService`
- [ ] 21.6 Wire-up em `AddAuthzSdk`
- [ ] 21.7 Testes

## Sequenciamento

- Bloqueado por: 7.0, 19.0
- Desbloqueia: 22.0
- Paralelizável: Sim (paralelo a 17.0)

## Rastreabilidade

- Esta tarefa cobre: RF-16 (lado .NET)
- Evidência esperada: app .NET de teste registra automaticamente; sem AuthZ, `/health/ready` retorna `Unhealthy`

## Detalhes de Implementação

**Health endpoint setup (no consumidor, exemplo):**
```csharp
app.MapHealthChecks("/health/ready", new HealthCheckOptions {
    Predicate = check => check.Tags.Contains("ready")
});
```

**Convenções:**
- Hosted service deve ser robusto a `OperationCanceledException` (graceful shutdown)
- Logs estruturados via `ILogger<T>` (`dotnet-observability`)
- Async com CancellationToken propagado

## Critérios de Sucesso (Verificáveis)

- [ ] Testes passam
- [ ] App .NET teste sobe + AuthZ rodando → `/health/ready` retorna `Healthy`
- [ ] AuthZ down → `/health/ready` retorna `Unhealthy` com motivo
- [ ] Heartbeat re-dispara a cada 15min
- [ ] Sem `permissions.yaml` → falha startup
