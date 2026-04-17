---
name: design-patterns
description: >
  Catálogo de Design Patterns (GoF e auxiliares) com guia de decisão e implementação.
  Use esta skill sempre que o contexto envolver padrões de projeto, refatoração estrutural,
  ou quando detectar code smells que um padrão resolve — como: cadeias de if/else para
  instanciar objetos (Factory), comportamentos condicionais que mudam por estado (State),
  algoritmos intercambiáveis (Strategy), acoplamento a bibliotecas externas (Adapter),
  funcionalidades empilháveis (Decorator), notificações reativas (Observer), controle de
  acesso ou cache em objetos (Proxy), eliminação de duplicação em fluxos similares
  (Template Method), ou instância única global (Singleton). Também dispare quando o
  usuário mencionar explicitamente nomes de padrões, pedir para "aplicar um design pattern",
  "refatorar usando padrões", "desacoplar", ou quando perguntar "qual padrão usar aqui?".
  Esta skill funciona em qualquer linguagem — os princípios são agnósticos de stack.
---

# Design Patterns — Catálogo e Guia de Aplicação

## Como usar esta skill

Esta skill opera em dois modos complementares:

1. **Modo Consultivo** — Quando o agente detecta um code smell ou o usuário pede orientação,
   a skill sugere o padrão adequado com justificativa, trade-offs e um esboço estrutural.

2. **Modo Executivo** — Quando o usuário pede para implementar ou refatorar, a skill guia
   a geração de código seguindo a estrutura canônica do padrão, adaptada à linguagem do projeto.

O fluxo recomendado: **diagnosticar → sugerir → confirmar com o usuário → implementar**.

---

## Tabela de Decisão Rápida

Use esta tabela para identificar qual padrão se aplica ao cenário:

| Sintoma / Necessidade | Padrão | Categoria |
|---|---|---|
| Integrar lib externa sem acoplar | **Adapter** | Estrutural |
| Empilhar comportamentos em runtime | **Decorator** | Estrutural |
| Reagir a mudanças de estado de outro objeto | **Observer** | Comportamental |
| Interceptar acesso (cache, log, auth) | **Proxy** | Estrutural |
| Centralizar criação de objetos por parâmetro | **Simple Factory** | Criacional |
| Garantir instância única global | **Singleton** | Criacional |
| Comportamento muda conforme estado interno | **State** | Comportamental |
| Algoritmos intercambiáveis sem if/else | **Strategy** | Comportamental |
| Fluxo comum com etapas variáveis | **Template Method** | Comportamental |

---

## Catálogo de Padrões

Para detalhes completos de cada padrão (estrutura, quando usar, quando NÃO usar,
trade-offs e esqueleto de implementação), consulte o arquivo de referência:

→ **`references/patterns-catalog.md`** — Leia a seção do padrão relevante antes de implementar.

---

## Fluxo de Trabalho do Agente

### Ao detectar um code smell ou receber um pedido de refatoração:

1. **Diagnosticar** — Identifique o sintoma usando a Tabela de Decisão acima.
2. **Consultar** — Leia a seção correspondente em `references/patterns-catalog.md`.
3. **Sugerir** — Apresente ao usuário:
   - Qual padrão e por quê
   - O trade-off principal (complexidade adicionada vs. benefício)
   - Esboço da estrutura (interfaces, classes concretas, relações)
4. **Confirmar** — Aguarde aprovação antes de refatorar.
5. **Implementar** — Siga o esqueleto estrutural do catálogo, adaptando à linguagem e
   convenções do projeto.
6. **Validar** — Verifique se o código resultante respeita os princípios SOLID relevantes
   ao padrão aplicado.

### Ao receber pedido direto ("implemente o padrão X aqui"):

1. Leia a seção do padrão em `references/patterns-catalog.md`.
2. Implemente seguindo o esqueleto estrutural.
3. Comente no código a intenção do padrão (um comentário breve no ponto de entrada).

---

## Princípios Transversais

- **Não force padrões** — Um padrão só se justifica se resolve um problema real.
  Código simples que funciona é melhor que código "padrão" desnecessariamente complexo.
- **Prefira composição a herança** — Exceto onde herança é a essência do padrão
  (Template Method).
- **Nomeie com intenção** — Classes e interfaces devem refletir o papel no padrão
  (ex.: `ImageProcessor` → `WatermarkDecorator`, não `Decorator1`).
- **Um padrão de cada vez** — Ao refatorar, aplique um padrão, estabilize, depois avalie
  se outro é necessário. Não empilhe três padrões numa única refatoração.
- **Considere o Singleton com cautela** — Hoje é amplamente considerado um anti-padrão.
  Prefira injeção de dependência. Só use se o framework não oferecer container de DI.