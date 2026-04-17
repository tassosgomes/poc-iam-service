---
name: dotnet-architecture
description: "Padroes arquiteturais e estrutura de projeto .NET C# / ASP.NET Core: Clean Architecture com camadas numeradas, Repository Pattern com Entity Framework Core, CQRS nativo (sem MediatR) com Commands, Queries, Handlers e Dispatcher, tratamento global de erros (IExceptionHandler, ProblemDetails), Custom Exceptions e Result Pattern, FluentValidation em handlers, estrutura de pastas e dependencias entre projetos. Usar quando: criar novo microservico; criar modulo/feature; implementar endpoints e fluxo CQRS; definir contratos (DTOs/requests/responses); definir ou revisar estrutura de camadas; organizar pastas e projetos; configurar referencias entre projetos."
---

# Padroes Arquiteturais e Estrutura de Projeto .NET C# e ASP.NET Core

## Indice

1. [Estrutura de Pastas](#estrutura-de-pastas)
2. [Dependencias entre Projetos](#dependencias-entre-projetos)
3. [Padroes de Arquitetura](#padroes-de-arquitetura)
4. [Repository Pattern](#repository-pattern)
5. [CQRS Nativo (Sem MediatR)](#cqrs-nativo-sem-mediatr)
6. [Tratamento de Erros](#tratamento-de-erros)
7. [Comandos para Criacao da Estrutura](#comandos-para-criacao-da-estrutura)

---

# PARTE 1 — ESTRUTURA DE PROJETO

## Estrutura de Pastas

### Visao Geral

Estrutura padrao para projetos .NET seguindo Clean Architecture, com camadas numeradas para facilitar navegacao e representar a hierarquia de dependencias.

```
ProjectName/
├── ProjectName.sln
├── 1-Services/
│   └── ProjectName.API/
│       └── ProjectName.API.csproj
├── 2-Application/
│   └── ProjectName.Application/
│       └── ProjectName.Application.csproj
├── 3-Domain/
│   └── ProjectName.Domain/
│       ├── ProjectName.Domain.csproj
│       ├── Entities/
│       ├── Services/
│       └── Interfaces/
├── 4-Infra/
│   └── ProjectName.Infra/
│       ├── ProjectName.Infra.csproj
│       └── Repositories/
└── 5-Tests/
    ├── ProjectName.UnitTests/
    │   └── ProjectName.UnitTests.csproj
    ├── ProjectName.IntegrationTests/
    │   └── ProjectName.IntegrationTests.csproj
    └── ProjectName.End2EndTests/
        └── ProjectName.End2EndTests.csproj
```

### Descricao das Camadas

#### 1. Services (Camada de Apresentacao)

- **Pasta:** `1-Services/`
- **Tipo:** ASP.NET Core Web API
- **Responsabilidade:**
  - Expor endpoints HTTP
  - Gerenciar controllers
  - Configuracao de middleware
  - Autenticacao e autorizacao
  - Documentacao da API (Swagger)

#### 2. Application (Camada de Aplicacao)

- **Pasta:** `2-Application/`
- **Tipo:** Class Library
- **Responsabilidade:**
  - Casos de uso (Use Cases)
  - Servicos de aplicacao
  - DTOs (Data Transfer Objects)
  - Mapeamentos
  - Validacoes de entrada
  - Orquestracao da logica de negocio

#### 3. Domain (Camada de Dominio)

- **Pasta:** `3-Domain/`
- **Tipo:** Class Library
- **Responsabilidade:**
  - Entidades de dominio
  - Regras de negocio
  - Interfaces de repositorios
  - Servicos de dominio
  - Value Objects
  - Eventos de dominio
- **Subpastas:**
  - `Entities/` — Classes de entidades do dominio
  - `Services/` — Servicos que encapsulam logicas de dominio
  - `Interfaces/` — Contratos e interfaces do dominio

#### 4. Infra (Camada de Infraestrutura)

- **Pasta:** `4-Infra/`
- **Tipo:** Class Library
- **Responsabilidade:**
  - Implementacao de repositorios
  - Acesso a dados (Entity Framework)
  - Configuracoes de banco de dados
  - Integracoes externas
  - Servicos de infraestrutura
- **Subpastas:**
  - `Repositories/` — Implementacoes concretas dos repositorios

#### 5. Tests (Camada de Testes)

- **Pasta:** `5-Tests/`
- **Tipo:** xUnit Test Projects
- **Projetos:**
  - `UnitTests` — Testes unitarios isolados, mocks e stubs
  - `IntegrationTests` — Testes de integracao com banco de dados e servicos
  - `End2EndTests` — Testes de ponta a ponta simulando usuario real

---

## Dependencias entre Projetos

### Fluxo de Dependencias

```
┌─────────────────┐
│   1-Services    │
│      (API)      │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│  2-Application  │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐    ┌─────────────────┐
│    3-Domain     │◄───│    4-Infra      │
└─────────────────┘    └─────────────────┘
          ▲                      ▲
          │                      │
          └──────────────────────┘
                     │
          ┌─────────────────┐
          │    5-Tests      │
          └─────────────────┘
```

### Referencias de Projeto

- **API** → Application
- **Application** → Domain
- **Infra** → Domain
- **UnitTests** → Application + Domain
- **IntegrationTests** → Application + Infra
- **End2EndTests** → API

### Principios Arquiteturais

1. **Inversao de Dependencia**: As camadas externas dependem das internas. O Domain nao possui dependencias externas. Interfaces no Domain sao implementadas na Infra.
2. **Separacao de Responsabilidades**: Cada camada tem uma responsabilidade bem definida. Baixo acoplamento entre as camadas. Alta coesao dentro de cada camada.
3. **Testabilidade**: Estrutura permite testes isolados. Dependencias podem ser mockadas. Testes cobrem todas as camadas.

### Convencoes de Nomenclatura (Camadas)

- **API**: `ProjectName.API`
- **Application**: `ProjectName.Application`
- **Domain**: `ProjectName.Domain`
- **Infra**: `ProjectName.Infra`
- **UnitTests**: `ProjectName.UnitTests`
- **IntegrationTests**: `ProjectName.IntegrationTests`
- **End2EndTests**: `ProjectName.End2EndTests`

---

# PARTE 2 — PADROES ARQUITETURAIS

## Padroes de Arquitetura

> **Por que seguir padroes arquiteturais?**
> - **Reduz complexidade**: Separacao de responsabilidades torna sistema mais compreensivel
> - **Facilita testes**: Camadas bem definidas permitem mocking e isolamento efetivos
> - **Acelera onboarding**: Desenvolvedores familiarizados com padroes se adaptam mais rapido
> - **Reduz acoplamento**: Mudancas em uma camada nao afetam outras
> - **Facilita evolucao**: Arquitetura limpa permite crescimento sustentavel do sistema
> - **Melhora manutenibilidade**: Bugs e mudancas ficam localizados

### Clean Architecture

```csharp
// Domain Layer
public class Order
{
    public int Id { get; private set; }
    public string CustomerEmail { get; private set; }
    public List<OrderItem> Items { get; private set; } = new();
    public OrderStatus Status { get; private set; }
    
    public void AddItem(int productId, int quantity, decimal price)
    {
        if (Status != OrderStatus.Draft)
            throw new InvalidOperationException("Cannot modify a confirmed order");
            
        Items.Add(new OrderItem(productId, quantity, price));
    }
}

// Application Layer
public class CreateOrderHandler : ICommandHandler<CreateOrderCommand, OrderResponse>
{
    private readonly IOrderRepository _repository;
    private readonly IUnitOfWork _unitOfWork;

    public async Task<OrderResponse> HandleAsync(CreateOrderCommand request, CancellationToken cancellationToken)
    {
        var order = new Order(request.CustomerEmail);
        
        foreach (var item in request.Items)
        {
            order.AddItem(item.ProductId, item.Quantity, item.Price);
        }
        
        await _repository.AddAsync(order, cancellationToken);
        await _unitOfWork.SaveChangesAsync(cancellationToken);
        
        return new OrderResponse(order.Id, order.CustomerEmail);
    }
}
```

---

## Repository Pattern

```csharp
public interface IRepository<T> where T : class
{
    Task<T?> GetByIdAsync(int id, CancellationToken cancellationToken);
    Task<IEnumerable<T>> GetAllAsync(CancellationToken cancellationToken);
    Task<T> AddAsync(T entity, CancellationToken cancellationToken);
    void Update(T entity);
    void Delete(T entity);
}

public class Repository<T> : IRepository<T> where T : class
{
    protected readonly AppDbContext _context;
    protected readonly DbSet<T> _dbSet;

    public Repository(AppDbContext context)
    {
        _context = context ?? throw new ArgumentNullException(nameof(context));
        _dbSet = context.Set<T>();
    }

    public virtual async Task<T?> GetByIdAsync(int id, CancellationToken cancellationToken)
    {
        return await _dbSet.FindAsync(new object[] { id }, cancellationToken);
    }

    public virtual async Task<IEnumerable<T>> GetAllAsync(CancellationToken cancellationToken)
    {
        return await _dbSet.ToListAsync(cancellationToken);
    }

    public virtual async Task<T> AddAsync(T entity, CancellationToken cancellationToken)
    {
        await _dbSet.AddAsync(entity, cancellationToken);
        return entity;
    }

    public virtual void Update(T entity)
    {
        _dbSet.Update(entity);
    }

    public virtual void Delete(T entity)
    {
        _dbSet.Remove(entity);
    }
}

// Specific repository implementation
public class UserRepository : Repository<User>, IUserRepository
{
    public UserRepository(AppDbContext context) 
        : base(context) { }

    public async Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken)
    {
        return await _dbSet
            .AsNoTracking()
            .FirstOrDefaultAsync(u => u.Email == email, cancellationToken);
    }

    public async Task<IEnumerable<User>> GetActiveUsersAsync(CancellationToken cancellationToken)
    {
        return await _dbSet
            .AsNoTracking()
            .Where(u => u.IsActive)
            .OrderByDescending(u => u.CreatedAt)
            .ToListAsync(cancellationToken);
    }
}
```

---

## CQRS Nativo (Sem MediatR)

### Interfaces Base para CQRS

```csharp
// Custom CQRS interfaces
public interface ICommand<TResponse>
{
}

public interface IQuery<TResponse>
{
}

public interface ICommandHandler<TCommand, TResponse> 
    where TCommand : ICommand<TResponse>
{
    Task<TResponse> HandleAsync(TCommand command, CancellationToken cancellationToken);
}

public interface IQueryHandler<TQuery, TResponse> 
    where TQuery : IQuery<TResponse>
{
    Task<TResponse> HandleAsync(TQuery query, CancellationToken cancellationToken);
}

// Native dispatcher
public interface IDispatcher
{
    Task<TResponse> SendAsync<TResponse>(ICommand<TResponse> command, CancellationToken cancellationToken);
    Task<TResponse> SendAsync<TResponse>(IQuery<TResponse> query, CancellationToken cancellationToken);
}
```

### Implementacao do Dispatcher

```csharp
public class Dispatcher : IDispatcher
{
    private readonly IServiceProvider _serviceProvider;
    private readonly ILogger<Dispatcher> _logger;

    public Dispatcher(IServiceProvider serviceProvider, ILogger<Dispatcher> logger)
    {
        _serviceProvider = serviceProvider;
        _logger = logger;
    }

    public async Task<TResponse> SendAsync<TResponse>(ICommand<TResponse> command, CancellationToken cancellationToken)
    {
        var commandType = command.GetType();
        var handlerType = typeof(ICommandHandler<,>).MakeGenericType(commandType, typeof(TResponse));
        
        using var scope = _logger.BeginScope(new Dictionary<string, object>
        {
            ["command.type"] = commandType.Name
        });

        _logger.LogDebug("Executing command {CommandType}", commandType.Name);
        
        var handler = _serviceProvider.GetRequiredService(handlerType);
        var method = handlerType.GetMethod("HandleAsync");
        
        var task = (Task<TResponse>)method!.Invoke(handler, new object[] { command, cancellationToken })!;
        return await task;
    }

    public async Task<TResponse> SendAsync<TResponse>(IQuery<TResponse> query, CancellationToken cancellationToken)
    {
        var queryType = query.GetType();
        var handlerType = typeof(IQueryHandler<,>).MakeGenericType(queryType, typeof(TResponse));
        
        using var scope = _logger.BeginScope(new Dictionary<string, object>
        {
            ["query.type"] = queryType.Name
        });

        _logger.LogDebug("Executing query {QueryType}", queryType.Name);
        
        var handler = _serviceProvider.GetRequiredService(handlerType);
        var method = handlerType.GetMethod("HandleAsync");
        
        var task = (Task<TResponse>)method!.Invoke(handler, new object[] { query, cancellationToken })!;
        return await task;
    }
}
```

### Commands e Queries

```csharp
// Command
public record CreateUserCommand(string Name, string Email) : ICommand<CreateUserResponse>;

public record CreateUserResponse(int Id, string Name, string Email);

// Command Handler
public class CreateUserHandler : ICommandHandler<CreateUserCommand, CreateUserResponse>
{
    private readonly IUserRepository _repository;
    private readonly IValidator<CreateUserCommand> _validator;
    private readonly ILogger<CreateUserHandler> _logger;

    public CreateUserHandler(
        IUserRepository repository, 
        IValidator<CreateUserCommand> validator,
        ILogger<CreateUserHandler> logger)
    {
        _repository = repository;
        _validator = validator;
        _logger = logger;
    }

    public async Task<CreateUserResponse> HandleAsync(CreateUserCommand command, CancellationToken cancellationToken)
    {
        await _validator.ValidateAndThrowAsync(command, cancellationToken);
        
        _logger.LogInformation("Creating user {Email}", command.Email);
        
        var user = new User(command.Name, command.Email);
        await _repository.AddAsync(user, cancellationToken);
        
        _logger.LogInformation("User {UserId} created successfully", user.Id);
        
        return new CreateUserResponse(user.Id, user.Name, user.Email);
    }
}

// Query
public record GetUserQuery(int Id) : IQuery<GetUserResponse>;

public record GetUserResponse(int Id, string Name, string Email);

// Query Handler
public class GetUserHandler : IQueryHandler<GetUserQuery, GetUserResponse>
{
    private readonly IUserRepository _repository;

    public GetUserHandler(IUserRepository repository)
    {
        _repository = repository;
    }

    public async Task<GetUserResponse> HandleAsync(GetUserQuery query, CancellationToken cancellationToken)
    {
        var user = await _repository.GetByIdAsync(query.Id, cancellationToken);
        return user == null 
            ? throw new UserNotFoundException($"User {query.Id} not found")
            : new GetUserResponse(user.Id, user.Name, user.Email);
    }
}
```

### Configuracao no DI

```csharp
// Program.cs
builder.Services.AddScoped<IDispatcher, Dispatcher>();

// Automatic handler registration
builder.Services.Scan(scan => scan
    .FromAssemblyOf<Program>()
    .AddClasses(classes => classes.AssignableTo(typeof(ICommandHandler<,>)))
    .AsImplementedInterfaces()
    .WithScopedLifetime());

builder.Services.Scan(scan => scan
    .FromAssemblyOf<Program>()
    .AddClasses(classes => classes.AssignableTo(typeof(IQueryHandler<,>)))
    .AsImplementedInterfaces()
    .WithScopedLifetime());

// Or specific manual registration
builder.Services.AddScoped<ICommandHandler<CreateUserCommand, CreateUserResponse>, CreateUserHandler>();
builder.Services.AddScoped<IQueryHandler<GetUserQuery, GetUserResponse>, GetUserHandler>();
```

### Uso em Controllers

```csharp
[ApiController]
[Route("api/users")]
public class UsersController : ControllerBase
{
    private readonly IDispatcher _dispatcher;
    private readonly ILogger<UsersController> _logger;

    public UsersController(IDispatcher dispatcher, ILogger<UsersController> logger)
    {
        _dispatcher = dispatcher;
        _logger = logger;
    }

    [HttpGet("{id}")]
    [ProducesResponseType(typeof(GetUserResponse), 200)]
    [ProducesResponseType(404)]
    public async Task<IActionResult> GetUserAsync(int id, CancellationToken cancellationToken)
    {
        try
        {
            var query = new GetUserQuery(id);
            var result = await _dispatcher.SendAsync(query, cancellationToken);
            return Ok(result);
        }
        catch (UserNotFoundException)
        {
            return NotFound($"User {id} not found");
        }
    }

    [HttpPost]
    [ProducesResponseType(typeof(CreateUserResponse), 201)]
    [ProducesResponseType(400)]
    public async Task<IActionResult> CreateUserAsync(
        [FromBody] CreateUserCommand command, 
        CancellationToken cancellationToken)
    {
        var result = await _dispatcher.SendAsync(command, cancellationToken);
        
        return CreatedAtAction(
            nameof(GetUserAsync), 
            new { id = result.Id }, 
            result);
    }
}
```

---

## Tratamento de Erros

> **Por que tratamento de erros e fundamental?**
> - **Resiliencia**: Aplicacoes robustas se recuperam graciosamente de falhas
> - **Debugging eficiente**: Stack traces e logs estruturados aceleram identificacao de problemas
> - **UX superior**: Usuarios recebem mensagens claras ao inves de crashes
> - **Monitoramento**: Erros estruturados permitem alertas e metricas uteis
> - **Compliance**: Muitas regulamentacoes exigem logging e auditoria de erros
> - **Previne vazamentos**: Tratamento adequado evita expor informacoes sensiveis

### Global Exception Handler (ASP.NET Core 8+)

```csharp
public class GlobalExceptionHandler : IExceptionHandler
{
    private readonly ILogger<GlobalExceptionHandler> _logger;

    public GlobalExceptionHandler(ILogger<GlobalExceptionHandler> logger)
    {
        _logger = logger;
    }

    public async ValueTask<bool> TryHandleAsync(
        HttpContext httpContext,
        Exception exception,
        CancellationToken cancellationToken)
    {
        var (statusCode, title, detail) = exception switch
        {
            ValidationException ex => (400, "Validation Error", ex.Message),
            UserNotFoundException ex => (404, "Resource Not Found", ex.Message),
            UnauthorizedAccessException => (401, "Unauthorized", "Authentication required"),
            ArgumentNullException ex => (400, "Invalid Request", $"Required parameter {ex.ParamName} is missing"),
            _ => (500, "Internal Server Error", "An unexpected error occurred")
        };

        _logger.LogError(exception, "Exception occurred: {Message}", exception.Message);

        var problemDetails = new ProblemDetails
        {
            Status = statusCode,
            Title = title,
            Detail = detail,
            Instance = httpContext.Request.Path
        };

        httpContext.Response.StatusCode = statusCode;
        await httpContext.Response.WriteAsJsonAsync(problemDetails, cancellationToken);

        return true;
    }
}

// Registration in Program.cs
builder.Services.AddExceptionHandler<GlobalExceptionHandler>();
app.UseExceptionHandler();
```

### Custom Exceptions

```csharp
public abstract class DomainException : Exception
{
    protected DomainException(string message) : base(message) { }
    protected DomainException(string message, Exception innerException) : base(message, innerException) { }
}

public class UserNotFoundException : DomainException
{
    public UserNotFoundException(int userId) 
        : base($"User with ID {userId} was not found") { }
}

public class ValidationException : DomainException
{
    public IDictionary<string, string[]> Errors { get; }

    public ValidationException(IDictionary<string, string[]> errors)
        : base("One or more validation errors occurred")
    {
        Errors = errors;
    }
}

public class BusinessException : DomainException
{
    public string RuleCode { get; }

    public BusinessException(string ruleCode, string message) 
        : base(message)
    {
        RuleCode = ruleCode;
    }
}
```

### Result Pattern

```csharp
public class Result<T>
{
    public bool IsSuccess { get; private set; }
    public T? Value { get; private set; }
    public string? Error { get; private set; }
    public Exception? Exception { get; private set; }

    private Result(T value)
    {
        IsSuccess = true;
        Value = value;
    }

    private Result(string error, Exception? exception = null)
    {
        IsSuccess = false;
        Error = error;
        Exception = exception;
    }

    public static Result<T> Success(T value) => new(value);
    public static Result<T> Failure(string error) => new(error);
    public static Result<T> Failure(string error, Exception exception) => new(error, exception);

    public TResult Match<TResult>(Func<T, TResult> onSuccess, Func<string, TResult> onFailure)
    {
        return IsSuccess ? onSuccess(Value!) : onFailure(Error!);
    }

    public async Task<TResult> MatchAsync<TResult>(
        Func<T, Task<TResult>> onSuccess, 
        Func<string, Task<TResult>> onFailure)
    {
        return IsSuccess ? await onSuccess(Value!) : await onFailure(Error!);
    }
}

// Usage
public async Task<Result<User>> GetUserAsync(int id, CancellationToken cancellationToken)
{
    try
    {
        var user = await _repository.GetByIdAsync(id, cancellationToken);
        return user == null 
            ? Result<User>.Failure($"User {id} not found")
            : Result<User>.Success(user);
    }
    catch (Exception ex)
    {
        _logger.LogError(ex, "Error retrieving user {UserId}", id);
        return Result<User>.Failure("An error occurred while retrieving the user", ex);
    }
}

// Usage in Controllers
[HttpGet("{id}")]
public async Task<IActionResult> GetUser(int id, CancellationToken cancellationToken)
{
    var result = await _userService.GetUserAsync(id, cancellationToken);
    
    return result.Match<IActionResult>(
        onSuccess: user => Ok(user),
        onFailure: error => NotFound(error)
    );
}
```

### Middleware para Logging de Erros

```csharp
public class ErrorLoggingMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<ErrorLoggingMiddleware> _logger;

    public ErrorLoggingMiddleware(RequestDelegate next, ILogger<ErrorLoggingMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await _next(context);
        }
        catch (Exception ex)
        {
            await LogErrorAsync(context, ex);
            throw; // Re-throw to allow other middlewares to process
        }
    }

    private async Task LogErrorAsync(HttpContext context, Exception exception)
    {
        var correlationId = context.TraceIdentifier;
        var userId = context.User?.Identity?.Name ?? "Anonymous";
        var endpoint = $"{context.Request.Method} {context.Request.Path}";

        using var scope = _logger.BeginScope(new Dictionary<string, object>
        {
            ["correlation.id"] = correlationId,
            ["user.id"] = userId,
            ["http.request.method"] = context.Request.Method,
            ["url.path"] = context.Request.Path,
            ["error.type"] = exception.GetType().Name
        });

        _logger.LogError(exception, 
            "Unhandled exception occurred for {Endpoint} by user {UserId}. Correlation ID: {CorrelationId}",
            endpoint, userId, correlationId);

        context.Items["Exception"] = exception;
        context.Items["CorrelationId"] = correlationId;
    }
}

// Pipeline registration
app.UseMiddleware<ErrorLoggingMiddleware>();
```

### Validacao com FluentValidation

```csharp
public class CreateUserCommandValidator : AbstractValidator<CreateUserCommand>
{
    public CreateUserCommandValidator()
    {
        RuleFor(x => x.Name)
            .NotEmpty()
            .WithMessage("Name is required")
            .MaximumLength(100)
            .WithMessage("Name must have at most 100 characters");

        RuleFor(x => x.Email)
            .NotEmpty()
            .WithMessage("Email is required")
            .EmailAddress()
            .WithMessage("Email must have a valid format")
            .MaximumLength(255)
            .WithMessage("Email must have at most 255 characters");
    }
}

// Manual validation in handlers
public class CreateUserHandler : ICommandHandler<CreateUserCommand, CreateUserResponse>
{
    private readonly IValidator<CreateUserCommand> _validator;
    
    public async Task<CreateUserResponse> HandleAsync(CreateUserCommand command, CancellationToken cancellationToken)
    {
        // Manual validation
        var validationResult = await _validator.ValidateAsync(command, cancellationToken);
        if (!validationResult.IsValid)
        {
            var errors = validationResult.Errors
                .GroupBy(x => x.PropertyName)
                .ToDictionary(g => g.Key, g => g.Select(x => x.ErrorMessage).ToArray());
            
            throw new ValidationException(errors);
        }
        
        // Handler logic...
    }
}

// DI registration
builder.Services.AddValidatorsFromAssembly(typeof(Program).Assembly);
```

---

## Comandos para Criacao da Estrutura

### 1. Criar Solution

```bash
dotnet new sln -n ProjectName
```

### 2. Criar Projetos

```bash
# API
mkdir 1-Services && cd 1-Services
dotnet new webapi -n ProjectName.API
cd ..

# Application
mkdir 2-Application && cd 2-Application
dotnet new classlib -n ProjectName.Application
cd ..

# Domain
mkdir 3-Domain && cd 3-Domain
dotnet new classlib -n ProjectName.Domain
cd ProjectName.Domain && mkdir Entities Services Interfaces
cd ../..

# Infra
mkdir 4-Infra && cd 4-Infra
dotnet new classlib -n ProjectName.Infra
cd ProjectName.Infra && mkdir Repositories
cd ../..

# Tests
mkdir 5-Tests && cd 5-Tests
dotnet new xunit -n ProjectName.UnitTests
dotnet new xunit -n ProjectName.IntegrationTests
dotnet new xunit -n ProjectName.End2EndTests
cd ..
```

### 3. Adicionar Projetos a Solution

```bash
dotnet sln add 1-Services/ProjectName.API/ProjectName.API.csproj
dotnet sln add 2-Application/ProjectName.Application/ProjectName.Application.csproj
dotnet sln add 3-Domain/ProjectName.Domain/ProjectName.Domain.csproj
dotnet sln add 4-Infra/ProjectName.Infra/ProjectName.Infra.csproj
dotnet sln add 5-Tests/ProjectName.UnitTests/ProjectName.UnitTests.csproj
dotnet sln add 5-Tests/ProjectName.IntegrationTests/ProjectName.IntegrationTests.csproj
dotnet sln add 5-Tests/ProjectName.End2EndTests/ProjectName.End2EndTests.csproj
```

### 4. Configurar Referencias

```bash
# API → Application
dotnet add 1-Services/ProjectName.API reference 2-Application/ProjectName.Application

# Application → Domain
dotnet add 2-Application/ProjectName.Application reference 3-Domain/ProjectName.Domain

# Infra → Domain
dotnet add 4-Infra/ProjectName.Infra reference 3-Domain/ProjectName.Domain

# UnitTests → Application + Domain
dotnet add 5-Tests/ProjectName.UnitTests reference 2-Application/ProjectName.Application
dotnet add 5-Tests/ProjectName.UnitTests reference 3-Domain/ProjectName.Domain

# IntegrationTests → Application + Infra
dotnet add 5-Tests/ProjectName.IntegrationTests reference 2-Application/ProjectName.Application
dotnet add 5-Tests/ProjectName.IntegrationTests reference 4-Infra/ProjectName.Infra

# End2EndTests → API
dotnet add 5-Tests/ProjectName.End2EndTests reference 1-Services/ProjectName.API
```

---

## Checklists

### Clean Architecture

- [ ] Domain layer isolada sem dependencias externas
- [ ] Application layer com handlers CQRS
- [ ] Infrastructure layer com implementacoes concretas
- [ ] Dependency Inversion respeitado
- [ ] Regras de negocio no dominio

### Repository Pattern

- [ ] `IRepository<T>` generico definido
- [ ] Implementacao base com Entity Framework Core
- [ ] Repositorios especificos quando necessario
- [ ] DbContext configurado com Unit of Work
- [ ] Queries otimizadas com `AsNoTracking` para leitura

### CQRS Nativo

- [ ] Interfaces `ICommand<T>` e `IQuery<T>` definidas
- [ ] `ICommandHandler<T,R>` e `IQueryHandler<T,R>` implementados
- [ ] Dispatcher nativo configurado no DI
- [ ] Handlers registrados automaticamente
- [ ] Logging estruturado nos handlers
- [ ] Validation pipeline implementado

### Tratamento de Erros

- [ ] Global exception handler configurado
- [ ] Custom exceptions por dominio
- [ ] Result pattern para operacoes criticas
- [ ] Logging estruturado de erros
- [ ] Validation pipeline automatico
- [ ] Problem Details padronizado

### Validacao

- [ ] FluentValidation configurado
- [ ] Validators por comando/query
- [ ] Pipeline behavior para validacao
- [ ] Mensagens de validacao em ingles
- [ ] Validation exceptions customizadas

### Estrutura de Projeto

- [ ] Camadas numeradas (1-Services a 5-Tests)
- [ ] Referencias de projeto corretas
- [ ] Namespaces seguindo convencao
- [ ] Domain sem dependencias externas
- [ ] Pastas internas organizadas

### Regras Críticas de Implementação

1. **Namespaces Limpos**: Jamais inclua os prefixos numéricos das pastas (ex: `1-`, `2-`) nos namespaces.
   - Correto: `namespace ProjectName.Application.UseCases`
   - Incorreto: `namespace ProjectName._2_Application.UseCases`

2. **Bibliotecas Obrigatórias**:
   - Para DI Scan: Instalar `Scrutor` (`dotnet add package Scrutor`).
   - Para Validação: Instalar `FluentValidation.DependencyInjectionExtensions`.
   - Para EF Core: Instalar `Microsoft.EntityFrameworkCore.Design`.

3. **Padrão UnitOfWork**:
   - A interface `IUnitOfWork` deve expor apenas `Task<int> SaveChangesAsync(CancellationToken ct)`.
   - A implementação deve injetar o `AppDbContext`.