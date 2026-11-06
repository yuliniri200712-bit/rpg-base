/**
 * TALLER CONCURRENCIA: hilos del simulador.
 * <ul>
 *   <li>{@link com.rpg.concurrency.TemporizadorTurnoTask} + {@link com.rpg.concurrency.GestorTurno}: temporizador de turno (seccion 1).</li>
 *   <li>{@link com.rpg.concurrency.EfectoContinuoTask}: efectos DoT/HoT, un hilo por efecto, cancelables con interrupt() (seccion 2).</li>
 *   <li>{@link com.rpg.concurrency.AutoGuardadoDaemon}: auto-guardado periodico en un hilo daemon (seccion 4).</li>
 *   <li>{@link com.rpg.concurrency.DemoCondicionCarrera} y {@link com.rpg.concurrency.VidaAtomica}: condicion de carrera y soluciones (seccion 6).</li>
 *   <li>{@link com.rpg.concurrency.DemoHiloDaemon}: investigacion obligatoria sobre hilos daemon.</li>
 * </ul>
 * Las semifinales en paralelo con join() estan en com.rpg.core.TorneoManager (seccion 3).
 */
package com.rpg.concurrency;
