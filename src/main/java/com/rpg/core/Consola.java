package com.rpg.core;

import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Entrada por teclado con validacion y reintento.
 * <p>
 * TALLER CONCURRENCIA: {@code Scanner.nextLine()} se bloquea y NO responde a interrupt(), asi que no se podria
 * cortar la espera cuando se acaba el tiempo del turno. Solucion: un hilo daemon ("lector-consola") es el unico
 * que usa el Scanner; deja cada linea en una {@link BlockingQueue}. El hilo principal espera en la cola con
 * {@code take()}, que SI es interrumpible: asi el temporizador de turno puede despertarlo.
 */
public class Consola {

    private static final int LARGO_MAXIMO_NOMBRE = 15;
    private static final String PATRON_NOMBRE = "[\\p{L}0-9 ]+";
    /**
     * Marca de fin de entrada. Aqui se compara con == A PROPOSITO (identidad del objeto, no contenido): es una
     * instancia unica creada con new String, asi que ninguna linea que escriba el jugador puede confundirse
     * con ella. Para comparar textos normales se usa siempre equals / equalsIgnoreCase.
     */
    @SuppressWarnings("StringOperationCanBeSimplified")
    private static final String FIN_DE_ENTRADA = new String("<fin-de-entrada>");

    private final BlockingQueue<String> lineas = new LinkedBlockingQueue<>();

    public Consola(Scanner scanner) {
        Thread lector = new Thread(() -> leerTeclado(scanner), "lector-consola");
        // Hilo de monitoreo del teclado: vive bloqueado en nextLine(); no debe impedir que la JVM cierre.
        lector.setDaemon(true);
        lector.start();
    }

    /** Cuerpo del hilo lector: pasa cada linea del teclado a la cola. */
    private void leerTeclado(Scanner scanner) {
        try {
            while (scanner.hasNextLine()) {
                lineas.put(scanner.nextLine());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restauramos la bandera y dejamos de leer
        } catch (IllegalStateException e) {
            System.out.println("La entrada de teclado se cerro: " + e.getMessage());
        } finally {
            lineas.offer(FIN_DE_ENTRADA); // avisa al hilo principal de que no habra mas lineas
        }
    }

    /** Espera la siguiente linea (interrumpible). Lanza NoSuchElementException si la entrada termino. */
    private String siguienteLinea() throws InterruptedException {
        String linea = lineas.take();
        if (linea == FIN_DE_ENTRADA) { // identidad a proposito (ver FIN_DE_ENTRADA)
            lineas.offer(FIN_DE_ENTRADA); // para que las lecturas posteriores tambien lo vean
            throw new NoSuchElementException("No hay mas entrada");
        }
        return linea.trim();
    }

    /** Lectura normal (no cancelable): si llegara una interrupcion, se recuerda y se sigue esperando. */
    public String leerLinea(String mensaje) {
        System.out.print(mensaje);
        boolean interrumpido = false;
        try {
            while (true) {
                try {
                    return siguienteLinea();
                } catch (InterruptedException e) {
                    // Esta lectura no admite cancelacion: anotamos la interrupcion y volvemos a esperar.
                    interrumpido = true;
                }
            }
        } finally {
            if (interrumpido) {
                Thread.currentThread().interrupt(); // devolvemos la bandera tal como la encontramos
            }
        }
    }

    public int leerEntero(String mensaje, int minimo, int maximo) {
        while (true) {
            String texto = leerLinea(mensaje);
            if (texto.isEmpty()) {
                System.out.println("No escribiste nada.");
                continue;
            }
            Integer valor = convertirEnRango(texto, minimo, maximo);
            if (valor != null) {
                return valor;
            }
        }
    }

    /**
     * TALLER CONCURRENCIA: igual que {@link #leerEntero}, pero la espera se puede cortar con interrupt()
     * (lo hace el temporizador de turno cuando se acaba el tiempo).
     * @throws InterruptedException si interrumpen al hilo mientras espera la respuesta
     */
    public int leerEnteroInterrumpible(String mensaje, int minimo, int maximo) throws InterruptedException {
        while (true) {
            System.out.print(mensaje);
            String texto = siguienteLinea();
            if (texto.isEmpty()) {
                System.out.println("No escribiste nada.");
                continue;
            }
            Integer valor = convertirEnRango(texto, minimo, maximo);
            if (valor != null) {
                return valor;
            }
        }
    }

    /** @return el numero si es valido y esta en rango; null (tras explicar el error) si no */
    private Integer convertirEnRango(String texto, int minimo, int maximo) {
        try {
            int valor = Integer.parseInt(texto);
            if (valor >= minimo && valor <= maximo) {
                return valor;
            }
            System.out.printf("Numero fuera de rango (%d a %d).%n", minimo, maximo);
        } catch (NumberFormatException e) {
            System.out.println("\"" + texto + "\" no es un numero.");
        }
        return null;
    }

    public String leerNombre(String mensaje) {
        while (true) {
            String nombre = leerLinea(mensaje);
            if (nombre.isEmpty()) {
                System.out.println("El nombre no puede estar vacio.");
            } else if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
                System.out.println("Maximo " + LARGO_MAXIMO_NOMBRE + " caracteres.");
            } else if (!nombre.matches(PATRON_NOMBRE)) {
                System.out.println("Usa solo letras, numeros y espacios.");
            } else {
                return nombre;
            }
        }
    }

    /** Pregunta de si/no (TALLER ARCHIVOS: confirmar antes de borrar). */
    public boolean leerConfirmacion(String mensaje) {
        while (true) {
            String respuesta = leerLinea(mensaje + " (s/n): ");
            if (respuesta.equalsIgnoreCase("s") || respuesta.equalsIgnoreCase("si")) {
                return true;
            }
            if (respuesta.equalsIgnoreCase("n") || respuesta.equalsIgnoreCase("no")) {
                return false;
            }
            System.out.println("Responde s o n.");
        }
    }
}
