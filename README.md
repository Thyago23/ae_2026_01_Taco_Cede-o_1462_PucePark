# PucePark — Backend de Microservicios

Sistema de gestión de parqueaderos universitarios (Proyecto Integrador P02 · PUCE TEC).
Backend de **microservicios** en Spring Boot 4 + Kotlin, con **app móvil iOS** y **panel web** de administración como clientes.

- **App iOS (frontend):** https://github.com/BryanTaco/PuceParkFront
- **Panel web admin:** servido por nginx en `/admin/` (incluido en este repo, carpeta `webadmin/`).

---

## Arquitectura

```
                         AWS Cognito (User Pool + JWKS)  ── emite y firma los JWT
                                        │
   ┌──────────┐   ┌──────────────┐      │ token
   │  App iOS │   │  Panel web    │ ─────┘
   │ (cliente)│   │  /admin/      │
   └────┬─────┘   └──────┬────────┘
        └──────HTTP :80──►│  nginx (reverse proxy · punto de entrada único)
                          ├── /api/*    → park-app      (:8080)  ── JDBC ─► db_park  (puce_park)
                          ├── /users/*  → users-service (:8686)  ── JDBC ─► db_micro (puce_micro)
                          └── /admin/   → panel web estático
```

- **park-app** — dominio de parqueo: zonas, puestos, historial, ranking (`/api/v1/*`).
- **users-service** — dominio de identidad: perfiles de usuario (`/users/*`).
- **Base de datos por servicio** (aislamiento): `puce_park` y `puce_micro`, sin *joins* cruzados.
- **Sin llamadas entre servicios:** cada uno valida el JWT de Cognito por su cuenta (Resource Server).

## Tecnologías

| Área | Tecnología |
|---|---|
| Lenguaje / JVM | Kotlin 2.2 · **Java 21** |
| Framework | **Spring Boot 4.0.6** (Web, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator) |
| Base de datos | PostgreSQL 16 (una por servicio) |
| Seguridad | AWS Cognito (JWT) · roles vía claim `cognito:groups` |
| Gateway | nginx (reverse proxy, único puerto expuesto: 80) |
| Pruebas | JUnit 5 · **mockito-kotlin** · MockMvc · **JaCoCo** (cobertura) |
| Contenedores | Docker · docker-compose (5 servicios) |
| Nube | AWS **EC2** (backend, IaaS) · AWS **S3** (página de descarga) |

## Estructura (por servicio, arquitectura en capas)

`controllers/` (REST, delega al service) · `services/` (lógica + `@Transactional`) · `repositories/` (Spring Data JPA, bloqueo pesimista) · `dto/` · `mappers/` · `entities/` · `exceptions/` (una por archivo + `GlobalExceptionHandler`) · `config/` (SecurityConfig, DataInitializer).

---

## Ejecución local (todo el stack)

Requisito: Docker Desktop / Docker Engine.

```bash
docker-compose up -d --build
```

Levanta los 5 contenedores. Punto de entrada único: **http://localhost** (nginx, puerto 80).

- `http://localhost/api/v1/zonas`  → 401 (requiere token)
- `http://localhost/users/me`       → 401 (requiere token)
- `http://localhost/admin/`         → panel web de administración

> Las BDs publican 5434 (puce_park) y 5435 (puce_micro) solo para inspección en desarrollo; en producción quedan internas.

## Pruebas

```bash
./gradlew test                 # park-app
cd users-service && ./gradlew test   # users-service
```
Incluye pruebas de **services** (Mockito), **controllers** (MockMvc + JWT, validación de roles) y **GlobalExceptionHandler** (rama de mensaje por defecto para JaCoCo).

---

## API — Endpoints y roles

Todos los endpoints requieren **JWT de Cognito** (salvo `/actuator/health`). El rol se toma del claim `cognito:groups` → `ROLE_ADMIN`, `ROLE_GUARD`, `ROLE_USER`.

### Zonas (`/api/v1/zonas`)
| Método | Ruta | Roles |
|---|---|---|
| GET | `/api/v1/zonas` | ADMIN, GUARD, USER |
| GET | `/api/v1/zonas/{id}/estadisticas` | ADMIN, GUARD, USER |
| POST | `/api/v1/zonas` | ADMIN |
| PUT | `/api/v1/zonas/{id}` | ADMIN |
| DELETE | `/api/v1/zonas/{id}` | ADMIN |

### Puestos (`/api/v1/puestos`)
| Método | Ruta | Roles |
|---|---|---|
| GET | `/api/v1/puestos`, `/api/v1/puestos/zona/{zonaId}` | ADMIN, GUARD, USER |
| POST | `/api/v1/puestos` | ADMIN |
| PUT | `/api/v1/puestos/{id}` (renombrar) | ADMIN |
| DELETE | `/api/v1/puestos/{id}` | ADMIN |
| PUT | `/api/v1/puestos/{id}/ocupar` | ADMIN, GUARD, USER |
| PUT | `/api/v1/puestos/{id}/liberar` | ADMIN, GUARD, USER |
| PUT | `/api/v1/puestos/{id}/forzar-ocupacion` | ADMIN, GUARD |
| PUT | `/api/v1/puestos/{id}/forzar-liberacion` | ADMIN, GUARD |

### Historial y ranking (`/api/v1/historial`)
| Método | Ruta | Roles |
|---|---|---|
| GET | `/api/v1/historial/me`, `/me/estadisticas?mes=YYYY-MM` | ADMIN, USER |
| GET | `/api/v1/historial/guardia/me` | ADMIN, GUARD |
| GET | `/api/v1/historial/ranking/mensual?mes=YYYY-MM` | ADMIN, GUARD, USER |
| GET | `/api/v1/historial/puesto/{id}` | ADMIN, GUARD |

### Perfiles — users-service (`/users`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/users/me` | Perfil del usuario autenticado |
| PUT | `/users/me` | Crear/actualizar perfil (onboarding, edición) |

**Errores estandarizados:** `GlobalExceptionHandler` devuelve `{ message, source }` con códigos HTTP semánticos (400, 401, 403, 404, 409). **Concurrencia:** bloqueo pesimista al ocupar un puesto para evitar doble ocupación.

---

## Panel web de administración (`/admin/`)

Página estática servida por el mismo nginx. El administrador inicia sesión con Cognito (solo grupo **ADMIN**) y gestiona todo desde el navegador: crear/editar/eliminar **zonas**, crear/renombrar/eliminar **puestos**, forzar ocupación/liberación, y ver **ranking mensual** e **historial por puesto**.

## Despliegue en la nube

- **EC2 (IaaS):** `cloud/ec2/user-data.sh` instala Docker, clona el repo y levanta el stack. Ver `cloud/ec2/README-ec2.md`.
- **S3 (página de descarga):** app iOS/APK. Ver `cloud/README-despliegue-s3.md`.

## Documentación del proyecto

En `docs/` (Markdown + PDF en `docs/pdf/`):
- `01-analisis-sistemas.md` — RF/RNF, casos de uso, GitFlow, pruebas, ADR.
- `02-computacion-nube.md` — conceptos cloud, contenedores, arquitectura de infraestructura.
- `03-emprendimiento.md` — Business Model Canvas, propuesta tecnológica, financiero.
- `04-historias-usuario.md` — backlog (HU + criterios de aceptación).
- `pucepark-historias-jira.csv` — importable a Jira.

## Colección Postman

En `postman/` — colección y entorno apuntando a nginx (`http://localhost`).
