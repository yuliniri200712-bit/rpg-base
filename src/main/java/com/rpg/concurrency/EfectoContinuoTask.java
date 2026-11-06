package com.rpg.concurrency;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Personaje;

/**
 * TALLER CONCURRENCIA (seccion 2): efecto continuo DoT (dano) o HoT (curacion) que corre en su propio hilo.
 * <p>
 * Reemplaza al antiguo Personaje.procesarEfectos (que aplicaba los efectos turno a turno): ahora cada efecto
 * hace tick cada {@code intervaloMs} milisegundos en segundo plano, de forma independiente del combate.
 * <p>
 * Ciclo de vida:
 * <ol>
 *   <li>{@code while (!Thread.currentThread().isInterrupted())}: duerme el intervalo y aplica un tick.</li>
 *   <li>Termina solo cuando se acaban sus ticks o el objetivo muere.</li>
 *   <li>Se cancela desde fuera con interrupt() (antidoto, purificacion o fin del combate): el sleep lanza
 *       InterruptedException; en el catch se limpia el estado del personaje, se restaura la bandera con
 *       Thread.currentThread().interrupt() y se sale del bucle con break.</li>
 *   <li>El finally libera siempre los recursos (quita el efecto y el registro del hilo en el Personaje),
 *       termine como termine.</li>
 * </ol>
 * Nunca se usa Thread.stop() (obsoleto y peligroso: corta el hilo en cualquier punto y puede dejar el estado
 * a medias) ni banderas booleanas propias: la bandera de interrupcion del hilo ya cumple esa funcion.
 */
public class EfectoContinuoTask implements Runnable {

    private final Personaje objetivo;
    private final EstadoEfecto efecto;
    private final String nombreEfecto;
    private final long intervaloMs;

    /**
     * @param objetivo    personaje que sufre (o disfruta) el efecto
     * @param efecto      instancia del efecto activo sobre el objetivo (de ahi salen nombre, valor y duracion)
     * @param intervaloMs milisegundos entre tick y tick
     */
    public EfectoContinuoTask(Personaje objetivo, EstadoEfecto efecto, long intervaloMs) {
        if (objetivo == null || efecto == null) {
            throw new IllegalArgumentException("Objetivo y efecto son obligatorios");
        }
        if (intervaloMs <= 0) {
            throw new IllegalArgumentException("El intervalo debe ser positivo");
        }
        this.objetivo = objetivo;
        this.efecto = efecto;
        this.nombreEfecto = efecto.getNombre();
        this.intervaloMs = intervaloMs;
    }

    @Override
    public void run() {
        objetivo.registrarEventoEfecto(String.format("%s: comienza %s (%s %d cada %d ms, %d ticks)",
                objetivo.getNombre(), nombreEfecto, efecto.esDanio() ? "dano" : "curacion",
                efecto.getValorPorTick(), intervaloMs, efecto.getTurnosRestantes()));
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervaloMs);
                } catch (InterruptedException e) {
                    // Cancelado desde fuera (antidoto, purificacion o fin del combate).
                    objetivo.limpiarEfecto(efecto);       // 1. limpiar el estado del personaje
                    Thread.currentThread().interrupt();   // 2. restaurar la bandera (sleep la borro)
                    objetivo.registrarEventoEfecto(String.format("%s: %s cancelado (hilo interrumpido)",
                            objetivo.getNombre(), nombreEfecto));
                    break;                                // 3. salir del bucle
                }
                // Desde aqui no hay ningun sleep que lance InterruptedException: si nos interrumpieron justo
                // despues de despertar, solo nos enteramos consultando la bandera a mano.
                if (Thread.currentThread().isInterrupted()) {
                    objetivo.limpiarEfecto(efecto);
                    break;
                }
                String mensaje = aplicarTick();
                if (mensaje == null) {
                    break; // el efecto ya no estaba activo (lo quitaron entre medias) o el objetivo murio
                }
                objetivo.registrarEventoEfecto(mensaje);
                if (!objetivo.estaVivo()) {
                    objetivo.registrarEventoEfecto(String.format("%s cae derrotado por %s",
                            objetivo.getNombre(), nombreEfecto));
                    break;
                }
                if (objetivo.consumirTickEfecto(efecto)) {
                    objetivo.registrarEventoEfecto(objetivo.getNombre() + ": termina " + nombreEfecto);
                    break;
                }
            }
        } finally {
            // Liberar recursos SIEMPRE: termine por duracion, por muerte del objetivo o por interrupcion.
            objetivo.limpiarEfecto(efecto);
            objetivo.liberarHiloEfecto(nombreEfecto, Thread.currentThread());
        }
    }

    /**
     * Aplica el dano o la curacion. Comprobar que el efecto sigue activo y aplicarlo se hace como UNA sola
     * operacion atomica (bloque synchronized sobre el mismo monitor que usa Personaje): asi un antidoto no
     * puede quitar el veneno entre la comprobacion y el dano.
     * @return mensaje para el registro, o null si el efecto ya no debia aplicarse
     */
    private String aplicarTick() {
        int valor = efecto.getValorPorTick();
        synchronized (objetivo) {
            if (!objetivo.tieneEfectoActivo(efecto) || !objetivo.estaVivo()) {
                return null;
            }
            if (efecto.esDanio()) {
                objetivo.recibirDanio(valor);
                return String.format("%s sufre %d de dano por %s (vida %d/%d)", objetivo.getNombre(), valor,
                        nombreEfecto, objetivo.getVida(), objetivo.getVidaMaxima());
            }
            int curado = objetivo.curar(valor);
            return String.format("%s recupera %d de vida por %s (vida %d/%d)", objetivo.getNombre(), curado,
                    nombreEfecto, objetivo.getVida(), objetivo.getVidaMaxima());
        }
    }

    public String getNombreEfecto() {
        return nombreEfecto;
    }

    public long getIntervaloMs() {
        return intervaloMs;
    }
}
