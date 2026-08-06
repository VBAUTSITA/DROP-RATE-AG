# La dimensión temporal del agente de drops

> Cómo el agente pasa de *promediar todo el histórico* a **responder sobre un periodo**,
> y por qué la decisión central no fue añadir un filtro de fechas sino elegir contra qué
> se ancla la palabra "última".

---

## 1. El problema

La tabla `nr_cell_drops` tiene **18,816 muestras horarias** de 29 celdas, cubriendo
**mayo–junio 2026**. Antes de este cambio, las tres tools del agente leían *todas* las
filas de la celda y devolvían un solo número:

```java
List<NrCellDrops> rows = dropsRepo.findByCellNameOrderBySampleTimeAsc(cellName);
```

Eso tiene dos consecuencias que no se ven hasta que alguien decide algo con el resultado:

1. **Dilución.** Una celda que se degradó los últimos tres días del export queda enterrada
   bajo dos meses de operación normal. El promedio dice "OK" y la celda está caída.
2. **Preguntas imposibles.** "¿Cómo estuvo la última semana?", "¿empeoró después del 15?",
   "¿cuándo empezó?" no tenían forma de responderse. La tool no aceptaba fechas.

---

## 2. La decisión central: contra qué se ancla "última semana"

Esta es la parte que importa y la que es fácil equivocar.

**Los datos son un export histórico, no un feed en vivo.** El dataset termina el
**30 de junio de 2026**. Si el sistema corre en agosto y "última semana" se resuelve
contra el reloj del sistema:

```
ventana solicitada:  2026-08-05 .. 2026-08-12
datos disponibles:   2026-05-01 .. 2026-06-30
intersección:        ∅
```

El agente responde **"no hay datos para esa celda"** — sobre una celda que tiene miles de
muestras. Es la peor clase de error: una respuesta equivocada que parece correcta y que
nadie va a cuestionar.

**Por eso `TimeRangeParser` ancla las expresiones relativas a `MAX(sample_time)`, no a
`now()`.** "Última semana" significa *la última semana que efectivamente se midió*, que es
lo que quiere decir cualquiera que esté mirando este dataset.

```java
LocalDateTime end = bounds.end();          // MAX(sample_time), no LocalDateTime.now()
LocalDateTime start = end.minusWeeks(1);
```

Hay un test que fija exactamente este comportamiento
(`TimeRangeParserTest.relativePeriodsAnchorToTheDataNotToToday`). Si algún día los datos
pasan a ser live, ese test es el que hay que revisar primero.

---

## 3. Todo rango se recorta, y se avisa

Un rango pedido puede caer parcial o totalmente fuera de los datos. Las tres situaciones
se tratan distinto, y **ninguna en silencio**:

| Situación | Qué hace | Qué reporta |
|---|---|---|
| Cabe completo | lo usa tal cual | el periodo resuelto |
| Cae parcialmente afuera | lo recorta a los límites | `clamped=true` + "trimmed to available data" |
| Cae totalmente afuera | usa el dataset completo | "lies entirely outside the loaded data" |
| No se entiende la frase | usa el dataset completo | "could not interpret ..." |

Cada `TimeRange` lleva un `label` con lo que se resolvió, y el prompt del agente le obliga
a repetirlo al usuario:

```
- ALWAYS state the period the tool actually used. Each one reports it back;
  repeat it to the user. If the window was trimmed or could not be
  interpreted, say so plainly instead of presenting the numbers as if they
  answered the question exactly as asked.
```

El razonamiento: analizar en silencio un periodo distinto al pedido es indistinguible de
haber respondido bien, hasta que alguien toma una decisión encima. Es preferible una
respuesta menos limpia y honesta que una limpia y falsa.

---

## 4. Formatos que entiende el parser

Español e inglés, sin distinguir mayúsculas ni acentos.

| Forma | Ejemplos | Resuelve a |
|---|---|---|
| Vacío / todo | `""`, `todo`, `all`, `histórico` | dataset completo |
| Fecha ISO | `2026-06-15` | ese día completo (00:00 → 23:59:59) |
| Rango ISO | `2026-06-01 a 2026-06-15`, `... to ...`, `...` | inclusive, se ordena solo |
| Últimos N | `últimos 7 días`, `last 14 days`, `últimas 48 horas`, `last 2 weeks` | N unidades desde el fin de datos |
| Relativo | `última semana`, `last month`, `ayer`, `hoy`, `último día` | anclado al fin de datos |
| Mes calendario | `junio`, `june`, `mayo 2026` | ese mes, recortado a los datos |

Lo que **no** entiende: "el fin de semana largo", "durante el mantenimiento", "cuando
llovía". Esas caen al dataset completo con el aviso correspondiente. La cobertura del
parser es exactamente la de sus tests — no adivina.

---

## 5. Las tools

| Tool | Responde a | Reemplaza a |
|---|---|---|
| `getDataCoverage` | ¿qué periodo existe? | — (nueva, se llama primero) |
| `getCellDropSummaryForPeriod` | ¿cómo estuvo X en el periodo P? | `getCellDropSummary` |
| `getCellDailyTrend` | ¿**cuándo** cambió? | — (nueva) |
| `compareCellPeriods` | ¿empeoró entre A y B? | — (nueva) |
| `getWorstCellsForPeriod` | ¿peores celdas en P? | `getWorstCells` |

