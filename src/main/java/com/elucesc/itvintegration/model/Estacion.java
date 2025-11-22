package com.elucesc.itvintegration.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "estacion", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Estacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_estacion")
    private Long codEstacion;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoEstacion tipo;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "codigo_postal")
    private Long codigoPostal;

    @Column(name = "longitud")
    private Double longitud;

    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "horario")
    private String horario;

    @Column(name = "contacto")
    private String contacto;

    @Column(name = "url")
    private String url;

    @Column(name = "cod_localidad")
    private Long codLocalidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cod_localidad", insertable = false, updatable = false)
    private Localidad localidad;
}