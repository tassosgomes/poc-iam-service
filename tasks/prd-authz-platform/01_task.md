---
status: completed
parallelizable: true
blocked_by: []
---

<task_context>
<domain>infra/monorepo</domain>
<type>configuration</type>
<scope>configuration</scope>
<complexity>medium</complexity>
<dependencies>none</dependencies>
<unblocks>"3.0,28.0"</unblocks>
</task_context>

# Tarefa 1.0: Setup do monorepo (pnpm + Turborepo + Maven parent + .NET solution) ✅ CONCLUÍDA

## Relacionada às User Stories

- Suporte a todas as RFs (infra base do projeto)

## Visão Geral

Estabelecer a estrutura de monorepo polyglot com pnpm workspaces + Turborepo (JS/TS), Maven multi-module parent (Java) e solution .NET, conforme TechSpec § Repo layout. Sem isso nenhuma outra tarefa pode iniciar.

## Requisitos

- Criar `package.json` raiz com scripts Turborepo
- Criar `pnpm-workspace.yaml` apontando para `apps/*` e `libs/*`
- Criar `turbo.json` com pipeline `lint`, `typecheck`, `test`, `build`
- Criar `pom.xml` parent agregando módulos Java (`apps/authz-service`, `apps/demo-ms-java`, `libs/sdk-java`)
- Criar `authz-stack.sln` agregando projetos .NET
- `.gitignore` cobrindo `target/`, `bin/`, `obj/`, `node_modules/`, `dist/`, `.env`
- Criar pastas vazias com `.gitkeep`: `apps/`, `libs/`, `infra/docker/`

## Arquivos Envolvidos

- **Criar:**
  - `package.json`
  - `pnpm-workspace.yaml`
  - `turbo.json`
  - `pom.xml`
  - `authz-stack.sln`
  - `.gitignore` (atualizar se já existe)
- **Modificar:**
  - `README.md` (placeholder com `pnpm install` e `mvn -N install`)
- **Skills para consultar durante implementação:**
  - `java-dependency-config` — versões padrão Spring Boot/Java
  - `dotnet-dependency-config` — TFM padrão .NET 8
  - `react-architecture` — pastas raiz e aliases

## Subtarefas

- [x] 1.1 Inicializar `package.json` raiz e `pnpm-workspace.yaml`
- [x] 1.2 Configurar `turbo.json` com pipeline e dependsOn
- [x] 1.3 Criar parent POM Maven com `<modules>` placeholder e `<properties>` (Java 21, Spring Boot 3.x)
- [x] 1.4 Criar `authz-stack.sln` com placeholder de projetos
- [x] 1.5 Atualizar `.gitignore`
- [x] 1.6 Validar `pnpm install` e `mvn -N validate` rodam sem erro

## Sequenciamento

- Bloqueado por: Nenhum
- Desbloqueia: 3.0, 28.0
- Paralelizável: Sim (com 2.0)

## Rastreabilidade

- Esta tarefa cobre: infra base
- Evidência esperada: `pnpm install` e `mvn -N validate` executam com sucesso

## Detalhes de Implementação

**Estrutura final esperada:**

```
/
├── package.json              # pnpm root
├── pnpm-workspace.yaml       # apps/* libs/*
├── turbo.json                # pipeline
├── pom.xml                   # Maven parent
├── authz-stack.sln           # .NET solution
├── .gitignore
├── README.md
├── apps/.gitkeep
├── libs/.gitkeep
└── infra/docker/.gitkeep
```

**`turbo.json` mínimo:**
```json
{
  "$schema": "https://turbo.build/schema.json",
  "tasks": {
    "build": { "dependsOn": ["^build"], "outputs": ["dist/**"] },
    "test":  { "dependsOn": ["^build"] },
    "lint":  {},
    "typecheck": {}
  }
}
```

**Convenções da stack:**
- Java 21 LTS, Spring Boot 3.x mais recente (`java-dependency-config`)
- .NET 8 TFM `net8.0` (`dotnet-dependency-config`)
- pnpm como package manager (declarar em `packageManager` no `package.json`)

## Critérios de Sucesso (Verificáveis)

- [x] `pnpm install` executa sem erro
- [x] `mvn -N validate` (no parent) executa sem erro
- [x] `dotnet sln list` lista a solução (mesmo vazia)
- [x] `git status` não mostra arquivos build (`target/`, `node_modules/`, etc.)

## Checklist de Conclusão

- [x] 1.0 Setup do monorepo ✅ CONCLUÍDA
- [x] 1.1 Implementação completada
- [x] 1.2 Definição da tarefa, PRD e tech spec validados
- [x] 1.3 Análise de regras e conformidade verificadas
- [x] 1.4 Revisão de código completada
- [x] 1.5 Pronto para deploy
