---
name: roles-naming
description: "Use este skill quando o usuario precisar padronizar nomenclatura de roles (perfis de acesso) em microservicos. Exemplos de ativacao: definir SCREAMING_SNAKE_CASE para roles; alinhar Keycloak e claims; configurar roles no .NET e Spring Security; manter catalogo oficial de roles e regras de migracao."
---

# Skill: Padronização de Nomenclatura de Roles (RBAC)

## 1. Objetivo

Estabelecer um padrão universal para a nomenclatura de perfis de acesso (Roles) em ecossistemas de microserviços. O foco é garantir que qualquer serviço (independente de linguagem ou framework) e qualquer provedor de identidade (IdP) se comuniquem sem necessidade de transformações complexas de dados.

## 2. Problemas Resolvidos

* **Ambiguidade de Case:** Evita falhas de autorização por diferença entre `admin`, `Admin` e `ADMIN`.
* **Acoplamento de Prefixo:** Elimina a dependência de prefixos proprietários (ex: `ROLE_` do Spring) no banco de dados do IdP.
* **Colisão de Escopo:** Define claramente a diferença entre roles globais e roles específicas de domínio.

## 3. Padrão Definido

### 3.1 Formato e Sintaxe

O padrão obrigatório é **SCREAMING_SNAKE_CASE**.

| Regra | Padrão | Exemplo Correto | Exemplo Incorreto |
| --- | --- | --- | --- |
| **Case** | Caixa alta | `EDITOR` | `editor`, `Editor` |
| **Separador** | Underscore | `TECH_LEAD` | `tech-lead`, `TechLead` |
| **Semântica** | Substantivo | `ANALYST` | `CAN_WRITE`, `DO_EDIT` |
| **Prefixo IdP** | Nenhum | `MANAGER` | `ROLE_MANAGER`, `APP_MANAGER` |

### 3.2 Hierarquia e Escopo

Para evitar confusão em ecossistemas grandes, as roles devem seguir esta lógica:

1. **Roles Globais:** Perfis que possuem o mesmo significado em todos os serviços (ex: `ADMIN`, `SUPPORT`).
2. **Roles de Domínio:** Perfis específicos de um contexto de negócio. Devem ser autoexplicativas.
* *Exemplo:* Em um módulo financeiro: `BILLING_OPERATOR`.
* *Exemplo:* Em um módulo de logística: `FLEET_MANAGER`.

## 4. Integração: IdP ↔ Microserviços

Para manter a agnosticidade, a responsabilidade é dividida da seguinte forma:

### 4.1 No Identity Provider (Auth0, Keycloak, Okta, etc.)

* **Armazenamento:** As roles devem ser cadastradas exatamente como definidas no catálogo (ex: `SALES_MANAGER`).
* **Token Claim:** O IdP deve entregar as roles em uma claim padrão chamada `roles` (array de strings).
* *Payload esperado:* `"roles": ["USER", "SALES_MANAGER"]`

### 4.2 No Microserviço (Consumidor)

Independente da tecnologia (Python, Go, Node, .NET, Java), o serviço deve:

1. Ler a claim `roles` do JWT.
2. **Normalização Interna:** Se o framework exigir um prefixo (como o `ROLE_` do Spring), o serviço deve injetá-lo programaticamente na camada de middleware/configuração, e **nunca** esperar que ele venha no token.
3. **Case Sensitivity:** Tratar a comparação de strings como *Case-Sensitive* (em conformidade com o padrão SCREAMING_SNAKE_CASE) para performance e segurança.

---

## 5. Catálogo de Referência (GestAuto)

### 5.1 Nível Administrativo (Cross-Service)

* **`SYSTEM_ADMIN`**: Acesso total e irrestrito.
* **`AUDITOR`**: Acesso de leitura (Read-Only) em todos os logs e registros.

### 5.2 Nível Operacional (Domínio)

* **`SALES_CONSULTANT`**: Operações de venda e CRM.
* **`INVENTORY_MANAGER`**: Gestão de estoque e entradas.
* **`TECHNICAL_EVALUATOR`**: Laudos e vistorias técnicas.

---

## 6. Checklist de Implementação

* [ ] A Role está em `SCREAMING_SNAKE_CASE`?
* [ ] A Role é um substantivo/perfil e não uma ação (permissão)?
* [ ] O IdP está enviando a claim no campo `roles`?
* [ ] O microserviço está configurado para converter a claim (se necessário) sem alterar o banco de identidades?
* [ ] A nova Role foi documentada no catálogo central antes do deploy?

---