# PRD — AuthZ Platform

**Projeto:** Plataforma de Autenticação e Autorização para MFE + MS
**Status:** Draft v1.1
**Autor:** Tasso (Product)
**Data:** 2026-04-16
**Revisão v1.1:** Substituição do sync via CI como caminho primário por auto-registro no bootstrap com chave compartilhada por módulo (agnóstico a IDP).

---

## 1. Visão geral

Construir uma plataforma de autorização greenfield que sustente um ecossistema de micro frontends (MFE) e microsserviços (MS), com granularidade até o nível de item de UI e provisionamento de acesso self-service por gestores de negócio — sem depender do time de TI para cada mudança de permissão.

A plataforma desacopla identidade (delegada ao CyberArk) de autorização (serviço próprio), permitindo que novos módulos sejam onboarded de forma padronizada e que permissões evoluam na mesma velocidade dos produtos, sem inflar o IDP nem acoplar o ciclo de vida de permissões ao ciclo de identidade corporativa.

## 2. Contexto e problema

A empresa aloca dinamicamente pessoas entre áreas em picos de demanda. Cada mudança de alocação exige liberação de acessos em sistemas diversos. Hoje, esse fluxo passa obrigatoriamente por TI, criando gargalo operacional e impactando time-to-productivity em momentos críticos do negócio.

Em paralelo, o roadmap prevê múltiplos MFEs e MSs novos. Sem uma plataforma unificada, cada time implementaria sua própria solução de autorização, resultando em: duplicação de esforço, inconsistência de experiência, dificuldade de auditoria, e impossibilidade de visão consolidada de acessos.

Modelos alternativos foram avaliados e descartados (ver seção 9): manter tudo no CyberArk tira autonomia do negócio e infla tokens; usar OPA/PlainID com PDP distribuído é overkill para a escala atual; delegar para cada MS é inviável para governança.

## 3. Objetivos

**Negócio**
- Reduzir dependência de TI para gestão de acessos operacionais
- Permitir que gestores de módulo provisionem/revoguem acesso em tempo real durante picos de demanda
- Acelerar onboarding de novos MFEs e MSs com padrão reutilizável

**Técnicos**
- Fornecer SDKs que abstraiam complexidade de autorização para desenvolvedores
- Centralizar decisões de autorização mantendo performance (lookup local via cache)
- Permitir evolução de permissões sem downtime ou coordenação manual entre times

## 4. Público-alvo e personas

**Admin de módulo (persona primária)**
Gestor de negócio responsável por uma área funcional (ex: Vendas, Estoque). Precisa atribuir/revogar acessos da sua equipe rapidamente, sem conhecimento técnico.

**Desenvolvedor de MFE/MS**
Constrói novos módulos. Precisa declarar permissões do seu domínio, integrar o SDK, e ter certeza de que autorização "funciona" sem virar especialista em AuthZ.

**Usuário final**
Colaborador que acessa o sistema para executar suas atividades. Autorização deve ser transparente — vê apenas o que pode usar.

**Admin global (persona secundária)**
Perfil sênior com visão cross-módulos. Audita, cria novos módulos, resolve conflitos de permissão entre áreas.

## 5. Success Metrics

| Métrica | Alvo | Como medir |
|---|---|---|
| Tempo de onboarding de usuário | < 5 minutos | Log de tempo entre "admin clica em Adicionar Usuário" e "usuário consegue acessar o módulo", em amostra de 20 operações |
| Tempo para liberar nova permissão em produção | < 1 hora | Diff entre deploy do MS com novo `permissions.yaml` e permissão disponível para atribuição no PAP, medido via evento de sync no log de auditoria |
| Cobertura do SDK nos MFEs/MSs do projeto | 100% dos módulos do MVP | Auditoria manual ao fim da POC |
| Disponibilidade do AuthZ Service | ≥ 99.5% em horário comercial | Monitoramento via healthcheck com alertas |

## 6. Requisitos funcionais

### RF-01 — Autenticação via CyberArk
**Descrição:** O App Shell autentica usuários via CyberArk usando OIDC, recebendo token JWT com identificação do usuário e pertencimento a módulos.
**Critérios de aceitação:**
- **Given** um usuário cadastrado no CyberArk com pertencimento ao módulo "vendas"
- **When** ele acessa o App Shell e completa o fluxo de login
- **Then** o Shell armazena o JWT e torna a identidade disponível para MFEs filhos
**MoSCoW:** Must

