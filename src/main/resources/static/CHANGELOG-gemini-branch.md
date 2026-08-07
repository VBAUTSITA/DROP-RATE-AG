# Changelog de la rama Gemini — para replicar en la rama OpenRouter

> Registro de **todo** lo cambiado desde `master` hasta `claude/gemini-analyst-upgrades`,
> agrupado por tema y con el motivo de cada decisión.
> Sirve para dos cosas: aplicar los cambios a mano en la máquina de oficina, y **portar
> después lo que sí aplica a la rama OpenRouter / PostgreSQL** del PC personal.
> Agosto 2026.

---

## 0. Cómo leer esto

Cada bloque marca si aplica a la otra rama:

| Marca | Significado |
|---|---|
| 🔵 **PORTABLE** | aplica igual en OpenRouter/PostgreSQL — cópialo tal cual |
| 🟡 **ADAPTAR** | la idea aplica, el código cambia |
| 🔴 **SOLO GEMINI/ORACLE** | no lo lleves; es específico de este entorno |

Linaje de ramas:

```
master (PostgreSQL + OpenRouter)
  └── claude/gemini-native-migration      Oracle + Gemini nativo   ← validado en oficina
        └── claude/gemini-pci-office      + módulo PCI
              └── claude/gemini-pci-timerange   + dimensión temporal
                    └── claude/gemini-analyst-upgrades   + las 5 mejoras de analista
```

---

## 1. Portabilidad a Oracle 🔴 SOLO ORACLE

**Motivo:** la máquina de oficina no tiene ni tendrá PostgreSQL.

| Archivo | Cambio |
|---|---|
| `pom.xml` | + `ojdbc11` (conviven los dos drivers; la URL decide) |
| `agent/entity/TelecomCommand.java` | `columnDefinition="TEXT"` → `@Column(length=4000)` |
| `logging/AgentLog.java` | 2 campos `TEXT` → `@Lob` |
| `eval/EvalCase.java` | 3 campos `TEXT` → `@Lob` |
| `eval/EvalResult.java` | 1 campo `TEXT` → `@Lob` |
| `application.properties` | URL/driver Oracle; **borrar** `spring.jpa.database-platform` |

**Dos trampas que costaron tiempo:**

1. `TEXT` no existe en Oracle → `ORA-00902`. Se usa `@Lob` (no la palabra `CLOB` a mano)
   para que Hibernate emita `text` en PostgreSQL y `clob` en Oracle desde el mismo mapeo.
2. **`TelecomCommand.description` es la excepción y va a `VARCHAR2(4000)`, no a `@Lob`.**
   `TelecomCommandRepository.findByKeyword` hace `LOWER(...) LIKE` sobre esa columna, y
   Hibernate rechaza eso contra un path CLOB. Esto rompe el arranque con un error de
   validación de query que no menciona CLOB en ningún lado.
3. **Dejar `PostgreSQLDialect` puesto** hace que Hibernate genere SQL de Postgres contra
   Oracle. Hibernate 6 autodetecta el dialecto; hay que **borrar** la línea, no cambiarla.

---

## 2. Gemini nativo 🔴 SOLO GEMINI

**Motivo:** OpenRouter está bloqueado por seguridad corporativa; Gemini está autorizado.

| Archivo | Cambio |
|---|---|
| `pom.xml` | BOM langchain4j `1.0.0-beta1` → `1.18.1`; **quitar** `langchain4j-open-ai`; + `langchain4j-google-ai-gemini`; + `langchain4j-http-client-jdk`; **quitar** `langchain4j-spring-boot-starter` |
| `config/ChatModelConfig.java` | **NUEVO** — único lugar donde se construye el modelo |
| `config/AiConfig.java` | recibe `ChatModel` inyectado |
| `drops/DropAgentConfig.java` | idem |
| `guardrail/LlmGuardrail.java` | idem + API 1.x |
| `service/ChatService.java` | idem + API 1.x |
| `config/RagConfig.java` | `OpenAiEmbeddingModel` → `GoogleAiEmbeddingModel` |

### 2.1 Migración de API langchain4j 1.x 🔵 PORTABLE

Esto **sí** hay que hacerlo en la rama OpenRouter cuando subas de versión:

