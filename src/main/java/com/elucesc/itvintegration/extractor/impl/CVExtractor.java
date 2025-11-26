package com.elucesc.itvintegration.extractor.impl;

import com.elucesc.itvintegration.dto.cv.EstacionCV;
import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.Localidad;
import com.elucesc.itvintegration.model.Provincia;
import com.elucesc.itvintegration.model.TipoEstacion;
import com.elucesc.itvintegration.service.SeleniumGeocodingService;
import com.elucesc.itvintegration.extractor.ItvDataExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CVExtractor implements ItvDataExtractor {

    private final List<EstacionCV> estacionesCV;
    private final SeleniumGeocodingService seleniumGeocodingService;

    private final Map<String, Long> provinciaCodigoMap = new HashMap<>();
    private final Map<String, Long> localidadCodigoMap = new HashMap<>();

    private Long provinciaCodigoCounter = 1L;
    private Long localidadCodigoCounter = 1L;

    @Autowired
    public CVExtractor(List<EstacionCV> estacionesCV, SeleniumGeocodingService seleniumGeocodingService) {
        this.estacionesCV = estacionesCV;
        this.seleniumGeocodingService = seleniumGeocodingService;
    }

    @Override
    public List<Localidad> transformarLocalidades() {
        transformarProvincias();

        Set<String> municipiosUnicos = estacionesCV.stream()
                .map(EstacionCV::getMunicipio)
                .filter(Objects::nonNull)
                .filter(m -> !m.trim().isEmpty())
                .collect(Collectors.toSet());

        List<Localidad> localidades = new ArrayList<>();
        for (EstacionCV estacion : estacionesCV) {
            String municipio = estacion.getMunicipio();
            if (municipio == null || municipio.trim().isEmpty()) continue;

            if (!localidadCodigoMap.containsKey(municipio)) {
                Long codigoProvincia = provinciaCodigoMap.get(estacion.getProvincia());

                Localidad localidad = Localidad.builder()
                        .codigo(null)
                        .nombre(municipio)
                        .codProvincia(codigoProvincia)
                        .build();

                localidades.add(localidad);
                localidadCodigoMap.put(municipio, localidadCodigoCounter++);
            }
        }

        return localidades;
    }

    @Override
    public List<Estacion> transformarEstaciones() {
        List<Estacion> estaciones = new ArrayList<>();

        log.info("Iniciando geocoding con Selenium de {} estaciones (puede tardar unos minutos...)",
                estacionesCV.size());

        // Verificar que Selenium está disponible
        if (!seleniumGeocodingService.isAvailable()) {
            log.error("❌ Selenium WebDriver no está disponible. Verifica que ChromeDriver esté instalado.");
            log.error("Descarga ChromeDriver desde: https://chromedriver.chromium.org/");
            throw new RuntimeException("Selenium WebDriver no disponible");
        }

        int procesadas = 0;
        int conCoordenadas = 0;

        for (EstacionCV estacionCV : estacionesCV) {
            // Obtener coordenadas usando Selenium
            Double[] coordenadas = obtenerCoordenadasInteligente(estacionCV);

            if (coordenadas[0] != null && coordenadas[1] != null) {
                conCoordenadas++;
            }

            Estacion estacion = Estacion.builder()
                    .nombre(construirNombre(estacionCV))
                    .tipo(mapearTipo(estacionCV.getTipoEstacion()))
                    .direccion(estacionCV.getDireccion())
                    .codigoPostal(parseCodigoPostal(estacionCV.getCodigoPostal()))
                    .longitud(coordenadas[0])
                    .latitud(coordenadas[1])
                    .descripcion("Descripción provisional de " + construirNombre(estacionCV))
                    .horario(estacionCV.getHorarios())
                    .contacto(estacionCV.getCorreo())
                    .url("https://www.sitval.com")
                    .codLocalidad(null)
                    .build();

            estaciones.add(estacion);

            procesadas++;
            if (procesadas % 10 == 0) {
                log.info("Procesadas {}/{} estaciones ({} con coordenadas)",
                        procesadas, estacionesCV.size(), conCoordenadas);
            }
        }

        log.info("Geocoding completado: {}/{} estaciones con coordenadas", conCoordenadas, procesadas);
        return estaciones;
    }

    @Override
    public Map<Integer, String> obtenerMapaEstacionLocalidad() {
        Map<Integer, String> mapa = new HashMap<>();
        for (int i = 0; i < estacionesCV.size(); i++) {
            String municipio = estacionesCV.get(i).getMunicipio();
            if (municipio != null && !municipio.trim().isEmpty()) {
                mapa.put(i, municipio);
            }
        }
        return mapa;
    }

    /**
     * Obtiene coordenadas de forma inteligente usando Selenium:
     * - Omite estaciones móviles/agrícolas
     * - Usa dirección completa cuando es válida
     * - Fallback a municipio si dirección no funciona
     * - Respeta delay entre peticiones para evitar bloqueos
     */
    private Double[] obtenerCoordenadasInteligente(EstacionCV estacion) {
        String direccion = estacion.getDireccion();
        String municipio = estacion.getMunicipio();
        String provincia = estacion.getProvincia();

        // Omitir estaciones móviles/agrícolas (no tienen ubicación fija)
        if (esTipoMovilOAgricola(direccion)) {
            log.debug("Omitiendo geocoding para estación móvil/agrícola: {}", direccion);
            return new Double[]{null, null};
        }

        // Si la dirección es válida, usarla completa
        if (esDireccionValida(direccion)) {
            Double[] coordenadas = seleniumGeocodingService.obtenerCoordenadasConDelay(
                    construirDireccionCompleta(direccion, municipio, provincia)
            );

            if (coordenadas[0] != null && coordenadas[1] != null) {
                return coordenadas;
            }

            log.warn("No se encontraron coordenadas con Selenium para dirección: {}", direccion);
        }

        // Fallback: buscar solo por municipio + provincia
        if (municipio != null && !municipio.trim().isEmpty()) {
            log.debug("Usando municipio como fallback: {}", municipio);
            return seleniumGeocodingService.obtenerCoordenadasConDelay(
                    construirDireccionCompleta(null, municipio, provincia)
            );
        }

        return new Double[]{null, null};
    }

    private boolean esTipoMovilOAgricola(String direccion) {
        if (direccion == null) return false;

        String dirLower = direccion.toLowerCase();
        return dirLower.contains("móvil") ||
                dirLower.contains("movil") ||
                dirLower.contains("agrícola") ||
                dirLower.contains("agricola");
    }

    private boolean esDireccionValida(String direccion) {
        if (direccion == null || direccion.trim().isEmpty()) {
            return false;
        }

        // Selenium/Google Maps maneja bien direcciones con "s/n", así que las permitimos
        return true;
    }

    private String construirDireccionCompleta(String direccion, String municipio, String provincia) {
        StringBuilder sb = new StringBuilder();

        if (direccion != null && !direccion.trim().isEmpty()) {
            sb.append(direccion);
        }

        if (municipio != null && !municipio.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(municipio);
        }

        if (provincia != null && !provincia.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(provincia);
        }

        sb.append(", España");

        return sb.toString();
    }

    private String construirNombre(EstacionCV estacion) {
        String municipio = estacion.getMunicipio();
        if (municipio != null && !municipio.trim().isEmpty()) {
            return "Estación ITV de " + municipio;
        }
        return "Estación ITV " + estacion.getNumeroEstacion();
    }

    private TipoEstacion mapearTipo(String tipoOriginal) {
        return TipoEstacion.fromString(tipoOriginal);
    }

    private Long parseCodigoPostal(String codigoPostal) {
        if (codigoPostal == null || codigoPostal.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(codigoPostal.trim());
        } catch (NumberFormatException e) {
            log.warn("No se pudo parsear código postal: {}", codigoPostal);
            return null;
        }
    }

    @Override
    public List<Provincia> transformarProvincias() {
        // Usar LinkedHashMap para mantener orden y evitar duplicados
        Map<Long, String> provinciasMap = new LinkedHashMap<>();

        for (EstacionCV estacion : estacionesCV) {
            String nombreProvinciaOriginal = estacion.getProvincia();
            if (nombreProvinciaOriginal == null) continue;

            // NORMALIZAR nombre de provincia (quitar typos)
            String nombreProvinciaNormalizado = normalizarNombreProvincia(nombreProvinciaOriginal);

            Long codigo = extraerCodigoProvincia(nombreProvinciaNormalizado);

            // Solo agregar si no existe ya (evita duplicados)
            if (!provinciasMap.containsKey(codigo)) {
                provinciasMap.put(codigo, nombreProvinciaNormalizado);
                log.debug("Provincia detectada: código={}, nombre='{}'", codigo, nombreProvinciaNormalizado);
            }

            // CRÍTICO: Guardar mapeo tanto del nombre original como del normalizado
            provinciaCodigoMap.put(nombreProvinciaOriginal, codigo);
            provinciaCodigoMap.put(nombreProvinciaNormalizado, codigo);
        }

        return provinciasMap.entrySet().stream()
                .map(e -> Provincia.builder()
                        .codigo(e.getKey())
                        .nombre(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Normaliza nombres de provincias para corregir typos comunes
     */
    private String normalizarNombreProvincia(String nombre) {
        if (nombre == null) return null;

        String normalizado = nombre.trim();

        // Corregir typos comunes
        if (normalizado.equalsIgnoreCase("Aligante") ||
                normalizado.equalsIgnoreCase("Aliacnte")) {
            return "Alicante";
        }

        if (normalizado.equalsIgnoreCase("Valéncia") ||
                normalizado.equalsIgnoreCase("València")) {
            return "Valencia";
        }

        if (normalizado.equalsIgnoreCase("Castelló") ||
                normalizado.equalsIgnoreCase("Castellon")) {
            return "Castellón";
        }

        return normalizado;
    }

    private Long extraerCodigoProvincia(String nombreProvincia) {
        if (nombreProvincia == null) return null;

        switch (nombreProvincia) {
            case "Alicante":
                return 3L;
            case "Castellón":
                return 12L;
            case "Valencia":
                return 46L;
            default:
                log.warn("Provincia desconocida en CV: '{}', usando código por defecto", nombreProvincia);
                return provinciaCodigoCounter++;
        }
    }
}