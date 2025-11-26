package com.elucesc.itvintegration.wrapper.impl;

import com.elucesc.itvintegration.wrapper.Wrapper;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Wrapper para Galicia
 * Detecta automáticamente el encoding del archivo CSV y lo convierte a JSON
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

        // Detectar encoding del archivo
        Charset detectedCharset = detectEncoding(fileContent);
        log.info("✓ Encoding detectado: {}", detectedCharset.name());

        try {
            // Convertir bytes a String con el encoding detectado
            String csvContent = new String(fileContent, detectedCharset);

            // Crear InputStream desde el String (ahora en UTF-8 internamente)
            InputStream contentStream = new ByteArrayInputStream(
                    csvContent.getBytes(StandardCharsets.UTF_8)
            );

            // Configurar esquema CSV con punto y coma como separador
            CsvSchema schema = CsvSchema.emptySchema()
                    .withHeader()
                    .withColumnSeparator(';')
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

            if (data.isEmpty()) {
                throw new IOException("El archivo CSV está vacío o no se pudo parsear correctamente");
            }

            log.info("✅ CSV parseado correctamente: {} registros", data.size());
            log.debug("Headers detectados: {}", data.get(0).keySet());
            log.debug("Primer registro: {}", data.get(0));

            // Convertir a JSON (UTF-8)
            String json = jsonMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(data);

            return json;

        } catch (Exception e) {
            log.error("❌ Error al parsear CSV con encoding {}", detectedCharset.name(), e);
            throw new IOException(
                    "Error al parsear CSV. Verifica que use punto y coma (;) como separador. " +
                            "Encoding detectado: " + detectedCharset.name(),
                    e
            );
        }
    }

    /**
     * Detecta el encoding del archivo usando UniversalDetector (juniversalchardet)
     * Esta librería es muy precisa y se usa en navegadores como Firefox
     */
    private Charset detectEncoding(byte[] fileContent) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);

        // Analizar el contenido
        detector.handleData(fileContent, 0, fileContent.length);
        detector.dataEnd();

        String detectedEncoding = detector.getDetectedCharset();
        detector.reset();

        if (detectedEncoding != null) {
            log.debug("UniversalDetector detectó: {}", detectedEncoding);

            // Normalizar nombres de charset
            detectedEncoding = normalizeCharsetName(detectedEncoding);

            try {
                return Charset.forName(detectedEncoding);
            } catch (Exception e) {
                log.warn("Charset detectado '{}' no es válido, usando fallback", detectedEncoding);
            }
        }

        // Fallback: Si no se detectó, usar ISO-8859-1 (más común en España)
        log.warn("No se pudo detectar encoding automáticamente, usando ISO-8859-1 como fallback");
        return Charset.forName("ISO-8859-1");
    }

    /**
     * Normaliza nombres de charset que pueden venir con diferentes variaciones
     */
    private String normalizeCharsetName(String charset) {
        if (charset == null) return null;

        String normalized = charset.toUpperCase().trim();

        // Mapear variaciones comunes
        switch (normalized) {
            case "WINDOWS-1252":
            case "CP1252":
                return "Windows-1252";
            case "ISO-8859-1":
            case "LATIN1":
                return "ISO-8859-1";
            case "UTF-8":
            case "UTF8":
                return "UTF-8";
            case "ISO-8859-15":
            case "LATIN9":
                return "ISO-8859-15";
            default:
                return charset;
        }
    }

    @Override
    public String getSourceFormat() {
        return "CSV";
    }
}