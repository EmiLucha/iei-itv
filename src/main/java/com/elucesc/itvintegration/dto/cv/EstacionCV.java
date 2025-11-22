package com.elucesc.itvintegration.dto.cv;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EstacionCV {
    @JsonProperty("TIPO ESTACIÓN")
    private String tipoEstacion;

    @JsonProperty("PROVINCIA")
    private String provincia;

    @JsonProperty("MUNICIPIO")
    private String municipio;

    @JsonProperty("C.POSTAL")
    private String codigoPostal;

    @JsonProperty("DIRECCIÓN")
    private String direccion;

    @JsonProperty("Nº ESTACIÓN")
    private String numeroEstacion;

    @JsonProperty("HORARIOS")
    private String horarios;

    @JsonProperty("CORREO")
    private String correo;
}

