---
name: restful-api
description: "Use este skill quando o usuario precisar definir padroes de APIs REST/HTTP. Exemplos de ativacao: padronizar versionamento via path; definir convencoes de URLs (plural, ingles, kebab-case); definir paginacao padrao; aplicar RFC 9457 (Problem Details) para erros; adotar OpenAPI 3 e design-first; padronizar codigos de status e payload JSON."
---

# Diretrizes para APIs REST/HTTP

## Contexto

A falta de um padrao para o design e documentacao de APIs REST pode resultar em inconsistencias entre os servicos. Cada equipe implementa aspectos criticos como versionamento, paginacao e, principalmente, a estrutura de respostas de erro de maneira distinta, podendo dificultar integracoes.

Estas divergencias podem acarretar consequencias diretas para o ecossistema de desenvolvimento:

- **Aumento da carga cognitiva**: Desenvolvedores que consomem as APIs sao forcados a aprender as particularidades de cada servico, aumentando o tempo de integracao e a probabilidade de erros.

- **Fragilidade do ecossistema**: A falta de previsibilidade nas interacoes entre servicos gera integracoes frageis e de dificil depuracao.

O objetivo e instituir boas praticas baseadas na especificacao **OpenAPI 3**, aliada a uma filosofia **"Design-First"**, como a fundacao de um "manual de estilo" para as APIs.

## 1. Mapeamento de Endpoints

Utilize **ASP.NET Core MVC (Controllers)** ou **Minimal APIs** para mapear os endpoints, escolhendo a abordagem mais adequada para o contexto do projeto.

## 2. Padroes de Roteamento e Nomenclatura

### 2.1. Estrutura de URLs
- Utilize o padrao REST para consultas, mantendo o nome dos recursos em **ingles** e no **plural**
- Permita a navegabilidade em recursos aninhados
  - Exemplo: `/playlists/{playlistId}/videos` ou `/customers/{customerId}/invoices`

### 2.2. Convencao de Nomenclatura
- Para as URLs dos recursos, prefira o padrao **kebab-case** para melhor legibilidade
  - Exemplo: `/scheduled-events`
- Configure o roteamento do ASP.NET Core para seguir este padrao

### 2.3. Limitacoes de Aninhamento
- Evite criar endpoints com mais de **3 niveis** de aninhamento de recursos

## 3. Versionamento Obrigatorio

### 3.1. Padrao de Versionamento
- O versionamento de API deve ser realizado **obrigatoriamente** atraves do **path da URI**
- A versao maior (_major_) da API deve ser incluida como o primeiro elemento do path apos o nome da API, prefixada com a letra `v`

**Exemplo**: `https://api.example.com/users/v1/profile`

### 3.2. Estrutura Recomendada
```
https://[dominio]/[api-name]/v[major-version]/[resource]
```

## 4. Tratamento de Mutacoes (Operacoes de Escrita)

Para acoes que nao se encaixam claramente no modelo CRUD (Create, Read, Update, Delete), utilize o verbo **POST** com URLs que descrevam a acao (estilo RPC).

**Exemplo**: `POST /users/{userId}/change-password` em vez de `PUT /users/{userId}` com um payload complexo.

## 5. Formato de Dados e Seguranca

### 5.1. Formato de Dados
- O formato do payload de requisicao e resposta deve ser sempre **JSON**

### 5.2. Autenticacao e Autorizacao
- Sempre valide a **autenticacao** (quem o usuario e) e a **autorizacao** (o que o usuario pode fazer) em todos os endpoints que requerem protecao
- Utilize os middlewares do ASP.NET Core para implementar estas validacoes
- Os esquemas de seguranca devem ser explicitamente definidos na documentacao OpenAPI

## 6. Codigos de Status de Retorno

### 6.1. Codigos de Sucesso
- **200 OK**: Sucesso na requisicao
- **201 Created**: Recurso criado com sucesso (usar em conjunto com o header Location)
- **204 No Content**: Sucesso, mas sem conteudo para retornar (comum em operacoes de DELETE)

### 6.2. Codigos de Erro do Cliente
- **400 Bad Request**: A requisicao esta mal formatada (ex: JSON invalido, parametros faltando)
- **401 Unauthorized**: O usuario nao esta autenticado
- **403 Forbidden**: O usuario esta autenticado, mas nao tem permissao para acessar o recurso
- **404 Not Found**: O recurso solicitado nao foi encontrado
- **422 Unprocessable Entity**: A requisicao estava bem formatada, mas contem erros de negocio (ex: e-mail ja cadastrado)

### 6.3. Codigos de Erro do Servidor
- **500 Internal Server Error**: Erro inesperado no servidor

### 6.4. Tabela de Códigos de Status HTTP

Utilize os códigos de status abaixo para garantir semântica e previsibilidade nas respostas da API:

