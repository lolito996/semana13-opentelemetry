# OpenTelemetry Java Agent Workshop

This workspace contains two Spring Boot services:

- `parte0-JaegerCourseApp`: course service on port `9001`
- `part0-JaegerCourseCatalog`: catalog service on port `9002`

## What was missing

The OpenTelemetry Java agent must be configured when the JVM starts. For that reason, `otel.*`
values inside `application.properties` are not the reliable way to configure the agent.
They need to be passed with `-Dotel.*` JVM arguments or `OTEL_*` environment variables.

## Prerequisites

1. Docker Desktop running
2. MySQL running locally on `localhost:3306`
3. Database credentials matching:
   - user: `root`
   - password: `techbankRootPsw`
4. `opentelemetry-javaagent.jar` in the workspace root
5. Java 17+

## Start Jaeger

From the workspace root:

```powershell
docker compose up -d
```

Jaeger UI will be available at `http://localhost:16686`.

## Build the services

From the workspace root:

```powershell
.\build-services.ps1
```

## Run the services with the agent

Open two terminals in the workspace root.

Terminal 1:

```powershell
.\run-course-app-otel.ps1
```

Terminal 2:

```powershell
.\run-course-catalog-otel.ps1
```

## Generate traces

Once both services are running, call:

```powershell
Invoke-WebRequest http://localhost:9002/
Invoke-WebRequest http://localhost:9002/catalog
Invoke-WebRequest http://localhost:9002/firstcourse
```

Then open Jaeger and inspect traces for:

- `fx-catalog-service`
- `fx-course-service`

## Locust: carga para cursos y catalogo en Grafana

Este repo incluye un servicio `locust` que simula trafico contra:

- Course service: `http://host.docker.internal:9001`
- Catalog service: `http://host.docker.internal:9002`

Locust expone metricas Prometheus en `http://localhost:8089/metrics`, Prometheus las scrapea con el
job `fx-locust` y Grafana provisiona automaticamente el dashboard **FutureX - Carga Locust**.

### 1) Levantar observabilidad y Locust

Desde la raiz del workspace:

```powershell
docker compose up -d prometheus grafana locust
```

Tambien puedes levantar todo el stack:

```powershell
docker compose up -d
```

### 2) Levantar las aplicaciones

Los servicios Spring Boot deben estar corriendo en el host:

- Course service: `http://localhost:9001`
- Catalog service: `http://localhost:9002`

### 3) Iniciar la prueba de carga

Abre Locust:

```text
http://localhost:8089
```

Usa, por ejemplo:

- Number of users: `20`
- Spawn rate: `2`
- Host: `http://host.docker.internal:9002`

### 4) Analizar en Grafana

Abre Grafana:

```text
http://localhost:3000
```

Credenciales:

- User: `admin`
- Password: `admin`

Dashboard:

- `FutureX / FutureX - Carga Locust`

El dashboard muestra usuarios activos, RPS, errores por segundo y latencias de Locust, junto con RPS y
p95 desde las metricas Spring Boot de ambos servicios.

## Important note

No manual tracing code is required for this workshop. The Java agent instruments Spring Boot,
HTTP server handling, outgoing `RestTemplate` calls, and JDBC interactions automatically.

## ELK: Query de logs (Punto 1)

Este repo ahora incluye un stack mínimo de **Elasticsearch + Logstash + Kibana** para que puedas
indexar logs y ejecutar la query pedida en el taller.

### 1) Levantar ELK

Desde la raíz del workspace:

```powershell
docker compose up -d elasticsearch kibana logstash
```

Kibana queda en: `http://localhost:5601`

### 2) Generar logs

Las apps escriben logs en formato **ECS JSON** a la carpeta `./logs` (en la raíz del repo).

- Si corres cada servicio desde su carpeta (lo típico con `mvnw`), el `LOG_DIR` por defecto es `../logs`.
- Si corres desde otra ruta, puedes forzar la carpeta con una variable de entorno:

```powershell
$env:LOG_DIR = "./logs"
```

Para generar un **ERROR** rápido (y que la query tenga resultados), levanta **sólo** el servicio catálogo
y deja apagado el de cursos, luego llama un endpoint del catálogo:

```powershell
Invoke-WebRequest http://localhost:8002/catalog
```

Eso provoca una excepción por conexión fallida al servicio de cursos, que queda registrada como log `ERROR`.

### 3) Crear Data View en Kibana

En Kibana → **Discover** → crea un *Data View* con patrón:

- `logs-futurex-*`
- Time field: `@timestamp`

### 4) Query requerida (últimas 24 horas)

**Opción A (KQL en Discover):**

```text
service.name : "fx-catalog-service" and log.level : "ERROR"
```

**Opción B (Dev Tools → Console, JSON):**

```http
GET logs-futurex-*/_search
{
   "query": {
      "bool": {
         "must": [
            { "match": { "service.name": "fx-catalog-service" } },
            { "match": { "log.level": "ERROR" } },
            { "range": { "@timestamp": { "gte": "now-24h" } } }
         ]
      }
   },
   "sort": [
      { "@timestamp": { "order": "desc" } }
   ]
}
```
