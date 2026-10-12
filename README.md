# zeal-marketplace-service

Business Domain de ZEAL (puerto **8082**): convocatorias internas (proyectos) y talleres con cupos
limitados. Implementa el **reto C-1: cero sobreasignación** bajo ráfagas de postulaciones.

## Ejecutar

```bash
cd ../zeal-infra-compose && docker compose up -d
./mvnw spring-boot:run
```

Flyway crea el esquema `marketplace`. Con `DEMO_DATA_ENABLED=true` se siembran las convocatorias
y talleres de la demo, incluida `proyecto-c1-carga` (10 cupos) para la prueba de carga.
El JWT lo emite `zeal-wellbeing-service`; este servicio solo lo valida (`JWT_SECRET` igual en ambos).

## API

| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/proyectos`, `/api/proyectos/{id}` | EMPLEADO (solo su empresa) |
| POST | `/api/proyectos/{id}/postulaciones` | EMPLEADO → `201` ACEPTADA o EN_ESPERA, `200` si ya existía |
| GET | `/api/postulaciones/mias` | EMPLEADO |
| GET | `/api/talleres` · POST `/api/talleres/{id}/inscripciones` · GET `/api/inscripciones/mias` | EMPLEADO (taller lleno → `409`) |
| GET/POST | `/api/admin/proyectos`, `/api/admin/talleres` | ADMIN (solo su empresa) |

## Concurrencia (C-1)

1. `SELECT … FOR UPDATE` sobre la convocatoria/taller: las postulaciones se atienden de una en una.
2. `CHECK (occupied <= capacity)` en la base de datos: una sobreasignación es imposible aunque falle la lógica.
3. `UNIQUE (project_id, user_id)`: una persona no ocupa dos cupos (la operación es idempotente).
4. `lock_timeout = 5s` → `409` reintentable en vez de peticiones colgadas.

## Eventos (Redis, JSON plano, después del commit)

`zeal.marketplace.projects.published`, `zeal.marketplace.projects.spots`,
`zeal.marketplace.applications`, `zeal.marketplace.workshops.published`,
`zeal.marketplace.workshops.spots`, `zeal.marketplace.enrollments`.

## Pruebas

`./mvnw verify` (Docker requerido). `OverbookingConcurrencyIT`: 500 postulaciones simultáneas a 10
cupos → exactamente 10 ACEPTADA y 490 EN_ESPERA con puestos 1..490.
