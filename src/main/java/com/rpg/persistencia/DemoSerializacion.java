package com.rpg.persistencia;

import com.rpg.core.RegistroCombate;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;

/**
 * TALLER ARCHIVOS (seccion 5): evidencias de serializacion. Ejecutar con:
 * <pre>java -cp out com.rpg.persistencia.DemoSerializacion</pre>
 * <ol>
 *   <li>Provoca una NotSerializableException: un objeto Serializable que contiene un RegistroCombate (clase
 *       anidada que NO implementa Serializable).</li>
 *   <li>La resuelve marcando ese campo como transient (y guardando solo su texto, que es un String).</li>
 *   <li>Comprueba que tras cargar, el Gremio y sus Personaje siguen relacionados (el ciclo se conserva).</li>
 *   <li>Comprueba que un campo transient vuelve con su valor por defecto.</li>
 * </ol>
 */
public final class DemoSerializacion {

    private static final File ARCHIVO_DEMO = new File(RutasJuego.CARPETA_PARTIDAS, "demo_serializacion.dat");

    private DemoSerializacion() {
    }

    /** VERSION CON ERROR: RegistroCombate no es Serializable -> NotSerializableException al escribir. */
    static class PartidaConRegistro implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Gremio gremio;
        private final RegistroCombate registro; // <- objeto anidado NO serializable

        PartidaConRegistro(Gremio gremio, RegistroCombate registro) {
            this.gremio = gremio;
            this.registro = registro;
        }
    }

    /** VERSION CORREGIDA: el registro es transient (no se guarda) y se guarda solo su texto. */
    static class PartidaCorregida implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Gremio gremio;
        private final PerfilJugador perfil;
        private final String textoRegistro;           // String SI es Serializable
        private final transient RegistroCombate registro; // transient: se ignora al serializar

        PartidaCorregida(Gremio gremio, PerfilJugador perfil, RegistroCombate registro) {
            this.gremio = gremio;
            this.perfil = perfil;
            this.registro = registro;
            this.textoRegistro = registro.getTexto();
        }
    }

    public static void main(String[] args) {
        RutasJuego.asegurarCarpeta();
        Gremio gremio = new Gremio("Guardianes del Alba");
        Personaje thor = new Guerrero("Thor");
        Personaje circe = new Mago("Circe");
        thor.unirseAGremio(gremio);
        circe.unirseAGremio(gremio);
        thor.recibirDanio(25);
        PerfilJugador perfil = new PerfilJugador();
        perfil.registrarVictoria();
        perfil.registrarCombateEnSesion();
        perfil.registrarCombateEnSesion();
        perfil.desbloquearLogro("Primera Victoria");
        RegistroCombate registro = new RegistroCombate();
        registro.agregar("Thor ataca al Goblin y causa 20 de dano");

        System.out.println("\n--- 1. Provocar NotSerializableException ---");
        escribir(new PartidaConRegistro(gremio, registro));

        System.out.println("\n--- 2. Resolverla con transient ---");
        if (!escribir(new PartidaCorregida(gremio, perfil, registro))) {
            return;
        }
        System.out.printf("Guardado %s (%d bytes)%n", ARCHIVO_DEMO.getPath(), ARCHIVO_DEMO.length());

        System.out.println("\n--- 3 y 4. Cargar y comprobar ---");
        PartidaCorregida cargada = leer();
        if (cargada != null) {
            Gremio g = cargada.gremio;
            Personaje thorCargado = g.getMiembros().get(0);
            System.out.println("Miembros del gremio cargado: " + g.getMiembros().size());
            System.out.println("Thor cargado: vida " + thorCargado.getVida() + "/" + thorCargado.getVidaMaxima()
                    + ", arma " + thorCargado.getArma().getNombre() + ", pociones " + thorCargado.getInventario().tamanio());
            System.out.println("thorCargado.getGremio() == gremio cargado -> " + (thorCargado.getGremio() == g)
                    + " (el ciclo Personaje <-> Gremio se reconstruyo con las mismas referencias)");
            System.out.println("Es un objeto nuevo, no el original -> " + (thorCargado != thor));
            System.out.println("textoRegistro (String) se conservo: " + cargada.textoRegistro.trim());
            System.out.println("registro (transient) tras cargar: " + cargada.registro + " (valor por defecto null)");
            System.out.println("perfil.combatesEnSesion (transient) antes de guardar: 2, tras cargar: "
                    + cargada.perfil.getCombatesEnSesion() + " (valor por defecto 0)");
            System.out.println("perfil.victorias (normal) tras cargar: " + cargada.perfil.getVictorias()
                    + ", logros: " + cargada.perfil.getLogros());
        }

        try {
            Files.deleteIfExists(ARCHIVO_DEMO.toPath());
            System.out.println("\nArchivo de la demo borrado.");
        } catch (IOException e) {
            System.out.println("No se pudo borrar el archivo de la demo: " + e.getMessage());
        }
    }

    private static boolean escribir(Object objeto) {
        try (ObjectOutputStream salida = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(ARCHIVO_DEMO)))) {
            salida.writeObject(objeto);
            System.out.println("writeObject OK: " + objeto.getClass().getSimpleName());
            return true;
        } catch (NotSerializableException e) {
            System.out.println("NotSerializableException: " + e.getMessage()
                    + " -> esa clase no implementa Serializable. Solucion: hacerla Serializable o marcar el"
                    + " campo como transient.");
        } catch (FileNotFoundException e) {
            System.out.println("No se pudo abrir el archivo: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error de escritura: " + e.getMessage());
        }
        return false;
    }

    private static PartidaCorregida leer() {
        try (ObjectInputStream entrada = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(ARCHIVO_DEMO)))) {
            return (PartidaCorregida) entrada.readObject();
        } catch (FileNotFoundException e) {
            System.out.println("No existe el archivo de la demo: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error de lectura: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("Clase desconocida en el archivo: " + e.getMessage());
        }
        return null;
    }
}
