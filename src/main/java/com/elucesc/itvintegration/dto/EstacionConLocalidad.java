package com.elucesc.itvintegration.dto;

import com.elucesc.itvintegration.model.Estacion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO temporal que contiene una estación y el nombre de su localidad
 * para vincularlas después de guardar las localidades en BD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstacionConLocalidad {
    private Estacion estacion;
    private String nombreLocalidad;
    private Long codigoProvincia;
}