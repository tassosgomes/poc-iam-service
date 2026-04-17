---
name: dotnet-dependency-config
description: "Dependencias e configuracoes padrao para projetos .NET C# / ASP.NET Core: pacotes NuGet base, Entity Framework Core com PostgreSQL (padrao oficial) e Oracle (alternativa suportada), Mapster para mapeamento, FluentValidation, CQRS nativo, Polly para resiliencia, RabbitMQ com Rmq.CloudEvents, configuration patterns (appsettings, options, DI), Unit of Work, migrations, interceptors, criacao de bibliotecas profissionais NuGet. Usar quando: criar projeto .NET; adicionar infra (DB, cache, messaging); configurar EF Core; alterar baseline de libs; criar ou publicar class library NuGet."
---

# Dependencias e Configuracoes .NET C# e ASP.NET Core

Documento normativo para padronizacao de dependencias, configuracoes e infraestrutura.
Acionada quando: criar projeto, adicionar nova integracao, alterar infraestrutura.

> **Politica de Banco de Dados**
> - **PostgreSQL e o padrao oficial** para novos servicos
> - **Oracle e alternativa suportada** apenas para: legado, integracoes existentes, ou aprovacao explicita
> - Exemplos nesta skill usam PostgreSQL como caminho principal e Oracle como alternativa

---

## Indice

