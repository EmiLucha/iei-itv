package com.elucesc.itvintegration.wrapper;

import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.Localidad;
import com.elucesc.itvintegration.model.Provincia;

import java.util.List;

public interface ItvDataWrapper {
    List<Estacion> transformarEstaciones();
    List<Localidad> transformarLocalidades();
    List<Provincia> transformarProvincias();
}


