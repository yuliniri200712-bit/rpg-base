package com.rpg.persistencia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * TALLER ARCHIVOS (seccion 7): archivo de texto que escribe el hilo daemon de auto-guardado
 * (partidas/autoguardado.txt) y que el hilo principal lee desde el menu.
 * <ul>
 *   <li><b>synchronized</b>: escribir(), leer() y cerrar() usan el mismo candado (el monitor de esta instancia,
 *       compartida por el daemon y el hilo principal). Nunca se lee el archivo mientras se esta escribiendo, ni
 *       escriben dos hilos a la vez.</li>
 *   <li><b>Escritura atomica</b>: se escribe primero un archivo temporal y despues se mueve sobre el definitivo
 *       con Files.move(ATOMIC_MOVE). Si la JVM corta el hilo daemon a mitad de escritura (al cerrar el juego),
 *       solo queda a medias el .tmp: autoguardado.txt siempre tiene la ultima copia completa.</li>
 *   <li><b>cerrar()</b>: lo llama el hilo principal al salir. Como es synchronized, espera a que termine una
 *       escritura en curso y despues impide escrituras nuevas.</li>
 * </ul>
 */
public class ArchivoAutoGuardado {

    private final File archivo;
    private final File temporal;
    private boolean cerrado;

    public ArchivoAutoGuardado(File archivo) {
        this.archivo = archivo;
        this.temporal = new File(archivo.getPath() + ".tmp");
    }

    /** @return true si la copia quedo completa en disco */
    public synchronized boolean escribir(String contenido) {
        if (cerrado || !RutasJuego.asegurarCarpeta()) {
            return false;
        }
        try (BufferedWriter escritor = new BufferedWriter(new FileWriter(temporal))) {
            for (String linea : contenido.split("\\R")) {
                escritor.write(linea);
                escritor.newLine();
            }
        } catch (IOException e) {
            System.out.println("[Auto-guardado] no se pudo escribir " + temporal.getPath() + ": " + e.getMessage());
            return false;
        }
        try {
            moverSobreDefinitivo();
            return true;
        } catch (IOException e) {
            System.out.println("[Auto-guardado] no se pudo reemplazar " + archivo.getPath() + ": " + e.getMessage());
            return false;
        }
    }

    private void moverSobreDefinitivo() throws IOException {
        try {
            Files.move(temporal.toPath(), archivo.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Algunos sistemas de archivos no soportan el movimiento atomico: reemplazo normal.
            Files.move(temporal.toPath(), archivo.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Lee la ultima copia guardada en disco. @return sus lineas (vacia si no existe o falla la lectura) */
    public synchronized List<String> leer() {
        List<String> lineas = new ArrayList<>();
        try (BufferedReader lector = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                lineas.add(linea);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Aun no existe " + archivo.getPath() + " (el daemon todavia no ha guardado).");
        } catch (IOException e) {
            System.out.println("No se pudo leer " + archivo.getPath() + ": " + e.getMessage());
        }
        return lineas;
    }

    /** Espera a que termine una escritura en curso (mismo candado) y bloquea las siguientes. */
    public synchronized void cerrar() {
        cerrado = true;
    }

    public File getArchivo() {
        return archivo;
    }
}
