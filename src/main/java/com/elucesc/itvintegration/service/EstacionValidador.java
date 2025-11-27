package com.elucesc.itvintegration.service;

import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.TipoEstacion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de estaciones ITV para detectar datos defectuosos
 * REGLAS ESTRICTAS:
 * 1. Coordenadas (lat/lon), contacto y CP son OBLIGATORIOS para TODAS las estaciones FIJAS
 * 2. EXCEPCIÓN: Estaciones MÓVILES o OTROS de Valencia pueden tener estos campos nulos
 * 3. CP debe tener formato español válido (01000-52999)
 * 4. Localidad es OBLIGATORIA solo para estaciones FIJAS
 */
@Slf4j
@Component
public class EstacionValidador {

    /**
     * Valida una estación y retorna la lista de errores encontrados
     * @return Lista de errores (vacía si no hay errores)
     */
    public List<String> validar(Estacion estacion) {
        List<String> errores = new ArrayList<>();

        // Detectar si es estación móvil/otros de Valencia (ÚNICA EXCEPCIÓN permitida)
        boolean esExcepcionValenciana = esEstacionExcepcionValenciana(estacion);

        // 1. VALIDACIÓN DE COORDENADAS (OBLIGATORIAS excepto móviles/otros de Valencia)
        if (estacion.getLatitud() == null || estacion.getLongitud() == null) {
            if (!esExcepcionValenciana) {
                errores.add("Coordenadas nulas (obligatorias). Latitud: " + estacion.getLatitud() +
                        ", Longitud: " + estacion.getLongitud());
            }
        } else {
            // Si existen coordenadas, validar que sean correctas
            if (!esCoordenadaValida(estacion.getLatitud(), estacion.getLongitud())) {
                errores.add(String.format("Coordenadas fuera de rango válido (lat=%.6f, lon=%.6f)",
                        estacion.getLatitud(), estacion.getLongitud()));
            }
        }

        // 2. VALIDACIÓN DE CÓDIGO POSTAL (OBLIGATORIO excepto móviles/otros de Valencia)
        if (estacion.getCodigoPostal() == null) {
            if (!esExcepcionValenciana) {
                errores.add("Código postal nulo (obligatorio)");
            }
        } else {
            // Validar formato del CP
            if (!validarCodigoPostal(estacion.getCodigoPostal())) {
                errores.add("Código postal inválido: " + estacion.getCodigoPostal() +
                        " (debe ser 5 dígitos entre 01000-52999)");
            }
        }

        // 3. VALIDACIÓN DE CONTACTO (OBLIGATORIO excepto móviles/otros de Valencia)
        if (estacion.getContacto() == null || estacion.getContacto().trim().isEmpty()) {
            if (!esExcepcionValenciana) {
                errores.add("Contacto nulo o vacío (obligatorio)");
            }
        }

        // 4. VALIDACIÓN DE VINCULACIÓN CON LOCALIDAD (OBLIGATORIO solo para estaciones fijas)
        if (estacion.getCodLocalidad() == null) {
            // Solo es obligatorio para estaciones fijas
            if (estacion.getTipo() == TipoEstacion.ESTACION_FIJA) {
                errores.add("Estación sin vincular a ninguna localidad (cod_localidad nulo)");
            }
            // Las estaciones móviles y otros no requieren localidad específica
        }

        // 5. VALIDACIÓN DE CAMPOS BÁSICOS OBLIGATORIOS
        if (estacion.getNombre() == null || estacion.getNombre().trim().isEmpty()) {
            errores.add("Nombre de estación vacío");
        }

        if (estacion.getTipo() == null) {
            errores.add("Tipo de estación no especificado");
        }

        return errores;
    }

    /**
     * Detecta si una estación es MÓVIL o OTROS de Valencia
     * Esta es la ÚNICA excepción donde se permiten campos nulos
     */
    private boolean esEstacionExcepcionValenciana(Estacion estacion) {
        // Verificar tipo de estación
        if (estacion.getTipo() != TipoEstacion.ESTACION_MOVIL &&
                estacion.getTipo() != TipoEstacion.OTROS) {
            return false;
        }

        // Verificar que sea de Valencia (CP 03xxx, 12xxx o 46xxx)
        return esDeValencia(estacion);
    }

    /**
     * Detecta si una estación es de Valencia basándose en el CP, dirección, nombre o contacto
     */
    private boolean esDeValencia(Estacion estacion) {
        // 1. Intentar detectar por código postal si existe
        if (estacion.getCodigoPostal() != null) {
            String cp = String.format("%05d", estacion.getCodigoPostal());
            String prefijo = cp.substring(0, 2);
            int provincia = Integer.parseInt(prefijo);

            // Provincias de Valencia: 03 (Alicante), 12 (Castellón), 46 (Valencia)
            if (provincia == 3 || provincia == 12 || provincia == 46) {
                return true;
            }
        }

        // 2. Detectar por contacto (email con patrón itv####@sitval.com)
        if (estacion.getContacto() != null && estacion.getContacto().contains("@sitval.com")) {
            log.debug("Estación detectada como valenciana por email: {}", estacion.getContacto());
            return true;
        }

        // 3. Detectar por dirección/nombre
        String texto = (estacion.getDireccion() != null ? estacion.getDireccion() : "") + " " +
                (estacion.getNombre() != null ? estacion.getNombre() : "");
        texto = texto.toLowerCase();

        boolean detectadaPorTexto = texto.contains("valencia") ||
                texto.contains("alicante") ||
                texto.contains("castellón") ||
                texto.contains("castellon");

        if (detectadaPorTexto) {
            log.debug("Estación detectada como valenciana por texto: {}", estacion.getNombre());
        }

        return detectadaPorTexto;
    }

