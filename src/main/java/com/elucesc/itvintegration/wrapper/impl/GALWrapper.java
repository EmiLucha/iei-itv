
package com.elucesc.itvintegration.wrapper.impl;

import com.elucesc.itvintegration.wrapper.Wrapper;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
        import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Wrapper para Galicia
 * Convierte CSV a JSON usando Jackson CSV
 * El CSV de Galicia usa punto y coma (;) como separador
 */
@Slf4j
@Component
public class GALWrapper implements Wrapper {

    private final CsvMapper csvMapper;
    private final ObjectMapper jsonMapper;

    public GALWrapper() {
        this.csvMapper = new CsvMapper();
        this.jsonMapper = new ObjectMapper();
    }

    @Override
    public String transformToJson(InputStream inputStream) throws IOException {
        log.info("Convirtiendo CSV de Galicia a JSON");

        // Leer todo el contenido en bytes
        byte[] fileContent = inputStream.readAllBytes();
        log.debug("Archivo leído: {} bytes", fileContent.length);

        // Intentar con diferentes encodings
        Charset[] encodings = {
                Charset.forName("ISO-8859-1"),     // Latin-1 (más común en España)
                Charset.forName("Windows-1252"),   // Windows Latin-1
                StandardCharsets.UTF_8,            // UTF-8
                Charset.forName("ISO-8859-15")     // Latin-9
        };

        IOException lastException = null;

        for (Charset encoding : encodings) {
            try {
                log.debug("Intentando parsear CSV con encoding: {}", encoding.name());

                // Convertir bytes a String con el encoding
                String csvContent = new String(fileContent, encoding);

                // Crear InputStream desde el String
                InputStream contentStream = new ByteArrayInputStream(
                        csvContent.getBytes(StandardCharsets.UTF_8)
                );

                // IMPORTANTE: Configurar punto y coma como separador
                CsvSchema schema = CsvSchema.emptySchema()
                        .withHeader()
                        .withColumnSeparator(';')  // ← PUNTO Y COMA, no coma
                        .withQuoteChar('"')
                        .withLineSeparator("\n")
                        .withNullValue("")
                        .withSkipFirstDataRow(false);

                // Leer el CSV
                MappingIterator<Map<String, String>> iterator = csvMapper
                        .readerFor(Map.class)
                        .with(schema)
                        .readValues(contentStream);

                List<Map<String, String>> data = iterator.readAll();

                // Verificar que los datos tengan sentido
                if (!data.isEmpty() && verificarDatosValidos(data)) {
                    log.info("✅ CSV leído correctamente con {}: {} registros",
                            encoding.name(), data.size());

                    // Mostrar headers para debug
                    if (!data.isEmpty()) {
                        log.debug("Headers detectados: {}", data.get(0).keySet());
                        log.debug("Primer registro: {}", data.get(0));
                    }

                    // Convertir a JSON
                    String json = jsonMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(data);

                    log.debug("JSON generado (primeros 500 chars): {}",
                            json.substring(0, Math.min(500, json.length())));

                    return json;
                }

                log.debug("Datos no válidos con encoding {}", encoding.name());

            } catch (Exception e) {
                log.debug("❌ Falló con encoding {}: {}", encoding.name(), e.getMessage());
                lastException = new IOException(e);
            }
        }

        // Si ninguno funcionó
        log.error("No se pudo leer el CSV con ningún encoding probado");
        throw new IOException(
                "Error al parsear CSV. Verifica que use punto y coma (;) como separador. " +
                        "Probados encodings: ISO-8859-1, Windows-1252, UTF-8, ISO-8859-15.",
                lastException
        );
    }

    /**
     * Verifica que los datos no contengan caracteres raros
     */
    private boolean verificarDatosValidos(List<Map<String, String>> data) {
        if (data.isEmpty()) {
            return false;
        }

        Map<String, String> firstRecord = data.get(0);

        // Verificar que tenga varios campos (no solo uno gigante)
        if (firstRecord.size() < 3) {
            log.debug("Pocos campos detectados: {}. Puede ser separador incorrecto.",
                    firstRecord.size());
            return false;
        }

        // Verificar que no haya caracteres de reemplazo
        for (String key : firstRecord.keySet()) {
            if (key.contains("�") || key.contains("\uFFFD")) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getSourceFormat() {
        return "CSV";
    }

}