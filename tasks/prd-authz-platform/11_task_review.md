# Review — Task 11

## Status: ✅ APROVADO

## 1. Resultados da validação da definição da tarefa

### Correções validadas
1. **TTL do cache ≤ TTL do JWT**
   - `CacheConfig` agora valida o invariante no startup e falha rápido quando `authz.cache.user-permissions-ttl > authz.security.jwt-ttl`.
   - `AuthzCacheProperties` e `AuthzSecurityProperties` foram introduzidas como fonte tipada de configuração.
   - O requisito da task e da TechSpec ficou atendido.

2. **Cobertura para roles revogados e permissões `REMOVED`**
   - `PermissionQueryControllerIntegrationTest` agora seeda dados reais no banco e valida o filtro efetivo:
     - exclui `user_role.revoked_at != null`
     - exclui `permission.status = REMOVED`
     - mantém apenas `ACTIVE` e `DEPRECATED`
   - Isso corrige o problema anterior de apenas mockar dados já filtrados.

3. **Perf test isolado em profile `perf`**
   - `BulkFetchPerformanceTest` usa `@ActiveProfiles("perf")`.
   - `pom.xml` adiciona profile Maven `perf` com `failsafe`, enquanto o `surefire` padrão exclui `*PerformanceTest`.
   - `application-perf.yml` foi criado para o cenário.

### Alinhamento geral com a task
- Endpoint `GET /v1/users/{userId}/permissions` implementado.
- Handler com cache Caffeine e invalidação em assign/revoke implementado.
- DTO e controller aderentes ao contrato.
- Métricas `authz_bulk_fetch_seconds` e `authz_user_permissions_cache_hit_ratio` presentes.
- Autorização “próprio usuário ou `PLATFORM_ADMIN`” preservada.

## 2. Descobertas da análise de regras

### Skills aplicadas
- `java-production-readiness`
- `java-architecture`
- `java-dependency-config`
- `java-testing`
- `java-observability`
- `common-restful-api`
- `common-roles-naming`

### Conformidades observadas
- Controller fino delegando para handler.
- Query de leitura com `@Transactional(readOnly = true)`.
- Cache local com Caffeine e invalidação nas mutações.
- Profile dedicado para teste de performance.
- Testes de integração compatíveis com `Testcontainers(disabledWithoutDocker = true)`.

### Observações não bloqueantes
1. `@EnableCaching` está duplicado em `AuthzServiceApplication` e `CacheConfig`.
2. A property `spring.cache.caffeine.spec` em `application.yml` ficou redundante porque o `CacheManager` é customizado.
3. Continua existindo pequena inconsistência documental entre task/techspec sobre o nome da métrica de cache hit ratio; o código segue a task.

## 3. Resumo da revisão de código

### Build e testes executados
- `mvn -pl apps/authz-service test` ✅
- `mvn -pl apps/authz-service -Dtest=PermissionQueryControllerIntegrationTest test` ✅
- `mvn -pl apps/authz-service -Pperf verify` ✅

### Observação de ambiente
- Os testes com Testcontainers ficaram **skipped** por indisponibilidade de Docker no ambiente de validação, comportamento compatível com a configuração atual dos testes.

## 4. Lista de problemas endereçados e suas resoluções

1. **Alta — ausência de validação do TTL do cache contra o JWT**
   - **Status:** resolvido
   - **Resolução:** validação fail-fast no startup via `CacheConfig`.

2. **Média — falta de evidência real para revogados/`REMOVED`**
   - **Status:** resolvido
   - **Resolução:** teste de integração com seed real cobrindo os filtros SQL.

3. **Média — perf test sem isolamento em profile `perf`**
   - **Status:** resolvido
   - **Resolução:** profile Maven `perf` + `@ActiveProfiles("perf")` + configuração dedicada.

## 5. Confirmação de conclusão e prontidão para deploy

- **Pronto para deploy:** Sim
- **Task pode ser marcada como concluída:** Sim
- **Veredito final:** **✅ APROVADO**

## Recomendações finais

- Remover a duplicidade de `@EnableCaching` em uma próxima limpeza.
- Alinhar task e techspec quanto ao nome da métrica de hit ratio para reduzir ambiguidade documental.
