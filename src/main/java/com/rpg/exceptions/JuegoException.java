package com.rpg.exceptions;

import java.io.Serial;

/** Base de las excepciones verificadas del dominio del juego. */
public abstract class JuegoException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    protected JuegoException(String mensaje) {
        super(mensaje);
    }
}
