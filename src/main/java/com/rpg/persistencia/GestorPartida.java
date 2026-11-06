package com.rpg.persistencia;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.nio.file.Files;

/**
 * TALLER ARCHIVOS (secciones 4, 5 y 6): guardar, cargar y borrar la partida (partidas/partida.dat).
 * <ul>
 *   <li>Guardar: ObjectOutputStream.writeObject(estado).</li>
 *   <li>Cargar: ObjectInputStream.readObject() con cast a EstadoPartida.</li>
 *   <li>Borrar: Files.deleteIfExists(Path).</li>
 * </ul>
 * Todos los flujos con try-with-resources (son AutoCloseable): ningun close() manual. Las excepciones se
 * capturan de la mas especifica a la mas general y ninguna tumba el juego: si el archivo no existe, esta
 * corrupto, es de otra version o no se puede escribir, se informa y se devuelve false / null.
 * <p>
 * <b>INVESTIGACION - Seguridad de Serializable:</b> readObject() reconstruye objetos de CUALQUIER clase que
 * aparezca en el archivo y ejecuta su codigo de deserializacion (readObject, readResolve...). Un archivo
 * manipulado puede encadenar clases presentes en el classpath ("gadget chains") para ejecutar codigo
 * arbitrario, o provocar una denegacion de servicio (por ejemplo colecciones anidadas enormes que agotan la
 * memoria o la CPU al reconstruirse). Por eso nunca se debe deserializar datos que no son de confianza
 * (descargados, subidos por usuarios, recibidos por red). Mitigaciones: un {@link ObjectInputFilter} con lista
 * blanca de clases y limites de tamano/profundidad (aqui se usa uno), y sobre todo usar formatos de datos que
 * no instancian clases arbitrarias: JSON (Jackson, Gson), XML con esquema, o un formato de texto propio
 * (por ejemplo "clave=valor" por linea) que el programa interpreta campo a campo.
 */
public class GestorPartida {

    /**
     * Lista blanca: solo clases del juego y de java.lang / java.util / java.time; cualquier otra se rechaza (!*).
     * Ademas limita profundidad, numero de referencias y bytes para evitar ataques de consumo de recursos.
     */
    private static final ObjectInputFilter FILTRO = ObjectInputFilter.Config.createFilter(
            "maxdepth=30;maxrefs=100000;maxbytes=10000000;com.rpg.**;java.lang.*;java.util.*;java.time.*;!*");

    private final File archivo;

    public GestorPartida(File archivo) {
        this.archivo = archivo;
    }

    /** @return true si la partida quedo guardada */
    public synchronized boolean guardar(EstadoPartida estado) {
        if (!RutasJuego.asegurarCarpeta()) {
            return false;
        }
        try (ObjectOutputStream salida = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(archivo)))) {
            salida.writeObject(estado);
            return true;
        } catch (NotSerializableException e) {
            // Algun objeto del grafo no implementa Serializable; el mensaje trae el nombre de la clase.
            System.out.println("No se pudo guardar: la clase " + e.getMessage() + " no es Serializable.");
        } catch (FileNotFoundException e) {
            System.out.println("No se puede abrir " + archivo.getPath() + " para escribir: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error de escritura al guardar la partida: " + e.getMessage());
        }
        return false;
    }

    /** @return la partida leida, o null si no se pudo cargar (el motivo ya se mostro por consola) */
    public synchronized EstadoPartida cargar() {
        try (ObjectInputStream entrada = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(archivo)))) {
            entrada.setObjectInputFilter(FILTRO);
            return (EstadoPartida) entrada.readObject(); // cast al tipo correcto
        } catch (FileNotFoundException e) {
            System.out.println("No hay partida guardada: no existe " + archivo.getPath()
                    + ". Usa la opcion 'Guardar partida' o juega un combate.");
        } catch (InvalidClassException e) {
            System.out.println("La partida guardada no es compatible con esta version del juego "
                    + "(serialVersionUID distinto o clase rechazada por el filtro): " + e.getMessage());
        } catch (StreamCorruptedException | EOFException e) {
            System.out.println("El archivo " + archivo.getPath() + " esta corrupto o incompleto: no se puede cargar.");
        } catch (IOException e) {
            System.out.println("Error de lectura al cargar la partida: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("La partida contiene una clase que no existe en este juego: " + e.getMessage());
        } catch (ClassCastException e) {
            System.out.println("El archivo no contiene una partida del juego (" + e.getMessage() + ").");
        }
        return null;
    }

    /**
     * Borra con Files.deleteIfExists(Path). Diferencia con File.delete() (ver HistorialCombates.borrar):
     * deleteIfExists devuelve false SOLO si el archivo no existia; si existe pero no se puede borrar lanza una
     * IOException que explica la causa (AccessDeniedException, DirectoryNotEmptyException, archivo en uso...).
     * File.delete() en cambio devuelve false en todos los casos, sin decir por que.
     */
    public synchronized boolean borrar() {
        try {
            boolean borrado = Files.deleteIfExists(archivo.toPath());
            System.out.println(borrado
                    ? "Partida borrada con Files.deleteIfExists: " + archivo.getPath()
                    : "No habia partida guardada que borrar.");
            return borrado;
        } catch (IOException e) {
            System.out.println("No se pudo borrar la partida (" + e.getClass().getSimpleName() + "): "
                    + e.getMessage());
            return false;
        }
    }

    public boolean existe() {
        return archivo.exists();
    }

    public File getArchivo() {
        return archivo;
    }
}
