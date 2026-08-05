const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.author = "Planificacion y Optimizacion RAN";
pres.title = "Agente de analisis RAN";

// ---------------------------------------------------------------- paleta
const INK = "1A1A1A";       // titulos
const BODY = "333333";      // texto
const MUTED = "6B6B6B";     // pie / notas
const BOXLINE = "595959";   // borde de formas
const ARROW = "8C8C8C";     // flechas
const TBORDER = "BFBFBF";   // borde de tabla
const THEAD = "EDEDED";     // fondo encabezado de tabla
const ZEBRA = "F7F7F7";     // fila alterna
const F = "Arial";

// barra superior corporativa (unico color de la lamina)
const BAR = [
  { c: "A8C93B", w: 1.95 },
  { c: "E77B26", w: 3.01 },
  { c: "F2A81D", w: 2.99 },
  { c: "E23A26", w: 2.99 },
  { c: "79C3E4", w: 2.39 },
];

let pageNo = 1; // la portada es la lamina 1 (sin numero impreso)

function chrome(slide, withNumber) {
  let x = 0;
  BAR.forEach((seg) => {
    slide.addShape(pres.ShapeType.rect, {
      x: x, y: 0, w: seg.w, h: 0.17,
      fill: { color: seg.c }, line: { color: seg.c, width: 0 },
    });
    x += seg.w;
  });

  // logo Claro, chico, esquina inferior derecha
  slide.addShape(pres.ShapeType.ellipse, {
    x: 12.42, y: 6.62, w: 0.56, h: 0.56,
    fill: { color: "D8261C" }, line: { color: "D8261C", width: 0 },
  });
  slide.addText("Claro", {
    x: 12.42, y: 6.62, w: 0.56, h: 0.56,
    fontFace: F, fontSize: 9, bold: true, color: "FFFFFF",
    align: "center", valign: "middle", margin: 0,
  });

  if (withNumber) {
    slide.addText(String(pageNo), {
      x: 0.6, y: 6.85, w: 1.0, h: 0.3,
      fontFace: F, fontSize: 9, color: MUTED, align: "left", margin: 0,
    });
  }
}

function newSlide(title, lead) {
  pageNo += 1;
  const slide = pres.addSlide();
  chrome(slide, true);
  slide.addText(title, {
    x: 0.6, y: 0.42, w: 12.13, h: 0.5,
    fontFace: F, fontSize: 24, bold: true, color: INK,
    align: "center", valign: "middle", margin: 0,
  });
  if (lead) {
    slide.addText(lead, {
      x: 0.6, y: 1.02, w: 12.13, h: 0.42,
      fontFace: F, fontSize: 12.5, color: BODY,
      align: "left", valign: "middle", margin: 0,
    });
  }
  return slide;
}

function box(slide, x, y, w, h, text, opts) {
  const o = opts || {};
  slide.addShape(pres.ShapeType.rect, {
    x: x, y: y, w: w, h: h,
    fill: { color: o.fill || "FFFFFF" },
    line: { color: o.line || BOXLINE, width: o.lw || 1 },
  });
  slide.addText(text, {
    x: x + 0.08, y: y, w: w - 0.16, h: h,
    fontFace: F, fontSize: o.size || 11, bold: !!o.bold,
    color: o.color || BODY, align: o.align || "center",
    valign: "middle", margin: 0, lineSpacingMultiple: 0.92,
  });
}

function vArrow(slide, x, y, h) {
  slide.addShape(pres.ShapeType.line, {
    x: x, y: y, w: 0, h: h,
    line: { color: ARROW, width: 1.25, endArrowType: "triangle" },
  });
}

function hArrow(slide, x, y, w) {
  slide.addShape(pres.ShapeType.line, {
    x: x, y: y, w: w, h: 0,
    line: { color: ARROW, width: 1.25, endArrowType: "triangle" },
  });
}

