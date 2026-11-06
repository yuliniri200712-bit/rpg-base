package com.rpg.entities;

import com.rpg.entities.enums.TipoEfecto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Efecto periodico (DoT o HoT): cambia la vida un valor fijo en cada tick durante cierta duracion.
 * Desde el taller de concurrencia cada efecto activo lo ejecuta su propio hilo (EfectoContinuoTask).
 */
// TALLER ARCHIVOS (resuelto): Serializable porque lo referencian los efectos de Personaje, Pocion y Enemigo.
public class EstadoEfecto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final TipoEfecto tipo;
    private final int valorPorTick;
    /**
     * Ticks que quedan. volatile para que el hilo principal (que lo muestra en "Ver estado") vea el ultimo
     * valor; las modificaciones se hacen siempre con el candado del Personaje tomado (consumirTickEfecto).
     */
    private volatile int turnosRestantes;

    public EstadoEfecto(String nombre, TipoEfecto tipo, int valorPorTick, int duracion) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.valorPorTick = valorPorTick;
        this.turnosRestantes = duracion;
    }

    public EstadoEfecto copia() {
        return new EstadoEfecto(nombre, tipo, valorPorTick, turnosRestantes);
    }

    public void consumirTurno() {
        turnosRestantes--;
    }

    /** Vuelve a aplicar el mismo efecto: reinicia la duracion sin crear otro hilo. */
    public void renovar(int duracion) {
        turnosRestantes = duracion;
    }

    public boolean haTerminado() {
        return turnosRestantes <= 0;
    }

    public boolean esDanio() {
        return tipo == TipoEfecto.DANIO_CONTINUO;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoEfecto getTipo() {
        return tipo;
    }

    public int getValorPorTick() {
        return valorPorTick;
    }

    public int getTurnosRestantes() {
        return turnosRestantes;
    }
}
