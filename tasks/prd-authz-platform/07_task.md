---
status: pending
parallelizable: false
blocked_by: [6.0]
---

<task_context>
<domain>backend/authz-service/catalog</domain>
<type>implementation</type>
<scope>core_feature</scope>
<complexity>high</complexity>
<dependencies>database</dependencies>
<unblocks>"8.0,15.0,19.0"</unblocks>
</task_context>

# Tarefa 7.0: Módulo `catalog` — endpoint `POST /v1/catalog/sync` idempotente com prefix binding

## Relacionada às User Stories

- RF-03 (sync) e suporte direto a RF-02, RF-04, RF-16

## Visão Geral

Implementar o coração da plataforma: o endpoint de sincronização de catálogo. Recebe permissões de um módulo, reconcilia o estado (adiciona, atualiza descrições, marca removidas como `DEPRECATED` com sunset, mantém `last_heartbeat_at`), com transação atômica e idempotência via hash do payload. Rejeita 403 para permissões fora do `allowedPrefix` da chave (RF-17 binding).

## Requisitos

- Endpoint `POST /v1/catalog/sync` autenticado por bearer module (filter da 6.0)
- DTO `SyncRequest`: `{ moduleId, schemaVersion, payloadHash, permissions: [{code, description}] }`
- DTO `SyncResponse`: `{ catalogVersion, added, updated, deprecated, changed: boolean }`
- `SyncCatalogHandler`:
  - Compara `payloadHash` com último sync gravado em `sync_event` para o módulo
  - Se igual → atualiza `last_heartbeat_at` e retorna `changed: false`
  - Se diferente → diff: insere novas `ACTIVE`, atualiza descrições, marca removidas como `DEPRECATED` com `sunset_at = now() + 30d`
- Validação `PermissionPrefixValidator` aplicada antes de qualquer escrita
- Operação atômica (`@Transactional`)
- Lock otimista por módulo (sem race em réplicas concorrentes): `SELECT ... FOR UPDATE` na linha do `module`
- Métrica `authz_catalog_sync_total{module,result=added|updated|deprecated|idempotent}`
- Atualizar `module.last_heartbeat_at` em todo sync (mesmo idempotente)

## Arquivos Envolvidos

- **Criar:**
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/api/CatalogController.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/api/dto/SyncRequest.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/api/dto/SyncResponse.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/api/dto/PermissionDeclaration.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/application/SyncCatalogCommand.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/application/SyncCatalogHandler.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/Permission.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/PermissionStatus.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/PermissionRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/infra/JpaPermissionRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/infra/PermissionJpaEntity.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/SyncEvent.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/domain/SyncEventRepository.java`
  - `apps/authz-service/src/main/java/com/platform/authz/catalog/infra/JpaSyncEventRepository.java`
  - `apps/authz-service/src/test/java/com/platform/authz/catalog/application/SyncCatalogHandlerTest.java`
  - `apps/authz-service/src/test/java/com/platform/authz/catalog/integration/CatalogSyncIntegrationTest.java`
- **Modificar:**
  - `apps/authz-service/src/main/resources/db/migration/V1__init_schema.sql` (adicionar `sync_event` se não estiver — TechSpec menciona separado de audit; se for a mesma tabela, ajustar)
- **Skills para consultar durante implementação:**
  - `java-architecture` — handler CQRS type-safe
  - `java-code-quality` — Bean Validation no DTO, records
  - `java-testing` — Testcontainers + cenários de concorrência
  - `common-restful-api` — versionamento + ProblemDetails
  - `common-roles-naming` — formato de permissão

## Subtarefas

- [ ] 7.1 `SyncRequest`/`SyncResponse` DTOs + validações (não-vazio, regex de code)
- [ ] 7.2 `PermissionPrefixValidator` aplicado: rejeitar 403 antes de tocar BD
- [ ] 7.3 `SyncCatalogHandler` com diff lógico (added/updated/deprecated)
- [ ] 7.4 Persistir `SyncEvent` com `payload_hash`, `permission_count`, `occurred_at`
- [ ] 7.5 Atualizar `module.last_heartbeat_at`
- [ ] 7.6 Lock otimista no módulo
- [ ] 7.7 Métrica + log estruturado
- [ ] 7.8 Testes unitários: idempotência, added, updated, deprecated, prefix violation, concorrência simulada
- [ ] 7.9 Teste de integração end-to-end (filter + handler + db)

## Sequenciamento

- Bloqueado por: 6.0
- Desbloqueia: 8.0 (libera trabalho em paralelo no JWT), 15.0/19.0 (SDKs precisam do contrato), 17.0/21.0 (auto-registro)
- Paralelizável: Não

## Rastreabilidade

- Esta tarefa cobre: RF-03; suporte a RF-02 (consome o YAML carregado), RF-16
- Evidência esperada: sync inicial cria permissões; sync repetido retorna `changed:false`; sync com prefix errado retorna 403

## Detalhes de Implementação

**Algoritmo de diff:**
1. Carregar permissões atuais do módulo (status `ACTIVE` ou `DEPRECATED`)
2. Construir map por `code`
3. Para cada permissão no payload:
   - Se não existe → INSERT como `ACTIVE`
   - Se existe e descrição mudou → UPDATE (mantém status)
   - Se existe e estava `DEPRECATED` → reativa para `ACTIVE`, limpa `sunset_at`
4. Para cada permissão atual não presente no payload e ainda `ACTIVE` → marca `DEPRECATED` com `sunset_at = now() + 30d`

**Convenções da stack:**
- `@Transactional` no handler (`java-architecture`)
- DTOs como records, validados com Bean Validation (`java-code-quality`)
- Testes nomeados `methodName_Condition_ExpectedBehavior` (`java-testing`)
- Permissões em `snake_case_with_dots`, aceitas regex `^[a-z][a-z0-9_]{0,30}(\\.[a-z][a-z0-9_]{0,30}){2,}$` (`common-roles-naming`)

## Critérios de Sucesso (Verificáveis)

- [ ] Testes unitários passam (`SyncCatalogHandlerTest`)
- [ ] Teste integração passa (`CatalogSyncIntegrationTest`)
- [ ] cURL: primeiro sync com payload válido → 200 com `added > 0, changed: true`
- [ ] cURL: segundo sync com mesmo payload → 200 com `changed: false`
- [ ] cURL: sync com permissão fora do prefixo → 403 ProblemDetail
- [ ] cURL: sync sem `description` → 422 ProblemDetail
- [ ] Métrica `authz_catalog_sync_total` incrementa por result
- [ ] `module.last_heartbeat_at` é atualizado mesmo em sync idempotente
