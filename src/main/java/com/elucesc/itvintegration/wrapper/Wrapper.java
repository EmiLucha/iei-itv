package com.elucesc.itvintegration.wrapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface para wrappers que transforman diferentes formatos de archivo a JSON
 */
public interface Wrapper {

    /**
     * Transforma el contenido del archivo a formato JSON
     * @param inputStream InputStream del archivo original
     * @return String con el contenido en formato JSON
     * @throws IOException si hay error leyendo o transformando el archivo
     */
    String transformToJson(InputStream inputStream) throws IOException;

    /**
     * Indica el formato de origen que maneja este wrapper
     * @return String con el formato (CSV, XML, etc.)
     */
    String getSourceFormat();
}