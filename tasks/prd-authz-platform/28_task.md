---
status: pending
parallelizable: true
blocked_by: [1.0]
---

<task_context>
<domain>infra/ci</domain>
<type>configuration</type>
<scope>configuration</scope>
<complexity>medium</complexity>
<dependencies>none</dependencies>
<unblocks>""</unblocks>
</task_context>

# Tarefa 28.0: CI GitHub Actions — build e teste de todos os apps/libs

## Relacionada às User Stories

- Suporte a todas (qualidade contínua)

## Visão Geral

Workflow GitHub Actions que detecta mudanças (Turborepo `dry-run`/`affected`) e roda lint, typecheck, build e testes nos apps/libs afetados. Para Java/.NET, usar matrix por projeto. Cache para dependências (pnpm, Maven, NuGet).

## Requisitos

- `.github/workflows/ci.yml` com jobs:
  - `js-ts`: pnpm install + Turborepo `lint`, `typecheck`, `test`, `build`
  - `java`: setup-java 21 + Maven cache + `mvn verify` no parent
  - `dotnet`: setup-dotnet 8 + `dotnet test` na solution
- Triggers: `push` em `main`, `pull_request`
- Cache de pnpm-store, ~/.m2/repository, ~/.nuget/packages
- Status checks obrigatórios para merge

## Arquivos Envolvidos

- **Criar:**
  - `.github/workflows/ci.yml`
  - `.github/dependabot.yml` (opcional, recomendado)
- **Skills para consultar durante implementação:**
  - `java-testing`, `dotnet-testing`, `react-testing` — comandos de teste padrão

## Subtarefas

- [ ] 28.1 Job `js-ts` com Turborepo cache key
- [ ] 28.2 Job `java` com Maven
- [ ] 28.3 Job `dotnet`
- [ ] 28.4 Validar workflow com PR de teste

## Sequenciamento

- Bloqueado por: 1.0
- Desbloqueia: nada bloqueado (ortogonal)
- Paralelizável: Sim

## Rastreabilidade

- Esta tarefa cobre: qualidade contínua
- Evidência esperada: PR mostra checks passando

## Critérios de Sucesso (Verificáveis)

- [ ] Workflow roda em PR e mostra os 3 jobs
- [ ] Falha em algum projeto reflete em check vermelho
- [ ] Cache reduz tempo da segunda execução
