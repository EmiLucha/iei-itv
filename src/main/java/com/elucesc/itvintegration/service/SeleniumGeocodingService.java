package com.elucesc.itvintegration.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de geocoding usando Selenium WebDriver con Google Maps
 * Obtiene coordenadas geográficas a partir de direcciones
 */
@Slf4j
@Service
public class SeleniumGeocodingService {

    private WebDriver driver;
    private static final String GOOGLE_MAPS_URL = "https://www.google.com/maps/search/";
    private static final int MAX_RETRIES = 3;
    private static final int WAIT_TIMEOUT = 10; // segundos

    /**
     * Inicializa el WebDriver de Chrome en modo headless con cookies de consentimiento
     */
    private void initializeDriver() {
        if (driver == null) {
            log.info("Inicializando Chrome WebDriver en modo headless...");

            // WebDriverManager descarga y configura ChromeDriver automáticamente
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless"); // Sin interfaz gráfica
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--lang=es");

            // Configurar preferencias para evitar consentimiento
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("profile.default_content_setting_values.cookies", 1);
            prefs.put("profile.cookie_controls_mode", 0);
            options.setExperimentalOption("prefs", prefs);

            // Desactivar logs innecesarios
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-logging"});

            driver = new ChromeDriver(options);

            // Pre-cargar cookies de consentimiento de Google
            try {
                log.debug("Configurando cookies de consentimiento...");
                driver.get("https://www.google.com");

                // Cookie de consentimiento principal
                driver.manage().addCookie(new Cookie.Builder("CONSENT", "YES+cb.20210720-07-p0.es+FX+410")
                        .domain(".google.com")
                        .path("/")
                        .isSecure(false)
                        .build());

                // Cookie SOCS (Single Origin Cookie State) para evitar el banner
                driver.manage().addCookie(new Cookie.Builder("SOCS", "CAESEwgDEgk2MTkxODMwNzIaAmVuIAEaBgiA_LyYBg")
                        .domain(".google.com")
                        .path("/")
                        .isSecure(true)
                        .build());

                // Cookie adicional para Google Maps específicamente
                driver.manage().addCookie(new Cookie.Builder("NID", "511=cookies_accepted")
                        .domain(".google.com")
                        .path("/")
                        .isSecure(true)
                        .build());

                log.debug("✅ Cookies de consentimiento configuradas");

            } catch (Exception e) {
                log.warn("⚠️ No se pudieron configurar cookies de consentimiento: {}", e.getMessage());
            }

            log.info("✅ Chrome WebDriver inicializado correctamente");
        }
    }