function elbow(slide, x1, y1, x2, y2) {
  // tramo horizontal + bajada con punta
  slide.addShape(pres.ShapeType.line, {
    x: Math.min(x1, x2), y: y1, w: Math.abs(x2 - x1), h: 0,
    line: { color: ARROW, width: 1.25 },
  });
  vArrow(slide, x2, y1, y2 - y1);
}

function table(slide, rows, o) {
  slide.addTable(rows, {
    x: o.x, y: o.y, w: o.w, colW: o.colW, rowH: o.rowH,
    border: { type: "solid", color: TBORDER, pt: 0.5 },
    fontFace: F, fontSize: o.size || 11, color: BODY,
    valign: "middle", margin: [4, 6, 4, 6], autoPage: false,
  });
}

function head(cells) {
  return cells.map((t) => ({
    text: t,
    options: { bold: true, color: INK, fill: { color: THEAD }, valign: "middle" },
  }));
}

function row(cells, zebra) {
  return cells.map((t, i) => ({
    text: t,
    options: {
      fill: { color: zebra ? ZEBRA : "FFFFFF" },
      bold: i === 0 && cells.length > 2 ? true : false,
      color: BODY,
    },
  }));
}

// ================================================================ 1 portada
{
  const s = pres.addSlide();
  chrome(s, false);
  s.addText("Agente de análisis RAN", {
    x: 0.9, y: 2.35, w: 11.53, h: 0.8,
    fontFace: F, fontSize: 34, bold: true, color: INK, align: "center", margin: 0,
  });
  s.addText("De un asistente de drops a un diagnóstico integrado: drops, cobertura y PCI", {
    x: 0.9, y: 3.15, w: 11.53, h: 0.5,
    fontFace: F, fontSize: 16, color: BODY, align: "center", margin: 0,
  });
  s.addText("Propuesta de alcance y siguiente fase", {
    x: 0.9, y: 4.15, w: 11.53, h: 0.35,
    fontFace: F, fontSize: 13, color: BODY, align: "center", margin: 0,
  });
  s.addText("Planificación y Optimización RAN  ·  Agosto 2026", {
    x: 0.9, y: 4.5, w: 11.53, h: 0.35,
    fontFace: F, fontSize: 11.5, color: MUTED, align: "center", margin: 0,
  });
  s.addNotes(
    "Presento una propuesta de alcance, no un producto terminado. La primera parte ya está funcionando; " +
    "lo que traigo es cómo extenderla y qué necesito para hacerlo. Voy a ser explícito con qué existe hoy y qué es propuesta."
  );
}

// ================================================================ 2 problema
{
  const s = newSlide(
    "Cómo diagnosticamos una celda hoy",
    "Los datos que necesito para explicar una caída están en tres sistemas distintos y ninguno se habla con el otro."
  );

  box(s, 0.6, 1.9, 3.3, 0.9, "Contadores del OSS\nRetenibilidad, causas de liberación, accesibilidad", { size: 10.5 });
  box(s, 0.6, 2.95, 3.3, 0.9, "Herramienta de PCI / RSI\nConflictos de identidad entre celdas", { size: 10.5 });
  box(s, 0.6, 4.0, 3.3, 0.9, "Cobertura en el terreno\nNivel y calidad de señal por zona", { size: 10.5 });

  hArrow(s, 3.95, 2.35, 1.15);
  hArrow(s, 3.95, 3.40, 1.15);
  hArrow(s, 3.95, 4.45, 1.15);

  box(s, 5.2, 1.9, 2.9, 3.0, "Analista RAN\n\nExporta, cruza en planilla\ne interpreta", { size: 11.5 });

  hArrow(s, 8.15, 3.40, 0.75);

  box(s, 8.95, 1.9, 3.78, 3.0, "Conclusión\n\nSe apoya en la experiencia\nde quien la arma", { size: 11.5 });

  const caps = [
    ["Tiempo", "Horas por celda cuando el caso no es evidente."],
    ["Trazabilidad", "El razonamiento no queda registrado en ningún lado."],
    ["Consistencia", "Dos analistas pueden llegar a conclusiones distintas."],
  ];
  caps.forEach((c, i) => {
    const x = 0.6 + i * 4.14;
    s.addText(
      [
        { text: c[0] + "\n", options: { bold: true, color: INK, fontSize: 11.5 } },
        { text: c[1], options: { color: BODY, fontSize: 11 } },
      ],
      { x: x, y: 5.3, w: 3.85, h: 0.85, fontFace: F, align: "left", valign: "top", margin: 0 }
    );
  });

  s.addNotes(
    "Este es el punto de partida. No es un problema de falta de datos: los datos están. El costo está en juntarlos, " +
    "cruzarlos y dejar registrado por qué concluimos lo que concluimos."
  );
}