1. [Bibliotecas Recomendadas](#bibliotecas-recomendadas)
2. [Entity Framework Core](#entity-framework-core)
3. [Mapeamento](#mapeamento)
4. [Configuracao e DI Patterns](#configuracao-e-di-patterns)
5. [Mensageria — RabbitMQ com Rmq.CloudEvents](#mensageria--rabbitmq-com-rmqcloudevents)
6. [Criacao de Bibliotecas Profissionais](#criacao-de-bibliotecas-profissionais)

---

# 1. Bibliotecas Recomendadas

> **Politica de Versionamento**: Sempre utilize a **ultima versao stable** disponivel de cada pacote.
> Nao fixe versoes nesta documentacao para evitar que fique desatualizada.
> Use `dotnet outdated` ou o NuGet Package Manager para verificar atualizacoes.

### Core Libraries
```xml
<PackageReference Include="Microsoft.Extensions.DependencyInjection" />
<PackageReference Include="Microsoft.Extensions.Logging" />
<PackageReference Include="Microsoft.Extensions.Configuration" />
<PackageReference Include="Microsoft.Extensions.Options" />
```

### Web Development (ASP.NET Core)
```xml
<PackageReference Include="Microsoft.AspNetCore.OpenApi" />
<PackageReference Include="Swashbuckle.AspNetCore" />
<PackageReference Include="FluentValidation.AspNetCore" />
<!-- AutoMapper removido por questoes de licenciamento -->
<!-- Use Mapster ou mapeamento manual conforme secao de mapeamento -->
```

### Database — PostgreSQL (Padrao Oficial)
```xml
<PackageReference Include="Microsoft.EntityFrameworkCore" />
<PackageReference Include="Microsoft.EntityFrameworkCore.Design" />
<PackageReference Include="Microsoft.EntityFrameworkCore.Tools" />
<PackageReference Include="Npgsql.EntityFrameworkCore.PostgreSQL" />
<PackageReference Include="Microsoft.Extensions.Configuration" />
```

### Database — Oracle (Alternativa Suportada)
```xml
<!-- Usar APENAS quando aprovado: legado, integracoes existentes -->
<PackageReference Include="Oracle.EntityFrameworkCore" />
```

### HTTP Client
```xml
<PackageReference Include="Microsoft.Extensions.Http" />
<PackageReference Include="RestSharp" />
<PackageReference Include="System.Net.Http.Json" />
```

### Serialization
```xml
<PackageReference Include="System.Text.Json" />
<PackageReference Include="Newtonsoft.Json" />
```

### Observabilidade (OpenTelemetry — Padrao Oficial)
```xml
<PackageReference Include="OpenTelemetry.Exporter.OpenTelemetryProtocol" />
<PackageReference Include="OpenTelemetry.Extensions.Hosting" />
<PackageReference Include="OpenTelemetry.Instrumentation.AspNetCore" />
<PackageReference Include="OpenTelemetry.Instrumentation.Http" />
<PackageReference Include="OpenTelemetry.Instrumentation.EntityFrameworkCore" />
```

### Mensageria — RabbitMQ
```xml
<PackageReference Include="Rmq.CloudEvents" />
```

### Resilience
```xml
<PackageReference Include="Polly" />
<PackageReference Include="Polly.Extensions.Http" />
```

### Utilities
```xml
<PackageReference Include="FluentValidation" />
<PackageReference Include="Mapster" />
```

---

# 2. Entity Framework Core

### Por que usar Entity Framework Core?
- **ORM Completo**: Mapeamento objeto-relacional com suporte a LINQ
- **Migrations**: Controle de versao do schema do banco de dados
- **Change Tracking**: Rastreamento automatico de alteracoes nas entidades
- **Lazy/Eager Loading**: Controle flexivel de carregamento de dados relacionados
- **Multi-Provider**: Suporte a diversos bancos de dados (PostgreSQL, Oracle, SQL Server)

### DbContext - Configuracao Base
```csharp
public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options)
        : base(options)
    {
    }

    public DbSet<User> Users { get; set; } = null!;
    public DbSet<Product> Products { get; set; } = null!;
    public DbSet<Order> Orders { get; set; } = null!;

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(AppDbContext).Assembly);
        base.OnModelCreating(modelBuilder);
    }
}
```

### Configuracao de Entidade com Fluent API
```csharp
public class UserConfiguration : IEntityTypeConfiguration<User>
{
    public void Configure(EntityTypeBuilder<User> builder)
    {
        builder.ToTable("users");
        
        builder.HasKey(u => u.Id);
        
        builder.Property(u => u.Id)
            .HasColumnName("user_id")
            .ValueGeneratedOnAdd();
        
        builder.Property(u => u.Name)
            .HasColumnName("name")
            .HasMaxLength(100)
            .IsRequired();
        
        builder.Property(u => u.Email)
            .HasColumnName("email")
            .HasMaxLength(255)
            .IsRequired();
        
        builder.HasIndex(u => u.Email)
            .IsUnique()
            .HasDatabaseName("ix_users_email");
        
        builder.HasMany(u => u.Orders)
            .WithOne(o => o.User)
            .HasForeignKey(o => o.UserId)
            .HasConstraintName("fk_orders_users")
            .OnDelete(DeleteBehavior.Restrict);
    }
}
```

### Registro no DI — PostgreSQL (Padrao)
```csharp
// Program.cs — PostgreSQL (padrao oficial)
var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<AppDbContext>(options =>
{
    var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
    options.UseNpgsql(connectionString, npgsqlOptions =>
    {
        npgsqlOptions.MigrationsHistoryTable("__ef_migrations_history");
    });
    
    if (builder.Environment.IsDevelopment())
    {
        options.EnableSensitiveDataLogging();
        options.EnableDetailedErrors();
    }
});

// Alternative: DbContext Pooling for better performance
builder.Services.AddDbContextPool<AppDbContext>(options =>
{
    var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
    options.UseNpgsql(connectionString);
}, poolSize: 128);

// Repository registration
builder.Services.AddScoped<IUserRepository, UserRepository>();
builder.Services.AddScoped<IProductRepository, ProductRepository>();
builder.Services.AddScoped<IUnitOfWork, UnitOfWork>();
```

### Registro no DI — Oracle (Alternativa)
```csharp
// Program.cs — Oracle (alternativa suportada por excecao)
builder.Services.AddDbContext<AppDbContext>(options =>
{
    var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
    options.UseOracle(connectionString, oracleOptions =>
    {
        oracleOptions.MigrationsHistoryTable("__EF_MIGRATIONS_HISTORY");
        oracleOptions.UseOracleSQLCompatibility(OracleSQLCompatibility.DatabaseVersion19);
    });
    
    if (builder.Environment.IsDevelopment())
    {
        options.EnableSensitiveDataLogging();
        options.EnableDetailedErrors();
    }
});
```

### Unit of Work Pattern
```csharp
public interface IUnitOfWork : IDisposable
{
    IUserRepository Users { get; }
    IProductRepository Products { get; }
    IOrderRepository Orders { get; }
    Task<int> CommitAsync(CancellationToken cancellationToken = default);
    Task RollbackAsync();
}

public class UnitOfWork : IUnitOfWork
{
    private readonly AppDbContext _context;
    private IUserRepository? _users;
    private IProductRepository? _products;
    private IOrderRepository? _orders;

    public UnitOfWork(AppDbContext context)
    {
        _context = context;
    }

    public IUserRepository Users => 
        _users ??= new UserRepository(_context);
    
    public IProductRepository Products => 
        _products ??= new ProductRepository(_context);
    
    public IOrderRepository Orders => 
        _orders ??= new OrderRepository(_context);

    public async Task<int> CommitAsync(CancellationToken cancellationToken = default)
    {
        return await _context.SaveChangesAsync(cancellationToken);
    }

    public async Task RollbackAsync()
    {
        await _context.Database.RollbackTransactionAsync();
    }

    public void Dispose()
    {
        _context.Dispose();
    }
}
```

### Generic Repository Pattern
```csharp
public interface IBaseRepository<T> where T : class
{
    Task<T?> GetByIdAsync(int id, CancellationToken cancellationToken = default);
    Task<IEnumerable<T>> GetAllAsync(CancellationToken cancellationToken = default);
    Task<IEnumerable<T>> SearchAsync(Expression<Func<T, bool>> predicate, CancellationToken cancellationToken = default);
    Task AddAsync(T entity, CancellationToken cancellationToken = default);
    void Update(T entity);
    void Remove(T entity);
}

public class BaseRepository<T> : IBaseRepository<T> where T : class
{
    protected readonly AppDbContext _context;
    protected readonly DbSet<T> _dbSet;

    public BaseRepository(AppDbContext context)
    {
        _context = context;
        _dbSet = context.Set<T>();
    }

    public virtual async Task<T?> GetByIdAsync(int id, CancellationToken cancellationToken = default)
    {
        return await _dbSet.FindAsync(new object[] { id }, cancellationToken);
    }

    public virtual async Task<IEnumerable<T>> GetAllAsync(CancellationToken cancellationToken = default)
    {
        return await _dbSet.ToListAsync(cancellationToken);
    }

    public virtual async Task<IEnumerable<T>> SearchAsync(
        Expression<Func<T, bool>> predicate, 
        CancellationToken cancellationToken = default)
    {
        return await _dbSet.Where(predicate).ToListAsync(cancellationToken);
    }

    public virtual async Task AddAsync(T entity, CancellationToken cancellationToken = default)
    {
        await _dbSet.AddAsync(entity, cancellationToken);
    }

    public virtual void Update(T entity)
    {
        _dbSet.Update(entity);
    }

    public virtual void Remove(T entity)
    {
        _dbSet.Remove(entity);
    }
}
```

### Repositorio Especifico com Queries Complexas
```csharp
public interface IUserRepository : IBaseRepository<User>
{
    Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default);
    Task<User?> GetWithOrdersAsync(int id, CancellationToken cancellationToken = default);
    Task<IEnumerable<User>> GetActiveUsersAsync(CancellationToken cancellationToken = default);
}

public class UserRepository : BaseRepository<User>, IUserRepository
{
    public UserRepository(AppDbContext context) : base(context) { }

    public async Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default)
    {
        return await _dbSet
            .AsNoTracking()
            .FirstOrDefaultAsync(u => u.Email == email, cancellationToken);
    }

    public async Task<User?> GetWithOrdersAsync(int id, CancellationToken cancellationToken = default)
    {
        return await _dbSet
            .Include(u => u.Orders)
                .ThenInclude(o => o.Items)
                    .ThenInclude(i => i.Product)
            .FirstOrDefaultAsync(u => u.Id == id, cancellationToken);
    }

    public async Task<IEnumerable<User>> GetActiveUsersAsync(CancellationToken cancellationToken = default)
    {
        return await _dbSet
            .AsNoTracking()
            .Where(u => u.Active)
            .OrderBy(u => u.Name)
            .ToListAsync(cancellationToken);
    }
}
```

### Migrations - Comandos Essenciais
```bash
# Create a new migration
dotnet ef migrations add MigrationName

# Apply pending migrations
dotnet ef database update

# Revert to a specific migration
dotnet ef database update PreviousMigrationName

# Generate SQL script from migrations
dotnet ef migrations script

# List migrations
dotnet ef migrations list

# Remove last migration (if not applied)
dotnet ef migrations remove
```

### Configuracao de Connection String
```json
// appsettings.json — PostgreSQL (padrao)
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Port=5432;Database=mydb;Username=myuser;Password=mypassword;"
  }
}

// appsettings.json — Oracle (alternativa)
// {
//   "ConnectionStrings": {
//     "DefaultConnection": "User Id=myUser;Password=myPassword;Data Source=localhost:1521/ORCLPDB1;"
//   }
// }
```

### Interceptors para Auditoria
```csharp
public class AuditInterceptor : SaveChangesInterceptor
{
    public override InterceptionResult<int> SavingChanges(
        DbContextEventData eventData,
        InterceptionResult<int> result)
    {
        UpdateAuditFields(eventData.Context);
        return base.SavingChanges(eventData, result);
    }

    public override ValueTask<InterceptionResult<int>> SavingChangesAsync(
        DbContextEventData eventData,
        InterceptionResult<int> result,
        CancellationToken cancellationToken = default)
    {
        UpdateAuditFields(eventData.Context);
        return base.SavingChangesAsync(eventData, result, cancellationToken);
    }

    private void UpdateAuditFields(DbContext? context)
    {
        if (context is null) return;

        var now = DateTime.UtcNow;
        
        foreach (var entry in context.ChangeTracker.Entries<IAuditableEntity>())
        {
            switch (entry.State)
            {
                case EntityState.Added:
                    entry.Entity.CreatedAt = now;
                    entry.Entity.UpdatedAt = now;
                    break;
                case EntityState.Modified:
                    entry.Entity.UpdatedAt = now;
                    break;
            }
        }
    }
}

// Interceptor registration
builder.Services.AddDbContext<AppDbContext>((sp, options) =>
{
    options.UseNpgsql(connectionString)
           .AddInterceptors(new AuditInterceptor());
});
```

---

# 3. Mapeamento

> **AutoMapper removido por questoes de licenciamento**

### Mapster (Recomendado)
```csharp
// Basic configuration
TypeAdapterConfig<User, UserDto>.NewConfig();

// DI registration
builder.Services.AddMapster();

// Usage
var dto = user.Adapt<UserDto>();
var list = users.Adapt<IEnumerable<UserDto>>();
```

### Alternativa: Extension Methods
```csharp
public static class UserExtensions
{
    public static UserDto ToDto(this User user) => new()
    {
        Id = user.Id,
        Name = user.Name,
        Email = user.Email
    };
}
```

---

# 4. Configuracao e DI Patterns

### Uso em Controller
```csharp
public class UsersController : ControllerBase
{
    private readonly IUnitOfWork _unitOfWork;
    private readonly ILogger<UsersController> _logger;

    public UsersController(
        IUnitOfWork unitOfWork,
        ILogger<UsersController> logger)
    {
        _unitOfWork = unitOfWork;
        _logger = logger;
    }

    [HttpGet("{id}")]
    public async Task<IActionResult> GetByIdAsync(int id, CancellationToken cancellationToken)
    {
        var user = await _unitOfWork.Users.GetByIdAsync(id, cancellationToken);
        
        if (user is null)
            return NotFound();
        
        return Ok(user.Adapt<UserDto>());
    }

    [HttpPost]
    public async Task<IActionResult> CreateAsync(
        [FromBody] CreateUserRequest request,
        CancellationToken cancellationToken)
    {
        var user = request.Adapt<User>();
        
        await _unitOfWork.Users.AddAsync(user, cancellationToken);
        await _unitOfWork.CommitAsync(cancellationToken);
        
        _logger.LogInformation("User {UserId} created successfully", user.Id);
        
        return CreatedAtAction(nameof(GetByIdAsync), new { id = user.Id }, user.Adapt<UserDto>());
    }
}
```

---

# 5. Mensageria — RabbitMQ com Rmq.CloudEvents

> **Rmq.CloudEvents** e a biblioteca padrao para mensageria com RabbitMQ.
> - NuGet: https://www.nuget.org/packages/Rmq.CloudEvents
> - GitHub: https://github.com/tassosgomes/dotnet-rabbimq-lib

### Por que usar Rmq.CloudEvents?
- **Quorum Queues**: Declaracao automatica de filas quorum com DLQ (`<queue>.dlq`) e DLX
- **CloudEvents**: Wrapping/unwrapping transparente no formato CloudEvents JSON (`application/cloudevents+json`)
- **Retry com Polly**: Retry exponencial integrado para publish e consumer handler
- **DI-first**: Registro nativo para ASP.NET Core e Worker Services
- **Consumer Pipeline**: ACK automatico em sucesso, NACK (`requeue: false`) em falha final com roteamento para DLQ

### Requisitos
- .NET SDK 8.0+
- RabbitMQ 3.8+ (quorum queues)

### Instalacao
```bash
dotnet add package Rmq.CloudEvents
```

### Configuracao de Servicos
```csharp
using Rmq.CloudEvents.Configuration;
using Rmq.CloudEvents.Extensions;

builder.Services.AddRmqCloudEvents(options =>
{
    options.Connection = new RmqConnectionOptions
    {
        HostName = "localhost",
        Port = 5672,
        UserName = "guest",
        Password = "guest",
        VirtualHost = "/"
    };

    options.DefaultCloudEvents = new CloudEventsOptions
    {
        Source = new Uri("/my-service", UriKind.Relative),
        DefaultType = "com.mycompany.events"
    };
});
```

### Configuracao via appsettings.json
```json
{
  "RabbitMQ": {
    "Connection": {
      "HostName": "localhost",
      "Port": 5672,
      "UserName": "guest",
      "Password": "guest",
      "VirtualHost": "/"
    },
    "DefaultCloudEvents": {
      "Source": "/my-service",
      "DefaultType": "com.mycompany.events"
    },
    "DefaultRetry": {
      "MaxAttempts": 5,
      "InitialDelay": "00:00:01",
      "BackoffType": "Exponential",
      "UseJitter": true
    }
  }
}
```

### Modelo de Configuracao (`RmqOptions`)

| Propriedade | Tipo | Descricao |
|---|---|---|
| `Connection` | `RmqConnectionOptions` | `HostName`, `Port`, `UserName`, `Password`, `VirtualHost`, `Ssl`, `NetworkRecoveryInterval` |
| `DefaultCloudEvents` | `CloudEventsOptions` | `Source`, `DefaultType`, `SpecVersion` |
| `DefaultRetry` | `RetryOptions` | `MaxAttempts` (default 5), `InitialDelay` (default 1s), `BackoffType` (Exponential/Linear/Constant), `UseJitter` (default true) |
| `Queues` | `Dictionary<string, QueueOptions>` | Overrides por fila: tamanho quorum, delivery limit, retry, sufixo DLQ |

### Registrar um Consumer
```csharp
using Rmq.CloudEvents.Consuming;

// Registra o consumer associado a fila "orders"
builder.Services.AddRmqConsumer<OrderCreated, OrderCreatedHandler>("orders");

// Handler implementa IRmqMessageHandler<T>
public sealed class OrderCreatedHandler : IRmqMessageHandler<OrderCreated>
{
    public Task HandleAsync(
        OrderCreated message,
        MessageContext context,
        CancellationToken cancellationToken)
    {
        Console.WriteLine(
            $"Order {message.OrderId} received from {context.QueueName}, eventId={context.EventId}");
        return Task.CompletedTask;
    }
}

// Mensagem como record imutavel
public sealed record OrderCreated(int OrderId, string CustomerId, decimal Total);
```

### Publicar Mensagens
```csharp
using Rmq.CloudEvents.Publishing;

var publisher = serviceProvider.GetRequiredService<IRmqPublisher>();

// Publicacao basica
await publisher.PublishAsync(
    queueName: "orders",
    payload: new OrderCreated(1, "cust-001", 99.90m),
    cloudEventType: "com.mycompany.order.created.v1",
    cancellationToken: cancellationToken);

// Publicacao com headers customizados
await publisher.PublishAsync(
    queueName: "orders",
    payload: new OrderCreated(2, "cust-002", 149.50m),
    headers: new Dictionary<string, object>
    {
        ["x-correlation-id"] = "corr-123",
        ["x-tenant"] = "tenant-a"
    },
    cancellationToken: cancellationToken);
```

### Comportamento em Runtime

**Publish:**
- Payload e envelopado como CloudEvent JSON
- Topologia da fila e declarada (idempotente) antes do primeiro publish
- Politica de retry trata erros transientes de RabbitMQ/rede

**Consume:**
- Mensagem e desenvelopada do CloudEvent; handler recebe apenas o payload
- Sucesso: ACK
- Falha final (apos retries): NACK com `requeue: false`, mensagem roteada para DLQ

### Fluxo de Retry e DLX
```
Publish request
    |---> Publish OK? --yes--> Main queue ---> Consume message
    |         |                                      |
    |        no                               Handler OK?
    |         |                              /          \
    |   Publish error                      yes          no
    |                                       |            |
    |                                      ACK     Retry left?
    |                                              /        \
    |                                            yes        no
    |                                             |          |
    |                                        Retry handler   NACK (no requeue)
    |                                                            |
    |                                                       DLX exchange
    |                                                            |
    |                                                       Queue.dlq
```

---

# 6. Criacao de Bibliotecas Profissionais

## Objetivo de uma biblioteca profissional

Uma biblioteca .NET profissional deve ser:
- **Inclusiva**: rodar em varios tipos de apps/plataformas
- **Estavel**: conviver bem com outras bibliotecas no mesmo processo
- **Projetada para evoluir**: permitir melhorias sem quebrar quem ja usa
- **Depuravel**: facil de diagnosticar problemas
- **Confiavel**: publicada e mantida seguindo boas praticas de seguranca e qualidade

## Decisoes iniciais de projeto

### Tipo de projeto e template
```bash
dotnet new classlib -n MinhaEmpresa.MinhaLib
```

- Prefira **SDK-style projects** (padrao no .NET Core/5+/6+/8+)
- Use a **versao mais recente de C#** possivel compativel com seus consumidores

### Target frameworks
```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFrameworks>netstandard2.0;net8.0</TargetFrameworks>
    <LangVersion>latest</LangVersion>
    <Nullable>enable</Nullable>
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
  </PropertyGroup>
</Project>
```

## Estrutura da solucao

```
src/MinhaEmpresa.MinhaLib/MinhaEmpresa.MinhaLib.csproj
tests/MinhaEmpresa.MinhaLib.Tests/MinhaEmpresa.MinhaLib.Tests.csproj
samples/MinhaEmpresa.MinhaLib.Samples/…  (opcional)
```

## Configuracao basica de qualidade

```xml
<PropertyGroup>
  <Nullable>enable</Nullable>
  <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
  <ImplicitUsings>enable</ImplicitUsings>
</PropertyGroup>
```

## Design da API publica

- **Scenario-Driven Design**: Liste cenarios principais e molde a API para que fiquem simples
- **Namespaces**: `MinhaEmpresa.MinhaArea.MinhaLib`
- **Classes publicas**: PascalCase
- **Metodos**: PascalCase com sufixo `Async` para operacoes assincronas
- Prefira APIs **assincronas** para I/O com `CancellationToken`
- Use tipos .NET conhecidos: `DateTimeOffset`, `IReadOnlyCollection<T>`, `IEnumerable<T>`
- Use **excecoes**, nao codigos de erro de retorno
- Prefira **tipos imutaveis** (especialmente DTOs/valores)
- `internal` em vez de `public` quando algo nao e para uso externo

## Empacotamento e publicacao (NuGet)

```xml
<PropertyGroup>
  <PackageId>MinhaEmpresa.MinhaLib</PackageId>
  <Version>1.0.0</Version>
  <Authors>Minha Empresa</Authors>
  <Description>Descricao clara e objetiva da biblioteca.</Description>
  <PackageTags>logging;rest;cliente-api</PackageTags>
  <RepositoryUrl>https://github.com/minha-empresa/minha-lib</RepositoryUrl>
  <PackageLicenseExpression>MIT</PackageLicenseExpression>
  <IncludeSymbols>true</IncludeSymbols>
  <SymbolPackageFormat>snupkg</SymbolPackageFormat>
  <PublishRepositoryUrl>true</PublishRepositoryUrl>
</PropertyGroup>
```

```bash
dotnet pack -c Release
dotnet nuget push bin/Release/MinhaEmpresa.MinhaLib.1.0.0.nupkg \
  --api-key <API_KEY> \
  --source https://api.nuget.org/v3/index.json
```

### SemVer 2.0.0

- **MAJOR**: quebra de compatibilidade de API
- **MINOR**: novas funcionalidades compativeis
- **PATCH**: correcoes de bug sem mudar API

### Planejando evolucao

- API publica e **contrato**: evite mudar ou remover membros publicos
- Em vez de remover, marque como `[Obsolete]` com mensagem e plano de remocao
- Quando precisar quebrar API: versao MAJOR nova + breaking changes documentados

---

## Checklist de Configuracao

### Bibliotecas Essenciais
- [ ] Microsoft.Extensions.* (DI, Logging, Configuration)
- [ ] Microsoft.EntityFrameworkCore + Npgsql.EntityFrameworkCore.PostgreSQL (padrao)
- [ ] OpenTelemetry para observabilidade (padrao oficial)
- [ ] FluentValidation para validacoes
- [ ] CQRS nativo (implementacao propria sem MediatR)
- [ ] Mapster ou mapeamento manual (alternativa ao AutoMapper)
- [ ] Rmq.CloudEvents para mensageria RabbitMQ

### Entity Framework Core
- [ ] DbContext configurado com DbContextOptions
- [ ] Entidades configuradas via Fluent API (IEntityTypeConfiguration)
- [ ] DbContext registrado no DI (AddDbContext ou AddDbContextPool)
- [ ] Unit of Work implementado
- [ ] Repositorios genericos e especificos implementados
- [ ] Migrations configuradas e aplicadas
- [ ] Interceptors de auditoria configurados (opcional)
- [ ] Connection string configurada (PostgreSQL ou Oracle)

### Mapeamento
- [ ] Mapster configurado (opcao recomendada)
- [ ] Extension methods implementados (opcao manual)
- [ ] Mapper patterns registrados no DI
- [ ] Configuracoes de mapeamento centralizadas

### Mensageria (RabbitMQ)
- [ ] Rmq.CloudEvents adicionado ao projeto
- [ ] AddRmqCloudEvents configurado no DI
- [ ] Connection options definidas (appsettings ou codigo)
- [ ] CloudEvents source e type configurados
- [ ] Consumers registrados com AddRmqConsumer<T, THandler>
- [ ] Handlers implementando IRmqMessageHandler<T>
- [ ] Publisher injetado via IRmqPublisher
- [ ] Retry e DLQ configurados conforme necessidade

### Criacao de Bibliotecas
- [ ] SDK-style project configurado
- [ ] Multi-target quando necessario
- [ ] Nullable e TreatWarningsAsErrors habilitados
- [ ] Metadata de pacote NuGet preenchida
- [ ] SemVer aplicado
- [ ] SourceLink e simbolos configurados
- [ ] README e CHANGELOG documentados