Las tools originales **siguen existiendo** y siguen promediando todo. Son la respuesta
correcta cuando nadie mencionó una fecha. El prompt decide:

```
- Time periods. getCellDropSummary, listAllCells and getWorstCells average
  the WHOLE history. The moment the user mentions a date, a month, or a
  relative window, switch to the period-aware tools.
```

### `getCellDailyTrend` — la que responde "cuándo"

Un promedio de dos meses no dice el día en que algo cambió. Esta tool agrupa por fecha:

```
Daily drop rate for ARR40312C1_Moran_Uribe — last 14 day(s) of data (up to 2026-06-30T23:00)
2026-06-17  drop=2.10%  releases=1204  dominant=RA Problem
2026-06-18  drop=2.35%  releases=1180  dominant=RA Problem
2026-06-19  drop=14.80% releases=1195  dominant=RA Problem   ← aquí
2026-06-20  drop=16.20% releases=1211  dominant=RA Problem
```

### `compareCellPeriods` — la causa dominante también cambia

Compara dos ventanas y reporta el delta. Lo que aporta de más: **avisa explícitamente si
cambió la causa dominante**.

```
Change: +12.40 percentage points -> WORSE
Dominant cause CHANGED: T310 Expiry -> RA Problem. A different failure
mode usually means a different fix.
```

Mirando solo el porcentaje eso se pasa por alto, y es justamente el dato que cambia el
plan de acción: T310 apunta a cobertura, RA Problem apunta a acceso — y, vía
`suggestPciFixForCell`, posiblemente a una colisión de PCI.

---

## 6. Cómo se conecta con el resto

La dimensión temporal no reemplaza nada; afina lo que ya existía.

```
"¿por qué cae ARR40312C1 y desde cuándo?"
  → getDataCoverage              ¿qué periodo existe?
  → getCellDailyTrend            saltó el 19 de junio
  → getCellDropSummaryForPeriod  causa dominante en esa ventana: RA Problem
  → getKnowledgeForCause         qué significa RA Problem
  → suggestPciFixForCell         ¿hay colisión de PCI detrás?
```

El paso temporal acota la ventana; el paso de PCI explica la causa dentro de ella. Antes
de esto, la causa dominante se calculaba sobre dos meses y podía no ser la que estaba
activa el día del problema.

---

## 7. Portabilidad a Oracle

Todo lo nuevo es JPQL, nada de SQL nativo:

```java
List<NrCellDrops> findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc(
        String cellName, LocalDateTime from, LocalDateTime to);

@Query("SELECT MIN(d.sampleTime) FROM NrCellDrops d")
LocalDateTime findEarliestSampleTime();
```

La query derivada rinde como `BETWEEN` y la proyección `MIN`/`MAX` es estándar. Funciona
igual sobre Oracle que sobre PostgreSQL, sin dialecto explícito.

El agrupamiento por día de `getCellDailyTrend` se hace **en Java**, no en SQL, precisamente
para no depender de funciones de fecha específicas del motor (`DATE_TRUNC` en PostgreSQL vs
`TRUNC` en Oracle). Con 29 celdas y muestras horarias el costo es despreciable.

---

## 8. Qué está probado y qué no

**Probado** — `TimeRangeParserTest`, 8 casos:

- el anclaje al dataset en vez de al reloj (el caso central)
- `últimos N días` en ambos idiomas
- rangos ISO explícitos, en cualquier orden
- fecha suelta → día completo
- nombre de mes → mes calendario recortado
- ventana totalmente fuera de datos → fallback avisado
- frase no interpretable → fallback avisado
- vacío / `todo` → dataset completo

**No probado:**

- Las tools contra un modelo real. Que el agente *elija* `getCellDropSummaryForPeriod`
  en vez de `getCellDropSummary` depende del prompt y de la descripción de la tool, y eso
  solo se valida ejecutándolo.
- Zonas horarias. Todo es `LocalDateTime`, sin `ZoneId`. Correcto mientras los datos y las
  preguntas vivan en la misma zona, que es el caso hoy.
- Volumen. El agrupamiento en memoria asume el tamaño actual del dataset. Con años de
  datos horarios convendría mover la agregación a SQL.

---

## 9. Si los datos pasan a ser live

Tres cosas hay que revisar, en este orden:

1. **El anclaje.** `TimeRangeParser.datasetBounds()` usa `MAX(sample_time)`. Con datos en
   vivo eso coincide con "ahora" y el comportamiento se vuelve el intuitivo — pero conviene
   decidirlo explícitamente, no heredarlo.
2. **`TimeRangeParserTest`.** Los tests fijan fechas de 2026; hay que reescribirlos contra
   un reloj inyectable en vez de constantes.
3. **`getDataCoverage`.** Su texto dice que los periodos relativos se resuelven contra el
   fin de los datos. Si eso deja de ser cierto, el mensaje miente.
