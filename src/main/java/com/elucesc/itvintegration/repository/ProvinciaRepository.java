package com.elucesc.itvintegration.repository;
import com.elucesc.itvintegration.model.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvinciaRepository extends JpaRepository<Provincia, Long> {
    boolean existsByCodigo(Long codigo);
    Provincia findByCodigo(Long codigo);
}