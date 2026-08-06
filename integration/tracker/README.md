# Lado del planner: qué hay que agregar en la app `tracker`

Este directorio **no se compila** con el agente: es Java 8, pertenece a la otra aplicación
(`com.tracker.web.recursos`) y está acá sólo como referencia de lo que el agente espera del
otro lado. Copiar los archivos al proyecto `tracker` y ajustar paquetes/seguridad según su
convención.

## Por qué hay dos aplicaciones y no una

El motor de asignación evalúa `planner_{3g,4g,5g}.js` con **Nashorn**, que fue removido del
JDK a partir de la versión 15 — `PlannerEngine.init()` falla explícitamente al arrancar si no
lo encuentra. El planner por lo tanto está clavado a **JDK 8**. El agente corre en **Java 17**
sobre Spring Boot 3. No pueden compartir JVM, así que la unión es por HTTP.

## Endpoints que consume el agente

| Endpoint | Costo | Quién lo llama |
|---|---|---|
| `GET  /api/pci/plan/{cellname}` | ms | `getCellPci` |
| `GET  /api/pci/audit/{cellname}` | ms | `auditCellPci`, y **`PciModule` en cada diagnóstico** |
| `GET  /api/pci/audit` | ms | `auditNetworkPci` |
| `POST /api/pci/propose` | segundos | `proposePciReplan` (sólo a pedido del usuario) |

**No hay endpoint de escritura, y es a propósito.** Cualquier método anotado `@Tool` en el
agente puede dispararlo el modelo por su cuenta a partir de una frase del chat. Aplicar un PCI
se queda donde está hoy: en `POST /recursos/asignar`, con una persona en la pantalla. Notar que
el flujo actual ya separa proponer de guardar — `POST /recursos/autoAsignar` recalcula en
memoria y vuelve a renderizar `/asignar` sin persistir nada.

## Las dos piezas que no existen hoy

### 1. Re-planificar una celda viva (`replanificarCeldaExistente`)

`PciRsiAsignacionService.autoAsignar(List<Long>)` recibe ids de `RECURSOS_FORMULARIO`, o sea
**celdas nuevas**. El caso del agente es otro: una celda que ya está en la red y tiene drops.
Por eso hace falta un método hermano, en `PciApiSupportService.java`.

Ahí está el detalle crítico:

```java
red.getCeldas().removeIf(c -> cellname.equalsIgnoreCase(c.getCellname()));
```

`planner_5g.js` **no se auto-excluye**: no filtra por `cellname` en ningún lado (`politica_cosito`
resuelve co-sitio, que es otro problema). En el flujo actual eso nunca importó porque las celdas
a planificar todavía no están en `tm_conf_actual_celda_5g`. Con una celda viva, si no se la saca
del pool, el motor ve su propio PCI actual como vecina: se auto-descarta el valor o reporta
colisión consigo misma. Sin ese `removeIf` la primera propuesta que genere el agente sale mal.

### 2. Auditoría materializada

`PciModule.analyze()` corre en **cada** `diagnoseCell`, junto a todos los demás módulos, así que
`/api/pci/audit/{cellname}` tiene que responder en milisegundos. Correr el motor por celda no
sirve: cada corrida consulta la red existente y serializa hasta ~14 MB hacia Nashorn.

La buena noticia es que la lógica ya está escrita. `planner_5g.js` exporta `auditarRed()`, que
escanea por carrier — con rejilla espacial, no O(n²) — colisiones de PCI, confusiones mod-3 y
colisiones de RSI entre celdas existentes, y devuelve cada conflicto con su distancia y su peso.
El agente no necesita un detector nuevo: necesita **acceso al que ya existe**.

Falta el puente: `PlannerEngine` hoy sólo envuelve `planificarLote`. Su wrapper JS es

```js
var planificador = new PCIPlanner.Planificador(existentes, {});
var resultado = planificador.planificarLote(plan);
```

y hace falta un `auditarRed(...)` análogo que llame a `PCIPlanner.auditarRed(byCarrier, opts)`.
Con eso, un job programado vuelca los conflictos a una tabla (`PCI_CONFLICTOS_AUDIT`) y el
endpoint pasa a ser un `SELECT`.

## Mientras tanto

El agente arranca por defecto con `pci.planner.backend=local`, que responde desde el espejo
sembrado en PostgreSQL. El flujo completo (diagnóstico → identificar → proponer) funciona hoy,
y cada respuesta dice de qué backend salió. Cambiar a `rest` no toca ninguna tool: sólo cambia
qué implementación de `PciPlannerPort` levanta Spring.
