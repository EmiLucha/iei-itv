package com.elucesc.itvintegration.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "provincia", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provincia {

    @Id
    @Column(name = "codigo")
    private Long codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;
}