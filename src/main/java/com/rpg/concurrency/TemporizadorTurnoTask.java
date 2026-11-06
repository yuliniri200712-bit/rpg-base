package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

/**
 * TALLER CONCURRENCIA (seccion 1): cuenta regresiva del turno del jugador.
 * <p>
 * Es un Runnable: describe QUE hace el hilo, pero no es el hilo. {@link GestorTurno} crea un Thread NUEVO con
 * esta tarea en cada turno y llama a start() (nunca a run() directamente, que ejecutaria la cuenta en el hilo
 * principal y lo bloquearia).
 * <ul>
 *   <li>Si llega a 0: llama a {@link TurnoCallback#onTiempoAgotado()}.</li>
 *   <li>Si el jugador responde antes: GestorTurno llama a interrupt(); el sleep lanza InterruptedException
 *       y la tarea termina SIN llamar al callback. No hace falta ninguna bandera booleana.</li>
 * </ul>
 */
public class TemporizadorTurnoTask implements Runnable {

    private static final long UN_SEGUNDO_MS = 1000;
    /** Segundos restantes en los que se avisa al jugador de que se le acaba el tiempo. */
    private static final int SEGUNDOS_AVISO = 5;

    private final int duracionSegundos;
    private final TurnoCallback callback;

    public TemporizadorTurnoTask(int duracionSegundos, TurnoCallback callback) {
        if (duracionSegundos <= 0) {
            throw new IllegalArgumentException("La duracion del turno debe ser positiva");
        }
        if (callback == null) {
            throw new IllegalArgumentException("El callback no puede ser null");
        }
        this.duracionSegundos = duracionSegundos;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            // Cuenta regresiva: un segundo por iteracion (for decreciente).
            for (int restantes = duracionSegundos; restantes > 0; restantes--) {
                if (restantes == SEGUNDOS_AVISO) {
                    System.out.printf("%n[Temporizador] Quedan %d segundos. Accion: ", restantes);
                }
                Thread.sleep(UN_SEGUNDO_MS);
            }
        } catch (InterruptedException e) {
            // El jugador eligio a tiempo y GestorTurno cancelo el temporizador con interrupt().
            // Restauramos la bandera de interrupcion (buena practica: el sleep la borro al lanzar la excepcion)
            // y salimos SIN avisar al callback: el turno NO se pierde.
            Thread.currentThread().interrupt();
            return;
        }
        // La cuenta llego a 0 sin interrupcion: el tiempo se agoto.
        callback.onTiempoAgotado();
    }

    public int getDuracionSegundos() {
        return duracionSegundos;
    }
}