| Antes | Después |
|---|---|
| `ChatLanguageModel` | `ChatModel` |
| `AiServices.chatLanguageModel()` | `.chatModel()` |
| `model.generate(String)` | `model.chat(String)` |
| `model.generate(List<ChatMessage>)` | `model.chat(...)` |
| `response.content().text()` | `response.aiMessage().text()` |

### 2.2 Los cuatro problemas que aparecieron en orden

Cada uno tapaba al siguiente. Documentados porque cualquiera que repita esto los va a encontrar:

**a) `thought_signature` — el problema central 🔴**

Los modelos Gemini "thinking" (2.5+, 3.x) emiten un `thought_signature` con cada
`functionCall` que hay que devolver en el turno siguiente. El endpoint compatible con
OpenAI **no tiene campo donde llevarlo**, así que el tool loop falla en la segunda vuelta:

```
400 INVALID_ARGUMENT: Function call is missing a thought_signature in functionCall parts
```

Cambiar de modelo no lo arregla: los que evitarían la firma (`2.5-flash`, `2.5-flash-lite`)
dan `404 no longer available to new users`, y el que sí funciona (`3.6-flash`) es thinking.

**Usar el módulo nativo tampoco basta por sí solo.** Hacen falta las tres piezas juntas:

```java
.thinkingConfig(GeminiThinkingConfig.builder().includeThoughts(true).build())
.returnThinking(true)
.sendThinking(true)
```

Si falta una, la firma se pierde y vuelve el mismo 400. Fallback: `gemini.thinking=off`
(pone `thinkingBudget=0`, el modelo no razona y no genera firma).

**b) El proxy corporativo, dos veces 🟡 ADAPTAR**

El módulo Gemini usa `java.net.http.HttpClient` (`langchain4j-http-client-jdk`), no OkHttp.
Ese cliente **no toma `-Dhttps.proxyHost`** por sí solo. `ChatModelConfig` le pasa un
`ProxySelector` explícito.

Diagnóstico que costó varias vueltas — cada herramienta tiene su propia idea de "la red":

| Herramienta | Usa |
|---|---|
| Edge / Chrome | proxy de Windows (WinINET) |
| `Test-NetConnection` | socket TCP crudo — ignora proxies |
| `curl.exe` | solo variables `HTTP_PROXY` |
| Java / OkHttp | propiedades `-Dhttps.proxyHost` |
| **`java.net.http.HttpClient`** | **nada, salvo ProxySelector explícito** |
| Python `requests` | **sí lee el registro de Windows** |

Por eso Python daba 200 y Java timeout. El comando que resolvió todo:
```python
import urllib.request; print(urllib.request.getproxies())
```

**c) `langchain4j-spring-boot-starter` rompe el arranque 🟡 ADAPTAR**

Al subir a 1.18.1 aparece un `RagAutoConfig` que construye un `ContentRetriever` a partir
del `EmbeddingStore`. Con el store nulo (Oracle) falla:

```
Error creating bean 'contentRetriever' ... embeddingStore cannot be null
```

El proyecto **nunca usó** ese starter — no hay `@AiService` ni propiedades `langchain4j.*`,
todos los beans se construyen a mano. Se quita y no se pierde nada.

**d) Propiedades obligatorias sin default 🔵 PORTABLE (el principio)**

`gemini.api-key` y `gemini.model` **no tienen default a propósito**: la app debe fallar al
arrancar antes que adivinar un destino de salida. La primera versión tenía
`llm.provider=openrouter` por default, y eso significaba que un `application.properties`
sin esa línea llamaba a OpenRouter solo — exactamente lo que había disparado una alerta de
seguridad. Un typo en el nombre del proveedor hacía lo mismo en silencio.

**El principio sí es portable:** nunca dejar que un default elija a dónde sale el tráfico.
En esta rama además se quitó `langchain4j-open-ai` del classpath, así la llamada es
imposible de construir, no solo improbable.

---

## 3. Módulo PCI 🔵 PORTABLE

**Motivo:** conectar el diagnóstico de drops con la corrección de identidad.

15 archivos nuevos (mínimo verificado, sin orquestador ni agente PCI separado):

```
pci/entity/PciCell.java          pci/planner/CellPlan.java
pci/entity/PciNeighbor.java      pci/planner/PciAudit.java
pci/repository/PciCellRepository.java      pci/planner/PciProposal.java
pci/repository/PciNeighborRepository.java  pci/planner/PciPlannerPort.java
pci/PciConflict.java             pci/planner/PciPlannerUnavailableException.java
pci/PciAnalysisTool.java         pci/planner/LocalPciPlannerAdapter.java
pci/PciPlannerTools.java
pci/PciTrackWorkflow.java
pci/PciDataLoader.java
```

