# Sistema de Integración de Datos de ITVs

## 📋 Descripción del Proyecto

Aplicación Spring Boot que integra datos heterogéneos de estaciones ITV de tres comunidades autónomas españolas (Comunidad Valenciana, Galicia y Cataluña) en una base de datos unificada en Supabase.

### Arquitectura

El proyecto implementa el **patrón Wrapper/Adapter** para transformar datos heterogéneos:

```
JSON Origen (CV/GAL/CAT) → Wrapper Específico → Modelo Unificado → Supabase
```

## 🏗️ Estructura del Proyecto

```
src/main/java/com/itv/integration/
├── model/                    # Modelos de dominio y enumeraciones
│   ├── Estacion.java
│   ├── Localidad.java
│   ├── Provincia.java
│   └── TipoEstacion.java
├── dto/                      # DTOs para cada origen
│   ├── cv/EstacionCV.java
│   ├── gal/EstacionGAL.java
│   └── cat/EstacionCAT.java
├── wrapper/                  # Implementación de wrappers
│   ├── ItvDataWrapper.java
│   └── impl/
│       ├── CVWrapper.java
│       ├── GALWrapper.java
│       └── CATWrapper.java
├── service/                  # Servicios de negocio
│   ├── IntegracionService.java
│   └── GeocodingService.java
├── repository/              # Repositorios JPA
│   ├── EstacionRepository.java
│   ├── LocalidadRepository.java
│   └── ProvinciaRepository.java
└── controller/              # Controladores REST
    └── IntegracionController.java
```

## 🔧 Configuración

### 1. Prerrequisitos

- Java 17 o superior
- Maven 3.6+
- Base de datos Supabase (PostgreSQL)

### 2. Configurar Supabase

En `application.yml`, actualiza las credenciales:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.YOUR_PROJECT.supabase.co:5432/postgres
    username: postgres
    password: YOUR_PASSWORD
```

### 3. Preparar los Archivos JSON

Coloca los tres archivos JSON en `src/main/resources/data/`:
- `estaciones.json` (Comunidad Valenciana)
- `csvjson.json` (Galicia)
- `xmltojson.json` (Cataluña)

### 4. Crear el Enum en Supabase

Ejecuta este SQL en tu base de datos Supabase:

```sql
CREATE TYPE tipo_estacion AS ENUM ('Estación_fija', 'Estación_móvil', 'Otros');

ALTER TABLE public.Estacion 
ALTER COLUMN tipo TYPE tipo_estacion 
USING tipo::tipo_estacion;
```

## 🚀 Ejecución

### Opción 1: Mediante REST API

1. Inicia la aplicación:
```bash
mvn spring-boot:run
```

2. Usa los endpoints:

```bash
# Integrar solo Comunidad Valenciana
curl -X POST http://localhost:8080/api/integracion/cv

# Integrar solo Galicia
curl -X POST http://localhost:8080/api/integracion/gal

# Integrar solo Cataluña
curl -X POST http://localhost:8080/api/integracion/cat

# Integrar todos los archivos
curl -X POST http://localhost:8080/api/integracion/all

