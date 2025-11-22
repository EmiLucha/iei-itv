package com.elucesc.itvintegration.model;

public enum TipoEstacion {
    ESTACION_FIJA("Estación_fija"),
    ESTACION_MOVIL("Estación_móvil"),
    OTROS("Otros");

    private final String valor;

    TipoEstacion(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public static TipoEstacion fromString(String texto) {
        if (texto == null) return OTROS;

        String textoNormalizado = texto.trim().toLowerCase()
                .replaceAll("[áàäâ]", "a")
                .replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i")
                .replaceAll("[óòöô]", "o")
                .replaceAll("[úùüû]", "u");

        if (textoNormalizado.contains("fija") || textoNormalizado.equals("estacion_fija")) {
            return ESTACION_FIJA;
        } else if (textoNormalizado.contains("movil") || textoNormalizado.contains("móvil")) {
            return ESTACION_MOVIL;
        }
        return OTROS;
    }
}