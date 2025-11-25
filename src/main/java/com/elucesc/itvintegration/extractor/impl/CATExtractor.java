package com.elucesc.itvintegration.extractor.impl;

import com.elucesc.itvintegration.dto.cat.EstacionCAT;
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
public class CATExtractor implements ItvDataExtractor {

    private final List<EstacionCAT> estacionesCAT;
    private final Map<String, Long> localidadCodigoMap = new HashMap<>();

    public CATExtractor(List<EstacionCAT> estacionesCAT) {
        this.estacionesCAT = estacionesCAT;
    }

    @Override
    public List<Provincia> transformarProvincias() {
        Map<Long, String> provinciasMap = new HashMap<>();

        for (EstacionCAT estacion : estacionesCAT) {
            Long codigoProvincia = extraerCodigoProvinciaPorCP(estacion.getCodigoPostal());
            if (codigoProvincia == null) continue;

            String nombre = estacion.getServeisTerritorials();
            if (nombre == null || nombre.trim().isEmpty()) nombre = "Desconocida";

            provinciasMap.putIfAbsent(codigoProvincia, nombre);
        }

        return provinciasMap.entrySet().stream()
                .map(e -> Provincia.builder()
                        .codigo(e.getKey())
                        .nombre(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<Localidad> transformarLocalidades() {
        List<Localidad> localidades = new ArrayList<>();
        for (EstacionCAT estacion : estacionesCAT) {
            String municipi = estacion.getMunicipi();
            if (municipi == null || municipi.trim().isEmpty()) continue;

            if (!localidadCodigoMap.containsKey(municipi)) {
                Long codigoProvincia = extraerCodigoProvinciaPorCP(estacion.getCodigoPostal());
                if (codigoProvincia == null) continue;

                Localidad localidad = Localidad.builder()
                        .codigo(null)
                        .nombre(municipi)
                        .codProvincia(codigoProvincia)
                        .build();

                localidades.add(localidad);
                localidadCodigoMap.put(municipi, null);
            }
        }
        return localidades;
    }

    @Override
    public List<Estacion> transformarEstaciones() {
        List<Estacion> estaciones = new ArrayList<>();
        for (EstacionCAT estacionCAT : estacionesCAT) {
            Double longitud = convertirCoordenada(estacionCAT.getLon());
            Double latitud = convertirCoordenada(estacionCAT.getLat());

            Estacion estacion = Estacion.builder()
                    .nombre("Estación ITV de " + estacionCAT.getDenominaci())
                    .tipo(TipoEstacion.ESTACION_FIJA)
                    .direccion(estacionCAT.getDireccion())
                    .codigoPostal(estacionCAT.getCodigoPostal() != null ? estacionCAT.getCodigoPostal().longValue() : null)
                    .longitud(longitud)
                    .latitud(latitud)
                    .descripcion("Descripción provisional de Estación ITV de " + estacionCAT.getDenominaci())
                    .horario(estacionCAT.getHorariDeServei())
                    .contacto(estacionCAT.getCorreuElectronic())
                    .url(extraerUrl(estacionCAT.getWeb()))
                    .codLocalidad(null)
                    .build();

            estaciones.add(estacion);
        }
        return estaciones;
    }

    @Override
    public Map<Integer, String> obtenerMapaEstacionLocalidad() {
        Map<Integer, String> mapa = new HashMap<>();
        for (int i = 0; i < estacionesCAT.size(); i++) {
            String municipi = estacionesCAT.get(i).getMunicipi();
            if (municipi != null && !municipi.trim().isEmpty()) {
                mapa.put(i, municipi);
            }
        }
        return mapa;
    }

    private Long extraerCodigoProvinciaPorCP(Integer codigoPostal) {
        if (codigoPostal == null) return null;

        String cpString = String.format("%05d", codigoPostal);
        int codigo = Integer.parseInt(cpString.substring(0, 2));

        if (codigo >= 1 && codigo <= 52) return (long) codigo;
        return null;
    }

    private Double convertirCoordenada(Double coordenada) {
        if (coordenada == null) return null;
        return (coordenada > 1_000_000) ? coordenada / 1_000_000.0 : coordenada;
    }

    private String extraerUrl(String web) {
        if (web == null || web.trim().isEmpty()) return null;
        return web.startsWith("http") ? web : null;
    }
}