# Health check
curl http://localhost:8080/api/integracion/health
```

### Opción 2: Ejecución Automática al Inicio

Descomenta `@Component` en `IntegracionRunner.java` para ejecutar la integración automáticamente al iniciar la aplicación.

## 📊 Mappings Implementados

### Comunidad Valenciana (CV)

| Campo Origen | Campo Destino | Transformación |
|--------------|---------------|----------------|
| MUNICIPIO | E.nombre | Concatenar "Estación ITV de " + valor |
| TIPO ESTACIÓN | E.tipo | Mapear según enum |
| DIRECCIÓN | E.dirección | Copiar |
| Nº ESTACIÓN | E.cod_estacion | Autogenerado (identity) |
| - | E.longitud/latitud | Geocodificación mediante API |
| HORARIOS | E.horario | Copiar |
| CORREO | E.contacto | Copiar |
| - | E.url | Valor fijo: "https://www.sitval.com" |
| PROVINCIA | P.codigo | Extraer 2 primeros dígitos |

### Galicia (GAL)

| Campo Origen | Campo Destino | Transformación |
|--------------|---------------|----------------|
| NOME DA ESTACIÓN | E.nombre | Copiar |
| - | E.tipo | Valor fijo: "Estación_fija" |
| ENDEREZO | E.dirección | Copiar |
| CÓDIGO POSTAL | E.codigo_postal | Copiar |
| COORDENADAS GMAPS | E.longitud | Truncar después de coma |
| COORDENADAS GMAPS | E.latitud | Truncar antes de coma |
| HORARIO | E.horario | Copiar |
| CORREO ELECTRÓNICO | E.contacto | Copiar |
| SOLICITUDE DE CITA PREVIA | E.url | Copiar/Truncar |
| CÓDIGO POSTAL | P.codigo | Extraer 2 primeros dígitos |

### Cataluña (CAT)

| Campo Origen | Campo Destino | Transformación |
|--------------|---------------|----------------|
| denominaci | E.nombre | Copiar |
| - | E.tipo | Valor fijo: "Estación_fija" |
| adre_a | E.dirección | Copiar |
| cp | E.codigo_postal | Copiar |
| long | E.longitud | Dividir por 1,000,000 |
| lat | E.latitud | Dividir por 1,000,000 |
| horari_de_servei | E.horario | Copiar |
| correu_electr_nic | E.contacto | Copiar |
| web | E.url | Extraer atributo url |
| cp | P.codigo | Extraer 2 primeros dígitos |

## 🔍 Características Clave

### Geocodificación Automática
Para CV (que no tiene coordenadas), se usa el servicio de Nominatim (OpenStreetMap) para obtener coordenadas a partir de direcciones.

### Normalización de Tipos
El sistema maneja diferentes variantes de "Estación Fija", "Estación Móvil", etc., con normalización de caracteres especiales.

### Manejo de Coordenadas
- **CV**: Geocodificación vía API
- **GAL**: Parsing de formato grados/minutos
- **CAT**: Conversión desde formato entero (dividir por 10^6)

### Gestión de Códigos
- **Provincias**: Extracción desde códigos postales (2 primeros dígitos)
- **Localidades**: Autogeneración con IDENTITY
- **Estaciones**: Autogeneración con IDENTITY

## 🧪 Testing

```bash
# Ejecutar tests
mvn test

# Generar reporte de cobertura
mvn jacoco:report
```

## 📝 Logs

Los logs se configuran en `application.yml`:
- Nivel INFO para la aplicación general
- Nivel DEBUG para el paquete `com.itv.integration`
- SQL queries visibles para debugging

## ⚠️ Consideraciones Importantes

1. **Rate Limiting**: La API de Nominatim tiene límite de 1 req/segundo. El servicio incluye delay automático.

2. **Transaccionalidad**: Todas las operaciones de integración son transaccionales. Si falla una parte, se hace rollback.

3. **Duplicados**: El sistema verifica provincias existentes antes de insertar, pero permite duplicados en localidades y estaciones (útil para múltiples ejecuciones).

4. **Códigos Postales**: Se manejan como String en el parsing para evitar problemas con valores vacíos o no numéricos.

## 🐛 Troubleshooting

### Error de conexión a Supabase
- Verifica que las credenciales sean correctas
- Asegúrate de que la IP esté en la whitelist de Supabase

### Error de tipo enum
- Ejecuta el script SQL para crear el enum `tipo_estacion`

### Error de geocodificación
- Revisa los logs para ver qué direcciones fallan
- Considera aumentar el delay entre peticiones

## 📚 Recursos Adicionales

- [Documentación Spring Boot](https://spring.io/projects/spring-boot)
- [Supabase Documentation](https://supabase.com/docs)
- [Nominatim API](https://nominatim.org/release-docs/latest/api/Overview/)

## 👥 Equipo

Equipo T21-02 - Integración e Interoperabilidad 2025-26