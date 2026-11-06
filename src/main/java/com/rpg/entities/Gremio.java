package com.rpg.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Agregacion: el gremio guarda referencias a personajes que existen por su cuenta. */
// TALLER ARCHIVOS (resuelto): Serializable; su lista de Personaje tambien lo es. El ciclo Gremio <-> Personaje
// se serializa sin problema: ObjectOutputStream escribe cada objeto una vez y luego solo referencias.
// TALLER CONCURRENCIA: synchronized porque el hilo daemon de auto-guardado recorre los miembros mientras el
// hilo principal puede agregar un heroe nuevo (sin proteccion: ConcurrentModificationException).
public class Gremio implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final List<Personaje> miembros = new ArrayList<>();

    public Gremio(String nombre) {
        this.nombre = nombre;
    }

    public synchronized void agregarMiembro(Personaje personaje) {
        if (personaje == null || miembros.contains(personaje)) {
            return;
        }
        miembros.add(personaje);
        if (personaje.getGremio() != this) {
            personaje.unirseAGremio(this);
        }
    }

    public synchronized void removerMiembro(Personaje personaje) {
        if (!miembros.remove(personaje)) {
            return;
        }
        if (personaje.getGremio() == this) {
            personaje.abandonarGremio();
        }
    }

    public String getNombre() {
        return nombre;
    }

    /** Copia inmutable del momento: se puede recorrer desde otro hilo sin ConcurrentModificationException. */
    public synchronized List<Personaje> getMiembros() {
        return List.copyOf(miembros);
    }
}
