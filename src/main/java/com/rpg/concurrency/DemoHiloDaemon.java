package com.rpg.concurrency;

/**
 * INVESTIGACION OBLIGATORIA - Hilos daemon.
 *
 * <h2>Hilo de usuario vs hilo daemon</h2>
 * <ul>
 *   <li><b>Hilo de usuario</b> (el tipo por defecto; main es uno): la JVM NO termina mientras quede al menos
 *       un hilo de usuario vivo. Se usa para trabajo que debe completarse (una semifinal, un efecto).</li>
 *   <li><b>Hilo daemon</b> (hilo "de servicio"): no mantiene viva la JVM. Cuando solo quedan hilos daemon, la
 *       JVM termina y los corta en seco, sin ejecutar sus bloques finally. Sirve para tareas de fondo que no
 *       importa interrumpir: auto-guardado, monitoreo, el recolector de basura de la propia JVM.</li>
 *   <li>Un hilo hereda el caracter daemon del hilo que lo crea. setDaemon(...) solo puede llamarse ANTES de
 *       start(); despues lanza IllegalThreadStateException.</li>
 * </ul>
 * Ejecutar:
 * <pre>
 * java -cp out com.rpg.concurrency.DemoHiloDaemon          (daemon: la JVM cierra en cuanto termina main)
 * java -cp out com.rpg.concurrency.DemoHiloDaemon usuario  (usuario: la JVM espera a que el hilo termine)
 * </pre>
 */
public final class DemoHiloDaemon {

    private static final long INTERVALO_LATIDO_MS = 400;
    private static final long DURACION_MAIN_MS = 1500;
    private static final int LATIDOS_HILO_USUARIO = 10;

    private DemoHiloDaemon() {
    }

    public static void main(String[] args) {
        boolean modoUsuario = args.length > 0 && args[0].equalsIgnoreCase("usuario");
        long inicio = System.currentTimeMillis();

        // Prueba 1: setDaemon(true) despues de start() -> IllegalThreadStateException
        Thread yaIniciado = new Thread(() -> dormir(200), "ya-iniciado");
        yaIniciado.start();
        try {
            yaIniciado.setDaemon(true);
            System.out.println("ERROR: setDaemon despues de start() no lanzo excepcion");
        } catch (IllegalThreadStateException e) {
            System.out.println("Prueba 1 OK: setDaemon(true) despues de start() lanza IllegalThreadStateException");
        }

        // Prueba 2: un hilo con bucle infinito (daemon) o finito (usuario)
        Thread latido = new Thread(() -> latir(modoUsuario), "latido");
        latido.setDaemon(!modoUsuario); // ANTES de start()
        latido.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.printf(
                "[shutdown hook] La JVM se cierra a los %d ms. latido.isAlive()=%b%n",
                System.currentTimeMillis() - inicio, latido.isAlive()), "shutdown-hook"));

        dormir(DURACION_MAIN_MS);
        System.out.printf("main termina a los %d ms. latido: vivo=%b, daemon=%b%n",
                System.currentTimeMillis() - inicio, latido.isAlive(), latido.isDaemon());
        System.out.println(modoUsuario
                ? "Es un hilo de USUARIO: la JVM esperara a que termine sus latidos."
                : "Es un hilo DAEMON con bucle infinito: la JVM se cierra YA aunque siga vivo (su finally no se ejecuta).");
    }

    private static void latir(boolean finito) {
        try {
            int latidos = 0;
            while (!finito || latidos < LATIDOS_HILO_USUARIO) { // daemon: while (true) en la practica
                latidos++;
                System.out.println("  latido " + latidos + " (" + Thread.currentThread().getName() + ")");
                Thread.sleep(INTERVALO_LATIDO_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("  latido interrumpido");
        } finally {
            System.out.println("  finally del latido (solo se ve si el hilo termina de forma normal)");
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Espera interrumpida");
        }
    }
}
