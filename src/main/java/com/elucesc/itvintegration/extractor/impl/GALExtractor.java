package com.elucesc.itvintegration.extractor.impl;

import com.elucesc.itvintegration.dto.gal.EstacionGAL;
import com.elucesc.itvintegration.extractor.ItvDataExtractor;
import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.Localidad;
import com.elucesc.itvintegration.model.Provincia;
import com.elucesc.itvintegration.model.TipoEstacion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GALExtractor implements ItvDataExtractor {

    private final List<EstacionGAL> estacionesGAL;
    private final Map<String, Long> provinciaCodigoMap = new HashMap<>();
    private final Map<String, Long> localidadCodigoMap = new HashMap<>();
    private Long localidadCodigoCounter = 1L;

    public GALExtractor(List<EstacionGAL> estacionesGAL) {
        this.estacionesGAL = estacionesGAL;
    }

    @Override
    public List<Provincia> transformarProvincias() {
        Set<String> provinciasUnicas = estacionesGAL.stream()
                .map(EstacionGAL::getProvincia)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Provincia> provincias = new ArrayList<>();
        for (String nombreProvincia : provinciasUnicas) {
            Long codigo = extraerCodigoProvincia(nombreProvincia);

            if (codigo == null) {
                log.error("❌ No se pudo determinar código para provincia: '{}'", nombreProvincia);
                continue;
            }

            provinciaCodigoMap.put(nombreProvincia, codigo);

            provincias.add(Provincia.builder()
                    .codigo(codigo)
                    .nombre(nombreProvincia)
                    .build());

            log.debug("Provincia Galicia: código={}, nombre='{}'", codigo, nombreProvincia);
        }

        return provincias;
    }

    @Override
    public List<Localidad> transformarLocalidades() {
        transformarProvincias();

        List<Localidad> localidades = new ArrayList<>();
        for (EstacionGAL estacion : estacionesGAL) {
            String concello = estacion.getConcello();
            if (concello == null || concello.trim().isEmpty()) continue;

            if (!localidadCodigoMap.containsKey(concello)) {
                Long codigoProvincia = extraerCodigoProvinciaDeCP(estacion.getCodigoPostal());

                Localidad localidad = Localidad.builder()
                        .codigo(null)
                        .nombre(concello)
                        .codProvincia(codigoProvincia)
                        .build();

                localidades.add(localidad);
                localidadCodigoMap.put(concello, localidadCodigoCounter++);
            }
        }

        return localidades;
    }

    @Override
    public List<Estacion> transformarEstaciones() {
        List<Estacion> estaciones = new ArrayList<>();

        for (EstacionGAL estacionGAL : estacionesGAL) {
            // Parsear coordenadas de Google Maps
            Double[] coordenadas = parsearCoordenadasGMaps(estacionGAL.getCoordenadasGmaps());

            Estacion estacion = Estacion.builder()
                    .nombre(estacionGAL.getNomeDaEstacion())
                    .tipo(TipoEstacion.ESTACION_FIJA)
                    .direccion(estacionGAL.getEnderezo())
                    .codigoPostal(estacionGAL.getCodigoPostal() != null ?
                            estacionGAL.getCodigoPostal().longValue() : null)
                    .longitud(coordenadas[0])
                    .latitud(coordenadas[1])
                    .descripcion("Descripción provisional de " + estacionGAL.getNomeDaEstacion())
                    .horario(estacionGAL.getHorario())
                    .contacto(estacionGAL.getCorreoElectronico())
                    .url(extraerUrl(estacionGAL.getSolicitudeCitaPrevia()))
                    .codLocalidad(null)
                    .build();

            estaciones.add(estacion);
        }

        return estaciones;
    }

    @Override
    public Map<Integer, String> obtenerMapaEstacionLocalidad() {
        Map<Integer, String> mapa = new HashMap<>();
        for (int i = 0; i < estacionesGAL.size(); i++) {
            String concello = estacionesGAL.get(i).getConcello();
            if (concello != null && !concello.trim().isEmpty()) {
                mapa.put(i, concello);
            }
        }
        return mapa;
    }

    /**
     * Parsea coordenadas de Google Maps que vienen en dos formatos:
     * 1. Grados minutos decimales: "43° 18.856', -8° 17.165'" → 43.314267, -8.286083
     * 2. Decimal directo: "42.135887,-8.788971" → 42.135887, -8.788971
     * 3. Decimal erróneo: "412.135887,-8.788971" → null (detectado y rechazado)
     */
    private Double[] parsearCoordenadasGMaps(String coordenadas) {
        if (coordenadas == null || coordenadas.trim().isEmpty()) {
            return new Double[]{null, null};
        }

        try {
            log.debug("Parseando coordenadas originales: '{}'", coordenadas);

            // Separar latitud y longitud
            String[] partes = coordenadas.split(",");
            if (partes.length != 2) {
                log.warn("Formato de coordenadas inválido (esperado: lat,lon): {}", coordenadas);
                return new Double[]{null, null};
            }

            Double latitud = convertirCoordenada(partes[0].trim());
            Double longitud = convertirCoordenada(partes[1].trim());

            log.debug("Coordenadas parseadas: lat={}, lon={}", latitud, longitud);

            // Validación básica de rangos
            if (latitud != null && (latitud < -90 || latitud > 90)) {
                log.warn("⚠️ Latitud fuera de rango [-90, 90]: {} (coordenadas: {})", latitud, coordenadas);
                // Si es > 90, probablemente es un error de dígitos (412 → 42)
                if (latitud > 100) {
                    log.error("❌ Coordenada errónea en archivo fuente. Latitud: {} es imposible (coordenadas: {})",
                            latitud, coordenadas);
                }
                return new Double[]{null, null};
            }
            if (longitud != null && (longitud < -180 || longitud > 180)) {
                log.warn("⚠️ Longitud fuera de rango [-180, 180]: {} (coordenadas: {})", longitud, coordenadas);
                return new Double[]{null, null};
            }

            // Redondear a 6 decimales (precisión GPS estándar: ~10cm)
            if (latitud != null) {
                latitud = Math.round(latitud * 1_000_000.0) / 1_000_000.0;
            }
            if (longitud != null) {
                longitud = Math.round(longitud * 1_000_000.0) / 1_000_000.0;
            }

            return new Double[]{longitud, latitud};
        } catch (Exception e) {
            log.warn("Error parseando coordenadas: {}", coordenadas, e);
            return new Double[]{null, null};
        }
    }

    /**
     * Convierte una coordenada que puede venir en dos formatos:
     * 1. Grados minutos decimales: "43° 18.856'" → 43 + (18.856/60) = 43.314267
     * 2. Decimal directo: "42.135887" → 42.135887
     */
    private Double convertirCoordenada(String coord) {
        coord = coord.trim();

        // Detectar si tiene símbolos de grados (°) o minutos (')
        boolean esFormatoGradosMinutos = coord.contains("°") || coord.contains("'");

        if (esFormatoGradosMinutos) {
            // Formato: "43° 18.856'" o "-8° 17.165'"
            // Limpiar símbolos
            coord = coord.replaceAll("[°'\"]", "").trim();

            // Separar por espacios
            String[] partes = coord.split("\\s+");
            if (partes.length != 2) {
                log.warn("Formato grados/minutos inválido: {}", coord);
                // Intentar como decimal
                return Double.parseDouble(coord.replaceAll("[^0-9.-]", ""));
            }

            try {
                double grados = Double.parseDouble(partes[0]);
                double minutos = Double.parseDouble(partes[1]);

                // Convertir minutos a grados: grados + (minutos / 60)
                // Si los grados son negativos, los minutos se restan
                if (grados >= 0) {
                    return grados + (minutos / 60.0);
                } else {
                    return grados - (minutos / 60.0);
                }
            } catch (NumberFormatException e) {
                log.error("Error parseando coordenada grados/minutos: {}", coord, e);
                return null;
            }
        } else {
            // Formato decimal directo: "42.135887" o "-8.788971"
            try {
                return Double.parseDouble(coord);
            } catch (NumberFormatException e) {
                log.error("Error parseando coordenada decimal: {}", coord, e);
                return null;
            }
        }
    }

    private String extraerUrl(String solicitudCitaPrevia) {
        if (solicitudCitaPrevia == null || solicitudCitaPrevia.trim().isEmpty()) {
            return null;
        }

        if (solicitudCitaPrevia.startsWith("http")) {
            try {
                return solicitudCitaPrevia.split("\\?")[0];
            } catch (Exception e) {
                return solicitudCitaPrevia;
            }
        }

        return solicitudCitaPrevia;
    }

    private Long extraerCodigoProvincia(String nombreProvincia) {
        if (nombreProvincia == null) return null;

        String normalized = nombreProvincia.toLowerCase()
                .replaceAll("[áàäâ]", "a")
                .replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i")
                .replaceAll("[óòöô]", "o")
                .replaceAll("[úùüû]", "u")
                .replaceAll("ñ", "n")
                .trim();

        // Códigos postales de Galicia
        if (normalized.contains("coruna") || normalized.contains("a coruna")) {
            return 15L;
        } else if (normalized.contains("lugo")) {
            return 27L;
        } else if (normalized.contains("ourense") || normalized.contains("orense")) {
            return 32L;
        } else if (normalized.contains("pontevedra")) {
            return 36L;
        }

        log.error("❌ Provincia de Galicia no reconocida: '{}'", nombreProvincia);
        return null;
    }

    private Long extraerCodigoProvinciaDeCP(Integer codigoPostal) {
        if (codigoPostal == null) return null;

        String cpString = String.format("%05d", codigoPostal);
        String prefijo = cpString.substring(0, 2);

        int codigo = Integer.parseInt(prefijo);

        // Validar que sea código de Galicia
        if (codigo == 15 || codigo == 27 || codigo == 32 || codigo == 36) {
            return (long) codigo;
        }

        log.warn("Código postal fuera de Galicia: {}", codigoPostal);
        return null;
    }
}