### RF-02 — Declaração de permissões em catálogo YAML
**Descrição:** Cada MS/MFE mantém suas permissões em arquivo versionado `permissions.yaml` no próprio repositório, seguindo convenção de nomenclatura `<modulo>.<recurso>.<acao>`. O arquivo é a fonte declarativa do módulo e é servido em runtime pelo endpoint de discovery (RF-04); o auto-registro no bootstrap (RF-16) consome essa fonte. CI pode enviar diretamente para o AuthZ Service como fallback em workloads sem HTTP (ex: jobs batch).
**Critérios de aceitação:**
- **Given** um repositório com arquivo `permissions.yaml` válido
- **When** o MS é construído
- **Then** o arquivo é embarcado no artefato e servido em runtime via `/.well-known/permissions`
- **And** permissões não pertencentes ao prefixo do módulo são rejeitadas pelo AuthZ Service no sync
- **And** permissões sem descrição são rejeitadas
**MoSCoW:** Must

### RF-03 — Endpoint de sincronização de catálogo
**Descrição:** O AuthZ Service expõe `POST /catalog/sync` que recebe declaração de permissões de um módulo e reconcilia o estado do catálogo. Autenticação via chave compartilhada por módulo (RF-17), com binding obrigatório ao prefixo autorizado.
**Critérios de aceitação:**
- **Given** um payload válido com permissões novas, modificadas e removidas e chave do módulo no header de autenticação
- **When** o endpoint é chamado
- **Then** novas permissões entram como `active`
- **And** permissões removidas do catálogo são marcadas como `deprecated` com sunset date
- **And** a operação é atômica (tudo ou nada)
- **And** o sync é rejeitado com 403 se o payload contiver permissões fora do `allowed_prefix` da chave
- **And** o sync é idempotente (payload idêntico ao estado atual retorna 200 sem efeito colateral)
**MoSCoW:** Must

### RF-04 — Endpoint de discovery de permissões
**Descrição:** Cada MS expõe `GET /.well-known/permissions` retornando suas permissões declaradas. É a fonte primária consumida pelo próprio MS no auto-registro (RF-16) e por ferramentas de auditoria.
**Critérios de aceitação:**
- **Given** um MS integrado à plataforma
- **When** o endpoint é consultado
- **Then** retorna JSON com lista de permissões ativas do módulo
- **And** o payload segue schema versionado compatível com o consumido por `POST /catalog/sync` (RF-03)
- **And** o endpoint não exige autenticação (informação não-sensível)
**MoSCoW:** Must

### RF-05 — Gestão de papéis (roles) via PAP UI
**Descrição:** A PAP UI permite criar, editar, clonar e remover papéis, e associar permissões atômicas a eles.
**Critérios de aceitação:**
- **Given** um admin autenticado com perfil `role_manager`
- **When** ele cria um papel e seleciona permissões do catálogo
- **Then** o papel é persistido e disponível para atribuição
- **And** o admin visualiza descrições em linguagem natural de cada permissão
**MoSCoW:** Must

### RF-06 — Atribuição de papéis a usuários (self-service)
**Descrição:** Admins de módulo atribuem e revogam papéis a usuários do CyberArk sem envolver TI.
**Critérios de aceitação:**
- **Given** um admin de módulo autenticado
- **When** ele busca um usuário pelo CyberArk e atribui um ou mais papéis
- **Then** o usuário obtém as permissões em < 5 minutos no próximo login/refresh
- **And** a operação é registrada em log de auditoria
**MoSCoW:** Must

### RF-07 — Delegação de gestão por módulo (`can_manage_users`)
**Descrição:** A plataforma suporta papéis administrativos escopados por módulo, permitindo que um admin de Vendas gerencie apenas usuários de Vendas.
**Critérios de aceitação:**
- **Given** um admin com permissão `vendas.can_manage_users`
- **When** ele acessa a PAP UI
- **Then** vê apenas usuários e papéis do módulo Vendas
- **And** tentativas de atribuir papéis de outros módulos são bloqueadas
**MoSCoW:** Must

### RF-08 — Endpoint de bulk fetch de permissões do usuário
**Descrição:** AuthZ Service expõe `GET /users/{userId}/permissions` retornando lista completa de permissões do usuário em uma única chamada.
**Critérios de aceitação:**
- **Given** um `userId` válido autenticado
- **When** o endpoint é chamado
- **Then** retorna todas as permissões agregadas dos papéis do usuário
- **And** responde em < 100ms no percentil 95
**MoSCoW:** Must