    /**
     * Obtiene coordenadas (longitud, latitud) usando Google Maps con Selenium
     */
    public Double[] obtenerCoordenadas(String direccion) {
        if (direccion == null || direccion.trim().isEmpty()) {
            log.debug("Dirección vacía, retornando null");
            return new Double[]{null, null};
        }

        int intentos = 0;
        while (intentos < MAX_RETRIES) {
            try {
                initializeDriver();

                // Construir URL de búsqueda
                String searchUrl = GOOGLE_MAPS_URL + direccion.replace(" ", "+");
                log.debug("Buscando coordenadas para: {} (intento {}/{})", direccion, intentos + 1, MAX_RETRIES);

                driver.get(searchUrl);

                // Verificar si seguimos en página de consentimiento
                Thread.sleep(1000);
                String currentUrl = driver.getCurrentUrl();

                if (currentUrl.contains("consent.google.com")) {
                    log.warn("⚠️ Todavía en página de consentimiento después de configurar cookies");
                    log.debug("URL actual: {}", currentUrl);

                    // Intentar recargar con las cookies
                    driver.navigate().refresh();
                    Thread.sleep(1000);
                }

                // Esperar a que la página cargue y extraer coordenadas de la URL
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));
                wait.until(ExpectedConditions.urlContains("@"));

                // Esperar un poco más para asegurar que la URL se estabilice
                Thread.sleep(2000);

                currentUrl = driver.getCurrentUrl();
                Double[] coordenadas = extraerCoordenadasDeUrl(currentUrl);

                if (coordenadas[0] != null && coordenadas[1] != null) {
                    log.debug("✅ Coordenadas obtenidas: [{}, {}] para: {}",
                            coordenadas[0], coordenadas[1], direccion);
                    return coordenadas;
                }

                log.warn("No se pudieron extraer coordenadas de la URL: {}", currentUrl);
                intentos++;

            } catch (Exception e) {
                intentos++;
                log.warn("Error en intento {}/{} para dirección '{}': {}",
                        intentos, MAX_RETRIES, direccion, e.getMessage());

                if (intentos >= MAX_RETRIES) {
                    log.error("❌ Falló la geocodificación después de {} intentos para: {}",
                            MAX_RETRIES, direccion);
                }

                // Si estamos atascados en consentimiento, reiniciar driver
                try {
                    if (driver != null && driver.getCurrentUrl().contains("consent.google.com")) {
                        log.info("Reiniciando driver por página de consentimiento...");
                        driver.quit();
                        driver = null;
                    }
                } catch (Exception ex) {
                    // Ignorar errores al verificar URL
                }
            }
        }

        return new Double[]{null, null};
    }

    /**
     * Extrae coordenadas de la URL de Google Maps
     * Formato típico: https://www.google.com/maps/place/.../@40.4167754,-3.7037902,15z/...
     */
    private Double[] extraerCoordenadasDeUrl(String url) {
        try {
            // Patrón para coordenadas: @latitud,longitud,zoom
            Pattern pattern = Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+),\\d+");
            Matcher matcher = pattern.matcher(url);

            if (matcher.find()) {
                Double latitud = Double.parseDouble(matcher.group(1));
                Double longitud = Double.parseDouble(matcher.group(2));
                return new Double[]{longitud, latitud};
            }

            // Intentar otro patrón común: !3d latitud !4d longitud
            pattern = Pattern.compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)");
            matcher = pattern.matcher(url);

            if (matcher.find()) {
                Double latitud = Double.parseDouble(matcher.group(1));
                Double longitud = Double.parseDouble(matcher.group(2));
                return new Double[]{longitud, latitud};
            }

        } catch (Exception e) {
            log.error("Error al parsear coordenadas de URL: {}", url, e);
        }

        return new Double[]{null, null};
    }

    /**
     * Obtiene coordenadas con dirección completa (incluye municipio y provincia)
     */
    public Double[] obtenerCoordenadasCompletas(String direccion, String municipio, String provincia) {
        StringBuilder direccionCompleta = new StringBuilder();

        if (direccion != null && !direccion.trim().isEmpty()) {
            direccionCompleta.append(direccion);
        }

        if (municipio != null && !municipio.trim().isEmpty()) {
            if (direccionCompleta.length() > 0) {
                direccionCompleta.append(", ");
            }
            direccionCompleta.append(municipio);
        }

        if (provincia != null && !provincia.trim().isEmpty()) {
            if (direccionCompleta.length() > 0) {
                direccionCompleta.append(", ");
            }
            direccionCompleta.append(provincia);
        }

        direccionCompleta.append(", España");

        return obtenerCoordenadas(direccionCompleta.toString());
    }

    /**
     * Versión con delay para evitar bloqueos por peticiones rápidas
     */
    public Double[] obtenerCoordenadasConDelay(String direccion) {
        try {
            Thread.sleep(1500); // 1.5 segundos entre peticiones
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return obtenerCoordenadas(direccion);
    }

    /**
     * Cierra el WebDriver al destruir el bean
     */
    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            log.info("Cerrando Chrome WebDriver...");
            try {
                driver.quit();
                driver = null;
                log.info("✅ Chrome WebDriver cerrado correctamente");
            } catch (Exception e) {
                log.error("Error al cerrar WebDriver", e);
            }
        }
    }

    /**
     * Verifica si el servicio está disponible
     */
    public boolean isAvailable() {
        try {
            initializeDriver();
            return driver != null;
        } catch (Exception e) {
            log.error("Selenium WebDriver no disponible: {}", e.getMessage());
            return false;
        }
    }
}