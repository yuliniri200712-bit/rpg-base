package com.rpg.core;

/** Acciones que el jugador puede elegir en su turno. */
public enum Accion {
    ATACAR,
    ESPECIAL,
    POCION,
    ESTADO,
    HUIR,
    /** TALLER CONCURRENCIA: no la elige el jugador; la produce el temporizador cuando se agota el tiempo. */
    TIEMPO_AGOTADO
}