### RF-09 — SDK React com bulk fetch e cache de sessão
**Descrição:** SDK React provê cliente HTTP tipado que busca permissões uma vez por sessão e mantém em memória.
**Critérios de aceitação:**
- **Given** um MFE usando o SDK
- **When** o App Shell injeta o contexto de auth
- **Then** o SDK faz 1 chamada HTTP ao AuthZ Service
- **And** verificações subsequentes via `usePermission()` são síncronas e em memória
- **And** o cache expira junto com o JWT
**MoSCoW:** Must

### RF-10 — SDK Java e SDK .NET com bulk fetch e cache por requisição
**Descrição:** SDKs server-side provêm cliente HTTP tipado com cache em escopo de requisição.
**Critérios de aceitação:**
- **Given** um MS usando o SDK
- **When** uma requisição HTTP chega ao MS
- **Then** a primeira verificação de permissão na requisição faz bulk fetch
- **And** verificações subsequentes na mesma requisição usam cache em memória
**MoSCoW:** Must

### RF-11 — Endpoint de decisão pontual
**Descrição:** AuthZ Service expõe `POST /check` para casos onde bulk fetch não é adequado (ex: jobs, integrações externas).
**Critérios de aceitação:**
- **Given** um payload `{userId, permission}`
- **When** o endpoint é chamado
- **Then** retorna decisão booleana em < 50ms no percentil 95
**MoSCoW:** Should

### RF-12 — Lifecycle de permissões (active/deprecated/removed)
**Descrição:** Permissões têm ciclo de vida formal com sunset date antes de remoção definitiva.
**Critérios de aceitação:**
- **Given** uma permissão marcada como `deprecated`
- **When** ela ainda está associada a papéis ativos
- **Then** admins recebem alerta na PAP UI
- **And** a permissão continua funcionando até a sunset date
**MoSCoW:** Should

### RF-13 — Log de auditoria de atribuições e eventos de catálogo
**Descrição:** Toda atribuição/revogação de papel e todo evento de catálogo (sync, criação de módulo, rotação de chave) gera entrada em log imutável.
**Critérios de aceitação:**
- **Given** qualquer operação de atribuição/revogação de papel
- **When** ela é executada
- **Then** um registro é gravado com `actor_id`, `target_user_id`, `role_id`, `action`, `timestamp`
- **Given** um evento de sync de catálogo, criação de módulo ou rotação de chave
- **When** ele é processado
- **Then** um registro é gravado com `module_id`, `event_type`, `payload_hash`, `source_ip`, `timestamp`
- **And** o log pode ser consultado via API administrativa
**MoSCoW:** Must

### RF-14 — Módulos de demo (showcase)
**Descrição:** Entregar 1 MFE + 2 MSs (Java, .NET) funcionais exercitando toda a plataforma.
**Critérios de aceitação:**
- **Given** stakeholders observando demo
- **When** admin de módulo atribui papel a usuário
- **Then** usuário vê no MFE os itens liberados
- **And** ações executadas no MFE são validadas no MS correspondente
- **And** a UI dos demos é polida o suficiente para apresentação executiva
**MoSCoW:** Must

### RF-15 — PAP UI como MFE integrado ao App Shell
**Descrição:** A interface de administração é entregue como MFE consumindo os mesmos padrões da plataforma.
**Critérios de aceitação:**
- **Given** um admin acessando o App Shell
- **When** navega para a rota de administração
- **Then** o PAP é carregado como MFE
- **And** o próprio PAP usa o SDK React para suas verificações de permissão (dogfooding)
**MoSCoW:** Must

### RF-16 — Auto-registro de módulos no bootstrap
**Descrição:** Cada MS, ao iniciar, lê suas permissões via `/.well-known/permissions` (RF-04) e submete automaticamente ao `POST /catalog/sync` (RF-03), autenticando com a chave compartilhada do módulo (RF-17). Este é o caminho primário de registro — a plataforma não depende de CI/CD para sincronização de catálogo.
**Critérios de aceitação:**
- **Given** um MS iniciando em qualquer ambiente
- **When** o processo de bootstrap executa
- **Then** o MS chama `/catalog/sync` antes de sinalizar readiness
- **And** a readiness probe permanece falha enquanto o primeiro sync não for bem-sucedido
- **And** falhas de sync aplicam backoff exponencial até ~5min, com alerta via log estruturado
- **And** o MS dispara re-sync como heartbeat a cada 15 minutos para reconciliar drift manual
- **And** múltiplas réplicas do mesmo MS podem sincronizar concorrentemente sem efeito colateral (idempotência de RF-03)
- **And** um módulo que deixa de emitir heartbeat por mais de 7 dias tem suas permissões marcadas como `stale` (não removidas) e gera alerta administrativo
**MoSCoW:** Must

