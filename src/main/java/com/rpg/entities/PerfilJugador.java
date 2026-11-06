package com.rpg.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

// TALLER ARCHIVOS (resuelto): Serializable (victorias, derrotas y logros son parte de la partida).
// TALLER CONCURRENCIA: metodos synchronized porque el daemon de auto-guardado lo lee desde otro hilo.
public class PerfilJugador implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int victorias;
    private int derrotas;
    private final Set<String> logros = new TreeSet<>();

    /**
     * transient: dato solo de la sesion actual (combates jugados desde que se abrio el juego); no debe
     * guardarse. Al cargar una partida vuelve con el valor por defecto de un int: 0. MotorJuego.cargarPartida
     * lo muestra para comprobarlo.
     */
    private transient int combatesEnSesion;

    public synchronized void registrarVictoria() {
        victorias++;
    }

    public synchronized void registrarDerrota() {
        derrotas++;
    }

    public synchronized void registrarCombateEnSesion() {
        combatesEnSesion++;
    }

    /** @return true si el logro era nuevo; false si ya estaba desbloqueado */
    public synchronized boolean desbloquearLogro(String logro) {
        return logros.add(logro);
    }

    public synchronized int getVictorias() {
        return victorias;
    }

    public synchronized int getDerrotas() {
        return derrotas;
    }

    public synchronized int getCombatesEnSesion() {
        return combatesEnSesion;
    }

    /** Los logros salen ordenados alfabeticamente porque se guardan en un TreeSet (se devuelve una copia). */
    public synchronized Set<String> getLogros() {
        return Collections.unmodifiableSet(new TreeSet<>(logros));
    }
}
