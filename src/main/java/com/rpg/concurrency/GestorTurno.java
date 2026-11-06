package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

/**
 * TALLER CONCURRENCIA (seccion 1): crea y cancela el hilo del temporizador de turno.
 * <p>
 * Reglas que cumple:
 * <ul>
 *   <li>Cada llamada a {@link #iniciar} crea un Thread NUEVO: un Thread que ya termino no puede volver a
 *       arrancarse (start() dos veces lanza IllegalThreadStateException).</li>
 *   <li>Siempre se usa start(), que es lo que crea el hilo nuevo; run() directo correria en el hilo actual.</li>
 *   <li>La cancelacion se hace con interrupt(), sin banderas booleanas manuales.</li>
 * </ul>
 */
public class GestorTurno {

    private Thread hiloTemporizador;

    /** Arranca la cuenta regresiva en un hilo propio. */
    public void iniciar(int segundos, TurnoCallback callback) {
        cancelar(); // por si quedara uno anterior activo
        TemporizadorTurnoTask tarea = new TemporizadorTurnoTask(segundos, callback);
        hiloTemporizador = new Thread(tarea, "temporizador-turno");
        // Es un hilo de monitoreo: si el jugador sale del juego no debe mantener viva la JVM.
        hiloTemporizador.setDaemon(true); // siempre ANTES de start()
        hiloTemporizador.start();
    }

    /**
     * El jugador respondio a tiempo (o el turno termino): interrumpe el temporizador y espera a que acabe.
     * Si el temporizador ya habia llegado a 0, interrupt() no tiene efecto y join() retorna enseguida.
     */
    public void cancelar() {
        Thread hilo = hiloTemporizador;
        if (hilo == null) {
            return;
        }
        hiloTemporizador = null;
        hilo.interrupt();
        try {
            hilo.join();
        } catch (InterruptedException e) {
            // Nos interrumpieron mientras esperabamos (puede ser el propio callback del temporizador que
            // acababa de dispararse): conservamos la bandera para que el llamador decida.
            Thread.currentThread().interrupt();
        }
    }

    public boolean estaActivo() {
        Thread hilo = hiloTemporizador;
        return hilo != null && hilo.isAlive();
    }
}
