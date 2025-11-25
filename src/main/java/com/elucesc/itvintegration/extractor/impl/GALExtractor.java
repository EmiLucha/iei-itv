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

//    @Override
//    public List<Provincia> transformarProvincias() {
//        Set<String> provinciasUnicas = estacionesGAL.stream()
//                .map(EstacionGAL::getProvincia)
//                .filter(Objects::nonNull)
//                .collect(Collectors.toSet());
//
//        List<Provincia> provincias = new ArrayList<>();
//        for (String nombreProvincia : provinciasUnicas) {
//            Long codigo = extraerCodigoProvincia(nombreProvincia);
//            provinciaCodigoMap.put(nombreProvincia, codigo);
//
//            provincias.add(Provincia.builder()
//                    .codigo(codigo)
//                    .nombre(nombreProvincia)
//                    .build());
//        }
//
//        return provincias;
//    }

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

//    private Long extraerCodigoProvincia(String nombreProvincia) {
//        if (nombreProvincia == null) return null;
//
//        String normalized = nombreProvincia.toLowerCase();
//        if (normalized.contains("coruña") || normalized.contains("coruna")) {
//            return 15L;
//        } else if (normalized.contains("lugo")) {
//            return 27L;
//        } else if (normalized.contains("ourense")) {
//            return 32L;
//        } else if (normalized.contains("pontevedra")) {
//            return 36L;
//        }
//        return null;
//    }

//    private Long extraerCodigoProvinciaDeCP(Integer codigoPostal) {
//        if (codigoPostal == null) return null;
//
//        String cpString = String.format("%05d", codigoPostal);
//        String prefijo = cpString.substring(0, 2);
//
//        return Long.parseLong(prefijo);
//    }

    private Double[] parsearCoordenadasGMaps(String coordenadas) {
        if (coordenadas == null || coordenadas.trim().isEmpty()) {
            return new Double[]{null, null};
        }

        try {
            coordenadas = coordenadas.replaceAll("[°'\"]", "").trim();

            String[] partes = coordenadas.split(",");
            if (partes.length != 2) {
                return new Double[]{null, null};
            }

            Double latitud = convertirCoordenada(partes[0].trim());
            Double longitud = convertirCoordenada(partes[1].trim());

            return new Double[]{longitud, latitud};
        } catch (Exception e) {
            log.warn("No se pudieron parsear coordenadas: {}", coordenadas, e);
            return new Double[]{null, null};
        }
    }

    private Double convertirCoordenada(String coord) {
        coord = coord.trim();

        if (!coord.contains(" ")) {
            return Double.parseDouble(coord);
        }

        String[] partes = coord.split("\\s+");
        if (partes.length == 2) {
            double grados = Double.parseDouble(partes[0]);
            double minutos = Double.parseDouble(partes[1]);
            double resultado = grados + (minutos / 60.0);
            return resultado;
        }

        return Double.parseDouble(coord);
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

    @Override
    public List<Provincia> transformarProvincias() {
        Set<String> provinciasUnicas = estacionesGAL.stream()
                .map(EstacionGAL::getProvincia)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Provincia> provincias = new ArrayList<>();
        for (String nombreProvincia : provinciasUnicas) {
            Long codigo = extraerCodigoProvincia(nombreProvincia);

            // VALIDACIÓN: No agregar si el código es null
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
        return null;  // NO devolver código por defecto, forzar corrección
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