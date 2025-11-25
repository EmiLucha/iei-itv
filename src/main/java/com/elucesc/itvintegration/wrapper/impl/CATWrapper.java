package com.elucesc.itvintegration.wrapper.impl;

import com.elucesc.itvintegration.wrapper.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper específico para archivos XML de Cataluña
 * Elimina los elementos <response> y <row> padre, extrayendo solo los <row> internos con datos
 */
@Slf4j
@Component
public class CATWrapper implements Wrapper {

    private final ObjectMapper objectMapper;

    public CATWrapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String transformToJson(InputStream inputStream) throws IOException {
        log.info("Iniciando transformación de XML de Cataluña a JSON");

        try {
            // Leer XML completo
            String xmlContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("XML leído: {} caracteres", xmlContent.length());

            // Parsear XML usando DOM
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(
                    new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))
            );

            // Normalizar el documento
            document.getDocumentElement().normalize();

            // Obtener todos los <row> del documento
            NodeList rowNodes = document.getElementsByTagName("row");
            log.debug("Encontrados {} elementos <row> en total", rowNodes.getLength());

            List<Map<String, Object>> estaciones = new ArrayList<>();

            // Procesar cada <row>
            for (int i = 0; i < rowNodes.getLength(); i++) {
                Node rowNode = rowNodes.item(i);

                if (rowNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element rowElement = (Element) rowNode;

                    // Extraer datos del <row>
                    Map<String, Object> estacion = extraerDatosDeRow(rowElement);

                    // Solo agregar si tiene datos reales (ignorar <row> padre vacío)
                    if (!estacion.isEmpty() && tieneDatosReales(estacion)) {
                        estaciones.add(estacion);
                        log.trace("Estación agregada: {}", estacion.get("denominaci"));
                    } else {
                        log.trace("Row vacío ignorado (probablemente el contenedor padre)");
                    }
                }
            }

            log.info("✅ Procesadas {} estaciones válidas desde XML", estaciones.size());

            // Convertir a JSON
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(estaciones);
            log.info("XML transformado exitosamente a JSON");

            return json;

        } catch (Exception e) {
            log.error("Error al transformar XML de Cataluña a JSON", e);
            throw new IOException("Error transformando XML a JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Extrae los datos de un elemento <row> y los convierte en un Map
     */
    private Map<String, Object> extraerDatosDeRow(Element rowElement) {
        Map<String, Object> datos = new LinkedHashMap<>();
        NodeList children = rowElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String tagName = element.getTagName();

                // Ignorar elementos con atributos especiales de metadatos
                if (tagName.startsWith("_") || tagName.equals("geocoded_column")) {
                    continue;
                }

                // Ignorar elementos <row> anidados (ya se procesan en el bucle principal)
                if (tagName.equals("row")) {
                    continue;
                }

                // Manejar elementos con atributo 'url'
                if (element.hasAttribute("url")) {
                    datos.put(tagName, element.getAttribute("url"));
                } else {
                    // Obtener el texto del elemento
                    String textContent = element.getTextContent();
                    if (textContent != null && !textContent.trim().isEmpty()) {
                        datos.put(tagName, textContent.trim());
                    }
                }
            }
        }

        return datos;
    }

    /**
     * Verifica si un Map tiene datos reales (campos de estación)
     * Esto ayuda a filtrar el <row> padre vacío que solo contiene otros <row>
     */
    private boolean tieneDatosReales(Map<String, Object> datos) {
        // Un <row> con datos reales debe tener campos como "estaci", "denominaci", etc.
        // Si solo tiene otros <row> anidados o está vacío, no es válido

        // Verificar que tenga al menos uno de los campos esperados
        return datos.containsKey("estaci") ||
                datos.containsKey("denominaci") ||
                datos.containsKey("municipi");
    }

    @Override
    public String getSourceFormat() {
        return "XML";
    }
}