-- Seed the eval_cases table with 20 ground-truth cases (10 drops, 10 telecom).
-- Run AFTER eval_cases.sql (or after the app has created the tables via Hibernate).
-- psql -U postgres -d victorb -f src/main/resources/db/eval_seed.sql
--
-- Safe to inspect before running. Re-running will insert duplicate rows; truncate first
-- if you want a clean reseed:  TRUNCATE eval_cases RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- Drops agent cases (agent = 'drops')
-- ---------------------------------------------------------------------------
INSERT INTO eval_cases (agent, question, expected, score_type, notes) VALUES
('drops', 'lista todas las celdas disponibles', 'ARR', 'contains',
 'listAllCells must return at least one cell with ARR prefix'),

('drops', 'cuál es la celda con mayor drop rate', 'ARR40312C1_Moran_Uribe', 'contains',
 'verified worst cell at 30.13% drop rate'),

('drops', 'dame el resumen de la celda ARR40312C1_Moran_Uribe', '30', 'contains',
 'drop rate is 30.13%, response must contain the number 30'),

('drops', 'cuál es la causa dominante de drops en ARR40312C1_Moran_Uribe', 'RA Problem', 'contains',
 'dominant cause confirmed as RA Problem from DB'),

('drops', 'muéstrame las 3 peores celdas', 'ARR40312C1_Moran_Uribe', 'contains',
 'worst cell must appear in top 3'),

('drops', 'qué significa el RA Problem en 5G NSA', 'RACH', 'contains',
 'RAG tool should return RACH-related explanation'),

('drops', 'dame las 5 celdas con más caídas', '%', 'contains',
 'response must include drop rate percentages'),

('drops', 'hay alguna celda en estado CRITICAL', 'CRITICAL', 'contains',
 'at least one cell exceeds 15% threshold'),

('drops', 'resumen de la celda ARR40312C2', 'ARR40312C2', 'contains',
 'cell name must appear in response'),

('drops', 'cuántas celdas hay en total', '29', 'contains',
 'confirmed 29 distinct cells in nr_cell_drops');

-- ---------------------------------------------------------------------------
-- Telecom agent cases (agent = 'telecom')
-- ---------------------------------------------------------------------------
INSERT INTO eval_cases (agent, question, expected, score_type, notes) VALUES
('telecom', 'dame el estado de la celda CELL_001', 'Cell:', 'contains',
 'getCellStatus must return formatted string starting with Cell:'),

('telecom', 'qué celdas están degradadas', 'RRC SR', 'contains',
 'getDegradedCells returns RRC SR formatted lines'),

('telecom', 'calcula el RRC Success Rate de CELL_001', 'RRC', 'contains',
 'calculateKpi must be called'),

('telecom', 'cómo diagnostico un problema de handover', 'HO', 'contains',
 'findCommands with HO keyword expected'),

('telecom', 'hay alguna celda en estado crítico', '%', 'contains',
 'must calculate KPIs and return percentages'),

('telecom', 'dame el estado de todas las celdas con fallo', 'DEGRADED', 'contains',
 'getDegradedCells must be called'),

('telecom', 'qué comando uso para revisar RACH', 'RACH', 'contains',
 'findCommands with RACH keyword'),

('telecom', 'disponibilidad de la celda CELL_001', 'Avail', 'contains',
 'getCellStatus returns Avail field'),

('telecom', 'cuál es la diferencia entre RRC y ERAB', 'RRC', 'contains',
 'plain knowledge question, no tool needed'),

('telecom', 'dame KPI de VoLTE para CELL_001', 'VoLTE', 'contains',
 'calculateKpi or getCellStatus covers VoLTE');
