---
status: pending
parallelizable: false
blocked_by: [30.0]
---

<task_context>
<domain>docs</domain>
<type>documentation</type>
<scope>configuration</scope>
<complexity>low</complexity>
<dependencies>none</dependencies>
<unblocks>""</unblocks>
</task_context>

# Tarefa 31.0: README e documentação operacional

## Relacionada às User Stories

- Suporte a todas (operabilidade)

## Visão Geral

Documentação consolidada para que um dev novo consiga rodar a stack local em < 30 minutos: pré-requisitos, `docker-compose up`, primeiro uso (criar módulo, copiar chave para `.env`, restartar demos), troubleshooting.

## Requisitos

- `README.md` raiz reescrito com:
  - Visão de 3 parágrafos
  - Pré-requisitos (Docker, pnpm, Java 21, .NET 8)
  - Quickstart `docker-compose up` + primeiros passos
  - Como criar novo módulo via PAP
  - Como rotacionar chave
  - Como rodar testes
  - Como publicar SDK (criar tag)
  - Estrutura do monorepo
  - Links para PRD e TechSpec
- `docs/troubleshooting.md` com problemas comuns:
  - Auto-registro falhando (chave errada, AuthZ down, prefix violation)
  - Permissões não refletindo (cache de 10min)
  - MFE não carrega (Module Federation cache, CORS)
- `docs/runbook.md` com procedimentos:
  - Resetar banco
  - Regenerar chave de módulo manualmente
  - Forçar logout de usuário
  - Marcar permissão como `REMOVED` manualmente
- `docs/architecture.md` com diagrama (referenciar TechSpec) + decisões resumidas

## Arquivos Envolvidos

- **Criar:**
  - `docs/troubleshooting.md`
  - `docs/runbook.md`
  - `docs/architecture.md`
- **Modificar:**
  - `README.md` (substituir conteúdo placeholder)
- **Skills para consultar durante implementação:**
  - `common-git-commit` — padrão de commit final

## Subtarefas

- [ ] 31.1 README.md
- [ ] 31.2 troubleshooting.md
- [ ] 31.3 runbook.md
- [ ] 31.4 architecture.md
- [ ] 31.5 Validar quickstart end-to-end seguindo apenas o README

## Sequenciamento

- Bloqueado por: 30.0 (precisa do sistema completo para validar quickstart)
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: operabilidade da plataforma
- Evidência esperada: dev novo segue README e tem ambiente em < 30min

## Critérios de Sucesso (Verificáveis)

- [ ] Validação humana (smoke test): seguir o README sem contexto adicional
- [ ] Links no README resolvem
- [ ] Quickstart funciona em máquina limpa (Docker + pnpm + Java + .NET instalados)
