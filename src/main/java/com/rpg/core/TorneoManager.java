package com.rpg.core;

import com.rpg.entities.Escuadron;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;

import java.util.List;

/** Torneo de 4 gladiadores: dos semifinales EN PARALELO (un hilo cada una) y una final. */
public class TorneoManager {

    /** Tiempo que espera el hilo principal antes de mirar el estado de las semifinales en plena ejecucion. */
    private static final long OBSERVACION_MS = 120;

    private final Arena arena;
    private final RegistroCombate registro;

    public TorneoManager(Arena arena, RegistroCombate registro) {
        this.arena = arena;
        this.registro = registro;
    }

    /** @return el campeon del torneo, o null si el torneo fue interrumpido */
    public Personaje jugarTorneo() {
        List<Personaje> gladiadores = crearGladiadores().getMiembros();
        for (Personaje gladiador : gladiadores) {
            gladiador.setRegistroEfectos(registro::agregar);
        }
        Personaje a1 = gladiadores.get(0);
        Personaje a2 = gladiadores.get(1);
        Personaje b1 = gladiadores.get(2);
        Personaje b2 = gladiadores.get(3);

        // TALLER CONCURRENCIA (resuelto): cada semifinal corre en su propio hilo, a la vez.
        // finalistas es la memoria compartida: cada hilo escribe SOLO su casilla y el hilo principal la lee
        // SOLO despues de los dos join(). join() establece una relacion happens-before: todo lo que el hilo
        // escribio antes de terminar es visible para quien hizo el join, sin necesidad de synchronized.
        Personaje[] finalistas = new Personaje[2];
        Thread semifinalA = new Thread(() -> finalistas[0] = simularSemifinal(a1, a2), "Semifinal-A");
        Thread semifinalB = new Thread(() -> finalistas[1] = simularSemifinal(b1, b2), "Semifinal-B");

        mostrarEstados("antes de start()", semifinalA, semifinalB);         // NEW
        semifinalA.start();                                                  // start(), nunca run()
        semifinalB.start();
        mostrarEstados("justo despues de start()", semifinalA, semifinalB); // RUNNABLE
        observarDuranteEjecucion(semifinalA, semifinalB);                    // TIMED_WAITING / RUNNABLE / BLOCKED

        try {
            semifinalA.join(); // el hilo principal espera a que termine la semifinal A...
            semifinalB.join(); // ...y luego a la B. Hasta que ambas terminan NO se leen los ganadores.
        } catch (InterruptedException e) {
            // Interrumpieron al hilo principal mientras esperaba: cancelamos las semifinales, restauramos la
            // bandera de interrupcion y abortamos el torneo sin leer resultados a medias.
            registro.agregar("Torneo interrumpido: se cancelan las semifinales");
            semifinalA.interrupt();
            semifinalB.interrupt();
            Thread.currentThread().interrupt();
            return null;
        }
        mostrarEstados("despues de join()", semifinalA, semifinalB);        // TERMINATED

        // Los dos join() ya retornaron: es seguro leer la memoria compartida.
        return simularFinal(finalistas[0], finalistas[1]);
    }

    public Personaje simularSemifinal(Personaje a, Personaje b) {
        registro.agregar("=== SEMIFINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        Personaje ganador = arena.duelo(a, b);
        registro.agregar("Clasifica a la final: %s", ganador.getNombre());
        return ganador;
    }

    private Personaje simularFinal(Personaje a, Personaje b) {
        a.restaurarVida();
        b.restaurarVida();
        registro.agregar("=== FINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        return arena.duelo(a, b);
    }

    /**
     * Ciclo de vida de un hilo:
     * NEW (creado, sin start) -> RUNNABLE (ejecutando o listo para ejecutar) -> TIMED_WAITING (dormido en
     * Thread.sleep entre turnos) / BLOCKED (esperando el candado synchronized del registro) -> TERMINATED
     * (run() termino; join() ya retorno).
     */
    private void mostrarEstados(String momento, Thread a, Thread b) {
        registro.agregar("[Estado hilos %s] %s=%s | %s=%s", momento, a.getName(), a.getState(),
                b.getName(), b.getState());
    }

    private void observarDuranteEjecucion(Thread a, Thread b) {
        try {
            Thread.sleep(OBSERVACION_MS);
        } catch (InterruptedException e) {
            // Restauramos la bandera: el join() siguiente lanzara InterruptedException y abortara el torneo.
            Thread.currentThread().interrupt();
            return;
        }
        mostrarEstados("durante la ejecucion", a, b);
        registro.agregar("  (TIMED_WAITING = dormido en Thread.sleep entre turnos; RUNNABLE = ejecutando un turno;"
                + " BLOCKED = esperando el candado del registro)");
    }

    private Escuadron crearGladiadores() {
        Escuadron escuadron = new Escuadron("Gladiadores");
        escuadron.agregar(new Guerrero("Maximo"));
        escuadron.agregar(new Mago("Circe"));
        escuadron.agregar(new Guerrero("Aquiles"));
        escuadron.agregar(new Mago("Merlina"));
        return escuadron;
    }
}
