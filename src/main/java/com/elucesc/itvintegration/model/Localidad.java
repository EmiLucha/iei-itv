package com.elucesc.itvintegration.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "localidad", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Localidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private Long codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "cod_provincia")
    private Long codProvincia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cod_provincia", insertable = false, updatable = false)
    private Provincia provincia;
}