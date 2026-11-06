package com.rpg.concurrency;

import com.rpg.entities.Enemigo;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.TipoPersonaje;

import java.io.Serial;
import java.util.function.Consumer;

/**
 * TALLER CONCURRENCIA (seccion 6): reproduce la condicion de carrera en la vida y muestra las soluciones.
 * Ejecutar con:
 * <pre>java -cp out com.rpg.concurrency.DemoCondicionCarrera</pre>
 * Dos hilos que simulan dos efectos DoT ("Veneno" y "Quemadura") golpean MUCHAS veces al mismo personaje.
 * En el juego real un tick ocurre cada 1,5 s y la ventana de la carrera es diminuta; aqui se repite cientos de
 * miles de veces para que el fallo aparezca siempre.
 * <ol>
 *   <li>Version original (leer-calcular-escribir sin proteccion): se pierden actualizaciones.</li>
 *   <li>Personaje.recibirDanio con synchronized: resultado exacto.</li>
 *   <li>Personaje.recibirDanioConLock con ReentrantLock (investigacion): resultado exacto.</li>
 *   <li>AtomicInteger.addAndGet: atomico, pero no acota la vida ni cambia el estado.</li>
 *   <li>Reto extra: VidaAtomica con updateAndGet / compareAndSet.</li>
 * </ol>
 */
public final class DemoCondicionCarrera {

    private static final int VIDA_INICIAL = 1_000_000;
    private static final int GOLPES_POR_HILO = 200_000;
    private static final int DANIO_POR_GOLPE = 1;
    private static final int INTENTOS_MAXIMOS = 5;

    private DemoCondicionCarrera() {
    }

    /**
     * Personaje con la version ORIGINAL de recibirDanio (sin synchronized). Ojo: getVida() y setVida() son
     * synchronized cada uno por separado, y aun asi falla: dos operaciones atomicas seguidas NO forman una
     * operacion atomica. Entre el getVida() de un hilo y su setVida() el otro hilo puede escribir, y esa
     * escritura se pierde (se sobrescribe con un valor calculado a partir de una vida vieja).
     */
    static class MunecoSinProteccion extends Personaje {
        @Serial
        private static final long serialVersionUID = 1L;

        MunecoSinProteccion(int vida) {
            super("Muneco", TipoPersonaje.MONSTRUO, vida, 0, 0);
        }

        @Override
        public void recibirDanio(int cantidad) { // SIN synchronized: la version del proyecto base
            int leida = getVida();          // 1. leer
            int nueva = leida - cantidad;   // 2. calcular
            setVida(nueva);                 // 3. escribir
        }

        @Override
        public String realizarAtaqueEspecial(Personaje objetivo) {
            return "El muneco no ataca";
        }
    }

    public static void main(String[] args) {
        int esperado = VIDA_INICIAL - 2 * GOLPES_POR_HILO * DANIO_POR_GOLPE;
        System.out.printf("Vida inicial %d, 2 hilos x %d golpes de %d -> vida esperada %d%n%n",
                VIDA_INICIAL, GOLPES_POR_HILO, DANIO_POR_GOLPE, esperado);

        System.out.println("1) SIN PROTECCION (leer-calcular-escribir intercalado):");
        for (int intento = 1; intento <= INTENTOS_MAXIMOS; intento++) {
            MunecoSinProteccion muneco = new MunecoSinProteccion(VIDA_INICIAL);
            golpearEnParalelo(muneco::recibirDanio);
            int perdidas = muneco.getVida() - esperado;
            System.out.printf("   intento %d: vida final %d -> %d actualizaciones PERDIDAS%n",
                    intento, muneco.getVida(), perdidas);
            if (perdidas > 0) {
                break;
            }
        }

        Enemigo conSynchronized = new Enemigo("Objetivo", VIDA_INICIAL, 0, 0);
        golpearEnParalelo(conSynchronized::recibirDanio);
        System.out.printf("%n2) CON synchronized (Personaje.recibirDanio): vida final %d -> %s%n",
                conSynchronized.getVida(), conSynchronized.getVida() == esperado ? "CORRECTO" : "ERROR");

        Enemigo conLock = new Enemigo("Objetivo", VIDA_INICIAL, 0, 0);
        golpearEnParalelo(conLock::recibirDanioConLock);
        System.out.printf("3) CON ReentrantLock (lock/unlock en try-finally): vida final %d -> %s%n",
                conLock.getVida(), conLock.getVida() == esperado ? "CORRECTO" : "ERROR");

        System.out.println("\n4) AtomicInteger.addAndGet(-danio) con vida 1000 y 2 hilos x 1000 golpes:");
        VidaAtomica ingenua = new VidaAtomica(1000);
        golpearEnParalelo(danio -> ingenua.recibirDanioIngenuo(danio), 1000);
        System.out.printf("   vida final %d (no se acota a 0) y ningun estado pasa a MUERTO: addAndGet solo"
                + " protege UNA variable%n", ingenua.getVida());

        System.out.println("\n5) RETO EXTRA: VidaAtomica.recibirDanio con updateAndGet (acota a 0..max):");
        VidaAtomica acotada = new VidaAtomica(1000);
        golpearEnParalelo(danio -> acotada.recibirDanio(danio), 1000);
        System.out.printf("   vida final %d, vivo=%b. Curar un muerto con CAS recupera: %d%n",
                acotada.getVida(), acotada.estaVivo(), acotada.curar(50));
        VidaAtomica curable = new VidaAtomica(1000);
        curable.recibirDanio(500);
        golpearEnParalelo(c -> curable.curar(c), 400);
        System.out.printf("   curar con compareAndSet desde 500, 2 hilos x 400 de +1 -> %d (tope 1000)%n",
                curable.getVida());
    }

    private static void golpearEnParalelo(Consumer<Integer> golpe) {
        golpearEnParalelo(golpe, GOLPES_POR_HILO);
    }

    /** Lanza dos hilos ("Veneno" y "Quemadura") que aplican el golpe a la vez y espera a que terminen. */
    private static void golpearEnParalelo(Consumer<Integer> golpe, int repeticiones) {
        Runnable dot = () -> {
            for (int i = 0; i < repeticiones; i++) {
                golpe.accept(DANIO_POR_GOLPE);
            }
        };
        Thread veneno = new Thread(dot, "Veneno");
        Thread quemadura = new Thread(dot, "Quemadura");
        veneno.start();
        quemadura.start();
        try {
            veneno.join();
            quemadura.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Demo interrumpida: el resultado no es fiable.");
        }
    }
}