// ================================================================ 3 lo que existe
{
  const s = newSlide(
    "Lo que ya está construido y funcionando",
    "La base existe y está probada. Lo que propongo es extenderla, no empezar de nuevo."
  );

  const rows = [
    head(["Componente", "Qué hace hoy", "Estado"]),
    row(["Agente de drops", "Responde en lenguaje natural sobre contadores de 5G NSA y arma el diagnóstico de retenibilidad de una celda", "En funcionamiento"], true),
    row(["Base de conocimiento", "Consulta documentación de causas de drop para justificar la respuesta con criterio técnico", "En funcionamiento"], false),
    row(["Controles y registro", "Descarta consultas fuera de alcance y deja traza de cada consulta y de los datos usados", "En funcionamiento"], true),
    row(["Banco de pruebas", "Mide si el agente responde bien sobre un conjunto de casos conocidos", "En funcionamiento"], false),
    row(["Coordinador y módulo de PCI", "Consulta varios módulos sobre la misma celda y cruza sus resultados", "Probado con datos de prueba"], true),
  ];
  table(s, rows, { x: 0.6, y: 1.7, w: 12.13, colW: [2.85, 6.63, 2.65], rowH: [0.45, 0.68, 0.62, 0.62, 0.62, 0.62], size: 11 });

  s.addText(
    "El módulo de cobertura todavía no existe: hoy no tenemos la fuente de datos. Se presenta como diseño, no como demostración.",
    { x: 0.6, y: 5.75, w: 12.13, h: 0.4, fontFace: F, fontSize: 11, color: MUTED, align: "left", margin: 0 }
  );

  s.addNotes(
    "Quiero que quede clara la línea entre lo que ya corre y lo que es propuesta. El agente de drops está operativo. " +
    "El coordinador está probado con datos sembrados. Cobertura es diseño."
  );
}

