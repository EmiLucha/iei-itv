package com.elucesc.itvintegration.extractor;

import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.Localidad;
import com.elucesc.itvintegration.model.Provincia;

import java.util.List;
import java.util.Map;

public interface ItvDataExtractor {
    List<Estacion> transformarEstaciones();
    List<Localidad> transformarLocalidades();
    List<Provincia> transformarProvincias();

    /**
     * Devuelve un mapa que relaciona el índice de cada estación
     * con el nombre de su localidad correspondiente
     */
    Map<Integer, String> obtenerMapaEstacionLocalidad();
}