Ediciones: `guardrail/InputGuardrail.java` (+palabras PCI al whitelist),
`drops/DropAnalysisTool.java` (+tool `suggestPciFixForCell`),
`drops/DropRateAgent.java` (+instrucciones), `application.properties`
(+`pci.planner.backend=local`).

**Decisiones de diseño que hay que preservar al portar:**

- **La secuencia identificar → re-planificar es determinista**, no la encadena el modelo.
  Solo se calcula una propuesta si la auditoría encontró algo. Una celda con identidad
  limpia no genera un cambio que aplicar, y la auditoría es barata mientras la propuesta no.
- **`PciTrackWorkflow` está extraído, no duplicado.** Lo llaman el agente de drops y el
  orquestador; si se copiaba, en tres meses darían respuestas distintas para la misma celda.
- **Ningún método del port escribe en la red.** `propose` es siempre simulación; aplicar un
  PCI queda como acción humana en la herramienta de planificación. No hay `apply()` y ningún
  `@Tool` puede alcanzar uno.
- **El adaptador local no disimula lo que no sabe:** no modela RSI ni geometría RF, y cada
  propuesta lo dice. Responde correctamente a "qué PCIs están libres", no a "cuál es el mejor".

**Sin el whitelist del guardrail el módulo es invisible:** la pregunta se bloquea antes de
llegar al agente.

---

## 4. Dimensión temporal 🔵 PORTABLE

**Motivo:** el agente promediaba 18,816 muestras horarias en un solo número. Una celda
degradada hace tres días quedaba enterrada bajo dos meses de operación normal.

| Archivo | Cambio |
|---|---|
| `drops/timerange/TimeRange.java` | **NUEVO** — ventana resuelta + label + flag de recorte |
| `drops/timerange/TimeRangeParser.java` | **NUEVO** — parseo de frases ES/EN |
| `drops/repository/NrCellDropsRepository.java` | +query `Between`, +`MIN`/`MAX` de `sampleTime` |
| `drops/DropAnalysisTool.java` | +5 tools |
| `drops/DropRateAgent.java` | +instrucciones |

**La decisión central, y la que hay que copiar sin pensarlo dos veces:**

Los datos son un export histórico (mayo–junio 2026). Si "última semana" se resuelve contra
`now()`, la ventana cae en un vacío y el agente responde **"no hay datos"** para una celda
con miles de muestras. Es la peor clase de error: una respuesta equivocada que parece
correcta.

`TimeRangeParser` ancla las frases relativas a **`MAX(sample_time)`**, no al reloj. Hay un
test que fija ese comportamiento (`relativePeriodsAnchorToTheDataNotToToday`).

> Si algún día los datos pasan a ser live, ese test es lo primero que hay que revisar —
> junto al texto de `getDataCoverage`, que afirma explícitamente que los periodos relativos
> se anclan al fin de los datos. Si eso deja de ser cierto, el mensaje miente.

Todo rango se recorta a los límites del dataset **avisando**. Frase no interpretada → dataset
completo, también avisando. El prompt obliga al agente a repetir el periodo que la tool
realmente usó.

Detalle de portabilidad: el agrupamiento por día se hace **en Java, no en SQL**, para no
depender de `DATE_TRUNC` (PostgreSQL) vs `TRUNC` (Oracle).

Documentado en detalle en `time-dimension-design.md`.

---

## 5. Las 5 mejoras de analista 🔵 PORTABLE

**Motivo:** revisando una salida real del agente, cinco cosas que un analista de RAN
rechazaría. Ninguna se arregla con más tools.

### 5.1 Agregación por sitio — el mayor salto de valor

| Archivo | Cambio |
|---|---|
| `drops/metrics/DropTotals.java` | **NUEVO** — matemática de contadores extraída |
| `drops/SiteAnalysisTool.java` | **NUEVO** — `getWorstSites`, `getSiteDropSummary` |
| `drops/repository/NrCellDropsRepository.java` | +3 queries de sitio |
| `drops/DropAgentConfig.java` | `.tools(tools, siteTools)` |