    /**
     * Valida que las coordenadas estén dentro de rangos válidos
     * - Rango global: lat [-90, 90], lon [-180, 180]
     * - Rango España (con margen): lat [27, 44], lon [-19, 5]
     */
    private boolean esCoordenadaValida(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return false;
        }

        // Validación de rangos globales (CRÍTICO)
        if (lat < -90 || lat > 90) {
            log.error("⚠️ Latitud {} fuera del rango global [-90, 90]", lat);
            return false;
        }

        if (lon < -180 || lon > 180) {
            log.error("⚠️ Longitud {} fuera del rango global [-180, 180]", lon);
            return false;
        }

        // Validación de rango para España (con margen para Canarias, etc.)
        if (lat < 27 || lat > 44) {
            log.warn("⚠️ Latitud {} fuera del rango típico de España (27-44)", lat);
            // No rechazar, solo advertir
        }

        if (lon < -19 || lon > 5) {
            log.warn("⚠️ Longitud {} fuera del rango típico de España (-19 a 5)", lon);
            // No rechazar, solo advertir
        }

        return true;
    }

    /**
     * Valida que el código postal tenga formato español correcto
     * CP español: 5 dígitos (01000 - 52999)
     */
    private boolean validarCodigoPostal(Long codigoPostal) {
        if (codigoPostal == null) {
            return false;
        }

        // Validar que no sea negativo
        if (codigoPostal < 0) {
            log.error("⚠️ Código postal negativo: {}", codigoPostal);
            return false;
        }

        // Formatear con ceros a la izquierda
        String cp = String.format("%05d", codigoPostal);

        // Validar longitud (debe ser exactamente 5 dígitos)
        if (cp.length() != 5) {
            log.error("⚠️ Código postal con longitud incorrecta: {} (formateado: {})", codigoPostal, cp);
            return false;
        }

        // Validar rango de provincia (01 a 52)
        try {
            int provincia = Integer.parseInt(cp.substring(0, 2));
            if (provincia < 1 || provincia > 52) {
                log.error("⚠️ Código de provincia inválido: {} (CP: {})", provincia, cp);
                return false;
            }
        } catch (NumberFormatException e) {
            log.error("⚠️ Error parseando código de provincia del CP: {}", cp);
            return false;
        }

        // Validar que el CP completo esté en rango válido
        long cpNumerico = Long.parseLong(cp);
        if (cpNumerico < 1000 || cpNumerico > 52999) {
            log.error("⚠️ Código postal fuera del rango español (01000-52999): {}", cp);
            return false;
        }

        return true;
    }

    /**
     * Intenta corregir automáticamente algunos errores comunes
     * IMPORTANTE: Solo corrige errores obvios, no inventa datos
     */
    public Estacion intentarCorregir(Estacion estacion, List<String> errores) {
        Estacion corregida = estacion;

        for (String error : errores) {
            // Corregir coordenadas claramente erróneas (ej: 412.xxx → rechazar)
            if (error.contains("Coordenadas fuera de rango")) {
                corregida = corregirCoordenadas(corregida);
            }

            // NO intentar corregir campos nulos - si son obligatorios y están nulos, rechazar
            // NO intentar corregir CPs inválidos - si el formato es incorrecto, rechazar
        }

        return corregida;
    }

    /**
     * Corrige coordenadas obviamente erróneas estableciéndolas a null
     * Si las coordenadas son inválidas y la estación NO es excepción valenciana, será rechazada
     */
    private Estacion corregirCoordenadas(Estacion estacion) {
        Double lat = estacion.getLatitud();
        Double lon = estacion.getLongitud();

        boolean coordenadasInvalidas = false;

        // Latitud fuera de rango global
        if (lat != null && (lat < -90 || lat > 90)) {
            log.warn("Latitud {} fuera de rango [-90, 90], estableciendo a null", lat);
            coordenadasInvalidas = true;
        }

        // Longitud fuera de rango global
        if (lon != null && (lon < -180 || lon > 180)) {
            log.warn("Longitud {} fuera de rango [-180, 180], estableciendo a null", lon);
            coordenadasInvalidas = true;
        }

        if (coordenadasInvalidas) {
            estacion.setLatitud(null);
            estacion.setLongitud(null);
        }

        return estacion;
    }

    /**
     * Genera un informe de validación legible
     */
    public String generarInforme(Estacion estacion, List<String> errores) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n❌ ESTACIÓN DEFECTUOSA: ").append(estacion.getNombre());
        sb.append("\n   Tipo: ").append(estacion.getTipo());
        sb.append("\n   Dirección: ").append(estacion.getDireccion());

        if (estacion.getCodigoPostal() != null) {
            sb.append("\n   CP: ").append(String.format("%05d", estacion.getCodigoPostal()));
        } else {
            sb.append("\n   CP: NULL");
        }

        sb.append("\n   Coordenadas: lat=").append(estacion.getLatitud())
                .append(", lon=").append(estacion.getLongitud());
        sb.append("\n   Contacto: ").append(estacion.getContacto());
        sb.append("\n   Cod Localidad: ").append(estacion.getCodLocalidad());

        sb.append("\n   Errores encontrados (").append(errores.size()).append("):");

        for (int i = 0; i < errores.size(); i++) {
            sb.append("\n     ").append(i + 1).append(". ").append(errores.get(i));
        }

        return sb.toString();
    }
}