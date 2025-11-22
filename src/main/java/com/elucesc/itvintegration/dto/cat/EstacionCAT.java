package com.elucesc.itvintegration.dto.cat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EstacionCAT {
    @JsonProperty("estaci")
    private String estaci;

    @JsonProperty("denominaci")
    private String denominaci;

    @JsonProperty("operador")
    private String operador;

    @JsonProperty("adre_a")
    private String direccion;

    @JsonProperty("cp")
    private Integer codigoPostal;

    @JsonProperty("municipi")
    private String municipi;

    @JsonProperty("codi_municipi")
    private Integer codiMunicipi;

    @JsonProperty("tel_atenc_public")
    private String telefono;

    @JsonProperty("lat")
    private Double lat;

    @JsonProperty("long")
    private Double lon;

    @JsonProperty("serveis_territorials")
    private String serveisTerritorials;

    @JsonProperty("horari_de_servei")
    private String horariDeServei;

    @JsonProperty("correu_electr_nic")
    private String correuElectronic;

    @JsonProperty("web")
    private String web;
}