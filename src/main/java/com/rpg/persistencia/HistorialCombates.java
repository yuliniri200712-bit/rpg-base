package com.rpg.persistencia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TALLER ARCHIVOS (secciones 2, 3 y 4): historial de combates en texto plano (partidas/historial_combates.txt).
 * <ul>
 *   <li>Escritura: BufferedWriter + FileWriter, linea a linea con newLine().</li>
 *   <li>Lectura: BufferedReader + FileReader con {@code while ((linea = lector.readLine()) != null)}.</li>
 *   <li>Borrado: File.delete(), revisando el boolean que devuelve.</li>
 * </ul>
 * Todos los flujos se abren con try-with-resources: se cierran solos (y hacen flush) aunque haya excepcion,
 * por eso no hay ningun close() manual.
 * <p>
 * <b>INVESTIGACION - FileWriter vs Files.writeString:</b> FileWriter (envuelto en BufferedWriter) es un FLUJO:
 * lo abres, escribes por partes cuando quieras (linea a linea, en un bucle, en modo append) y lo cierras tu
 * (aqui con try-with-resources). Files.writeString(path, texto) es una sola llamada de NIO que abre, escribe
 * TODO el texto y cierra en un paso. Usaria FileWriter/BufferedWriter para ir escribiendo mucho contenido o
 * por partes (un log que crece); Files.writeString para volcar de golpe un texto pequeno ya construido
 * (por ejemplo un archivo de configuracion), con menos codigo.
 */
public class HistorialCombates {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final File archivo;

    public HistorialCombates(File archivo) {
        this.archivo = archivo;
    }

    /**
     * Escribe una cabecera con la fecha y despues el texto, linea a linea.
     * @param agregar true -> new FileWriter(archivo, true): APPEND, se anade al final sin borrar lo anterior.
     *                false -> new FileWriter(archivo, false) (el modo por defecto): SOBRESCRIBE el archivo.
     * @return true si se escribio correctamente
     */
    public synchronized boolean escribir(String texto, boolean agregar) {
        if (!RutasJuego.asegurarCarpeta()) {
            return false;
        }
        try (BufferedWriter escritor = new BufferedWriter(new FileWriter(archivo, agregar))) {
            escritor.write("===== Registro guardado el " + LocalDateTime.now().format(FORMATO_FECHA)
                    + (agregar ? " (append)" : " (sobrescribir)") + " =====");
            escritor.newLine(); // salto de linea del sistema, en vez de concatenar "\n" a mano
            for (String linea : texto.split("\\R")) {
                escritor.write(linea);
                escritor.newLine();
            }
            escritor.flush(); // vacia el buffer al disco ya (el close() automatico tambien lo haria)
            return true;
        } catch (IOException e) {
            System.out.println("No se pudo escribir el historial en " + archivo.getPath() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Lee el historial completo.
     * @return las lineas leidas (lista vacia si el archivo no existe o no se pudo leer)
     */
    public synchronized List<String> leer() {
        List<String> lineas = new ArrayList<>();
        try (BufferedReader lector = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                lineas.add(linea);
            }
            if (lineas.isEmpty()) {
                System.out.println("El historial de combates esta vacio.");
            }
        } catch (FileNotFoundException e) {
            // La mas especifica primero: FileNotFoundException es subclase de IOException.
            System.out.println("Aun no hay historial de combates: no existe el archivo " + archivo.getPath()
                    + ". Juega un combate para crearlo.");
        } catch (IOException e) {
            System.out.println("No se pudo leer el historial: " + e.getMessage());
        }
        return lineas;
    }

    /**
     * Borra con File.delete(). Diferencia con Files.deleteIfExists (ver GestorPartida.borrar): delete() no
     * lanza excepciones, solo devuelve false, sin decir POR QUE fallo (no existia, sin permisos, archivo
     * abierto por otro programa...). Por eso hay que revisar siempre su boolean.
     */
    public synchronized boolean borrar() {
        if (!archivo.exists()) {
            System.out.println("No hay historial que borrar.");
            return false;
        }
        boolean borrado = archivo.delete();
        if (borrado) {
            System.out.println("Historial borrado con File.delete(): " + archivo.getPath());
        } else {
            System.out.println("File.delete() devolvio false: no se pudo borrar " + archivo.getPath()
                    + " (puede estar abierto o sin permisos).");
        }
        return borrado;
    }

    public File getArchivo() {
        return archivo;
    }
}
