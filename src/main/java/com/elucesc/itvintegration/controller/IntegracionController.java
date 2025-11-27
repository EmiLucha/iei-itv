package com.elucesc.itvintegration.controller;

import com.elucesc.itvintegration.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Integración ITV", description = "Endpoints para integrar datos de distintas comunidades autónomas")
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

    @Operation(summary = "Integrar datos de Comunidad Valenciana",
            description = "Procesa e integra únicamente el archivo de la Comunidad Valenciana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Integración realizada correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno al integrar los datos")
    })
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

    @Operation(summary = "Integrar datos de Galicia",
            description = "Procesa e integra únicamente el archivo de Galicia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Integración realizada correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno al integrar los datos")
    })
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

    @Operation(summary = "Integrar datos de Cataluña",
            description = "Procesa e integra únicamente el archivo de Cataluña.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Integración realizada correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno al integrar los datos")
    })
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

    @Operation(summary = "Integración completa",
            description = "Integra los archivos de todas las comunidades disponibles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Integración completa realizada correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno al integrar los datos")
    })
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

    @Operation(summary = "Endpoint de salud", description = "Verifica que el servicio está activo.")
    @ApiResponse(responseCode = "200", description = "Servicio activo")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