**El hallazgo:** las 9 "peores celdas" que reportaba el agente son en realidad **5 sitios**,
y **4 de los 5 tienen dos sectores** fallando con la misma causa dominante:

| Sitio | Sectores |
|---|---|
| `MBTS_AR4031_MORAN_URIBE` | ARR40312C1 (30,13%) + ARR40311C1 (22,11%) |
| `MBTS_AR3889_MELGAR` | ARR38893C1 (25,69%) + ARR38892C1 (16,38%) |
| `MBTS_AR1891_JM_CUADROS` | ARR18913C1 (23,24%) + ARR18911C1 (15,34%) |
| `MBTS_AR3993_PUENTE_QUINONES` | ARR39931C1 (12,79%) + ARR39933C1 (12,74%) |
| `MBTS_AR3936_RESIDENCIAL_LA_LOMADA` | ARR39364C1 (12,09%) |

Un sector solo apunta a ese sector (RF, vecinos, su PCI). Dos o tres sectores del mismo
sitio fallando igual apunta a algo compartido: transporte, banda base, sincronización,
alimentación, licencia. Son equipos distintos y arreglos distintos.

**El flag exige misma causa dominante, no solo coincidencia de sitio.** Dos sectores
fallando por razones distintas es casualidad, no causa compartida.

Y el flag se reporta como **hipótesis**: este módulo lee contadores PM, no alarmas ni KPIs
de transporte ni interferencia UL. Puede decir "estos sectores fallan juntos", nunca "el
transporte está mal". Un agente que exagere esto manda gente al equipo equivocado.

### 5.2 Ligar ambos lados del conflicto PCI

`pci/PciTrackWorkflow.java` — cuando hay colisión o confusión, reporta el drop rate de
**ambas** celdas.

`ARR40312C1` (30,13%) colisiona en PCI 168 con `ARR39931C1` (12,79%). Antes se reportaban
como dos problemas independientes → dos tickets, y después del re-plan nadie verificaba si
la segunda mejoró. **Es una falla con dos víctimas.**

Solo aplica a colisión y confusión. El mod-3 degrada calidad pero no hace indistinguibles
dos celdas.

### 5.3 Tasas en vez de totales

`drops/metrics/DropTotals.java` + `buildSummary` en `DropAnalysisTool.java`.

> Liberaciones totales: **57.234.077**

Es la suma de 672 muestras horarias. Ningún analista usa ese número, y esconde lo único que
importa: si el 30% es estable desde mayo o si saltó hace tres días. Ahora se reporta
**liberaciones/hora y anormales/día** primero; los totales quedan al final "for audit".

Detalle: `spanHours()` usa el **conteo de muestras**, no el span de reloj. Un hueco en el
export inflaría todas las tasas al reducir el divisor.

### 5.4 Historia de PCI — lo que convierte hipótesis en evidencia

| Archivo | Cambio |
|---|---|
| `pci/entity/PciChange.java` | **NUEVO** — log de cambios |
| `pci/repository/PciChangeRepository.java` | **NUEVO** |
| `pci/PciTimelineCorrelator.java` | **NUEVO** — el correlador |
| `pci/PciDataLoader.java` | +`seedChangeHistory()` |
| `pci/PciTrackWorkflow.java` | +bloque "Timeline check" |

**El problema que resuelve:** el agente decía *"la colisión de PCI está provocando los
fallos"*. Una colisión que lleva dos años puesta **no puede** causar una degradación que
empezó el martes — es una condición preexistente, y algo más cambió. Reportarla como causa
raíz manda a alguien a cambiar un PCI mientras la falla real se queda.

Tres veredictos:

| Veredicto | Significado |
|---|---|
| **SUPPORTED** | el conflicto apareció en los 7 días previos al salto |
| **PRE-EXISTING** | el conflicto es más viejo que la degradación — no la explica |
| **UNKNOWN** | sin historia de cambios, o sin salto detectable |

**La semilla incluye los dos casos a propósito:** `ARR40312C1` tomó el PCI 168 el
**2026-06-18** (dentro de la ventana observada → correlacionable), mientras el mod-3 de
`ARR18911C1` y la confusión de `ARR39092C1` son de 2023–2024 (preexistentes). Un agente que
reporte todo conflicto como causa de toda degradación no está diagnosticando, y la única
forma de ver la diferencia es tener ambos casos presentes.

Se usa un **log de cambios** y no `valid_from`/`valid_to` porque la pregunta es "cuándo
apareció este valor", no "cuál era el plan en la fecha X".

