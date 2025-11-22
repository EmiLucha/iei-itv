package com.elucesc.itvintegration.service;

import com.elucesc.itvintegration.dto.cat.EstacionCAT;
import com.elucesc.itvintegration.dto.cv.EstacionCV;
import com.elucesc.itvintegration.dto.gal.EstacionGAL;
import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.Localidad;
import com.elucesc.itvintegration.model.Provincia;
import com.elucesc.itvintegration.repository.EstacionRepository;
import com.elucesc.itvintegration.repository.LocalidadRepository;
import com.elucesc.itvintegration.repository.ProvinciaRepository;
import com.elucesc.itvintegration.wrapper.ItvDataWrapper;
import com.elucesc.itvintegration.wrapper.impl.CATWrapper;
import com.elucesc.itvintegration.wrapper.impl.CVWrapper;
import com.elucesc.itvintegration.wrapper.impl.GALWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IntegrationService {

    private final ProvinciaRepository provinciaRepository;
    private final LocalidadRepository localidadRepository;
    private final EstacionRepository estacionRepository;
    private final GeocodingService geocodingService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Autowired
    public IntegrationService(
            ProvinciaRepository provinciaRepository,
            LocalidadRepository localidadRepository,
            EstacionRepository estacionRepository,
            GeocodingService geocodingService,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.provinciaRepository = provinciaRepository;
        this.localidadRepository = localidadRepository;
        this.estacionRepository = estacionRepository;
        this.geocodingService = geocodingService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Integra datos de un archivo JSON según su tipo
     */
    @Transactional
    public void integrarArchivo(String rutaArchivo, TipoOrigen tipoOrigen) throws IOException {
        log.info("Iniciando integración de archivo: {} (Tipo: {})", rutaArchivo, tipoOrigen);

        ItvDataWrapper wrapper = crearWrapper(rutaArchivo, tipoOrigen);

        // 1. Transformar y guardar provincias
        List<Provincia> provincias = wrapper.transformarProvincias();
        log.info("Transformadas {} provincias", provincias.size());
        guardarProvincias(provincias);

        // 2. Transformar y guardar localidades
        List<Localidad> localidades = wrapper.transformarLocalidades();
        log.info("Transformadas {} localidades", localidades.size());
        guardarLocalidades(localidades);

        // 3. Transformar y guardar estaciones (ahora las localidades ya tienen IDs)
        List<Estacion> estaciones = wrapper.transformarEstaciones();
        log.info("Transformadas {} estaciones", estaciones.size());
        guardarEstaciones(estaciones);

        log.info("Integración completada exitosamente");
    }

    /**
     * Integra todos los archivos de una vez
     */
    @Transactional
    public void integrarTodosLosArchivos(
            String rutaCV,
            String rutaGAL,
            String rutaCAT) throws IOException {

        log.info("Iniciando integración completa de todos los archivos");

        integrarArchivo(rutaCV, TipoOrigen.COMUNIDAD_VALENCIANA);
        integrarArchivo(rutaGAL, TipoOrigen.GALICIA);
        integrarArchivo(rutaCAT, TipoOrigen.CATALUNA);

        log.info("Integración completa finalizada");
    }

    private ItvDataWrapper crearWrapper(String rutaArchivo, TipoOrigen tipoOrigen) throws IOException {
        // Cargar el recurso desde el classpath
        Resource resource = resourceLoader.getResource(rutaArchivo);

        if (!resource.exists()) {
            throw new IOException("No se encontró el archivo: " + rutaArchivo);
        }

        log.debug("Leyendo archivo: {}", resource.getFilename());

        try (InputStream inputStream = resource.getInputStream()) {
            switch (tipoOrigen) {
                case COMUNIDAD_VALENCIANA:
                    List<EstacionCV> estacionesCV = objectMapper.readValue(inputStream, new TypeReference<List<EstacionCV>>() {});
                    return new CVWrapper(estacionesCV, geocodingService);

                case GALICIA:
                    List<EstacionGAL> estacionesGAL = objectMapper.readValue(inputStream, new TypeReference<List<EstacionGAL>>() {});
                    return new GALWrapper(estacionesGAL);

                case CATALUNA:
                    List<EstacionCAT> estacionesCAT = objectMapper.readValue(inputStream, new TypeReference<List<EstacionCAT>>() {});
                    return new CATWrapper(estacionesCAT);

                default:
                    throw new IllegalArgumentException("Tipo de origen no soportado: " + tipoOrigen);
            }
        }
    }

    private void guardarProvincias(List<Provincia> provincias) {
        for (Provincia provincia : provincias) {
            // Verificar si ya existe
            if (!provinciaRepository.existsByCodigo(provincia.getCodigo())) {
                provinciaRepository.save(provincia);
                log.debug("Provincia guardada: {}", provincia.getNombre());
            } else {
                log.debug("Provincia {} ya existe, omitiendo", provincia.getNombre());
            }
        }
    }

    private Map<String, Long> guardarLocalidades(List<Localidad> localidades) {
        Map<String, Long> nombreACodigo = new HashMap<>();

        for (Localidad localidad : localidades) {
            // Verificar si ya existe por nombre y provincia
            Localidad existente = localidadRepository.findByNombreAndCodProvincia(
                    localidad.getNombre(),
                    localidad.getCodProvincia()
            );

            if (existente != null) {
                // Si ya existe, usar el código existente
                nombreACodigo.put(localidad.getNombre(), existente.getCodigo());
                log.debug("Localidad {} ya existe con código {}",
                        localidad.getNombre(), existente.getCodigo());
            } else {
                // Guardar nueva localidad (el código será autogenerado)
                Localidad guardada = localidadRepository.save(localidad);
                nombreACodigo.put(guardada.getNombre(), guardada.getCodigo());
                log.debug("Localidad guardada: {} con código {}",
                        guardada.getNombre(), guardada.getCodigo());
            }
        }

        return nombreACodigo;
    }

    private void actualizarCodigosLocalidad(List<Estacion> estaciones,
                                            Map<String, Long> localidadNombreACodigo) {
        // Este método necesitará acceso al nombre de la localidad
        // Por ahora, las estaciones ya tienen codLocalidad asignado en el wrapper
        // pero si falla, podríamos necesitar otra estrategia
    }

    private void guardarEstaciones(List<Estacion> estaciones) {
        int guardadas = 0;
        int fallidas = 0;

        for (Estacion estacion : estaciones) {
            try {
                estacionRepository.save(estacion);
                guardadas++;
                log.debug("Estación guardada: {}", estacion.getNombre());
            } catch (Exception e) {
                fallidas++;
                log.error("Error guardando estación '{}': {}",
                        estacion.getNombre(), e.getMessage());
            }
        }

        log.info("Estaciones guardadas: {}, fallidas: {}", guardadas, fallidas);
    }

    public enum TipoOrigen {
        COMUNIDAD_VALENCIANA,
        GALICIA,
        CATALUNA
    }
}