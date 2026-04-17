---
status: pending
parallelizable: true
blocked_by: [15.0, 19.0, 23.0]
---

<task_context>
<domain>infra/ci</domain>
<type>configuration</type>
<scope>configuration</scope>
<complexity>medium</complexity>
<dependencies>external_apis</dependencies>
<unblocks>""</unblocks>
</task_context>

# Tarefa 29.0: CI GitHub Actions — publish dos 3 SDKs em GitHub Packages

## Relacionada às User Stories

- RF-09, RF-10 (distribuição dos SDKs)

## Visão Geral

Workflow disparado por tag (`sdk-react-vX.Y.Z`, `sdk-java-vX.Y.Z`, `sdk-dotnet-vX.Y.Z`) que publica o SDK correspondente em GitHub Packages. Detecta o SDK via prefixo da tag.

## Requisitos

- `.github/workflows/publish-sdks.yml`:
  - Trigger: `push.tags: ['sdk-*-v*']`
  - Step "detect SDK" parseando o nome da tag
  - Job condicional por SDK
- npm publish via `GITHUB_TOKEN` com `registry=https://npm.pkg.github.com`
- Maven deploy via `distributionManagement` para `https://maven.pkg.github.com/<org>/<repo>`
- NuGet push via `dotnet nuget push` para `https://nuget.pkg.github.com/<org>/index.json`
- Versão extraída da tag injetada no artefato (npm `version`, Maven `revision`, csproj `<Version>`)

## Arquivos Envolvidos

- **Criar:**
  - `.github/workflows/publish-sdks.yml`
- **Modificar:**
  - `libs/sdk-react/package.json` (`publishConfig.registry`)
  - `libs/sdk-java/pom.xml` (`distributionManagement`)
  - `libs/sdk-dotnet/AuthzSdk/AuthzSdk.csproj` (`<RepositoryUrl>`, `<PackageId>`)
- **Skills para consultar durante implementação:**
  - `dotnet-dependency-config` — pack metadata
  - `java-dependency-config` — Maven distribution
  - `react-architecture` — packaging

## Subtarefas

- [ ] 29.1 Workflow com matriz e condicional por tag
- [ ] 29.2 Configurar metadata de publishing nos 3 SDKs
- [ ] 29.3 Documentar no README como criar tag e disparar publish

## Sequenciamento

- Bloqueado por: 15.0, 19.0, 23.0 (precisa dos SDKs prontos)
- Paralelizável: Sim

## Rastreabilidade

- Esta tarefa cobre: distribuição dos SDKs
- Evidência esperada: criar tag `sdk-react-v0.1.0` publica no GH Packages

## Critérios de Sucesso (Verificáveis)

- [ ] Tag teste cria artefato visível em `github.com/<org>/<repo>/packages`
- [ ] Versão do artefato reflete a tag
- [ ] Workflow falha cedo em tag inválida
