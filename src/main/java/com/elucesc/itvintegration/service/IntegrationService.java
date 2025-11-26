package com.elucesc.itvintegration.service;

import com.elucesc.itvintegration.dto.cat.EstacionCAT;
import com.elucesc.itvintegration.dto.cv.EstacionCV;
import com.elucesc.itvintegration.dto.gal.EstacionGAL;
import com.elucesc.itvintegration.extractor.ItvDataExtractor;
import com.elucesc.itvintegration.extractor.impl.CATExtractor;
import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.Localidad;
import com.elucesc.itvintegration.model.Provincia;
import com.elucesc.itvintegration.repository.EstacionRepository;
import com.elucesc.itvintegration.repository.LocalidadRepository;
import com.elucesc.itvintegration.repository.ProvinciaRepository;
import com.elucesc.itvintegration.extractor.impl.CVExtractor;
import com.elucesc.itvintegration.extractor.impl.GALExtractor;
import com.elucesc.itvintegration.wrapper.Wrapper;
import com.elucesc.itvintegration.wrapper.WrapperFactory;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class IntegrationService {

    private final ProvinciaRepository provinciaRepository;
    private final LocalidadRepository localidadRepository;
    private final EstacionRepository estacionRepository;
    private final SeleniumGeocodingService seleniumGeocodingService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final WrapperFactory wrapperFactory;

    @Autowired
    public IntegrationService(
            ProvinciaRepository provinciaRepository,
            LocalidadRepository localidadRepository,
            EstacionRepository estacionRepository,
            SeleniumGeocodingService seleniumGeocodingService,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            WrapperFactory wrapperFactory) {
        this.provinciaRepository = provinciaRepository;
        this.localidadRepository = localidadRepository;
        this.estacionRepository = estacionRepository;
        this.seleniumGeocodingService = seleniumGeocodingService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.wrapperFactory = wrapperFactory;
    }

    /**
     * Integra datos de un archivo (CSV, XML o JSON) según su tipo
     */
    @Transactional
    public void integrarArchivo(String rutaArchivo, TipoOrigen tipoOrigen) throws IOException {
        log.info("Iniciando integración de archivo: {} (Tipo: {})", rutaArchivo, tipoOrigen);

        ItvDataExtractor extractor = crearExtractor(rutaArchivo, tipoOrigen);

        // 1. Transformar y guardar provincias
        List<Provincia> provincias = extractor.transformarProvincias();
        log.info("Transformadas {} provincias", provincias.size());
        guardarProvincias(provincias);

        // 2. Transformar y guardar localidades
        List<Localidad> localidades = extractor.transformarLocalidades();
        log.info("Transformadas {} localidades", localidades.size());
        Map<String, Long> localidadNombreACodigo = guardarLocalidades(localidades);

        // 3. Transformar estaciones
        List<Estacion> estaciones = extractor.transformarEstaciones();
        log.info("Transformadas {} estaciones", estaciones.size());

        // 4. Vincular estaciones con localidades
        vincularEstacionesConLocalidades(estaciones, extractor, localidadNombreACodigo);

        // 5. Guardar estaciones
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

    /**
     * Crea el extractor adecuado según el tipo de origen
     * Primero transforma el archivo a JSON usando el wrapper correspondiente
     */
    private ItvDataExtractor crearExtractor(String rutaArchivo, TipoOrigen tipoOrigen) throws IOException {
        Resource resource = resourceLoader.getResource(rutaArchivo);

        if (!resource.exists()) {
            throw new IOException("No se encontró el archivo: " + rutaArchivo);
        }

        String fileName = resource.getFilename();
        log.info("Procesando archivo: {} (formato: {})", fileName,
                fileName != null ? fileName.substring(fileName.lastIndexOf('.') + 1) : "desconocido");

        // Obtener wrapper según la extensión del archivo
        Wrapper wrapper = wrapperFactory.getWrapper(fileName);

        String jsonContent;
        try (InputStream inputStream = resource.getInputStream()) {
            // Transformar a JSON usando el wrapper
            jsonContent = wrapper.transformToJson(inputStream);
            log.debug("Contenido transformado a JSON: {} caracteres", jsonContent.length());
        }

        // Parsear JSON y crear extractor
        switch (tipoOrigen) {
            case COMUNIDAD_VALENCIANA:
                List<EstacionCV> estacionesCV = objectMapper.readValue(
                        jsonContent,
                        new TypeReference<List<EstacionCV>>() {}
                );
                log.info("Parseadas {} estaciones de Comunidad Valenciana", estacionesCV.size());
                return new CVExtractor(estacionesCV, seleniumGeocodingService);

            case GALICIA:
                List<EstacionGAL> estacionesGAL = objectMapper.readValue(
                        jsonContent,
                        new TypeReference<List<EstacionGAL>>() {}
                );
                log.info("Parseadas {} estaciones de Galicia", estacionesGAL.size());
                return new GALExtractor(estacionesGAL);

            case CATALUNA:
                List<EstacionCAT> estacionesCAT = objectMapper.readValue(
                        jsonContent,
                        new TypeReference<List<EstacionCAT>>() {}
                );
                log.info("Parseadas {} estaciones de Cataluña", estacionesCAT.size());
                return new CATExtractor(estacionesCAT);

            default:
                throw new IllegalArgumentException("Tipo de origen no soportado: " + tipoOrigen);
        }
    }

    private void guardarProvincias(List<Provincia> provincias) {
        log.info("=== GUARDANDO PROVINCIAS ===");

        for (Provincia provincia : provincias) {
            log.debug("Intentando guardar provincia: código={}, nombre='{}'",
                    provincia.getCodigo(), provincia.getNombre());

            if (!provinciaRepository.existsByCodigo(provincia.getCodigo())) {
                provinciaRepository.save(provincia);
                log.info("✅ Provincia guardada: {} (código: {})",
                        provincia.getNombre(), provincia.getCodigo());
            } else {
                log.debug("⚠️ Provincia {} (código: {}) ya existe, omitiendo",
                        provincia.getNombre(), provincia.getCodigo());
            }
        }

        // Mostrar todas las provincias guardadas
        List<Provincia> todasProvincias = provinciaRepository.findAll();
        log.info("=== PROVINCIAS EN BD: {} ===", todasProvincias.size());
        todasProvincias.forEach(p ->
                log.debug("  - Código: {}, Nombre: {}", p.getCodigo(), p.getNombre())
        );
    }

    private Map<String, Long> guardarLocalidades(List<Localidad> localidades) {
        Map<String, Long> nombreACodigo = new HashMap<>();

        log.info("=== GUARDANDO LOCALIDADES ===");

        for (Localidad localidad : localidades) {
            // VALIDACIÓN: Verificar que la provincia existe
            if (localidad.getCodProvincia() != null &&
                    !provinciaRepository.existsByCodigo(localidad.getCodProvincia())) {

                log.error("❌ ERROR: Localidad '{}' referencia provincia inexistente: código={}",
                        localidad.getNombre(), localidad.getCodProvincia());
                log.error("   Provincias disponibles en BD: {}",
                        provinciaRepository.findAll().stream()
                                .map(p -> p.getCodigo() + "=" + p.getNombre())
                                .collect(Collectors.joining(", ")));

                // Saltar esta localidad en lugar de fallar
                log.warn("⚠️ Saltando localidad '{}' por provincia inválida", localidad.getNombre());
                continue;
            }

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
                log.debug("✅ Localidad guardada: {} con código {} (provincia: {})",
                        guardada.getNombre(), guardada.getCodigo(), guardada.getCodProvincia());
            }
        }

        return nombreACodigo;
    }

    private void vincularEstacionesConLocalidades(
            List<Estacion> estaciones,
            ItvDataExtractor extractor,
            Map<String, Long> localidadNombreACodigo) {

        Map<Integer, String> indiceEstacionALocalidad = extractor.obtenerMapaEstacionLocalidad();

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