// ================================================================ 4 la idea
{
  const s = newSlide(
    "La idea: un punto de entrada, tres especialistas",
    "El analista pregunta una sola vez. El coordinador reparte la consulta, cruza lo que vuelve y devuelve una conclusión con su evidencia."
  );

  box(s, 5.02, 1.62, 3.3, 0.5, "Analista RAN — pregunta por una celda", { size: 11 });
  vArrow(s, 6.67, 2.12, 0.32);
  box(s, 4.32, 2.44, 4.7, 0.5, "Coordinador — reparte la consulta", { size: 11, bold: true, color: INK });

  // fan-out
  s.addShape(pres.ShapeType.line, { x: 2.35, y: 3.16, w: 8.63, h: 0, line: { color: ARROW, width: 1.25 } });
  s.addShape(pres.ShapeType.line, { x: 6.67, y: 2.94, w: 0, h: 0.22, line: { color: ARROW, width: 1.25 } });
  vArrow(s, 2.35, 3.16, 0.3);
  vArrow(s, 6.67, 3.16, 0.3);
  vArrow(s, 10.98, 3.16, 0.3);

  box(s, 0.95, 3.46, 2.8, 1.0, "Drops\n\nQué está fallando\nen los contadores", { size: 11 });
  box(s, 5.27, 3.46, 2.8, 1.0, "Cobertura\n\nQué se ve\nen el terreno", { size: 11 });
  box(s, 9.58, 3.46, 2.8, 1.0, "PCI / RSI\n\nSi hay conflicto\nde identidad", { size: 11 });

  // fan-in
  [2.35, 6.67, 10.98].forEach((cx) => {
    s.addShape(pres.ShapeType.line, { x: cx, y: 4.46, w: 0, h: 0.3, line: { color: ARROW, width: 1.25 } });
  });
  s.addShape(pres.ShapeType.line, { x: 2.35, y: 4.76, w: 8.63, h: 0, line: { color: ARROW, width: 1.25 } });
  vArrow(s, 6.67, 4.76, 0.24);

  box(s, 3.62, 5.0, 6.1, 0.5, "Cruce de señales — regla escrita y revisable", { size: 11, bold: true, color: INK });
  vArrow(s, 6.67, 5.5, 0.24);
  box(s, 2.72, 5.74, 7.9, 0.5, "Causa probable  ·  nivel de confianza  ·  acción sugerida", { size: 11 });

  s.addText(
    "El modelo de lenguaje entiende la pregunta y redacta la respuesta. La conclusión la fija la regla: ante el mismo caso, la misma salida.",
    { x: 0.6, y: 6.4, w: 11.6, h: 0.35, fontFace: F, fontSize: 11, color: MUTED, align: "left", margin: 0 }
  );

  s.addNotes(
    "La pieza que agrega valor no es el modelo: es el cruce. Un módulo solo te da una señal; dos módulos independientes " +
    "apuntando a la misma celda por razones compatibles ya es un diagnóstico."
  );
}

// ================================================================ 5 rol de cada uno
{
  const s = newSlide(
    "Qué aporta cada especialista",
    "Cada dominio tiene un límite distinto. Solo uno puede proponer una corrección; los otros dos son de lectura."
  );

  const rows = [
    head(["Especialista", "Qué aporta", "Hasta dónde llega"]),
    row(["Drops\n(contadores)", "Detecta la anomalía de retenibilidad y su causa dominante: accesos fallidos, degradación de cobertura, handover, transporte", "Detecta. Es el síntoma: acá no hay nada que corregir"], true),
    row(["Cobertura\n(geolocalización)", "Valida contra el terreno: huecos de cobertura, celdas sirviendo más lejos de su radio de diseño, exceso de celdas dominantes", "Solo lectura. Deriva una recomendación al equipo de RF con la evidencia"], false),
    row(["PCI / RSI", "Valida conflictos de identidad entre celdas: colisión, confusión y mod 3 contra la red existente", "Valida y propone la corrección. Es el único que puede actuar sobre la red"], true),
  ];
  table(s, rows, { x: 0.6, y: 1.75, w: 12.13, colW: [2.45, 5.9, 3.78], rowH: [0.45, 1.05, 1.05, 1.05], size: 11 });

  s.addText(
    "Los cambios de RF —tilt, potencia, azimut— se siguen ejecutando en las herramientas del proveedor. El agente aporta el caso armado, no el cambio.",
    { x: 0.6, y: 5.65, w: 12.13, h: 0.4, fontFace: F, fontSize: 11, color: MUTED, align: "left", margin: 0 }
  );

  s.addNotes(
    "Es importante para la aprobación: dos de los tres dominios no tocan nada. El único con capacidad de cambio es PCI, " +
    "y ahí ya tenemos una herramienta con su auditoría."
  );
}

