---
agent: agent
description: Generate C4 diagrams from a Feature Design Document (FDD).
---

Você DEVE usar a Skill `c4-diagram-creator`.

Extraia dos argumentos do comando: o caminho do arquivo FDD, a pasta de saída opcional e a flag opcional --no-images:
- Caminho do arquivo FDD (obrigatório)
- Pasta de saída (opcional, padrão: "docs/c4")
- Flag --no-images (opcional; se presente: pular a geração de PNG; se ausente: gerar imagens PNG por padrão)

Envie o seguinte prompt para o agente:

"Gere diagramas C4 a partir do Feature Design Document localizado em [FDD_FILE_PATH].

Pasta de saída: [OUTPUT_FOLDER]
Geração de PNG: [PNG_INSTRUCTION]

Execute seu workflow completo (Fases 1-6) seguindo todas as diretrizes internas.

LEMBRETES CRÍTICOS:
- Crie arquivos .puml separados usando a ferramenta Write para CADA diagrama (c1, c2, c3, c4) - isto é OBRIGATÓRIO
- Crie UM único arquivo .md somente com a análise (SEM código PlantUML dentro)
- Só gere diagramas quando houver informações suficientes no FDD - nunca invente
- [PNG_BEHAVIOR]

O RELATÓRIO deve incluir:
- Idioma detectado do FDD
- Lista explícita de TODOS os arquivos criados (.puml, .md e .png, se aplicável)
- Quantidade de diagramas gerados com breve justificativa
- Diagramas pulados com motivos específicos
- Verificação: 'Created N .puml files for N diagrams'
- Resultados da geração de PNG (se aplicável)"

Substitua [FDD_FILE_PATH] pelo caminho real do arquivo vindo dos argumentos do comando.
Substitua [OUTPUT_FOLDER] pela pasta de saída especificada ou "docs/c4" se não for fornecida.
Substitua [feature-name] pelo nome apropriado da feature extraído do nome do arquivo FDD.

Substitua [PNG_INSTRUCTION] e [PNG_BEHAVIOR] com base na flag --no-images:

Se a flag --no-images ESTIVER presente:
- [PNG_INSTRUCTION] = "DISABLED"
- [PNG_BEHAVIOR] = "Skip PNG generation (Phase 5.6) entirely"

Se a flag --no-images NÃO estiver presente (padrão):
- [PNG_INSTRUCTION] = "ENABLED"
- [PNG_BEHAVIOR] = "Execute Phase 5.6: Generate PNG images with automatic error correction (max 3 attempts per file)"