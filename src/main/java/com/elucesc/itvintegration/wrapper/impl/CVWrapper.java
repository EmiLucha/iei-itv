package com.elucesc.itvintegration.wrapper.impl;
import com.elucesc.itvintegration.dto.cv.EstacionCV;
import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.Localidad;
import com.elucesc.itvintegration.model.Provincia;
import com.elucesc.itvintegration.model.TipoEstacion;
import com.elucesc.itvintegration.service.GeocodingService;
import com.elucesc.itvintegration.wrapper.ItvDataWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CVWrapper implements ItvDataWrapper {

    private final List<EstacionCV> estacionesCV;
    private final GeocodingService geocodingService;
    private final Map<String, Long> provinciaCodigoMap = new HashMap<>();
    private final Map<String, Long> localidadCodigoMap = new HashMap<>();
    private Long provinciaCodigoCounter = 1L;
    private Long localidadCodigoCounter = 1L;

    @Autowired
    public CVWrapper(List<EstacionCV> estacionesCV, GeocodingService geocodingService) {
        this.estacionesCV = estacionesCV;
        this.geocodingService = geocodingService;
    }

    @Override
    public List<Provincia> transformarProvincias() {
        Set<String> provinciasUnicas = estacionesCV.stream()
                .map(EstacionCV::getProvincia)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Provincia> provincias = new ArrayList<>();
        for (String nombreProvincia : provinciasUnicas) {
            Long codigo = extraerCodigoProvincia(nombreProvincia);
            provinciaCodigoMap.put(nombreProvincia, codigo);

            provincias.add(Provincia.builder()
                    .codigo(codigo)
                    .nombre(nombreProvincia)
                    .build());
        }

        return provincias;
    }

    @Override
    public List<Localidad> transformarLocalidades() {
        // Primero procesar provincias para tener los códigos
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
                // NO establecer el código - dejarlo null para que sea autogenerado
                Long codigoProvincia = provinciaCodigoMap.get(estacion.getProvincia());

                Localidad localidad = Localidad.builder()
                        .codigo(null) // ✅ Dejar en null para autogenerar
                        .nombre(municipio)
                        .codProvincia(codigoProvincia)
                        .build();

                localidades.add(localidad);
                // Guardar temporalmente con el contador para referencia
                localidadCodigoMap.put(municipio, localidadCodigoCounter++);
            }
        }

        return localidades;
    }

    @Override
    public List<Estacion> transformarEstaciones() {
        // NO necesitamos llamar a transformarProvincias/Localidades aquí
        // El servicio se encargará de guardarlas primero

        List<Estacion> estaciones = new ArrayList<>();

        for (EstacionCV estacionCV : estacionesCV) {
            String municipio = estacionCV.getMunicipio();

            // Obtener coordenadas mediante servicio de geocodificación
            Double[] coordenadas = geocodingService.obtenerCoordenadas(estacionCV.getDireccion());

            Estacion estacion = Estacion.builder()
                    .nombre(construirNombre(estacionCV))
                    .tipo(mapearTipo(estacionCV.getTipoEstacion()))
                    .direccion(estacionCV.getDireccion())
                    .codigoPostal(parseCodigoPostal(estacionCV.getCodigoPostal()))
                    .longitud(coordenadas[0])
                    .latitud(coordenadas[1])
                    .descripcion(null)
                    .horario(estacionCV.getHorarios())
                    .contacto(estacionCV.getCorreo())
                    .url("https://www.sitval.com")
                    .codLocalidad(null) // ✅ Lo dejamos null por ahora
                    .build();

            estaciones.add(estacion);
        }

        return estaciones;
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

    private Long extraerCodigoProvincia(String nombreProvincia) {
        // Los códigos postales de CV empiezan con: 03 (Alicante), 12 (Castellón), 46 (Valencia)
        switch (nombreProvincia) {
            case "Alicante": return 3L;
            case "Castellón": return 12L;
            case "Valencia": return 46L;
            default: return provinciaCodigoCounter++;
        }
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
}