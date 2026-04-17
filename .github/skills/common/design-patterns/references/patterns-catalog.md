# Catálogo de Design Patterns — Referência Detalhada

> Leia apenas a seção do padrão que você precisa aplicar. Não carregue o arquivo inteiro
> em contexto desnecessariamente.

## Índice

1. [Adapter](#adapter)
2. [Decorator](#decorator)
3. [Observer](#observer)
4. [Proxy](#proxy)
5. [Simple Factory](#simple-factory)
6. [Singleton](#singleton)
7. [State](#state)
8. [Strategy](#strategy)
9. [Template Method](#template-method)

---

## Adapter

**Categoria:** Estrutural
**Intenção:** Converter a interface de uma classe em outra interface que o cliente espera,
permitindo que classes com interfaces incompatíveis trabalhem juntas.

### Quando usar

- Você precisa integrar uma biblioteca externa (SDK, API client, gerador de PDF, etc.)
  sem acoplar sua regra de negócio a ela.
- Você quer poder trocar a implementação concreta (ex.: trocar DomPDF por TCPDF) sem
  alterar o código de alto nível.
- Está fazendo uma migração gradual e precisa que código legado funcione com interfaces novas.

### Quando NÃO usar

- A interface da lib já é compatível com o que seu código espera — não crie um adapter
  desnecessário (wrapper inútil).
- Quando o problema real é que sua própria interface está mal definida — refatore a
  interface primeiro.

### Trade-offs

| Benefício | Custo |
|---|---|
| Desacoplamento total da lib externa | Uma camada extra de indireção |
| Facilita testes (mock do adapter) | Mais arquivos/classes no projeto |
| Troca de implementação sem impacto | Pode esconder complexidade da lib |

### Estrutura

```
«interface» Target
  + operation()

Adapter implements Target
  - adaptee: ExternalLib
  + operation()  →  adaptee.specificMethod()

Client → depende apenas de Target
```

### Esqueleto de implementação

```pseudo
interface ReportGenerator:
    method generate(data): bytes

class DomPdfAdapter implements ReportGenerator:
    private lib: DomPdfLib

    constructor(lib: DomPdfLib):
        this.lib = lib

    method generate(data):
        this.lib.loadHtml(data.toHtml())
        this.lib.render()
        return this.lib.output()

// Trocar implementação: criar TcPdfAdapter implements ReportGenerator
// O código cliente não muda.
```

---

## Decorator

**Categoria:** Estrutural
**Intenção:** Adicionar responsabilidades a objetos dinamicamente por composição recursiva,
sem alterar a classe original. Respeita o princípio Open/Closed.

### Quando usar

- Você precisa empilhar funcionalidades opcionais sobre um objeto base
  (ex.: compressão + criptografia + logging sobre um stream de dados).
- Subclassing geraria uma explosão combinatória de classes.
- Funcionalidades precisam ser adicionadas/removidas em runtime.

### Quando NÃO usar

- Quando há apenas uma variação fixa — uma simples herança ou composição direta é mais clara.
- Quando a ordem dos decoradores não importa E são sempre aplicados juntos — considere
  um único wrapper com tudo.

### Trade-offs

| Benefício | Custo |
|---|---|
| Composição flexível em runtime | Stack de decoradores pode dificultar debug |
| Cada decorator tem responsabilidade única | Muitas classes pequenas |
| Evita herança profunda | Ordem dos decoradores pode importar |

### Estrutura

```
«interface» Component
  + execute()

ConcreteComponent implements Component
  + execute()  →  lógica base

BaseDecorator implements Component
  - wrapped: Component
  + execute()  →  wrapped.execute()

DecoratorA extends BaseDecorator
  + execute()  →  extraLogic() + super.execute()

DecoratorB extends BaseDecorator
  + execute()  →  super.execute() + otherLogic()
```

### Esqueleto de implementação

```pseudo
interface ImageProcessor:
    method process(image): Image

class BasicProcessor implements ImageProcessor:
    method process(image):
        return image  // retorna sem alteração

class WatermarkDecorator implements ImageProcessor:
    private wrapped: ImageProcessor

    constructor(wrapped: ImageProcessor):
        this.wrapped = wrapped

    method process(image):
        result = this.wrapped.process(image)
        return addWatermark(result)

class ResizeDecorator implements ImageProcessor:
    private wrapped: ImageProcessor
    private dimensions: Size

    constructor(wrapped: ImageProcessor, dimensions: Size):
        this.wrapped = wrapped
        this.dimensions = dimensions

    method process(image):
        result = this.wrapped.process(image)
        return resize(result, this.dimensions)

// Uso: pipeline = ResizeDecorator(WatermarkDecorator(BasicProcessor()))
// Execução: basic → watermark → resize
```

---

## Observer

**Categoria:** Comportamental
**Intenção:** Definir uma dependência um-para-muitos entre objetos. Quando o objeto
observado muda de estado, todos os dependentes são notificados automaticamente.

### Quando usar

- Múltiplos componentes precisam reagir a uma mudança de estado sem acoplamento direto.
- Você está substituindo polling (ficar perguntando "mudou?") por notificação push.
- Arquiteturas orientadas a eventos, sistemas de mensageria, hooks de domínio.

### Quando NÃO usar

- Quando há apenas um observador fixo — uma chamada direta é mais simples.
- Quando a ordem de notificação importa de forma estrita — Observer não garante ordem
  (a menos que você a controle manualmente).

### Trade-offs

| Benefício | Custo |
|---|---|
| Desacoplamento publisher ↔ subscribers | Fluxo de execução pode ser difícil de rastrear |
| Fácil adicionar novos observadores | Memory leaks se observadores não forem desregistrados |
| Escalável — N observers sem alterar o subject | Cascata de notificações pode causar loops |

### Estrutura

```
«interface» Observer
  + update(data)

«interface» Subject
  + subscribe(observer)
  + unsubscribe(observer)
  + notify()

ConcreteSubject implements Subject
  - observers: List<Observer>
  - state
  + notify()  →  for each observer: observer.update(this.state)

ConcreteObserverA implements Observer
  + update(data)  →  reage à mudança

ConcreteObserverB implements Observer
  + update(data)  →  reage de outra forma
```

### Esqueleto de implementação

```pseudo
interface PriceObserver:
    method onPriceChange(asset: string, newPrice: decimal)

class Bitcoin:
    private observers: List<PriceObserver> = []
    private price: decimal

    method subscribe(observer: PriceObserver):
        this.observers.add(observer)

    method setPrice(newPrice: decimal):
        this.price = newPrice
        this.notifyAll()

    private method notifyAll():
        for observer in this.observers:
            observer.onPriceChange("BTC", this.price)

class LogObserver implements PriceObserver:
    method onPriceChange(asset, newPrice):
        log("Price of {asset} changed to {newPrice}")

class NotificationObserver implements PriceObserver:
    method onPriceChange(asset, newPrice):
        pushNotification("New price for {asset}: {newPrice}")
```

---

## Proxy

**Categoria:** Estrutural
**Intenção:** Fornecer um substituto ou placeholder para outro objeto, controlando o acesso
a ele. Permite executar lógica antes/depois da chamada ao objeto real.

### Variantes comuns

- **Cache Proxy** — Armazena resultados para evitar reprocessamento.
- **Protection Proxy** — Verifica permissões antes de delegar.
- **Virtual Proxy (Lazy Loading)** — Adia a criação do objeto pesado até o primeiro uso.
- **Logging Proxy** — Registra chamadas para auditoria.

### Quando usar

- Operações caras que podem ser cacheadas.
- Controle de acesso sem alterar a classe de serviço.
- Lazy initialization de objetos pesados.
- Auditoria/logging transparente.

### Quando NÃO usar

- Quando a lógica de interceptação já é fornecida pelo framework (middlewares, interceptors,
  AOP) — use o mecanismo nativo.
- Quando adiciona latência sem benefício real.

### Trade-offs

| Benefício | Custo |
|---|---|
| Transparente para o cliente | Indireção adicional |
| Responsabilidade de cross-cutting separada | Pode mascarar o custo real da operação |
| Pode melhorar performance (cache) | Invalidação de cache é um problema à parte |

### Esqueleto de implementação

```pseudo
interface ReportService:
    method generate(params): Report

class HeavyReportService implements ReportService:
    method generate(params):
        // operação custosa — 5 segundos
        return buildComplexReport(params)

class CachingReportProxy implements ReportService:
    private realService: ReportService
    private cache: Map<string, Report>

    constructor(realService: ReportService):
        this.realService = realService
        this.cache = {}

    method generate(params):
        key = hashOf(params)
        if key in this.cache:
            return this.cache[key]

        result = this.realService.generate(params)
        this.cache[key] = result
        return result
```

---

## Simple Factory

**Categoria:** Criacional (não é GoF oficial, mas é precursor de Abstract Factory e Factory Method)
**Intenção:** Centralizar a lógica de instanciação de objetos, removendo blocos de
if/else espalhados pelo código que apenas decidem qual classe criar.

### Quando usar

- Múltiplos pontos do sistema criam objetos com a mesma lógica condicional.
- Você quer um único lugar para adicionar novos tipos sem varrer o codebase.
- Os objetos criados compartilham uma interface comum.

### Quando NÃO usar

- Quando só existe um tipo concreto — a factory é overhead.
- Quando a criação envolve lógicas complexas e hierárquicas — considere Abstract Factory
  ou Factory Method.

### Trade-offs

| Benefício | Custo |
|---|---|
| Lógica de criação centralizada | A factory pode virar um "god method" se tiver muitos tipos |
| Fácil adicionar novos tipos | Viola Open/Closed (precisa alterar a factory para adicionar tipo) |
| Código cliente limpo | — |

### Esqueleto de implementação

```pseudo
interface Notification:
    method send(message: string)

class EmailNotification implements Notification:
    method send(message): // envia por e-mail

class SmsNotification implements Notification:
    method send(message): // envia por SMS

class SlackNotification implements Notification:
    method send(message): // envia por Slack

class NotificationFactory:
    static method create(channel: string): Notification
        switch channel:
            case "email":  return new EmailNotification()
            case "sms":    return new SmsNotification()
            case "slack":  return new SlackNotification()
            default:       throw UnknownChannelError(channel)
```

---

## Singleton

**Categoria:** Criacional
**Intenção:** Garantir que uma classe tenha apenas uma instância e fornecer um ponto de
acesso global a ela.

### ⚠️ AVISO: Anti-padrão em contextos modernos

O Singleton é amplamente considerado problemático porque:
- Dificulta testes unitários (chamadas estáticas são difíceis de mockar).
- Esconde dependências (a classe "sabe" onde buscar a instância).
- Não é thread-safe por padrão (race conditions em ambientes concorrentes).
- Viola o princípio de inversão de dependência.

**Recomendação moderna:** Use o container de injeção de dependência (DI) do seu framework
para registrar serviços como *singleton scope*. O resultado é o mesmo (uma instância),
mas com testabilidade e desacoplamento.

### Quando (ainda) faz sentido

- Código legado sem container de DI.
- Scripts utilitários simples sem framework.
- Recursos que genuinamente devem ser únicos e o ambiente não oferece DI
  (ex.: wrapper de configuração em CLI tools).

### Estrutura (para referência)

```pseudo
class DatabaseConnection:
    private static instance: DatabaseConnection = null
    private connection: Connection

    private constructor():
        this.connection = createConnection()

    static method getInstance(): DatabaseConnection
        if instance == null:
            instance = new DatabaseConnection()
        return instance

    method query(sql: string): Result
        return this.connection.execute(sql)

// Uso: DatabaseConnection.getInstance().query("SELECT ...")
// Preferível: registrar no container de DI como singleton scope.
```

---

## State

**Categoria:** Comportamental
**Intenção:** Permitir que um objeto altere seu comportamento quando seu estado interno
muda, delegando a lógica para classes de estado discretas. Elimina cadeias de if/else
baseadas em strings de status.

### Quando usar

- O objeto tem um ciclo de vida com estados bem definidos e transições restritas
  (ex.: pedido, pagamento, ticket de suporte).
- Comportamentos diferentes dependem do estado atual.
- Regras de negócio proíbem certas transições (ex.: não pode ir de "Criado" direto
  para "Concluído").

### Quando NÃO usar

- O objeto tem apenas 2 estados simples (ativo/inativo) — um boolean basta.
- As transições não têm regras — qualquer estado pode ir para qualquer outro.

### Trade-offs

| Benefício | Custo |
|---|---|
| Transições controladas e explícitas | Uma classe por estado |
| Elimina "primitive obsession" (strings como status) | Mais complexo que um enum simples |
| Cada estado encapsula seu comportamento | Pode ser over-engineering para fluxos triviais |

### Esqueleto de implementação

```pseudo
interface OrderState:
    method next(order: Order)
    method cancel(order: Order)
    method getStatus(): string

class CreatedState implements OrderState:
    method next(order):
        order.setState(new PreparingState())

    method cancel(order):
        order.setState(new CancelledState())

    method getStatus(): return "Criado"

class PreparingState implements OrderState:
    method next(order):
        order.setState(new DeliveringState())

    method cancel(order):
        throw InvalidTransitionError("Não pode cancelar em preparação")

    method getStatus(): return "Preparando"

class DeliveringState implements OrderState:
    method next(order):
        order.setState(new DeliveredState())

    method cancel(order):
        throw InvalidTransitionError("Não pode cancelar em entrega")

    method getStatus(): return "Em entrega"

class DeliveredState implements OrderState:
    method next(order):
        throw InvalidTransitionError("Pedido já finalizado")

    method cancel(order):
        throw InvalidTransitionError("Pedido já finalizado")

    method getStatus(): return "Entregue"

class Order:
    private state: OrderState = new CreatedState()

    method setState(newState: OrderState):
        this.state = newState

    method next():
        this.state.next(this)

    method cancel():
        this.state.cancel(this)

    method getStatus(): string
        return this.state.getStatus()
```

---

## Strategy

**Categoria:** Comportamental
**Intenção:** Encapsular famílias de algoritmos em classes separadas e torná-los
intercambiáveis. O cliente delega o cálculo/processamento à estratégia recebida,
eliminando cadeias de if/else.

### Quando usar

- Você tem múltiplas variações de uma regra de negócio (cálculo de imposto, desconto,
  frete, scoring) que mudam com frequência.
- Novas variações são adicionadas regularmente (novo imposto, nova modalidade de frete).
- Você quer testar cada algoritmo isoladamente.

### Quando NÃO usar

- Quando há apenas um algoritmo que nunca muda — a abstração não se paga.
- Quando a variação é tão simples que um lambda/função resolve.

### Trade-offs

| Benefício | Custo |
|---|---|
| Cada algoritmo é testável isoladamente | Uma classe por estratégia |
| Adicionar nova variação não altera código existente (OCP) | Cliente precisa saber qual estratégia instanciar |
| Responsabilidade única por classe | Pode ser combinado com Factory para resolver a seleção |

### Esqueleto de implementação

```pseudo
interface TaxStrategy:
    method calculate(amount: decimal): decimal

class IcmsStrategy implements TaxStrategy:
    method calculate(amount):
        return amount * 0.18

class IssStrategy implements TaxStrategy:
    method calculate(amount):
        return amount * 0.05

class IpiStrategy implements TaxStrategy:
    method calculate(amount):
        return amount * 0.12

class TaxCalculator:
    method calculate(amount: decimal, strategy: TaxStrategy): decimal
        return strategy.calculate(amount)

// Uso:
// calculator = new TaxCalculator()
// tax = calculator.calculate(1000, new IcmsStrategy())  // 180
// tax = calculator.calculate(1000, new IssStrategy())   // 50
```

---

## Template Method

**Categoria:** Comportamental
**Intenção:** Definir o esqueleto de um algoritmo em uma classe base, delegando
etapas específicas para subclasses. Unifica o fluxo comum e permite variação
nos detalhes.

### Quando usar

- Múltiplas classes executam fluxos muito parecidos com pequenas variações em
  etapas específicas (ex.: parsers de diferentes formatos, pipelines de ETL).
- Há duplicação de código entre classes que seguem o mesmo "roteiro".
- A sequência dos passos é fixa e não deve ser alterada pelas subclasses.

### Quando NÃO usar

- Quando cada implementação tem um fluxo completamente diferente — não há template a extrair.
- Quando composição (Strategy, Decorator) resolve melhor do que herança.

### Trade-offs

| Benefício | Custo |
|---|---|
| Elimina duplicação de fluxo | Usa herança (acoplamento mais forte) |
| Subclasses só implementam o que varia | Hierarquia pode ficar rígida |
| Sequência garantida pela classe base | Difícil de compor (vs. Strategy) |

### Esqueleto de implementação

```pseudo
abstract class DataMiner:
    // Template method — define a sequência, final/sealed para não ser sobrescrito
    final method mine(path: string):
        file = this.openFile(path)
        rawData = this.extractData(file)
        analysis = this.analyzeData(rawData)
        this.sendReport(analysis)
        this.closeFile(file)

    // Etapas variáveis — subclasses obrigatoriamente implementam
    abstract method openFile(path): File
    abstract method extractData(file: File): RawData
    abstract method closeFile(file: File)

    // Etapas comuns — implementação padrão na base
    method analyzeData(rawData: RawData): Analysis:
        return defaultAnalysis(rawData)

    method sendReport(analysis: Analysis):
        emailService.send(analysis.toReport())

class CsvMiner extends DataMiner:
    method openFile(path):
        return CsvReader.open(path)

    method extractData(file):
        return file.readAllRows()

    method closeFile(file):
        file.close()

class PdfMiner extends DataMiner:
    method openFile(path):
        return PdfReader.open(path)

    method extractData(file):
        return file.extractText()

    method closeFile(file):
        file.release()
```