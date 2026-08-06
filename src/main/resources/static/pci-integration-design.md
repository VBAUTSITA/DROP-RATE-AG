# Integración del planner de PCI con el agente de drops

> Cómo el agente pasa de *detectar* drops a *identificar* y *proponer* una corrección de PCI,
> y cómo esa misma superficie de tools funciona contra el espejo local o contra el planner real.

---

## 1. El flujo

```
  usuario: "¿por qué se cae ARR40312C1?"
        │
        ▼
  diagnoseCell(celda)                      ← fan-out a todos los módulos + correlación
        │  drops: CRITICAL [HIGH_DROP, RACH_FAILURE]
        │  pci:   CRITICAL [PCI_COLLISION]
        │  → "la colisión de PCI es la causa raíz probable"  [confianza ALTA]
        ▼
  usuario: "dale, vamos por el PCI"
        │
        ▼
  tacklePciIssue(celda)
        │
        ├─ Paso 1 — IDENTIFICAR:  audit(celda)      ← barato
        │     · si está limpio → se corta acá, y eso también es un resultado
        │
        └─ Paso 2 — RE-PLANIFICAR: propose(celda)   ← caro, sólo si hay algo que corregir
              · PCI candidato + RSI + la traza de razonamiento del motor
              · NO escribe: aplicarlo es una acción humana en la herramienta
```

El orden es **determinista, no queda librado al modelo**. `tacklePciIssue` sólo pide una
propuesta si la auditoría encontró algo. Re-planificar una celda con identidad limpia sería
entregarle al usuario un cambio para aplicar que no arregla nada — y, como la auditoría es
barata y la propuesta no, además paga el motor de asignación para responder una pregunta que
nadie hizo.

---

## 2. El port y sus dos backends

```
                       ┌──────────────────────┐
   PciPlannerTools ───▶│   PciPlannerPort     │   plan · audit · auditNetwork · propose
   (4 @Tool)           └──────────┬───────────┘
   PciModule ─────────────────────┤
   OrchestratorTools ─────────────┤
                       ┌──────────┴───────────┐
                       ▼                      ▼
          LocalPciPlannerAdapter    RestPciPlannerAdapter
          espejo en PostgreSQL      planner real (JDK 8) por HTTP
          (default, corre hoy)      pci.planner.backend=rest
```

Cambiar de backend **no cambia ninguna firma de tool ni ninguna regla de correlación**: sólo
cambia qué bean levanta Spring. Cada respuesta dice de qué backend salió, porque un ingeniero
de RF no puede actuar sobre una celda viva sin saber quién contestó.

### Qué sabe hacer cada uno

| | Espejo local | Planner real |
|---|---|---|
| Detectar colisión / confusión / mod-3 | sí | sí (`auditarRed()`, con rejilla espacial) |
| Colisión de RSI | no | sí |
| Proponer PCI libre de conflictos | sí | sí |
| RSI de la propuesta | **no** — queda `null` y se avisa | sí (tabla de lookup) |
| Geometría RF (wedges, radio por TA) | **no** | sí (algoritmo de 12 pasos) |
| Multi-tecnología 3G/4G/5G | no (sólo el espejo 5G) | sí |

El adaptador local no disimula lo que no sabe: cada propuesta incluye entre sus advertencias
que el RSI es desconocido y que el candidato satisface las restricciones de identidad pero no
está optimizado por RF.

---

## 3. Por qué `audit` y `propose` están separados

`PciModule.analyze()` corre en **cada** `diagnoseCell`, junto a todos los demás módulos. Tiene
que responder en milisegundos. Calcular una propuesta significa correr el motor de asignación
sobre la red circundante: segundos, más una consulta a Oracle y hasta ~14 MB serializados a
Nashorn. Meter eso adentro de un fan-out haría inusable el diagnóstico cruzado.

Por eso el módulo **sólo audita, nunca propone**, y la propuesta es un paso aparte que dispara
el usuario.

---

## 4. Ninguna tool escribe en la red

