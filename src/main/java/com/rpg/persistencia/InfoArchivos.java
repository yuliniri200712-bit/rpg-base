package com.rpg.persistencia;

import java.io.File;
import java.io.IOException;

/**
 * TALLER ARCHIVOS (seccion 1): uso de la clase {@link File}.
 * <p>
 * Un objeto File es solo una RUTA (un nombre), no el archivo abierto: crearlo no toca el disco. Para saber si
 * existe, cuanto ocupa o para crearlo hay que llamar a sus metodos (exists, length, createNewFile, mkdirs...).
 */
public final class InfoArchivos {

    private InfoArchivos() {
    }

    /** Muestra rutas relativas vs absolutas, la informacion de cada archivo y crea partidas/LEEME.txt. */
    public static void mostrar(File... archivos) {
        System.out.println("\n=== INFORMACION DE ARCHIVOS (clase File) ===");
        RutasJuego.asegurarCarpeta();
        compararRutas();
        crearLeeme();
        System.out.println("--- Archivos del juego ---");
        for (File archivo : archivos) {
            describir(archivo);
        }
        describir(RutasJuego.archivoLeeme());
    }

    /**
     * Ruta relativa: "partidas/partida.dat" depende del directorio de trabajo actual (user.dir); el mismo
     * programa lanzado desde otra carpeta apunta a otro archivo.
     * Ruta absoluta: empieza en la raiz del disco (C:\... o /home/...) y siempre apunta al mismo sitio.
     * getAbsolutePath() convierte la relativa en absoluta anteponiendo user.dir.
     */
    private static void compararRutas() {
        File relativo = RutasJuego.archivoPartida();
        File absoluto = new File(relativo.getAbsolutePath());
        System.out.println("Directorio de trabajo (user.dir): " + System.getProperty("user.dir"));
        System.out.printf("Ruta relativa: %-28s isAbsolute()=%b%n", relativo.getPath(), relativo.isAbsolute());
        System.out.printf("Ruta absoluta: %s  isAbsolute()=%b%n", absoluto.getPath(), absoluto.isAbsolute());
        System.out.println("Ambos File apuntan al mismo archivo: " + relativo.getAbsoluteFile().equals(absoluto));
    }

    /** createNewFile() crea el archivo vacio solo si no existia (devuelve false si ya estaba). */
    private static void crearLeeme() {
        File leeme = RutasJuego.archivoLeeme();
        try {
            boolean creado = leeme.createNewFile();
            System.out.println(creado
                    ? "createNewFile(): se creo " + leeme.getPath() + " (vacio)"
                    : "createNewFile(): " + leeme.getPath() + " ya existia, no se toca");
        } catch (IOException e) {
            System.out.println("No se pudo crear " + leeme.getPath() + ": " + e.getMessage());
        }
    }

    private static void describir(File archivo) {
        if (archivo.exists()) {
            System.out.printf("  %-24s existe: si | tamano: %6d bytes | ruta: %s%n",
                    archivo.getName(), archivo.length(), archivo.getPath());
        } else {
            System.out.printf("  %-24s existe: no | (aun no se ha creado)%n", archivo.getName());
        }
    }
}
