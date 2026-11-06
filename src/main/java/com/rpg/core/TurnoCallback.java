package com.rpg.core;

/**
 * TALLER CONCURRENCIA (seccion 1): callback que el temporizador de turno invoca cuando la cuenta regresiva
 * llega a 0 sin que el jugador haya elegido su accion.
 * <p>
 * OJO: se ejecuta en el hilo del temporizador, NO en el hilo principal. Por eso la implementacion de
 * MotorJuego no toca el combate directamente: solo interrumpe al hilo principal, que esta esperando la
 * entrada del teclado, y es este quien decide que el turno se pierde.
 */
@FunctionalInterface
public interface TurnoCallback {

    void onTiempoAgotado();
}
