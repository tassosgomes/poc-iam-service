---
name: dotnet-testing
description: "Estrategias de teste .NET C# / ASP.NET Core: testes unitarios com xUnit + AwesomeAssertions + Moq (padrao AAA), testes de integracao com WebApplicationFactory + Testcontainers (PostgreSQL padrao oficial), testes E2E com Playwright, Dev Containers para ambiente isolado, naming convention (MethodName_Condition_ExpectedBehavior), fixtures reutilizaveis, cobertura > 80% para logica de negocio. Usar quando: criar testes; revisar testes; garantir cobertura; configurar Testcontainers; setup de ambiente de teste."
---

# Estrategias de Teste .NET C# e ASP.NET Core

Documento normativo para estrategias de teste.
Pode bloquear geracao de codigo sem teste.

> **Politica de Banco de Dados em Testes**
> - **PostgreSQL e o padrao oficial** para Testcontainers e Dev Containers
> - **Oracle apenas para servicos oficialmente Oracle** (legado/aprovacao explicita)

---

## Indice
1. [Testes Unitarios](#testes-unitarios)
2. [Testes de Integracao](#testes-de-integracao)
3. [Testes End-to-End (E2E)](#testes-end-to-end-e2e)
4. [Dev Containers para Testes de Integracao](#dev-containers-para-testes-de-integracao)

---

## Testes Unitarios

> **Por que investir em testes unitarios?**
> - **ROI comprovado**: Cada hora investida em testes economiza 3-10 horas de debugging
> - **Deteccao precoce**: Bugs encontrados em desenvolvimento custam 100x menos que em producao
> - **Refatoracao segura**: Testes permitem mudancas com confianca
> - **Documentacao viva**: Testes descrevem o comportamento esperado melhor que comentarios
> - **Melhora design**: Codigo testavel e naturalmente melhor estruturado

### Framework Recomendado: xUnit + AwesomeAssertions
```xml
<PackageReference Include="xunit" Version="2.6.6" />
<PackageReference Include="xunit.runner.visualstudio" Version="2.5.6" />
<PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.8.0" />
<PackageReference Include="Moq" Version="4.20.70" />
<PackageReference Include="AwesomeAssertions" Version="6.15.1" />
<PackageReference Include="AutoFixture" Version="4.18.1" />
```

> **Por que AwesomeAssertions ao inves de FluentAssertions?**
> - **Licenca Apache 2.0**: Mantem licenca open-source sempre gratuita
> - **Fork ativo**: Continuacao comunitaria do FluentAssertions com melhorias
> - **Compatibilidade**: API identica ao FluentAssertions, migracao transparente

### Estrutura de Teste - AAA Pattern
```csharp
using AwesomeAssertions;

public class TestesServicoPedido
{
    private readonly Mock<IRepositorioPedido> _repositorioMock;
    private readonly Mock<ILogger<ServicoPedido>> _loggerMock;
    private readonly ServicoPedido _sut; // System Under Test

    public TestesServicoPedido()
    {
        _repositorioMock = new Mock<IRepositorioPedido>();
        _loggerMock = new Mock<ILogger<ServicoPedido>>();
        _sut = new ServicoPedido(_repositorioMock.Object, _loggerMock.Object);
    }

    [Fact]
    public async Task CriarPedidoAsync_ComSolicitacaoValida_DeveRetornarPedidoCriado()
    {
        // Arrange
        var cancellationToken = CancellationToken.None;
        var solicitacao = new SolicitacaoCriarPedido
        {
            IdCliente = 1,
            Itens = new[] { new ItemPedido { IdProduto = 1, Quantidade = 2 } }
        };
        
        var pedidoEsperado = new Pedido { Id = 123, IdCliente = 1 };
        _repositorioMock
            .Setup(r => r.CriarAsync(It.IsAny<Pedido>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(pedidoEsperado);

        // Act
        var resultado = await _sut.CriarPedidoAsync(solicitacao, cancellationToken);

        // Assert
        resultado.Should().NotBeNull();
        resultado.Id.Should().Be(123);
        resultado.IdCliente.Should().Be(1);
        
        _repositorioMock.Verify(
            r => r.CriarAsync(It.IsAny<Pedido>(), It.IsAny<CancellationToken>()),
            Times.Once);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public async Task CriarPedidoAsync_ComNomeClienteInvalido_DeveLancarArgumentException(string nomeCliente)
    {
        // Arrange
        var cancellationToken = CancellationToken.None;
        var solicitacao = new SolicitacaoCriarPedido { NomeCliente = nomeCliente };

        // Act & Assert
        var acao = () => _sut.CriarPedidoAsync(solicitacao, cancellationToken);
        await acao.Should().ThrowAsync<ArgumentException>()
            .WithMessage("Nome do cliente nao pode ser nulo ou vazio");
    }

    [Fact]
    public async Task CriarPedidoAsync_ComCancelamento_DeveLancarOperationCanceledException()
    {
        // Arrange
        using var cts = new CancellationTokenSource();
        var solicitacao = new SolicitacaoCriarPedido { IdCliente = 1 };
        cts.Cancel();

        // Act & Assert
        var acao = () => _sut.CriarPedidoAsync(solicitacao, cts.Token);
        await acao.Should().ThrowAsync<OperationCanceledException>();
    }
}
```

### Naming Convention para Testes
```csharp
// Padrao: NomeMetodo_CondicaoTeste_ComportamentoEsperado
[Fact]
public void CalcularDesconto_ComClientePremium_DeveAplicarDesconto20Porcento()

[Fact]
public void ValidarEmail_ComFormatoInvalido_DeveRetornarFalse()

[Fact]
public async Task ObterUsuarioAsync_QuandoUsuarioNaoEncontrado_DeveLancarUsuarioNaoEncontradoException()
```

### Testes Parametrizados
```csharp
[Theory]
[InlineData("admin@teste.com", true)]
[InlineData("usuario@empresa.org", true)]
[InlineData("email-invalido", false)]
[InlineData("", false)]
[InlineData(null, false)]
public void EhEmailValido_ComVariasEntradas_DeveRetornarResultadoEsperado(string email, bool esperado)
{
    // Arrange & Act
    var resultado = ValidadorEmail.EhValido(email);

    // Assert
    resultado.Should().Be(esperado);
}

[Theory]
[MemberData(nameof(ObterDadosTestePedido))]
public async Task CalcularTotal_ComDiferentesPedidos_DeveRetornarTotalCorreto(Pedido pedido, decimal totalEsperado, CancellationToken cancellationToken)
{
    // Arrange & Act
    var total = await _calculadora.CalcularTotalAsync(pedido, cancellationToken);

    // Assert
    total.Should().Be(totalEsperado);
}

public static IEnumerable<object[]> ObterDadosTestePedido()
{
    yield return new object[] { new Pedido { Itens = [] }, 0m, CancellationToken.None };
    yield return new object[] { new Pedido { Itens = [new() { Preco = 10m, Quantidade = 2 }] }, 20m, CancellationToken.None };
}
```

---

## Testes de Integracao

> **Por que testes de integracao sao essenciais?**
> - **Validam integracoes reais**: Bugs frequentemente ocorrem nas integracoes entre componentes
> - **Detectam problemas de configuracao**: Banco de dados, APIs externas, configuracoes
> - **Confidence em deploys**: Reduzem drasticamente o risco de falhas em producao
> - **Complementam testes unitarios**: Juntos fornecem cobertura abrangente (piramide de testes)

### Framework e Setup
```xml
<PackageReference Include="Microsoft.AspNetCore.Mvc.Testing" Version="8.0.0" />
<PackageReference Include="Testcontainers" Version="3.7.0" />
<PackageReference Include="Testcontainers.PostgreSql" Version="3.7.0" />
<PackageReference Include="Npgsql" Version="8.0.0" />
```

### WebApplicationFactory Customizada — PostgreSQL (Padrao)
```csharp
public class CustomWebApplicationFactory : WebApplicationFactory<Program>, IAsyncLifetime
{
    private readonly PostgreSqlContainer _dbContainer = new PostgreSqlBuilder()
        .WithImage("postgres:16-alpine")
        .WithDatabase("testdb")
        .WithUsername("testuser")
        .WithPassword("testpass")
        .Build();

    public async Task InitializeAsync()
    {
        await _dbContainer.StartAsync();
        
        await using var connection = new NpgsqlConnection(_dbContainer.GetConnectionString());
        await connection.OpenAsync();
        
        var createTablesSql = @"
            CREATE TABLE Users (
                Id SERIAL PRIMARY KEY,
                Name VARCHAR(100) NOT NULL,
                Email VARCHAR(255) NOT NULL UNIQUE,
                IsActive BOOLEAN DEFAULT TRUE NOT NULL,
                CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
            );
            
            CREATE TABLE Products (
                Id SERIAL PRIMARY KEY,
                Name VARCHAR(200) NOT NULL,
                Price DECIMAL(18,2) NOT NULL,
                IsActive BOOLEAN DEFAULT TRUE NOT NULL
            );";
        
        await connection.ExecuteAsync(createTablesSql);
        await SeedTestDataAsync(connection);
    }

    public new async Task DisposeAsync()
    {
        await _dbContainer.StopAsync();
        await base.DisposeAsync();
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.ConfigureTestServices(services =>
        {
            var descriptor = services.SingleOrDefault(
                d => d.ServiceType == typeof(DbContextOptions<AppDbContext>));
            if (descriptor != null)
                services.Remove(descriptor);

            services.AddDbContext<AppDbContext>(options =>
                options.UseNpgsql(_dbContainer.GetConnectionString()));
        });
    }

    private static async Task SeedTestDataAsync(NpgsqlConnection connection)
    {
        var users = new[]
        {
            new { Name = "Test User", Email = "test@example.com" },
            new { Name = "Admin User", Email = "admin@example.com" }
        };

        var products = new[]
        {
            new { Name = "Product 1", Price = 10.99m },
            new { Name = "Product 2", Price = 25.50m }
        };

        await connection.ExecuteAsync(
            "INSERT INTO Users (Name, Email) VALUES (@Name, @Email)", users);
        
        await connection.ExecuteAsync(
            "INSERT INTO Products (Name, Price) VALUES (@Name, @Price)", products);
    }
}
```

### Testes de API
```csharp
[Collection("IntegrationTests")]
public class UsersControllerTests : IAsyncLifetime
{
    private readonly CustomWebApplicationFactory _factory;
    private readonly HttpClient _client;

    public UsersControllerTests(CustomWebApplicationFactory factory)
    {
        _factory = factory;
        _client = factory.CreateClient();
    }

    [Fact]
    public async Task GetUsers_ShouldReturnAllUsers()
    {
        // Act
        var response = await _client.GetAsync("/api/users");

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        
        var users = await response.Content.ReadFromJsonAsync<List<User>>();
        users.Should().HaveCount(2);
        users.Should().Contain(u => u.Name == "Test User");
    }

    [Fact]
    public async Task CreateUser_WithValidData_ShouldReturnCreatedUser()
    {
        // Arrange
        var newUser = new CreateUserRequest
        {
            Name = "New User",
            Email = "newuser@example.com"
        };

        // Act
        var response = await _client.PostAsJsonAsync("/api/users", newUser);

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.Created);
        
        var createdUser = await response.Content.ReadFromJsonAsync<User>();
        createdUser.Should().NotBeNull();
        createdUser!.Name.Should().Be("New User");
        createdUser.Email.Should().Be("newuser@example.com");
    }

    public Task InitializeAsync() => Task.CompletedTask;
    public Task DisposeAsync() => Task.CompletedTask;
}

[CollectionDefinition("IntegrationTests")]
public class IntegrationTestCollection : ICollectionFixture<CustomWebApplicationFactory>
{
}
```

---

## Testes End-to-End (E2E)

> **Por que testes E2E sao o topo da piramide?**
> - **Validacao final**: Testam a aplicacao exatamente como o usuario a utilizara
> - **Detectam problemas de UX**: Problemas de usabilidade que outros testes nao capturam
> - **Confidence para releases**: Ultimas verificacoes antes de entregar valor ao usuario
> - **Complemento, nao substituto**: Poucos testes E2E focados em happy paths criticos

### Framework Recomendado: Playwright
```xml
<PackageReference Include="Microsoft.Playwright" Version="1.41.0" />
<PackageReference Include="Microsoft.Playwright.NUnit" Version="1.41.0" />
```

### Page Object Model
```csharp
public class LoginPage
{
    private readonly IPage _page;

    public LoginPage(IPage page)
    {
        _page = page;
    }

    public async Task NavigateAsync()
    {
        await _page.GotoAsync("/login");
    }

    public async Task LoginAsync(string email, string password)
    {
        await _page.FillAsync("#email", email);
        await _page.FillAsync("#password", password);
        await _page.ClickAsync("#login-button");
    }

    public async Task<bool> IsErrorMessageVisibleAsync()
    {
        return await _page.Locator(".error-message").IsVisibleAsync();
    }

    public async Task<string> GetErrorMessageAsync()
    {
        return await _page.Locator(".error-message").TextContentAsync() ?? "";
    }
}

// Uso nos testes
[Test]
public async Task Login_WithInvalidCredentials_ShouldShowError()
{
    var loginPage = new LoginPage(Page);
    
    await loginPage.NavigateAsync();
    await loginPage.LoginAsync("invalid@test.com", "wrong");
    
    (await loginPage.IsErrorMessageVisibleAsync()).Should().BeTrue();
    (await loginPage.GetErrorMessageAsync()).Should().Contain("Invalid credentials");
}
```

---

## Dev Containers para Testes de Integracao

> **Por que usar Dev Containers?**
> - **Isolamento total**: Cada execucao de teste usa banco limpo e isolado
> - **Paralelizacao**: Multiplos containers para testes paralelos sem conflitos
> - **Reprodutibilidade**: Mesmo ambiente PostgreSQL em CI/CD e localmente
> - **Cleanup automatico**: Containers sao destruidos apos testes, sem residuos
> - **Versionamento de schema**: Scripts de teste versionados junto com codigo

### Estrutura de Arquivos
```
tests/
├── IntegrationTests/
│   ├── .devcontainer/
│   │   ├── devcontainer.json
│   │   ├── docker-compose.yml
│   │   └── test-data/
│   │       ├── 01-schema.sql
│   │       └── 02-test-data.sql
│   ├── Infrastructure/
│   │   ├── PostgresTestFixture.cs
│   │   └── TestDatabaseFactory.cs
│   └── Tests/
│       ├── UserRepositoryTests.cs
│       └── OrderServiceTests.cs
```

### docker-compose.yml (PostgreSQL Padrao)
```yaml
version: '3.8'

services:
  test-runner:
    build: 
      context: ../..
      dockerfile: tests/IntegrationTests/.devcontainer/Dockerfile
    volumes:
      - ../../:/workspace:cached
    working_dir: /workspace/tests/IntegrationTests
    command: sleep infinity
    depends_on:
      postgres-test-db:
        condition: service_healthy
    environment:
      - POSTGRES_TEST_CONNECTION=Host=postgres-test-db;Port=5432;Database=testdb;Username=testuser;Password=Test123;

  postgres-test-db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_USER=testuser
      - POSTGRES_PASSWORD=Test123
      - POSTGRES_DB=testdb
    volumes:
      - ./test-data:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U testuser -d testdb"]
      interval: 5s
      timeout: 5s
      retries: 10
    tmpfs:
      - /var/lib/postgresql/data
```

### Infraestrutura de Testes
```csharp
using Npgsql;
using Xunit;

namespace IntegrationTests.Infrastructure;

public class PostgresTestFixture : IAsyncLifetime
{
    private readonly string _connectionString;
    
    public PostgresTestFixture()
    {
        _connectionString = Environment.GetEnvironmentVariable("POSTGRES_TEST_CONNECTION") 
            ?? "Host=localhost;Port=5432;Database=testdb;Username=testuser;Password=Test123;";
    }

    public NpgsqlConnection CreateConnection()
    {
        var connection = new NpgsqlConnection(_connectionString);
        connection.Open();
        return connection;
    }

    public async Task InitializeAsync()
    {
        await using var connection = CreateConnection();
        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM Users";
        var count = await command.ExecuteScalarAsync();
        
        if (count == null)
            throw new InvalidOperationException("Test database is not properly initialized");
    }

    public async Task DisposeAsync()
    {
        await Task.CompletedTask;
    }

    public async Task CleanupDataAsync()
    {
        await using var connection = CreateConnection();
        await using var command = connection.CreateCommand();
        command.CommandText = @"
            DELETE FROM Orders WHERE Id > 3;
            DELETE FROM Users WHERE Id > 3;
        ";
        await command.ExecuteNonQueryAsync();
    }
}

[CollectionDefinition("PostgreSQL Integration Tests")]
public class PostgresTestCollection : ICollectionFixture<PostgresTestFixture>
{
}
```

### Exemplo de Teste de Integracao com Fixture
```csharp
using IntegrationTests.Infrastructure;
using AwesomeAssertions;
using Xunit;

namespace IntegrationTests.Tests;

[Collection("PostgreSQL Integration Tests")]
public class UserRepositoryTests
{
    private readonly PostgresTestFixture _fixture;
    private readonly UserRepository _repository;

    public UserRepositoryTests(PostgresTestFixture fixture)
    {
        _fixture = fixture;
        _repository = new UserRepository(_fixture.CreateConnection());
    }

    [Fact]
    public async Task GetAllAsync_ShouldReturnTestUsers()
    {
        // Arrange
        var cancellationToken = CancellationToken.None;

        // Act
        var users = await _repository.GetAllAsync(cancellationToken);

        // Assert
        users.Should().NotBeNull();
        users.Should().HaveCountGreaterOrEqualTo(3);
        users.Should().Contain(u => u.Email == "test1@example.com");
    }

    [Fact]
    public async Task CreateAsync_WithValidUser_ShouldPersistToDatabase()
    {
        // Arrange
        var cancellationToken = CancellationToken.None;
        var newUser = new User 
        { 
            Name = "Integration Test User", 
            Email = $"integration.{Guid.NewGuid()}@test.com" 
        };

        try
        {
            // Act
            var createdUser = await _repository.AddAsync(newUser, cancellationToken);

            // Assert
            createdUser.Should().NotBeNull();
            createdUser.Id.Should().BeGreaterThan(0);
            createdUser.Name.Should().Be("Integration Test User");

            var retrievedUser = await _repository.GetByIdAsync(createdUser.Id, cancellationToken);
            retrievedUser.Should().NotBeNull();
            retrievedUser!.Email.Should().Be(newUser.Email);
        }
        finally
        {
            await _fixture.CleanupDataAsync();
        }
    }
}
```

---

## Checklist de Estrategias de Teste

### Testes Unitarios
- [ ] xUnit + AwesomeAssertions configurado
- [ ] Moq para mocks e stubs
- [ ] AAA Pattern (Arrange-Act-Assert)
- [ ] Naming convention: MethodName_Condition_ExpectedBehavior
- [ ] Cobertura > 80% para logica de negocio
- [ ] Testes parametrizados para multiplos cenarios
- [ ] CancellationToken testado

### Testes de Integracao
- [ ] WebApplicationFactory configurada
- [ ] Testcontainers para PostgreSQL (padrao oficial)
- [ ] Database seeding automatico
- [ ] HTTP client configurado
- [ ] Cleanup entre testes
- [ ] Isolamento de dados

### Testes E2E
- [ ] Playwright configurado
- [ ] Page Object Model implementado
- [ ] Testes de fluxos criticos
- [ ] Screenshots em falhas
- [ ] Paralelizacao configurada

### Dev Containers
- [ ] PostgreSQL container configurado (padrao)
- [ ] Scripts SQL versionados
- [ ] Healthchecks implementados
- [ ] Dados deterministicos
- [ ] Cleanup automatico
- [ ] CI/CD integration