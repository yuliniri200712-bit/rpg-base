package com.rpg.concurrency;

import com.rpg.persistencia.ArchivoAutoGuardado;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * TALLER CONCURRENCIA (seccion 4): auto-guardado periodico en un hilo daemon.
 * <p>
 * Cada {@code intervaloMs} toma una "foto" del estado de la partida (estadisticas de los personajes del gremio,
 * combate en curso, perfil y registro del ultimo combate) y:
 * <ol>
 *   <li>La guarda EN MEMORIA: {@link #getUltimaCopia()} y un historial con las ultimas copias
 *       (lo que pide el taller de concurrencia).</li>
 *   <li>TALLER ARCHIVOS (seccion 7): ademas la escribe a disco a traves de {@link ArchivoAutoGuardado}, cuyos
 *       metodos synchronized impiden que este hilo y el principal usen el archivo a la vez.</li>
 * </ol>
 * Es un hilo DAEMON: la JVM no lo espera al salir. Al terminar main solo quedan hilos daemon, la JVM se cierra y
 * este hilo se corta en seco. Por eso la escritura a disco es atomica (archivo temporal + move) y protegida
 * con un candado: nunca queda un autoguardado.txt a medio escribir.
 */
public class AutoGuardadoDaemon implements Runnable {

    private static final int MAXIMO_COPIAS_EN_MEMORIA = 5;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Supplier<String> generadorInstantanea;
    private final long intervaloMs;
    private final ArchivoAutoGuardado archivo;

    /** Ultimas copias en memoria (la mas reciente al final). Protegida por synchronized(historial). */
    private final Deque<String> historial = new ArrayDeque<>();
    private volatile String ultimaCopia = "";
    private volatile int guardadosRealizados;
    private volatile String horaUltimoGuardado = "-";
    private Thread hilo;

    /**
     * @param generadorInstantanea produce el texto con el estado actual (MotorJuego::generarInstantanea)
     * @param intervaloMs          milisegundos entre guardado y guardado
     * @param archivo              destino en disco (taller de archivos), o null para guardar solo en memoria
     */
    public AutoGuardadoDaemon(Supplier<String> generadorInstantanea, long intervaloMs, ArchivoAutoGuardado archivo) {
        if (generadorInstantanea == null || intervaloMs <= 0) {
            throw new IllegalArgumentException("Generador obligatorio e intervalo positivo");
        }
        this.generadorInstantanea = generadorInstantanea;
        this.intervaloMs = intervaloMs;
        this.archivo = archivo;
    }

    /** Crea el hilo, lo marca como daemon ANTES de start() y lo arranca. Solo puede iniciarse una vez. */
    public synchronized void iniciar() {
        if (hilo != null) {
            throw new IllegalStateException("El auto-guardado ya esta iniciado (un Thread no se inicia dos veces)");
        }
        hilo = new Thread(this, "auto-guardado");
        // setDaemon(true) DEBE ir antes de start(): sobre un hilo ya iniciado lanza IllegalThreadStateException.
        hilo.setDaemon(true);
        hilo.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(intervaloMs);
            } catch (InterruptedException e) {
                // Alguien pidio detener el auto-guardado: restauramos la bandera y salimos del bucle.
                Thread.currentThread().interrupt();
                System.out.println("[Auto-guardado] detenido por interrupcion");
                break;
            }
            guardarAhora();
        }
    }

    /** Toma una copia del estado y la guarda en memoria (y en disco si hay archivo configurado). */
    public void guardarAhora() {
        int numero = guardadosRealizados + 1;
        String hora = LocalTime.now().format(HORA);
        String copia = String.format("=== AUTO-GUARDADO #%d (%s, hilo %s) ===%n%s",
                numero, hora, Thread.currentThread().getName(), generadorInstantanea.get());
        synchronized (historial) {
            historial.addLast(copia);
            while (historial.size() > MAXIMO_COPIAS_EN_MEMORIA) {
                historial.removeFirst();
            }
        }
        ultimaCopia = copia;
        horaUltimoGuardado = hora;
        guardadosRealizados = numero; // solo escribe este hilo; volatile basta para que el principal lo lea
        if (archivo != null) {
            archivo.escribir(copia);
        }
    }

    public String getUltimaCopia() {
        return ultimaCopia;
    }

    public List<String> getHistorial() {
        synchronized (historial) {
            return new ArrayList<>(historial);
        }
    }

    public int getGuardadosRealizados() {
        return guardadosRealizados;
    }

    public String getHoraUltimoGuardado() {
        return horaUltimoGuardado;
    }

    public long getIntervaloMs() {
        return intervaloMs;
    }

    public synchronized boolean estaVivo() {
        return hilo != null && hilo.isAlive();
    }

    public synchronized boolean esDaemon() {
        return hilo != null && hilo.isDaemon();
    }
}
