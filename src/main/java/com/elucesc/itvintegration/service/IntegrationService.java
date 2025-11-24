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
    private final OpenCageGeocodingService openCageGeocodingService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Autowired
    public IntegrationService(
            ProvinciaRepository provinciaRepository,
            LocalidadRepository localidadRepository,
            EstacionRepository estacionRepository,
            OpenCageGeocodingService openCageGeocodingService,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.provinciaRepository = provinciaRepository;
        this.localidadRepository = localidadRepository;
        this.estacionRepository = estacionRepository;
        this.openCageGeocodingService = openCageGeocodingService;
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
        Map<String, Long> localidadNombreACodigo = guardarLocalidades(localidades);

        // 3. Transformar estaciones
        List<Estacion> estaciones = wrapper.transformarEstaciones();
        log.info("Transformadas {} estaciones", estaciones.size());

        // 4. Vincular estaciones con localidades usando los IDs guardados
        vincularEstacionesConLocalidades(estaciones, wrapper, localidadNombreACodigo);

        // 5. Guardar estaciones con cod_localidad establecido
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
        Resource resource = resourceLoader.getResource(rutaArchivo);

        if (!resource.exists()) {
            throw new IOException("No se encontró el archivo: " + rutaArchivo);
        }

        log.debug("Leyendo archivo: {}", resource.getFilename());

        try (InputStream inputStream = resource.getInputStream()) {
            switch (tipoOrigen) {
                case COMUNIDAD_VALENCIANA:
                    List<EstacionCV> estacionesCV = objectMapper.readValue(inputStream, new TypeReference<List<EstacionCV>>() {});
                    return new CVWrapper(estacionesCV, openCageGeocodingService);

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
            Localidad existente = localidadRepository.findByNombreAndCodProvincia(
                    localidad.getNombre(),
                    localidad.getCodProvincia()
            );

            if (existente != null) {
                nombreACodigo.put(localidad.getNombre(), existente.getCodigo());
                log.debug("Localidad {} ya existe con código {}",
                        localidad.getNombre(), existente.getCodigo());
            } else {
                Localidad guardada = localidadRepository.save(localidad);
                nombreACodigo.put(guardada.getNombre(), guardada.getCodigo());
                log.debug("Localidad guardada: {} con código {}",
                        guardada.getNombre(), guardada.getCodigo());
            }
        }

        return nombreACodigo;
    }

    private void vincularEstacionesConLocalidades(List<Estacion> estaciones,
                                                  ItvDataWrapper wrapper,
                                                  Map<String, Long> localidadNombreACodigo) {
        Map<Integer, String> indiceEstacionALocalidad = wrapper.obtenerMapaEstacionLocalidad();

        for (int i = 0; i < estaciones.size(); i++) {
            Estacion estacion = estaciones.get(i);
            String nombreLocalidad = indiceEstacionALocalidad.get(i);

            if (nombreLocalidad != null && localidadNombreACodigo.containsKey(nombreLocalidad)) {
                Long codLocalidad = localidadNombreACodigo.get(nombreLocalidad);
                estacion.setCodLocalidad(codLocalidad);
                log.debug("Vinculada estación '{}' con localidad '{}' (código: {})",
                        estacion.getNombre(), nombreLocalidad, codLocalidad);
            } else {
                log.warn("No se pudo vincular estación '{}' con localidad '{}'",
                        estacion.getNombre(), nombreLocalidad);
            }
        }
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