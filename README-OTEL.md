# OpenTelemetry Java Agent Workshop

This workspace contains two Spring Boot services:

- `parte0-JaegerCourseApp`: course service on port `8001`
- `part0-JaegerCourseCatalog`: catalog service on port `8002`

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
Invoke-WebRequest http://localhost:8002/
Invoke-WebRequest http://localhost:8002/catalog
Invoke-WebRequest http://localhost:8002/firstcourse
```

Then open Jaeger and inspect traces for:

- `fx-catalog-service`
- `fx-course-service`

## Important note

No manual tracing code is required for this workshop. The Java agent instruments Spring Boot,
HTTP server handling, outgoing `RestTemplate` calls, and JDBC interactions automatically.
