package com.rpg.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * TALLER CONCURRENCIA - Reto extra (seccion 6): vida thread-safe SIN synchronized, solo con AtomicInteger.
 * <p>
 * {@link #recibirDanioIngenuo(int)} usa addAndGet(-danio): es atomico, pero NO acota. Con varios golpes a la
 * vez la vida baja de 0 (por ejemplo a -37) y nadie marca al personaje como MUERTO.
 * <p>
 * {@link #recibirDanio(int)} usa updateAndGet con una funcion que acota a 0..vidaMaxima: el calculo completo
 * (leer, restar, acotar, escribir) se aplica de forma atomica; si otro hilo cambio el valor entre medias,
 * updateAndGet reintenta solo. {@link #curar(int)} hace lo mismo "a mano" con un bucle compareAndSet (CAS).
 * <p>
 * Limite de esta solucion: funciona porque aqui el "estado" se DERIVA de la vida (vida == 0 -> muerto) y no
 * es otra variable. En Personaje el estado tambien puede ser ENVENENADO o ATURDIDO y depende del mapa de
 * efectos: son varias variables que deben cambiar juntas, y un atomico solo protege UNA. Por eso Personaje
 * usa synchronized.
 */
public class VidaAtomica {

    private final AtomicInteger vida;
    private final int vidaMaxima;

    public VidaAtomica(int vidaMaxima) {
        this.vidaMaxima = vidaMaxima;
        this.vida = new AtomicInteger(vidaMaxima);
    }

    /** INCORRECTO a proposito: atomico pero sin acotar (puede quedar negativa). */
    public int recibirDanioIngenuo(int danio) {
        return vida.addAndGet(-danio);
    }

    /** Correcto: updateAndGet aplica la funcion completa de forma atomica (reintenta si hay conflicto). */
    public int recibirDanio(int danio) {
        return vida.updateAndGet(actual -> Math.max(0, Math.min(actual - danio, vidaMaxima)));
    }

    /** Correcto con un bucle compareAndSet: un muerto no se cura. @return vida realmente recuperada */
    public int curar(int cantidad) {
        while (true) {
            int actual = vida.get();
            if (actual == 0) {
                return 0;
            }
            int nueva = Math.min(actual + cantidad, vidaMaxima);
            if (vida.compareAndSet(actual, nueva)) { // solo escribe si nadie la cambio desde que la leimos
                return nueva - actual;
            }
            // Otro hilo gano la carrera: se vuelve a leer y a intentar.
        }
    }

    /** El estado se deriva de la vida: no hay una segunda variable que mantener sincronizada. */
    public boolean estaVivo() {
        return vida.get() > 0;
    }

    public int getVida() {
        return vida.get();
    }
}
