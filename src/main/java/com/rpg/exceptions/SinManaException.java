package com.rpg.exceptions;

import java.io.Serial;

/** Se lanza cuando un hechizo cuesta mas mana del disponible. */
public class SinManaException extends JuegoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SinManaException(int requerido, int disponible) {
        super("Mana insuficiente: necesita " + requerido + " y tiene " + disponible);
    }
}
