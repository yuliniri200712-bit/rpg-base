package com.rpg.persistencia;

import java.io.File;

/**
 * TALLER ARCHIVOS (seccion 1): rutas de los archivos del juego, todas dentro de la carpeta partidas/.
 * <p>
 * Son rutas RELATIVAS: se resuelven contra el directorio de trabajo desde el que se lanza java
 * (System.getProperty("user.dir")). Si ejecutas el juego desde otra carpeta, partidas/ se creara alli.
 */
public final class RutasJuego {

    public static final String CARPETA_PARTIDAS = "partidas";
    public static final String NOMBRE_PARTIDA = "partida.dat";
    public static final String NOMBRE_HISTORIAL = "historial_combates.txt";
    public static final String NOMBRE_AUTOGUARDADO = "autoguardado.txt";
    public static final String NOMBRE_LEEME = "LEEME.txt";

    private RutasJuego() {
    }

    public static File carpeta() {
        return new File(CARPETA_PARTIDAS);
    }

    public static File archivoPartida() {
        return new File(carpeta(), NOMBRE_PARTIDA);
    }

    public static File archivoHistorial() {
        return new File(carpeta(), NOMBRE_HISTORIAL);
    }

    public static File archivoAutoGuardado() {
        return new File(carpeta(), NOMBRE_AUTOGUARDADO);
    }

    public static File archivoLeeme() {
        return new File(carpeta(), NOMBRE_LEEME);
    }

    /**
     * Crea la carpeta partidas/ si no existe, sin fallar si ya existe. mkdirs() crea tambien las carpetas
     * intermedias y devuelve false si no creo nada (por ejemplo porque ya existia, o porque otro hilo la
     * acaba de crear), por eso despues se comprueba isDirectory().
     * synchronized: el daemon de auto-guardado y el hilo principal pueden llamarlo a la vez.
     * @return true si la carpeta existe al terminar
     */
    public static synchronized boolean asegurarCarpeta() {
        File carpeta = carpeta();
        if (carpeta.isDirectory()) {
            return true;
        }
        boolean creada = carpeta.mkdirs();
        if (!creada && !carpeta.isDirectory()) {
            System.out.println("No se pudo crear la carpeta " + carpeta.getAbsolutePath());
            return false;
        }
        return true;
    }
}