No existe `apply(...)` en el port, y por lo tanto no hay ninguna `@Tool` que pueda llegar a
escribir. Es deliberado: cualquier método anotado `@Tool` lo puede invocar el modelo por su
cuenta a partir de una frase del chat, y el camino de escritura es exactamente lo que no puede
tener esa propiedad.

Tampoco hace falta: la herramienta de planificación **ya separa proponer de guardar**.
`POST /recursos/autoAsignar` recalcula en memoria y vuelve a mostrar la pantalla de revisión
sin persistir; recién `POST /recursos/asignar` guarda. El gate humano ya existe en ese código —
esta integración lo respeta en lugar de rodearlo.

`PciProposal.applied()` devuelve `false` siempre, para que un llamador no pueda confundir una
propuesta con un cambio aplicado.

---

## 5. Dos hallazgos del código del planner

### 5.1 El motor no se auto-excluye del pool

`planner_5g.js` no filtra por `cellname` en ninguna parte (`politica_cosito` resuelve co-sitio,
que es otro problema). En el flujo original eso nunca importó, porque se planifican celdas
**nuevas** que todavía no están en `tm_conf_actual_celda_5g` — de ahí el
`dto.setPhycellid(null); // celda nueva, todavia sin PCI` en `PciRsiAsignacionServiceImpl`.

El caso del agente es otro: una celda **viva**, que sí está en esa tabla. Si no se la saca del
pool de existentes, el motor ve su propio PCI actual como vecina y se auto-descarta el valor o
reporta colisión consigo misma. La exclusión va del lado Java, en el servicio de la API — ver
`integration/tracker/PciApiSupportService.java`.

### 5.2 La auditoría ya está escrita, falta el puente

`planner_5g.js` exporta `auditarRed()`, que escanea por carrier — con rejilla espacial, no
O(n²) — colisiones de PCI, confusiones mod-3 y colisiones de RSI entre celdas existentes, con
distancia y peso por conflicto. El agente no necesita un detector nuevo: necesita acceso al que
ya existe.

Lo que falta es el puente en `PlannerEngine`, que hoy sólo envuelve `planificarLote`. Con un
método análogo que llame a `PCIPlanner.auditarRed(...)`, un job programado materializa los
conflictos en una tabla y el endpoint de auditoría pasa a ser un `SELECT`.

---

## 6. Un cambio en el correlacionador

Al existir un backend remoto aparece un estado que antes no era posible: **el dominio de PCI no
contesta**. Un `ModuleFinding` de un backend caído no trae tags, y las reglas 3 y 4 leían la
ausencia de tags de conflicto como "el plan de PCI está limpio" — o sea que una caída del
planner se habría reportado como identidad descartada, en una regla que además baja la
confianza del veredicto.

Ahora el correlacionador distingue `pciConfirmedClean` de `pciUnchecked`, y hay una regla 4b
que dice explícitamente que la identidad quedó abierta y sugiere repetir el diagnóstico cuando
el backend vuelva. Cubierto por
`correlator_treatsAnUnreachablePciBackendAsUnknown_notAsCleanPci`.

---

## 7. Superficie de tools

| Tool | Costo | Qué hace |
|---|---|---|
| `getCellPci(cell)` | ms | PCI/RSI/carrier/azimut actuales |
| `auditCellPci(cell)` | ms | IDENTIFICAR: conflictos de una celda |
| `auditNetworkPci()` | ms | IDENTIFICAR: conflictos de toda la red |
| `proposePciReplan(cell)` | segundos | RE-PLANIFICAR: candidato + traza de razonamiento |
| `tacklePciIssue(cell)` *(orquestador)* | ms + segundos | la secuencia identificar → re-planificar |

La traza de razonamiento es el punto de la propuesta. Un entero pelado no es accionable: antes
de tocar una celda viva hay que poder ver qué PCIs quedaron excluidos y por qué. El motor real
ya la genera (`AUDITORIA_PCI` por celda); el backend local reconstruye una equivalente a partir
de su propia búsqueda.