La detección del salto es un **test de escalón simple** (≥5 puntos sobre la media de 5 días
previos), a propósito: el operador tiene que poder verificar el veredicto mirando la serie
diaria, y un umbral que ve gana a un modelo que no ve.

### 5.5 RAG en memoria — restaurar el fundamento 🟡 ADAPTAR

`config/RagConfig.java` + `knowledge/KnowledgeIngestionService.java`.

En Oracle `RagConfig` devolvía `null` y **el RAG quedaba apagado**. Consecuencia real que se
vio en una salida del agente: todo el consejo experto (N310/N311, umbrales B1/B2,
`prach-ConfigIndex`, VSWR, tilts) **no salía de ninguna tool** — era recall del modelo,
indistinguible de un consejo inventado. La capa diseñada específicamente contra la
alucinación estaba desactivada justo en el entorno de uso.

Ahora cae a `InMemoryEmbeddingStore`. Son 23 chunks: los vectores en memoria cuestan nada y
se re-embeben en cada arranque.

Detalle necesario: `KnowledgeIngestionService` consultaba `telecom_knowledge` por
`JdbcTemplate` para saber si ya había ingerido. Con store en memoria esa tabla no existe y
la guarda abortaba una ingesta que sí era necesaria. Ahora detecta
`instanceof InMemoryEmbeddingStore` y siempre ingiere.

**En la rama OpenRouter esto no hace falta** — pgvector funciona. Pero la lección sí: si el
RAG está apagado, todo consejo del agente es improvisación con formato de fuente.

---

## 6. Tests

| Test | Cubre | Casos |
|---|---|---|
| `TimeRangeParserTest` | anclaje al dataset, recorte, frases no interpretables | 8 |
| `DropTotalsTest` | tasas, causa dominante, severidad, entrada vacía | 6 |
| `OrchestrationLogicTest` | reglas de correlación (venía de la rama PCI) | 13 |

**27 de 28 pasan.** El que falla es `com.example.demo.RanParameterCopilotV2ApplicationTests`,
scaffolding preexistente que vive fuera del paquete `com.ranadvisor` y falla igual en
`master`. No se tocó.

---

## 7. Lo que NO se hizo, y por qué

Documentado para no re-litigarlo:

- **No se agregaron 1000 tools.** Cada tool diluye la precisión con que el modelo elige. Con
  ~10 acierta; con 40 empieza a llamar la equivocada. Los huecos reales están en el modelo
  de datos, no en la cantidad de tools.
- **No se implementaron módulos de interferencia UL, cobertura, transporte, ni alarmas.**
  No es un problema de código: **son otros planos de medición**. Ningún número de tools sobre
  `nr_cell_drops` detecta un repetidor mal ajustado — eso necesita RSSI de uplink por PRB.
  El cuello de botella es el acceso a datos.
- **No se validó contra un modelo real desde este entorno.** Que el agente *elija*
  `getWorstSites` antes que `getWorstCells` depende del prompt y de la descripción de la
  tool. Eso solo se comprueba ejecutándolo en la red de la oficina.
- **No hay manejo de zonas horarias.** Todo es `LocalDateTime`. Correcto mientras datos y
  preguntas vivan en la misma zona.
- **El agrupamiento en memoria asume el tamaño actual del dataset.** Con años de datos
  horarios habría que mover la agregación a SQL.

---

## 8. Orden sugerido para portar a OpenRouter

1. **Migración de API langchain4j 1.x** (§2.1) — es el prerrequisito de todo lo demás.
2. **Quitar `langchain4j-spring-boot-starter`** (§2.2c) — o el arranque falla.
3. **Dimensión temporal** (§4) — portable tal cual, mayor valor por esfuerzo.
4. **Módulo PCI** (§3) — portable tal cual, incluido el whitelist del guardrail.
5. **`DropTotals` + tasas + sitios** (§5.1, §5.3) — portables tal cual.
6. **Historia de PCI** (§5.4) — portable tal cual.
7. **Ligar ambos lados** (§5.2) — portable tal cual.
8. **RAG**: en PostgreSQL no toques nada; pgvector ya funciona.
9. **No lleves:** driver Oracle, `@Lob`, `ChatModelConfig` de Gemini, `ProxySelector`,
   `thinkingConfig`, ni el fallback en memoria.
