package com.elucesc.itvintegration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Servicio de geocoding usando OpenCage API
 * Plan gratuito: 2,500 peticiones/día sin tarjeta de crédito
 * Límite: 1 petición/segundo
 */
@Slf4j
@Service
public class OpenCageGeocodingService {

    private static final String OPENCAGE_URL = "https://api.opencagedata.com/geocode/v1/json";

    @Value("${opencage.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public OpenCageGeocodingService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Obtiene coordenadas (longitud, latitud) usando OpenCage API
     */
    public Double[] obtenerCoordenadas(String direccion) {
        if (direccion == null || direccion.trim().isEmpty()) {
            log.debug("Dirección vacía, retornando null");
            return new Double[]{null, null};
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("OpenCage API Key no configurada. Agrega 'opencage.api.key' en application.properties");
            return new Double[]{null, null};
        }

        try {
            // Construir URL con parámetros
            URI uri = UriComponentsBuilder.fromHttpUrl(OPENCAGE_URL)
                    .queryParam("q", direccion)
                    .queryParam("key", apiKey)
                    .queryParam("countrycode", "es") // Filtrar solo España
                    .queryParam("limit", "1") // Solo el mejor resultado
                    .queryParam("no_annotations", "1") // Reducir datos innecesarios
                    .queryParam("language", "es") // Respuesta en español
                    .build()
                    .encode()
                    .toUri();

            log.debug("Solicitando coordenadas para: {}", direccion);

            // Hacer petición
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);

            if (response != null) {
                Map<String, Object> status = (Map<String, Object>) response.get("status");
                Integer code = (Integer) status.get("code");

                if (code == 200) {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

                    if (results != null && !results.isEmpty()) {
                        Map<String, Object> geometry = (Map<String, Object>) results.get(0).get("geometry");

                        Double lat = ((Number) geometry.get("lat")).doubleValue();
                        Double lng = ((Number) geometry.get("lng")).doubleValue();

                        log.debug("Coordenadas obtenidas: [{}, {}] para: {}", lng, lat, direccion);
                        return new Double[]{lng, lat};
                    } else {
                        log.warn("No se encontraron resultados para: {}", direccion);
                    }
                } else if (code == 402) {
                    log.error("Límite de peticiones diarias excedido (2,500/día)");
                } else if (code == 403) {
                    log.error("API Key inválida o acceso denegado");
                } else if (code == 429) {
                    log.warn("Demasiadas peticiones por segundo. Máximo: 1 req/seg");
                } else {
                    String message = (String) status.get("message");
                    log.warn("Error de OpenCage API (código {}): {}", code, message);
                }
            }

            return new Double[]{null, null};

        } catch (Exception e) {
            log.error("Error al obtener coordenadas de OpenCage para: {}", direccion, e);
            return new Double[]{null, null};
        }
    }

    /**
     * Obtiene coordenadas con dirección completa (incluye municipio y provincia)
     */
    public Double[] obtenerCoordenadasCompletas(String direccion, String municipio, String provincia) {
        StringBuilder direccionCompleta = new StringBuilder();

        if (direccion != null && !direccion.trim().isEmpty()) {
            direccionCompleta.append(direccion);
        }

        if (municipio != null && !municipio.trim().isEmpty()) {
            if (direccionCompleta.length() > 0) {
                direccionCompleta.append(", ");
            }
            direccionCompleta.append(municipio);
        }

        if (provincia != null && !provincia.trim().isEmpty()) {
            if (direccionCompleta.length() > 0) {
                direccionCompleta.append(", ");
            }
            direccionCompleta.append(provincia);
        }

        direccionCompleta.append(", España");

        return obtenerCoordenadas(direccionCompleta.toString());
    }

    /**
     * Versión con delay para respetar límite de 1 petición/segundo
     * IMPORTANTE: OpenCage límite es 1 req/seg (más estricto que Google)
     */
    public Double[] obtenerCoordenadasConDelay(String direccion) {
        try {
            Thread.sleep(1100); // 1.1 segundos para estar seguros
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return obtenerCoordenadas(direccion);
    }

    /**
     * Verifica si la API está configurada correctamente
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}