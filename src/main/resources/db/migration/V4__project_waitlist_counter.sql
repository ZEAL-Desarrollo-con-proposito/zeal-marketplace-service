-- R2-27 · Posición en lista de espera en O(1): un contador en la fila de la convocatoria (que ya está
-- bloqueada al postularse) en lugar de contar las postulaciones en espera dentro del bloqueo.
ALTER TABLE projects ADD COLUMN waitlisted INTEGER NOT NULL DEFAULT 0 CHECK (waitlisted >= 0);

UPDATE projects p
SET waitlisted = (SELECT count(*) FROM applications a WHERE a.project_id = p.id AND a.status = 'EN_ESPERA');