// ================================================================ 6 como se combinan
{
  const s = newSlide(
    "Cómo se combinan las señales",
    "Cada módulo devuelve señales acotadas, no texto libre. El cruce ocurre en un solo lugar y siempre de la misma forma."
  );

  box(s, 0.6, 1.9, 2.55, 0.9, "Drops\n\"accesos fallidos\"", { size: 10.5 });
  box(s, 0.6, 2.95, 2.55, 0.9, "Cobertura\n\"sobre-alcance\"", { size: 10.5 });
  box(s, 0.6, 4.0, 2.55, 0.9, "PCI\n\"colisión\"", { size: 10.5 });

  hArrow(s, 3.2, 2.35, 0.7);
  hArrow(s, 3.2, 3.40, 0.7);
  hArrow(s, 3.2, 4.45, 0.7);

  box(s, 3.95, 1.9, 2.35, 3.0, "Cruce de\nseñales", { size: 11.5, bold: true, color: INK });
  hArrow(s, 6.35, 3.40, 0.55);
  box(s, 6.95, 1.9, 1.9, 3.0, "Conclusión\ncon confianza", { size: 11, bold: true, color: INK });

  const pts = [
    ["Los módulos no se consultan entre sí.", "Todo el cruce pasa por un solo punto, que se puede leer y discutir sin abrir el modelo."],
    ["Mismo caso, misma conclusión.", "La regla está escrita. No cambia de un día para el otro ni depende de cómo se redactó la pregunta."],
    ["Sumar un dominio es sumar un módulo.", "Transporte o energía entrarían igual: se agrega el módulo y sus reglas, sin rehacer el agente."],
  ];
  pts.forEach((p, i) => {
    s.addText(
      [
        { text: p[0] + "\n", options: { bold: true, color: INK, fontSize: 12 } },
        { text: p[1], options: { color: BODY, fontSize: 11 } },
      ],
      { x: 9.15, y: 1.9 + i * 1.12, w: 3.58, h: 1.05, fontFace: F, align: "left", valign: "top", margin: 0, lineSpacingMultiple: 0.95 }
    );
  });

  s.addText(
    [
      { text: "Ejemplo de cruce:  ", options: { bold: true, color: INK, fontSize: 12 } },
      { text: "\"accesos fallidos\" (drops)  +  \"colisión\" (PCI)  →  el conflicto de identidad explica los drops, con confianza alta y una corrección posible.", options: { color: BODY, fontSize: 12 } },
    ],
    { x: 0.6, y: 5.35, w: 12.13, h: 0.45, fontFace: F, align: "left", valign: "middle", margin: 0 }
  );
  s.addText(
    "Si en cambio PCI y cobertura vuelven sin observaciones, el cruce no fuerza una causa: baja la confianza y devuelve la causa dominante de los contadores.",
    { x: 0.6, y: 5.82, w: 12.13, h: 0.4, fontFace: F, fontSize: 11, color: MUTED, align: "left", valign: "middle", margin: 0 }
  );

  s.addNotes(
    "Este es el punto que suele preguntarse primero: qué pasa si el modelo se equivoca. La respuesta es que el modelo no " +
    "decide la causa. Interpreta la pregunta y redacta; la causa sale de una regla que podemos auditar."
  );
}

// ================================================================ 7 reglas
{
  const s = newSlide(
    "Reglas de cruce: ejemplos concretos",
    "Así se ve el criterio escrito. Descartar también es un resultado: si no hay evidencia cruzada, no se propone acción."
  );

  const rows = [
    head(["Lo que ve drops", "Lo que ve el otro dominio", "Conclusión", "Confianza", "Acción"]),
    row(["Accesos fallidos altos", "Colisión o confusión de PCI con una vecina", "El conflicto de PCI explica los drops", "Alta", "Proponer un PCI nuevo y validarlo antes de aplicarlo"], true),
    row(["Degradación de cobertura", "Celda sirviendo bastante más lejos de su radio de diseño", "Sobre-alcance: los drops están en el borde", "Alta", "Caso armado al equipo de RF con la evidencia"], false),
    row(["Degradación de cobertura", "Nivel de señal bajo confirmado en el terreno", "Hueco de cobertura real, no falsa alarma de contadores", "Alta", "Caso armado al equipo de RF"], true),
    row(["Drops altos", "PCI limpio y cobertura sin observaciones", "La causa está fuera de estos dos dominios", "Baja", "No proponer acción; seguir la causa dominante de contadores"], false),
    row(["Todavía sin impacto", "Conflicto de PCI detectado", "Riesgo latente", "Media", "Corregir antes de que aparezca el impacto"], true),
  ];
  table(s, rows, { x: 0.6, y: 1.75, w: 12.13, colW: [2.15, 2.85, 2.85, 1.2, 3.08], rowH: [0.45, 0.78, 0.78, 0.78, 0.78, 0.78], size: 10.5 });

  s.addNotes(
    "Estas reglas son el criterio que hoy aplicamos a mano, puesto por escrito. No son inferencia causal: son los playbooks " +
    "del área. Se revisan y se ajustan con los casos reales."
  );
}

