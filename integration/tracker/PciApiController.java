package com.tracker.web.recursos.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tracker.web.recursos.planner.PciApiSupportService;
import com.tracker.web.recursos.planner.dto.ResultadoPlanificacionDTO;

/**
 * REFERENCIA — Java 8, va en la app `tracker`. No se compila con el agente.
 *
 * Superficie JSON que consume el agente RAN. Es intencionalmente delgada: toda la
 * inteligencia sigue en PciRsiAsignacionService / PlannerEngine / planner_*.js. Aca solo
 * se traduce a HTTP.
 *
 * Notar que es @RestController y no @Controller: RecursosController devuelve vistas
 * Thymeleaf porque lo maneja una persona con el mouse. Esta API la consume otro proceso.
 *
 * SIN ENDPOINT DE ESCRITURA. Aplicar un PCI se hace donde se hace hoy: POST /recursos/asignar,
 * con una persona revisando. El agente propone; no asigna.
 */
@RestController
@RequestMapping("/api/pci")
public class PciApiController {

	@Autowired
	private PciApiSupportService soporte;

	@Autowired
	private PciConflictoAuditRepository conflictosRepo; // tabla materializada, ver README

	/** Plan actual de una celda viva. Consulta barata: no toca el motor. */
	@GetMapping("/plan/{cellname}")
	public ResponseEntity<?> plan(@PathVariable("cellname") String cellname) {
		try {
			return ResponseEntity.ok(soporte.planActual(cellname));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(e.getMessage()));
		}
	}

	/**
	 * Conflictos que afectan a una celda. Lee la tabla materializada, NO corre el motor:
	 * el agente llama a este endpoint en cada diagnostico cruzado, asi que tiene que
	 * responder en milisegundos. Ver el README para el job que la puebla con auditarRed().
	 */
	@GetMapping("/audit/{cellname}")
	public ResponseEntity<List<Map<String, Object>>> auditCelda(@PathVariable("cellname") String cellname) {
		return ResponseEntity.ok(aJson(conflictosRepo.findByCelda(cellname)));
	}

	/** Conflictos de toda la red, desde la misma tabla materializada. */
	@GetMapping("/audit")
	public ResponseEntity<List<Map<String, Object>>> auditRed() {
		return ResponseEntity.ok(aJson(conflictosRepo.findAllVigentes()));
	}

	/**
	 * Re-plan de una celda viva. Este SI corre el motor (segundos), por eso es POST y por eso
	 * el agente solo lo llama cuando el usuario pidio explicitamente atacar el PCI.
	 *
	 * Devuelve la propuesta con su auditoria. NO persiste.
	 */
	@PostMapping("/propose")
	public ResponseEntity<Map<String, Object>> propose(@RequestBody Map<String, String> body) {
		String cellname = body == null ? null : body.get("cellName");
		Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
		try {
			ResultadoPlanificacionDTO r = soporte.replanificarCeldaExistente(cellname);
			out.put("currentPci", soporte.pciActual(cellname));
			out.put("pci", r.getPci());
			out.put("rsi", r.getRsi());
			// La auditoria del motor es lo que hace accionable la propuesta: sin ella el
			// numero no se puede revisar. Viaja tal cual la genera planner_5g.js.
			out.put("auditoria", r.getAuditoria());
			out.put("advertencias", r.getAdvertencias());
			out.put("error", r.getError());
			return ResponseEntity.ok(out);
		} catch (IllegalArgumentException e) {
			out.put("error", e.getMessage());
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(out);
		} catch (RuntimeException e) {
			out.put("error", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(out);
		}
	}

	/**
	 * Shape que espera RestPciPlannerAdapter.ConflictResponse del lado del agente:
	 * {type, cellA, pciA, cellB, pciB, note}.
	 */
	private static List<Map<String, Object>> aJson(List<PciConflictoAudit> filas) {
		List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
		for (PciConflictoAudit f : filas) {
			Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
			m.put("type", f.getTipo());        // COLLISION | CONFUSION | MOD3
			m.put("cellA", f.getCeldaA());
			m.put("pciA", f.getPciA());
			m.put("cellB", f.getCeldaB());
			m.put("pciB", f.getPciB());
			m.put("note", f.getMotivo() == null
					? "Distancia " + f.getDistanciaM() + " m."
					: f.getMotivo() + " (distancia " + f.getDistanciaM() + " m)");
			out.add(m);
		}
		return out;
	}

	private static Map<String, Object> error(String msg) {
		Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
		m.put("error", msg);
		return m;
	}
}
