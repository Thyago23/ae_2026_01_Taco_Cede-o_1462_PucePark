# PucePark — Emprendimiento

**Proyecto Integrador P02 · PUCE TEC**

> Cifras del modelo de negocio estimadas para el contexto PUCE (piloto: 1 campus, operación en la nube, equipo reducido). Moneda: USD.

---

## 1. Business Model Canvas (criterio 5.1)

**PucePark** — plataforma inteligente de gestión de parqueaderos universitarios.

| Bloque | Contenido |
|---|---|
| **1. Segmentos de clientes** | • Universidades e institutos (cliente que paga). • Estudiantes/docentes con vehículo (usuarios). • Personal de seguridad/guardias (usuarios operativos). • Administración de campus. |
| **2. Propuesta de valor** | • Encontrar parqueo disponible en tiempo real (menos tiempo dando vueltas). • Control y trazabilidad para guardias (entradas/salidas, placas). • Datos de ocupación para la administración. • Gamificación (ranking) que incentiva buen uso. • Un solo login institucional (Cognito). |
| **3. Canales** | • App móvil (iOS; roadmap Android). • Portal web administrativo. • Difusión por la propia universidad (correo institucional, inducción). |
| **4. Relación con clientes** | • Autoservicio en la app. • Soporte/SLA a la institución. • Onboarding guiado dentro de la app. |
| **5. Fuentes de ingreso** | • **Licencia SaaS por campus** (suscripción mensual/anual). • Módulos premium (reportes avanzados, integración con talanquera/IoT). • Setup inicial de implementación. |
| **6. Recursos clave** | • Equipo de desarrollo. • Infraestructura en la nube (contenedores + Cognito + Postgres). • Código base (2 microservicios + app). |
| **7. Actividades clave** | • Desarrollo y mantenimiento. • Operación/monitoreo en la nube. • Soporte y capacitación. • Ventas B2B a instituciones. |
| **8. Socios clave** | • Proveedor cloud (AWS/GCP). • Universidad piloto (PUCE). • Proveedores de hardware IoT/talanquera (fase 2). |
| **9. Estructura de costos** | • Nube (cómputo, BD, Cognito). • Salarios de desarrollo/soporte. • Licencias y dominios. • Marketing/ventas. |

## 2. Propuesta tecnológica e innovación (criterio 5.2)

**Stack:**
- **App móvil**: SwiftUI (iOS 17+), MVVM, Alamofire, async/await.
- **Backend**: Spring Boot 4 + Kotlin, arquitectura de **microservicios** (park-app + users-service), PostgreSQL (BD por servicio).
- **Infraestructura**: Docker + docker-compose, **nginx** como punto de entrada único; preparado para IaaS/orquestadores.
- **Seguridad**: AWS Cognito (JWT), autorización por roles (estudiante/guardia/admin).

**Elementos de innovación y diferenciación:**
1. **Disponibilidad en tiempo real** con mapa de puestos por colores (verde/amarillo/rojo).
2. **Arquitectura de microservicios** escalable horizontalmente, lista para crecer a varios campus.
3. **Gamificación** (ranking mensual) para fomentar rotación y buen uso de plazas.
4. **Rol de guardia** integrado (registro manual, forzar liberación) — cubre el proceso real del campus.
5. **Login institucional único** que diferencia el rol automáticamente.
6. **Roadmap IoT**: integración con sensores/talanquera para detección automática (fase 2).

## 3. Planificación financiera (criterio 5.3)

> Supuestos: 1 universidad piloto (PUCE), operación en la nube IaaS, equipo pequeño. Moneda: USD.

### Inversión inicial (una vez)
| Concepto | Estimado |
|---|---|
| Desarrollo MVP (ya realizado por el equipo) | $4,000 |
| Configuración cloud + dominios + Cognito | $300 |
| Diseño de marca / materiales | $200 |
| **Total inicial** | **$4,500** |

### Costos operativos mensuales
| Concepto | Estimado/mes |
|---|---|
| Infraestructura cloud (instancia IaaS + almacenamiento) | $60 |
| Base de datos gestionada / respaldo | $30 |
| AWS Cognito (bajo volumen, capa gratuita + excedente) | $10 |
| Mantenimiento/soporte (parcial) | $150 |
| **Total mensual** | **$250** |

### Ingresos (modelo SaaS B2B)
| Concepto | Valor |
|---|---|
| Licencia por campus (suscripción) | **$400 / mes** |
| Setup inicial de implementación (una vez) | $500 |

### Proyección simple (1 campus, año 1)
| Rubro | Monto |
|---|---|
| Ingresos año 1 (12 × $400 + $500 setup) | $5,300 |
| Costos operativos año 1 (12 × $250) | $3,000 |
| Inversión inicial | $4,500 |
| **Resultado año 1** | **−$2,200** |
| **Punto de equilibrio** | ~**mes 14–15** (o antes con un 2.º campus) |

**Análisis:** el modelo B2B se vuelve rentable al sumar campus (el costo marginal por campus adicional es bajo gracias a la arquitectura multi-servicio en contenedores). Con 3 campus el resultado del año 1 ya sería positivo (ingresos ≈ $15,900 vs costos ≈ $3,000 + $4,500).
