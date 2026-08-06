package com.tracker.web.recursos.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tracker.web.recursos.planner.dto.CeldaExistenteDTO5G;
import com.tracker.web.recursos.planner.dto.CeldaPlanDTO;
import com.tracker.web.recursos.planner.dto.ResultadoLoteDTO;
import com.tracker.web.recursos.planner.dto.ResultadoPlanificacionDTO;

/**
 * REFERENCIA — Java 8, va en la app `tracker`. No se compila con el agente.
 *
 * Servicio de apoyo para la API que consume el agente. Se apoya en los servicios que ya
 * existen (RedExistenteService5G, PlannerEngine) y NO toca el motor.
 *
 * Diferencia con PciRsiAsignacionService.autoAsignar: aquel recibe ids de
 * RECURSOS_FORMULARIO, es decir celdas NUEVAS todavia sin PCI. Aca la celda ya esta en la
 * red y se la re-planifica desde cero.
 *
 * NINGUN metodo de esta clase persiste. Devuelven propuestas.
 */
@Service
public class PciApiSupportService {

	private static final Logger log = LoggerFactory.getLogger(PciApiSupportService.class);

	@Autowired
	private RedExistenteService5G redExistenteService5G;

	@Autowired
	private PlannerEngine plannerEngine;

	/**
	 * Re-planifica una celda que YA esta en la red. Simulacion: no guarda nada.
	 *
	 * [CRITICO] El motor no se auto-excluye del pool de celdas existentes: planner_5g.js no
	 * filtra por cellname en ninguna parte (politica_cosito resuelve co-sitio, que es otro
	 * problema). En el flujo original eso nunca importo porque las celdas a planificar aun no
	 * estaban en tm_conf_actual_celda_5g. Aca la celda SI esta, asi que hay que sacarla del
	 * pool a mano; de lo contrario el motor ve su propio PCI actual como vecina y se
	 * auto-descarta el valor o reporta colision consigo misma.
	 */
	public ResultadoPlanificacionDTO replanificarCeldaExistente(String cellname) {
		if (cellname == null || cellname.trim().isEmpty()) {
			throw new IllegalArgumentException("cellname vacio");
		}

		CeldaExistenteDTO5G viva = buscarCeldaViva(cellname);
		if (viva == null) {
			throw new IllegalArgumentException("Celda no encontrada en la red existente: " + cellname);
		}

		CeldaPlanDTO fila = new CeldaPlanDTO();
		fila.setCellname(viva.getCellname());
		fila.setCarrier(viva.getCarrier());
		fila.setFreqBandText(viva.getFreqBand());
		fila.setTxrxmode(viva.getTxrxmode());
		fila.setLatitud(viva.getLatitud());
		fila.setLongitud(viva.getLongitud());
		fila.setAzimuth(viva.getAzimuth());
		// Se re-planifica desde cero: el motor no debe partir del PCI actual.
		fila.setPhycellid(null);

		List<CeldaPlanDTO> plan = Collections.singletonList(fila);

		ResultadoRedExistente5G red = redExistenteService5G.obtenerCeldasExistentes(plan);

		// [CRITICO] ver el javadoc de arriba.
		List<CeldaExistenteDTO5G> pool = new ArrayList<CeldaExistenteDTO5G>(red.getCeldas());
		int antes = pool.size();
		for (int i = pool.size() - 1; i >= 0; i--) {
			if (cellname.equalsIgnoreCase(pool.get(i).getCellname())) {
				pool.remove(i);
			}
		}
		log.info("Re-plan {}: pool de existentes {} -> {} (se excluyo la propia celda)",
				cellname, antes, pool.size());

		ResultadoLoteDTO lote;
		try {
			lote = plannerEngine.planificarLote5g(pool, plan);
		} catch (PlannerEngineException e) {
			throw new IllegalStateException("El motor de planificacion fallo: " + e.getMessage(), e);
		}

		if (lote.getResultados() == null || lote.getResultados().isEmpty()) {
			throw new IllegalStateException("El motor no devolvio resultado para " + cellname);
		}
		return lote.getResultados().get(0);
	}

	/**
	 * Lee una celda viva de tm_conf_actual_celda_5g. Se apoya en obtenerCeldasExistentes con
	 * un plan de una sola fila ubicada en las coordenadas de la celda buscada; si en el
	 * proyecto ya existe una consulta directa por cellname, usar esa y borrar este metodo.
	 */
	private CeldaExistenteDTO5G buscarCeldaViva(String cellname) {
		List<CeldaExistenteDTO5G> todas = redExistenteService5G
				.obtenerCeldasExistentes(Collections.<CeldaPlanDTO>emptyList())
				.getCeldas();
		for (CeldaExistenteDTO5G c : todas) {
			if (cellname.equalsIgnoreCase(c.getCellname())) {
				return c;
			}
		}
		return null;
	}
}
