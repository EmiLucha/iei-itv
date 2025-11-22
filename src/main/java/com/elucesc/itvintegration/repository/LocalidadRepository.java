package com.elucesc.itvintegration.repository;
import com.elucesc.itvintegration.model.Localidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalidadRepository extends JpaRepository<Localidad, Long> {
    Localidad findByNombreAndCodProvincia(String nombre, Long codProvincia);
}