// ================================================================ 8 ejemplo
{
  const s = newSlide(
    "Ejemplo de una consulta punta a punta",
    "Una celda con drops altos, desde la pregunta hasta la verificación."
  );

  const steps = [
    "El analista pregunta por una celda con drops altos, en lenguaje natural",
    "El coordinador consulta a los tres módulos sobre esa misma celda",
    "Drops: crítico por accesos fallidos\nCobertura: sin observaciones\nPCI: colisión con una vecina",
    "Conclusión: el conflicto de PCI explica los drops, con la evidencia de cada módulo",
    "Propuesta de PCI nuevo. La aplica una persona y se vuelve a medir a las 24-48 h",
  ];

  const bw = 2.15, gap = 0.345;
  steps.forEach((t, i) => {
    const x = 0.6 + i * (bw + gap);
    s.addShape(pres.ShapeType.ellipse, {
      x: x + bw / 2 - 0.19, y: 2.15, w: 0.38, h: 0.38,
      fill: { color: "FFFFFF" }, line: { color: BOXLINE, width: 1 },
    });
    s.addText(String(i + 1), {
      x: x + bw / 2 - 0.19, y: 2.15, w: 0.38, h: 0.38,
      fontFace: F, fontSize: 11, bold: true, color: INK, align: "center", valign: "middle", margin: 0,
    });
    box(s, x, 2.7, bw, 1.7, t, { size: 10 });
    if (i < steps.length - 1) hArrow(s, x + bw + 0.06, 3.55, gap - 0.12);
  });

  s.addText(
    [
      { text: "Qué gana el analista:  ", options: { bold: true, color: INK, fontSize: 12 } },
      { text: "el cruce ya viene hecho y con su evidencia. El criterio de aplicar o no aplicar sigue siendo de él.", options: { color: BODY, fontSize: 12 } },
    ],
    { x: 0.6, y: 4.85, w: 12.13, h: 0.4, fontFace: F, align: "left", valign: "middle", margin: 0 }
  );
  s.addText(
    "La re-medición a 24-48 h cierra el circuito: si el drop no bajó, la hipótesis estaba mal y queda registrado.",
    { x: 0.6, y: 5.3, w: 12.13, h: 0.4, fontFace: F, fontSize: 11, color: MUTED, align: "left", valign: "middle", margin: 0 }
  );

  s.addNotes(
    "Este caso es real en la parte de drops y PCI; la respuesta de cobertura hoy sería simulada. " +
    "El tiempo de este recorrido pasa de horas a minutos."
  );
}

