package com.rpg.exceptions;

import java.io.Serial;

/** Excepcion no verificada: la habilidad costaria mas vida de la que se tiene. */
public class VidaInsuficienteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public VidaInsuficienteException(String nombre, int costo, int vidaActual) {
        super(nombre + " no puede pagar " + costo + " de vida (le quedan " + vidaActual + ")");
    }
}
