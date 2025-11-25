package com.elucesc.itvintegration.wrapper;

import com.elucesc.itvintegration.wrapper.impl.GALWrapper;
import com.elucesc.itvintegration.wrapper.impl.CVWrapper;
import com.elucesc.itvintegration.wrapper.impl.CATWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory para obtener el wrapper adecuado según la extensión del archivo
 */
@Slf4j
@Component
public class WrapperFactory {

    private final GALWrapper GALWrapper;
    private final CATWrapper CATWrapper;
    private final CVWrapper CVWrapper;

    @Autowired
    public WrapperFactory(
            GALWrapper GALWrapper,
            CATWrapper CATWrapper,
            CVWrapper CVWrapper) {
        this.GALWrapper = GALWrapper;
        this.CATWrapper = CATWrapper;
        this.CVWrapper = CVWrapper;
    }

    /**
     * Obtiene el wrapper adecuado según la extensión del archivo
     * @param fileName nombre del archivo
     * @return Wrapper correspondiente
     */
    public Wrapper getWrapper(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del archivo no puede ser nulo o vacío");
        }

        String extension = getFileExtension(fileName).toLowerCase();

        log.debug("Obteniendo wrapper para extensión: {}", extension);

        switch (extension) {
            case "csv":
                return GALWrapper;
            case "xml":
                return CATWrapper;
            case "json":
                return CVWrapper;
            default:
                throw new IllegalArgumentException(
                        "Formato de archivo no soportado: " + extension +
                                ". Formatos válidos: csv, xml, json"
                );
        }
    }

    /**
     * Extrae la extensión de un nombre de archivo
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("El archivo no tiene extensión válida: " + fileName);
        }
        return fileName.substring(lastDotIndex + 1);
    }
}