// ================================================================ 9 alcance
{
  const s = newSlide(
    "Alcance de la propuesta",
    "Prefiero dejar los límites por escrito antes de empezar, para que no haya expectativas distintas a mitad de camino."
  );

  const rows = [
    head(["Sí incluye", "No incluye"]),
    row(["Agregar el módulo de cobertura al coordinador, arrancando con datos de prueba", "Cambiar parámetros de RF: tilt, potencia y azimut siguen en las herramientas del proveedor"], true),
    row(["Conectar por interfaz con la herramienta de PCI/RSI que ya usamos, en modo consulta y simulación", "Modificar el motor de PCI existente: se consulta, no se toca"], false),
    row(["Las reglas de cruce documentadas, revisables y ajustables por el área", "Reemplazar el criterio del analista: toda corrección requiere aprobación humana"], true),
    row(["Registro de cada consulta, los datos usados y la propuesta generada", "La licencia ni la ingesta de la plataforma de geolocalización de cobertura"], false),
  ];
  table(s, rows, { x: 0.6, y: 1.8, w: 12.13, colW: [6.065, 6.065], rowH: [0.45, 0.85, 0.85, 0.85, 0.85], size: 11 });

  s.addText(
    "En esta etapa el agente no escribe nada en la red.",
    { x: 0.6, y: 5.95, w: 12.13, h: 0.4, fontFace: F, fontSize: 12.5, bold: true, color: INK, align: "left", valign: "middle", margin: 0 }
  );

  s.addNotes(
    "Esta lámina es la que responde la pregunta de riesgo. El alcance de la primera etapa es diagnóstico, " +
    "no ejecución. Lo que se automatiza es el cruce de información, no el cambio en la red."
  );
}

// ================================================================ 10 fases
{
  const s = newSlide(
    "Fases y dependencias",
    "Las fases están ordenadas para que cada una entregue algo utilizable, aunque la siguiente se demore."
  );

  const rows = [
    head(["Fase", "Entregable", "De qué depende"]),
    row(["A", "Diagnóstico de drops con cruce de señales y módulo de PCI simulado", "Ya está corriendo"], true),
    row(["B", "Módulo de cobertura con datos de prueba y sus reglas de cruce", "Nada externo"], false),
    row(["C", "Conexión con la herramienta de PCI/RSI en modo consulta y simulación", "Acceso a la herramienta y un punto de integración de su lado"], true),
    row(["D", "Cobertura con datos reales de geolocalización", "Definición sobre la fuente de datos y su licencia"], false),
    row(["E", "Corrección de PCI con aprobación y re-medición a 24-48 h", "Fase C en producción y proceso de cambio acordado"], true),
  ];
  table(s, rows, { x: 0.6, y: 1.8, w: 12.13, colW: [1.0, 6.53, 4.6], rowH: [0.45, 0.68, 0.68, 0.68, 0.68, 0.68], size: 11 });

  s.addText(
    "Si la fuente de cobertura no se define, las fases B, C y E igual entregan valor: el cruce entre drops y PCI ya cubre los casos de identidad de celda.",
    { x: 0.6, y: 5.95, w: 12.13, h: 0.4, fontFace: F, fontSize: 11, color: MUTED, align: "left", valign: "middle", margin: 0 }
  );

  s.addNotes(
    "Pido aprobación para B y C. D depende de una decisión que no es mía. E requiere que antes acordemos el proceso de cambio."
  );
}

// ================================================================ 11 riesgos
{
  const s = newSlide(
    "Riesgos y cómo se manejan",
    "Los riesgos que veo, con la contención que ya está prevista en el diseño."
  );

  const rows = [
    head(["Riesgo", "Cómo se maneja"]),
    row(["Que el modelo invente una conclusión", "La causa la fija una regla escrita. El modelo interpreta la pregunta y redacta la respuesta; no decide el diagnóstico"], true),
    row(["Alarmas de más y desgaste del equipo", "Sin evidencia cruzada la confianza es baja y no se propone acción. Se prioriza precisión antes que volumen"], false),
    row(["Que el agente toque la red", "La única escritura posible es PCI: se simula primero, queda auditoría por celda y la aplicación la hace una persona"], true),
    row(["Depender de una plataforma que hoy no tenemos", "El módulo de cobertura arranca con datos de prueba. Si la fuente no llega, el resto sigue funcionando"], false),
    row(["Convivencia con la herramienta de PCI actual", "Se integra por interfaz. Las dos aplicaciones siguen separadas y la que ya funciona no se modifica"], true),
    row(["Umbrales mal calibrados al principio", "Se ajustan contra casos reales ya resueltos por el área, con el resultado a la vista"], false),
  ];
  table(s, rows, { x: 0.6, y: 1.75, w: 12.13, colW: [4.13, 8.0], rowH: [0.45, 0.66, 0.66, 0.66, 0.66, 0.66, 0.66], size: 11 });

  s.addNotes(
    "No los presento como resueltos, sino como identificados y con una respuesta concreta. " +
    "El riesgo de negocio real es bajo porque la primera etapa no ejecuta cambios."
  );
}