### RF-17 — Gestão de chaves compartilhadas por módulo
**Descrição:** A plataforma gerencia o ciclo de vida das chaves secretas usadas pelos MSs para autenticar no `POST /catalog/sync`, de forma agnóstica ao IDP corporativo. O binding `chave → módulo → prefixo` é o controle de autoridade que impede um módulo registrar permissões fora do seu escopo.
**Critérios de aceitação:**
- **Given** um admin global autenticado na PAP UI
- **When** ele cria um novo módulo
- **Then** o sistema gera uma chave secreta associada ao `module_id` com `allowed_prefix` explícito
- **And** a chave em texto claro é exibida uma única vez para cópia e armazenada apenas como hash no banco
- **Given** um admin global que aciona rotação de chave
- **When** a rotação é confirmada
- **Then** uma nova chave é gerada e ambas permanecem válidas por janela de graça configurável (default 24h)
- **And** o campo `rotated_at` é atualizado e evento de auditoria é gerado (RF-13)
- **Given** uma chave com idade superior a 180 dias
- **When** o admin acessa a PAP UI
- **Then** recebe alerta visual recomendando rotação (sem bloqueio)
- **Given** uma tentativa de sync com chave inválida, expirada ou fora do grace period
- **When** o endpoint é chamado
- **Then** a requisição é rejeitada com 401 e o evento é logado
**MoSCoW:** Must

## 7. Non-goals

Itens explicitamente **fora do escopo** deste projeto:

- **ABAC ou ReBAC** — somente RBAC puro
- **Escopo fino de recurso** (ex: "aprovar pedido da minha filial") — decisões são binárias sobre permissões atômicas; filtros por escopo ficam no domínio de cada MS
- **Revogação instantânea** (< 1 segundo) — aceita-se janela de staleness até expiração do JWT
- **Sincronização entre abas do navegador** no SDK React
- **Storage offline** de permissões
- **Circuit breaker, retry complexo, fallback policies** nos SDKs
- **Migração de sistemas legados** — projeto greenfield
- **Certificação SOX/LGPD/ISO formal** — sem requisitos regulatórios no MVP
- **Autenticação alternativa ao CyberArk**
- **Impersonação ou "login as user"**
- **Aprovação multi-etapa** (workflow de request/approval de acessos)

## 8. Riscos e premissas

**Premissas assumidas**
- MSs têm conectividade HTTP de saída para o AuthZ Service durante o bootstrap (para auto-registro); workloads sem HTTP usam fallback via CI
- O App Shell roda Module Federation ou tecnologia equivalente de MFE
- O CyberArk expõe API de busca de usuários utilizável pelo PAP
- Stack de infra já decidida pela organização suportará Node/Go/Java + banco relacional
- JWT tem TTL entre 15 e 60 minutos (define janela de staleness)
- Infra da organização oferece algum mecanismo para distribuir secrets aos MSs (env var, K8s Secret, Vault — a definir na TechSpec)

**Riscos**

| Risco | Impacto | Mitigação |
|---|---|---|
| Adoção do SDK baixa (devs burlam e chamam AuthZ direto) | Alto | Documentação, dogfooding no PAP, code review obrigatório |
| AuthZ Service vira gargalo em horário de pico | Alto | Bulk fetch + cache reduzem carga ~100×; load test na POC |
| Sync no bootstrap falha e bloqueia readiness do MS | Médio | Backoff exponencial, alerta imediato, sync manual via CLI/endpoint administrativo como fallback de emergência |
| Chave compartilhada de módulo vazada permite sync malicioso | Médio | Binding de prefixo limita blast radius ao namespace do módulo; rotação manual via PAP com grace period; auditoria de todo sync (RF-13) |
| Admin atribui permissão errada por falta de review | Médio | Logs de auditoria, visualização clara, future: workflow de aprovação |
| Staleness de 15min gera incidente de segurança | Baixo | Aceito como trade-off; força-logout manual disponível na PAP |
| Convenção de nomenclatura ignorada por algum time | Médio | Validação no AuthZ Service bloqueia sync fora do prefixo; exemplos no repo template |

