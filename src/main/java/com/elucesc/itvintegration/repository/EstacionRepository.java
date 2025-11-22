package com.elucesc.itvintegration.repository;
import com.elucesc.itvintegration.model.Estacion;
import com.elucesc.itvintegration.model.TipoEstacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EstacionRepository extends JpaRepository<Estacion, Long> {
    List<Estacion> findByTipo(TipoEstacion tipo);
    List<Estacion> findByCodLocalidad(Long codLocalidad);
}