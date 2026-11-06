package com.rpg.entities;

import com.rpg.entities.enums.RaridadItem;

import java.io.Serial;
import java.io.Serializable;

// TALLER ARCHIVOS (resuelto): Serializable porque Personaje.arma la referencia. Si no lo fuera, guardar un
// Personaje con arma lanzaria NotSerializableException: com.rpg.entities.Arma.
public class Arma implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final int danioBase;
    private final RaridadItem raridad;

    public Arma(String nombre, int danioBase, RaridadItem raridad) {
        if (danioBase < 0) {
            throw new IllegalArgumentException("El danio del arma no puede ser negativo");
        }
        this.nombre = nombre;
        this.danioBase = danioBase;
        this.raridad = raridad;
    }

    public double getDanioEfectivo() {
        return danioBase * raridad.getMultiplicadorStat();
    }

    public String getNombre() {
        return nombre;
    }
}
