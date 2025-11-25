package com.elucesc.itvintegration.wrapper.impl;

import com.elucesc.itvintegration.wrapper.Wrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper para archivos que ya están en formato JSON
 * Simplemente lee el contenido sin transformación
 */
@Slf4j
@Component
public class CVWrapper implements Wrapper {

    @Override
    public String transformToJson(InputStream inputStream) throws IOException {
        log.info("Leyendo archivo JSON (sin transformación)");

        try {
            String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Archivo JSON leído exitosamente: {} caracteres", jsonContent.length());
            return jsonContent;
        } catch (IOException e) {
            log.error("Error al leer archivo JSON", e);
            throw new IOException("Error leyendo JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceFormat() {
        return "JSON";
    }
}