| Código | Status | Descrição e Uso Recomendado |
|--------|--------|------------------------------|
| 200 | OK | Sucesso na requisição. Retorna o recurso solicitado no corpo. |
| 201 | Created | Recurso criado com sucesso. Obrigatório retornar o header `Location`. |
| 204 | No Content | Sucesso, mas sem corpo de resposta (comum em DELETE ou PUT). |
| 400 | Bad Request | Erro de sintaxe (ex: JSON malformado ou campos obrigatórios faltando). |
| 401 | Unauthorized | O usuário não está autenticado ou o token é inválido/expirado. |
| 403 | Forbidden | Usuário autenticado, mas sem permissão de acesso ao recurso específico. |
| 404 | Not Found | O recurso solicitado (ID ou rota) não foi encontrado no servidor. |
| 422 | Unprocessable Entity | Erros de regra de negócio (ex: CPF já cadastrado, saldo insuficiente). |
| 429 | Too Many Requests | O cliente excedeu o limite de requisições (Rate Limiting). |
| 500 | Internal Server Error | Erro inesperado no servidor. Não deve expor detalhes sensíveis (Stack Trace). |

## 7. Padrao de Respostas de Erro (RFC 9457)

### 7.1. Formato Obrigatorio
As respostas de erro devem **obrigatoriamente** aderir ao formato definido pela **[RFC 9457 (Problem Details for HTTP APIs)](https://www.rfc-editor.org/rfc/rfc9457.html)**.

### 7.2. Estrutura da Resposta de Erro
```json
{
  "type": "https://example.com/probs/out-of-credit",
  "title": "You do not have enough credit.",
  "status": 403,
  "detail": "Your current balance is 30, but that costs 50.",
  "instance": "/account/12345/msgs/abc"
}
```

### 7.3. Implementacao no ASP.NET Core
- Utilize o pacote `Microsoft.AspNetCore.Http.Extensions` para implementar Problem Details
- Configure o middleware de tratamento de excecoes para retornar respostas no formato RFC 9457

## 8. Paginacao Obrigatoria

### 8.1. Padrao de Paginacao
Respostas que retornam uma **colecao** de recursos devem **obrigatoriamente** suportar paginacao atraves dos query parameters padronizados:
- **`_page`**: numero da pagina (iniciando em 1)
- **`_size`**: quantidade de itens por pagina

### 8.2. Exemplo de Implementacao
```csharp
[HttpGet]
public async Task<IActionResult> GetUsers(
    [FromQuery] int _page = 1, 
    [FromQuery] int _size = 10)
{
    // Implementacao da paginacao
}
```

### 8.3. Estrutura da Resposta Paginada
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "size": 10,
    "total": 100,
    "totalPages": 10
  }
}
```

## 9. Documentacao e OpenAPI

### 9.1. Especificacao OpenAPI 3 (Recomendado)
- **Recomenda-se fortemente** o uso da especificacao **[OpenAPI 3 (OAS3)](https://swagger.io/specification/)** para definicao formal de contratos de API
- O formato **YAML** (`openapi.yaml`) e o padrao recomendado para escrita e manutencao da especificacao

### 9.2. Filosofia "Design-First" (Recomendada)
- **Recomenda-se** a adocao da abordagem **["Design-First"](https://swagger.io/blog/code-first-vs-design-first-api/)**
- O contrato OpenAPI deve ser projetado, revisado e validado antes da implementacao do codigo

### 9.3. Ferramentas para .NET
- Utilize bibliotecas como **Swashbuckle** para gerar a documentacao automaticamente
- Configure o Swagger UI para facilitar o teste e exploracao da API durante o desenvolvimento

### 9.4. Documentacao Obrigatoria
- Documente todos os endpoints, metodos e codigos de status
- Inclua exemplos de requisicoes e respostas
- Descreva claramente os esquemas de autenticacao e autorizacao

## 10. Features Avancadas

### 10.1. Partial Responses
Considere implementar partial responses para consultas que podem retornar grandes volumes de dados, permitindo que o cliente especifique os campos desejados.

**Exemplo**: `?fields=id,name,email`

### 10.2. Filtros e Ordenacao
Implemente padroes consistentes para:
- **Filtros**: `?status=active&category=tech`
- **Ordenacao**: `?sort=name&order=asc`

## 11. Comunicacao com APIs Externas

### 11.1. HttpClient
- Utilize a **IHttpClientFactory** e a classe **HttpClient** para realizar chamadas a APIs externas de forma segura e eficiente
- Configure politicas de retry e circuit breaker quando apropriado

### 11.2. Exemplo de Implementacao
```csharp
public class ExternalApiService
{
    private readonly HttpClient _httpClient;
    
    public ExternalApiService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }
    
    // Implementacao dos metodos
}
```

## 12. Justificativas

### 12.1. Versionamento Obrigatorio
- **Clareza**: O versionamento na URL e explicito e inequivoco para os consumidores da API
- **Facilita o roteamento**: Em API Gateways e a exploracao da documentacao

### 12.2. RFC 9457 para Erros
- **Previsibilidade**: Garante que todas as APIs respondam a erros de forma consistente
- **Rastreabilidade**: O formato padronizado inclui campos que facilitam a depuracao

### 12.3. Paginacao Padronizada
- **Protecao**: Previne sobrecarga do servidor ou da rede
- **Consistencia**: Simplifica a implementacao em clientes

### 12.4. OpenAPI e Design-First
- **Desenvolvimento paralelo**: Permite que equipes trabalhem simultaneamente com base em um contrato mockado
- **Reducao de retrabalho**: Alinha expectativas antes da codificacao
