package com.elucesc.itvintegration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Servicio para obtener coordenadas geográficas a partir de direcciones.
 * Utiliza la API de Nominatim de OpenStreetMap (gratuita, sin API key)
 */
@Slf4j
@Service
public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private final RestTemplate restTemplate;

    public GeocodingService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Obtiene las coordenadas (longitud, latitud) a partir de una dirección.
     * @param direccion La dirección completa
     * @return Array con [longitud, latitud] o [null, null] si no se encuentra
     */
    public Double[] obtenerCoordenadas(String direccion) {
        if (direccion == null || direccion.trim().isEmpty()) {
            return new Double[]{null, null};
        }

        try {
            // Construir la URL con parámetros
            URI uri = UriComponentsBuilder.fromHttpUrl(NOMINATIM_URL)
                    .queryParam("q", direccion + ", España")
                    .queryParam("format", "json")
                    .queryParam("limit", "1")
                    .build()
                    .encode()
                    .toUri();

            // Hacer la petición
            Map<String, Object>[] response = restTemplate.getForObject(uri, Map[].class);

            if (response != null && response.length > 0) {
                Map<String, Object> resultado = response[0];
                String lon = (String) resultado.get("lon");
                String lat = (String) resultado.get("lat");

                if (lon != null && lat != null) {
                    return new Double[]{
                            Double.parseDouble(lon),
                            Double.parseDouble(lat)
                    };
                }
            }

            log.warn("No se encontraron coordenadas para la dirección: {}", direccion);
            return new Double[]{null, null};

        } catch (Exception e) {
            log.error("Error al obtener coordenadas para la dirección: {}", direccion, e);
            return new Double[]{null, null};
        }
    }

    /**
     * Versión con delay para respetar los límites de Nominatim (1 req/seg)
     */
    public Double[] obtenerCoordenadasConDelay(String direccion) {
        try {
            Thread.sleep(1000); // Esperar 1 segundo entre peticiones
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return obtenerCoordenadas(direccion);
    }
}