// ================================================================ 12 pedido
{
  const s = newSlide(
    "Lo que pido para avanzar",
    "Tres definiciones concretas. Con las dos primeras arranco; la tercera destraba el módulo de cobertura."
  );

  const asks = [
    ["Aprobación de las fases B y C", "Módulo de cobertura con datos de prueba y conexión de consulta con la herramienta de PCI/RSI."],
    ["Acceso de consulta a la herramienta de PCI/RSI", "Un punto de integración de su lado. Sin cambios en el motor que ya está en uso."],
    ["Una definición sobre la fuente de cobertura", "Qué plataforma de geolocalización usamos y bajo qué licencia, para pasar de datos de prueba a datos reales."],
  ];

  const bw = 3.85, gap = 0.29;
  asks.forEach((a, i) => {
    const x = 0.6 + i * (bw + gap);
    s.addShape(pres.ShapeType.rect, {
      x: x, y: 2.05, w: bw, h: 2.0,
      fill: { color: "FFFFFF" }, line: { color: BOXLINE, width: 1 },
    });
    s.addText(String(i + 1), {
      x: x + 0.22, y: 2.18, w: 0.6, h: 0.35,
      fontFace: F, fontSize: 15, bold: true, color: INK, align: "left", valign: "middle", margin: 0,
    });
    s.addText(a[0], {
      x: x + 0.22, y: 2.56, w: bw - 0.44, h: 0.62,
      fontFace: F, fontSize: 12.5, bold: true, color: INK, align: "left", valign: "top", margin: 0, lineSpacingMultiple: 0.95,
    });
    s.addText(a[1], {
      x: x + 0.22, y: 3.22, w: bw - 0.44, h: 0.75,
      fontFace: F, fontSize: 11, color: BODY, align: "left", valign: "top", margin: 0, lineSpacingMultiple: 0.95,
    });
  });

  s.addText(
    "En esta etapa el agente diagnostica sobre celdas reales y deja registrada la evidencia de cada conclusión. No escribe nada en la red.",
    { x: 0.6, y: 4.5, w: 12.13, h: 0.4, fontFace: F, fontSize: 12.5, bold: true, color: INK, align: "left", valign: "middle", margin: 0 }
  );
  s.addText(
    "El objetivo medible de la primera etapa es bajar el tiempo de diagnóstico de una celda y dejar registrado el porqué de cada conclusión, sobre un conjunto de casos que el área ya resolvió a mano.",
    { x: 0.6, y: 4.98, w: 12.13, h: 0.6, fontFace: F, fontSize: 11.5, color: BODY, align: "left", valign: "top", margin: 0, lineSpacingMultiple: 0.95 }
  );

  s.addNotes(
    "Cierre: lo que pido no es presupuesto de infraestructura, son accesos y una definición. " +
    "El trabajo de desarrollo lo absorbo yo dentro del área."
  );
}

pres.writeFile({ fileName: "/tmp/claude-0/-home-user-DROP-RATE-AG/c1067c80-04c6-5f8b-8bf3-867dfb42cf7f/scratchpad/deck/Agente-RAN-propuesta.pptx" })
  .then((f) => console.log("OK:", f));
