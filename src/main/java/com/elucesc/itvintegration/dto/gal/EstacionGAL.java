package com.elucesc.itvintegration.dto.gal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EstacionGAL {
    @JsonProperty("NOME DA ESTACIÓN")
    private String nomeDaEstacion;

    @JsonProperty("ENDEREZO")
    private String enderezo;

    @JsonProperty("CONCELLO")
    private String concello;

    @JsonProperty("CÓDIGO POSTAL")
    private Integer codigoPostal;

    @JsonProperty("PROVINCIA")
    private String provincia;

    @JsonProperty("TELÉFONO")
    private String telefono;

    @JsonProperty("HORARIO")
    private String horario;

    @JsonProperty("SOLICITUDE DE CITA PREVIA")
    private String solicitudeCitaPrevia;

    @JsonProperty("CORREO ELECTRÓNICO")
    private String correoElectronico;

    @JsonProperty("COORDENADAS GMAPS")
    private String coordenadasGmaps;
}