## 9. Alternativas consideradas

**Permissões dentro do CyberArk (via claims/groups)**
Rejeitada. Tira autonomia do negócio (toda mudança passa por TI), infla o JWT, acopla ciclo de vida de permissões ao ciclo de identidade. Identificada na conversa inicial como bloqueador.

**OPA / PlainID como PDP central com políticas declarativas**
Rejeitada por over-engineering. Motor de políticas ABAC com linguagem própria (Rego) introduz curva de aprendizado e operação que não se paga para RBAC puro. Reavaliar caso requisitos evoluam para ABAC.

**Permissões embutidas nos próprios MSs (sem serviço central)**
Rejeitada. Inviabiliza governança centralizada, duplica esforço, impede self-service cross-módulos.

**Casbin embarcado nos MSs**
Considerada. Boa para RBAC leve, mas descentraliza atribuições e dificulta o PAP UI unificado. Rejeitada em favor do AuthZ Service próprio por alinhamento com requisito de centralização.

**SDK sem cache (chamada HTTP por check)**
Rejeitada. Gera 50-200 chamadas HTTP por tela renderizada, inflando carga, latência e custo de infra. Trade-off de revogação instantânea não compensa em cenário sem compliance crítico.

**Sincronização de catálogo via CI como caminho primário**
Considerada na v1.0. Rejeitada por acoplar a plataforma ao ambiente de build e pressupor CI/CD unificado entre todos os times. O auto-registro no bootstrap (RF-16) remove essa dependência, coloca o sync junto ao ciclo de deploy natural do MS, e funciona em qualquer infra que rode HTTP. CI permanece disponível como fallback para jobs/batch sem endpoint HTTP.

**Autenticação M2M via IDP (CyberArk client credentials)**
Considerada. Rejeitada por reintroduzir acoplamento IDP↔plataforma que o próprio PRD busca quebrar (seção 1). Chave compartilhada por módulo com prefix binding (RF-17) atende o escopo sem criar dependência externa nem inflar o ciclo de vida de identidade.

## 10. Impacto técnico (alto nível)

**Novos componentes**
- 1 serviço backend (AuthZ Service) com API REST e banco relacional
- 1 aplicação App Shell
- 1 MFE PAP UI
- 3 bibliotecas SDK (React, Java, .NET) publicadas em registries internos
- 3 módulos de demo (1 MFE, 1 MS Java, 1 MS .NET)

**Integrações externas**
- CyberArk (OIDC para login, API de busca para PAP)
- CI/CD de cada repo (opcional, apenas para fallback em workloads sem HTTP)
- Mecanismo de distribuição de secrets da infra (env var, K8s Secret, Vault — a definir)

**Pontos de atenção técnica**
- Gestão de versão/compatibilidade entre SDK e AuthZ Service
- Estratégia de deploy do AuthZ Service (zero downtime é obrigatório — afeta todos os módulos)
- Observabilidade: métricas de cache hit ratio, latência de bulk fetch, volume de sync events

**Questões abertas para a TechSpec**
- Escolha de stack do AuthZ Service (Node, Go, Java?)
- Banco de dados (Postgres provavelmente, confirmar com infra)
- Mecanismo de distribuição inicial das chaves de módulo aos MSs (env var, K8s Secret, Vault)
- Formato do header de autenticação de sync (Bearer simples vs HMAC de body para replay protection)
- Janela de grace period de rotação (default 24h proposto)
- Formato exato do payload de sync (versionamento do schema)
- Política de retenção de logs de auditoria
- Mecanismo de invalidação ativa de cache (para future enhancement)

---

## Questões abertas

1. Qual o mecanismo padrão da infra para distribuir secrets aos MSs (env var via pipeline, K8s Secret, Vault, outro)?
2. O CyberArk suporta busca de usuários por API? Qual a latência esperada?
3. Existe registry interno para publicar os SDKs (npm privado, Maven interno, NuGet interno)?
4. Há padrão da casa para aplicações backend (stack, observabilidade, deploy)?
5. JWT do CyberArk tem TTL fixo ou configurável? Isso define a janela de staleness.
6. Existem workloads sem HTTP de saída (jobs batch isolados, funções serverless em VPC restrita) que exigirão o fallback via CI?

## Próximos passos

Para gerar a Especificação Técnica, use a skill `techspec-creator`.