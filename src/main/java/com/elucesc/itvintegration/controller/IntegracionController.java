package com.elucesc.itvintegration.controller;

import com.elucesc.itvintegration.service.IntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/integracion")
public class IntegracionController {

    private final IntegrationService integracionService;

    @Value("${integration.files.cv}")
    private String rutaCV;

    @Value("${integration.files.gal}")
    private String rutaGAL;

    @Value("${integration.files.cat}")
    private String rutaCAT;

    @Autowired
    public IntegracionController(IntegrationService integracionService) {
        this.integracionService = integracionService;
    }

    /**
     * Integra solo el archivo de Comunidad Valenciana
     */
    @PostMapping("/cv")
    public ResponseEntity<Map<String, String>> integrarCV() {
        try {
            log.info("Iniciando integración de Comunidad Valenciana");
            integracionService.integrarArchivo(rutaCV, IntegrationService.TipoOrigen.COMUNIDAD_VALENCIANA);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Datos de Comunidad Valenciana integrados correctamente");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error en integración CV", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Integra solo el archivo de Galicia
     */
    @PostMapping("/gal")
    public ResponseEntity<Map<String, String>> integrarGAL() {
        try {
            log.info("Iniciando integración de Galicia");
            integracionService.integrarArchivo(rutaGAL, IntegrationService.TipoOrigen.GALICIA);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Datos de Galicia integrados correctamente");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error en integración GAL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Integra solo el archivo de Cataluña
     */
    @PostMapping("/cat")
    public ResponseEntity<Map<String, String>> integrarCAT() {
        try {
            log.info("Iniciando integración de Cataluña");
            integracionService.integrarArchivo(rutaCAT, IntegrationService.TipoOrigen.CATALUNA);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Datos de Cataluña integrados correctamente");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error en integración CAT", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Integra todos los archivos de una vez
     */
    @PostMapping("/all")
    public ResponseEntity<Map<String, String>> integrarTodos() {
        try {
            log.info("Iniciando integración completa");
            integracionService.integrarTodosLosArchivos(rutaCV, rutaGAL, rutaCAT);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Todos los datos integrados correctamente");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error en integración completa", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Endpoint de salud
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}