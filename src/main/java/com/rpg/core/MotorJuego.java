package com.rpg.core;

import com.rpg.concurrency.AutoGuardadoDaemon;
import com.rpg.concurrency.GestorTurno;
import com.rpg.entities.Enemigo;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.exceptions.CapacidadExcedidaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;
import com.rpg.persistencia.ArchivoAutoGuardado;
import com.rpg.persistencia.EstadoPartida;
import com.rpg.persistencia.GestorPartida;
import com.rpg.persistencia.HistorialCombates;
import com.rpg.persistencia.InfoArchivos;
import com.rpg.persistencia.RutasJuego;

import java.util.List;
import java.util.Random;

/**
 * Coordina el flujo del juego: crear heroe, combatir, torneo, bestiario, guardado y carga.
 * <p>
 * Hilos que intervienen (taller de concurrencia):
 * <ul>
 *   <li>main: menu, turnos del jugador y del enemigo.</li>
 *   <li>temporizador-turno: cuenta regresiva de cada turno del jugador (uno nuevo por turno).</li>
 *   <li>Efecto@Personaje: un hilo por cada efecto DoT/HoT activo.</li>
 *   <li>Semifinal-A / Semifinal-B: las semifinales del torneo, en paralelo.</li>
 *   <li>auto-guardado: daemon que guarda el estado cada cierto intervalo.</li>
 *   <li>lector-consola: daemon que lee el teclado (ver Consola).</li>
 * </ul>
 */
public class MotorJuego {

    private static final String NOMBRE_GREMIO = "Guardianes del Alba";
    private static final int CURACION_POCION_VIDA = 30;
    /** Segundos que tiene el jugador para elegir su accion. */
    public static final int SEGUNDOS_POR_TURNO = 15;
    /** Cada cuanto guarda el daemon de auto-guardado. */
    public static final long INTERVALO_AUTOGUARDADO_MS = 10_000;
    private static final int LINEAS_MAXIMAS_A_MOSTRAR = 30;

    private final Consola consola;
    private final RegistroCombate registro = new RegistroCombate();
    private final Arena arena = new Arena(registro, new Random());
    private final Bestiario bestiario = new Bestiario();
    private final TorneoManager torneo = new TorneoManager(arena, registro);

    // TALLER ARCHIVOS: clases de com.rpg.persistencia
    private final HistorialCombates historial = new HistorialCombates(RutasJuego.archivoHistorial());
    private final GestorPartida gestorPartida = new GestorPartida(RutasJuego.archivoPartida());
    private final ArchivoAutoGuardado archivoAutoGuardado = new ArchivoAutoGuardado(RutasJuego.archivoAutoGuardado());
    private final AutoGuardadoDaemon autoGuardado;

    // volatile: el daemon de auto-guardado lee estas referencias desde otro hilo, y el hilo principal puede
    // reemplazarlas (al cargar una partida o al empezar/terminar un combate). volatile garantiza que el daemon
    // vea siempre la referencia mas reciente.
    private volatile PerfilJugador perfil = new PerfilJugador();
    private volatile Gremio gremio = new Gremio(NOMBRE_GREMIO);
    private volatile Personaje jugadorEnCombate;
    private volatile Enemigo enemigoEnCombate;
    // TALLER ARCHIVOS (resuelto): ultimoGuardado es el estado de la partida en memoria; ahora ademas se guarda
    // en disco (guardarPartida / guardarProgreso) y se recupera con cargarPartida(), que se llama desde Main.
    private volatile String ultimoGuardado = "";

    public MotorJuego(Consola consola) {
        this.consola = consola;
        this.autoGuardado = new AutoGuardadoDaemon(this::generarInstantanea, INTERVALO_AUTOGUARDADO_MS,
                archivoAutoGuardado);
    }

    /** TALLER CONCURRENCIA (seccion 4): arranca el hilo daemon de auto-guardado (se llama una vez desde Main). */
    public void iniciarAutoGuardado() {
        autoGuardado.iniciar();
        System.out.printf("[Auto-guardado] hilo daemon activo: guarda cada %d s en memoria y en %s%n",
                INTERVALO_AUTOGUARDADO_MS / 1000, archivoAutoGuardado.getArchivo().getPath());
    }

    // ---- opciones del menu principal

    public void combateRapido() {
        registro.limpiar();
        Personaje jugador = crearJugador();
        Enemigo enemigo = elegirEnemigo();
        // Los hilos de efectos escriben en el mismo registro del combate
        jugador.setRegistroEfectos(registro::agregar);
        enemigo.setRegistroEfectos(registro::agregar);
        jugadorEnCombate = jugador;
        enemigoEnCombate = enemigo;
        registro.agregar("%n=== %s vs %s ===", jugador.getNombre(), enemigo.getNombre());

        boolean huyo;
        try {
            huyo = combatir(jugador, enemigo);
        } finally {
            // TALLER CONCURRENCIA (seccion 2): al terminar el combate (o si algo falla) se interrumpen los
            // hilos de los efectos activos y se espera a que terminen limpios.
            jugador.detenerEfectos();
            enemigo.detenerEfectos();
            jugadorEnCombate = null;
            enemigoEnCombate = null;
        }

        cerrarCombate(jugador, enemigo, huyo);
        perfil.registrarCombateEnSesion();
        guardarProgreso();
    }

    public void jugarTorneo() {
        registro.limpiar();
        Personaje campeon = torneo.jugarTorneo();
        if (campeon == null) {
            System.out.println("El torneo se cancelo antes de terminar.");
            return;
        }
        registro.agregar("%nCAMPEON DEL TORNEO: %s", campeon.getNombre());
        perfil.desbloquearLogro("Campeon del Torneo");
        perfil.registrarCombateEnSesion();
        guardarProgreso();
    }

    public void mostrarBestiario() {
        PerfilJugador p = perfil;
        System.out.println("\n=== BESTIARIO ===");
        System.out.print(bestiario.describir());
        System.out.printf("Victorias: %d | Derrotas: %d | Logros: %s | Combates en esta sesion: %d%n",
                p.getVictorias(), p.getDerrotas(), p.getLogros(), p.getCombatesEnSesion());
    }

    // ---- creacion de personajes

    private Personaje crearJugador() {
        int clase = consola.leerEntero("Clase (1. Guerrero, 2. Mago): ", 1, 2);
        String nombre = consola.leerNombre("Nombre del heroe: ");
        Personaje jugador = switch (clase) {
            case 1 -> new Guerrero(nombre);
            default -> new Mago(nombre);
        };
        entregarKitInicial(jugador);
        Gremio g = gremio;
        jugador.unirseAGremio(g);
        System.out.printf("%s se une al gremio %s (%d miembros).%n",
                jugador.getNombre(), g.getNombre(), g.getMiembros().size());
        return jugador;
    }

    private void entregarKitInicial(Personaje jugador) {
        List<Pocion> kit = List.of(
                new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN),
                new Pocion("Elixir de Regeneracion", 0, RaridadItem.RARO,
                        new EstadoEfecto("Regeneracion", TipoEfecto.CURACION_CONTINUA, 6, 3), false),
                new Pocion("Antidoto", 0, RaridadItem.COMUN, null, true));
        try {
            for (Pocion pocion : kit) {
                jugador.getInventario().agregar(pocion);
            }
        } catch (CapacidadExcedidaException e) {
            System.out.println("No se pudo entregar todo el kit: " + e.getMessage());
        }
    }

    private Enemigo elegirEnemigo() {
        List<String> nombres = bestiario.nombres();
        System.out.println("Rivales disponibles:");
        for (int i = 0; i < nombres.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, nombres.get(i));
        }
        while (true) {
            String texto = consola.leerLinea("Rival (numero o nombre): ");
            Enemigo enemigo = resolverRival(texto, nombres);
            if (enemigo != null) {
                return enemigo;
            }
            System.out.println("No existe ese rival.");
        }
    }

    private Enemigo resolverRival(String texto, List<String> nombres) {
        try {
            int numero = Integer.parseInt(texto);
            boolean valido = numero >= 1 && numero <= nombres.size();
            return valido ? bestiario.crearEnemigo(nombres.get(numero - 1)) : null;
        } catch (NumberFormatException e) {
            return bestiario.crearEnemigo(texto);
        }
    }

    // ---- bucle de combate

    /** @return true si el jugador huyo */
    private boolean combatir(Personaje jugador, Enemigo enemigo) {
        int ronda = 1;
        while (jugador.estaVivo() && enemigo.estaVivo()) {
            registro.agregar("%n-- Ronda %d --", ronda);
            if (arena.iniciarTurno(jugador) && turnoJugador(jugador, enemigo)) {
                return true;
            }
            arena.turnoAutomatico(enemigo, jugador);
            ronda++;
        }
        return false;
    }

    /**
     * Turno del jugador. Las acciones que no gastan turno (ver estado, cancelar) vuelven a pedir.
     * @return true si el jugador decidio huir
     */
    private boolean turnoJugador(Personaje jugador, Enemigo enemigo) {
        boolean turnoGastado;
        do {
            registro.agregar(FormatoCombate.resumen(jugador));
            registro.agregar(FormatoCombate.resumen(enemigo));
            Accion accion = pedirAccion();
            if (accion == Accion.HUIR) {
                return true;
            }
            if (!jugador.estaVivo() || !enemigo.estaVivo()) {
                // Un efecto (otro hilo) decidio el combate mientras el jugador elegia
                return false;
            }
            turnoGastado = switch (accion) {
                case ATACAR -> {
                    arena.atacar(jugador, enemigo);
                    yield true;
                }
                case ESPECIAL -> arena.intentarEspecial(jugador, enemigo);
                case POCION -> usarPocion(jugador);
                case TIEMPO_AGOTADO -> {
                    registro.agregar("%s tardo demasiado y pierde el turno", jugador.getNombre());
                    yield true;
                }
                default -> {
                    mostrarEstado(jugador);
                    yield false;
                }
            };
        } while (!turnoGastado && enemigo.estaVivo());
        return false;
    }

    /**
     * Pide la accion al jugador con un limite de tiempo.
     * <p>
     * TALLER CONCURRENCIA (resuelto, seccion 1): antes esta lectura bloqueaba el programa sin limite. Ahora:
     * <ol>
     *   <li>GestorTurno arranca un hilo NUEVO con TemporizadorTurnoTask (cuenta regresiva de
     *       {@value #SEGUNDOS_POR_TURNO} s).</li>
     *   <li>El hilo principal espera la respuesta con una lectura interrumpible (Consola).</li>
     *   <li>Si el jugador responde a tiempo, el finally cancela el temporizador con interrupt().</li>
     *   <li>Si el tiempo se agota, el callback (que corre en el hilo del temporizador) interrumpe al hilo
     *       principal; la lectura lanza InterruptedException y el turno se pierde (TIEMPO_AGOTADO).</li>
     * </ol>
     */
    private Accion pedirAccion() {
        System.out.println("1. Atacar  2. Habilidad especial  3. Usar pocion  4. Ver estado  0. Huir");
        System.out.printf("(Tienes %d segundos para elegir)%n", SEGUNDOS_POR_TURNO);
        Thread hiloJugador = Thread.currentThread();
        GestorTurno gestor = new GestorTurno();
        TurnoCallback alAgotarse = () -> {
            System.out.println();
            System.out.println("[Temporizador] Se acabo el tiempo!");
            hiloJugador.interrupt(); // despierta al hilo principal, que esta esperando la entrada
        };
        gestor.iniciar(SEGUNDOS_POR_TURNO, alAgotarse);
        try {
            int opcion = consola.leerEnteroInterrumpible("Accion: ", 0, 4);
            return switch (opcion) {
                case 1 -> Accion.ATACAR;
                case 2 -> Accion.ESPECIAL;
                case 3 -> Accion.POCION;
                case 4 -> Accion.ESTADO;
                default -> Accion.HUIR;
            };
        } catch (InterruptedException e) {
            // Esta interrupcion es la senal del propio temporizador ("se acabo el tiempo"), no una peticion de
            // cerrar el programa: la consumimos aqui convirtiendola en la accion TIEMPO_AGOTADO.
            return Accion.TIEMPO_AGOTADO;
        } finally {
            // Respondio a tiempo -> se cancela el temporizador con interrupt(). Si ya se habia disparado,
            // cancelar() no hace nada malo. Despues se limpia una posible interrupcion tardia del callback
            // (la carrera "respondio justo cuando llegaba a 0") para que no afecte a la siguiente espera.
            gestor.cancelar();
            boolean interrupcionTardia = Thread.interrupted();
            if (interrupcionTardia) {
                System.out.println("(La respuesta llego justo al agotarse el tiempo: se acepta la respuesta)");
            }
        }
    }

    private boolean usarPocion(Personaje jugador) {
        Inventario<Pocion> inventario = jugador.getInventario();
        if (inventario.estaVacio()) {
            System.out.println("No tienes pociones.");
            return false;
        }
        int numero = 1;
        for (Pocion pocion : inventario) {
            System.out.printf("  %d. %s%n", numero++, pocion.getNombre());
        }
        int eleccion = consola.leerEntero("Pocion (0 = cancelar): ", 0, inventario.tamanio());
        if (eleccion == 0) {
            return false;
        }
        registro.agregar(jugador.usarPocion(inventario.quitar(eleccion - 1)));
        return true;
    }

    private void mostrarEstado(Personaje jugador) {
        System.out.printf("Habilidades: %s | Arma: %s | Defensa: %d | Pociones: %d%n",
                jugador.getHabilidades(),
                jugador.getArma() != null ? jugador.getArma().getNombre() : "ninguna",
                jugador.getDefensa(), jugador.getInventario().tamanio());
        for (EstadoEfecto efecto : jugador.getEfectos()) {
            System.out.printf("Efecto activo: %s (%d ticks restantes, en su propio hilo)%n",
                    efecto.getNombre(), efecto.getTurnosRestantes());
        }
    }

    // ---- resultado del combate

    private void cerrarCombate(Personaje jugador, Enemigo enemigo, boolean huyo) {
        PerfilJugador p = perfil;
        if (huyo) {
            registro.agregar("%s huye del combate.", jugador.getNombre());
        } else if (jugador.estaVivo()) {
            registro.agregar("%nVictoria de %s!", jugador.getNombre());
            p.registrarVictoria();
            bestiario.registrarEnemigoDerrotado(enemigo.getNombre());
            desbloquearLogros(jugador, enemigo);
            repartirBotin(jugador);
        } else {
            registro.agregar("%n%s ha caido ante %s.", jugador.getNombre(), enemigo.getNombre());
            p.registrarDerrota();
        }
    }

    private void desbloquearLogros(Personaje jugador, Enemigo enemigo) {
        boolean intacto = jugador.getVida() == jugador.getVidaMaxima();
        List<String> candidatos = List.of("Primera Victoria", "Cazador de " + enemigo.getNombre(),
                intacto ? "Intocable" : "");
        for (String logro : candidatos) {
            if (!logro.isEmpty() && perfil.desbloquearLogro(logro)) {
                registro.agregar("Logro desbloqueado: %s", logro);
            }
        }
    }

    private void repartirBotin(Personaje jugador) {
        try {
            jugador.getInventario().agregar(new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN));
            registro.agregar("Botin: Pocion de Vida.");
        } catch (CapacidadExcedidaException e) {
            registro.agregar("Botin perdido: %s", e.getMessage());
        }
    }

    // ---- guardado

    /**
     * Guarda las estadisticas al terminar una pelea.
     * <ul>
     *   <li>En memoria: el texto queda en {@code ultimoGuardado} (como en el proyecto base).</li>
     *   <li>TALLER ARCHIVOS (resuelto): el registro del combate se AGREGA (append) al historial .txt con
     *       BufferedWriter, y la partida completa se serializa en partidas/partida.dat con ObjectOutputStream.
     *       Ambos con try-with-resources (ver com.rpg.persistencia).</li>
     *   <li>TALLER CONCURRENCIA (resuelto): este guardado manual sigue existiendo, pero ya no es el unico: el
     *       hilo daemon {@link AutoGuardadoDaemon} guarda solo, de fondo, cada
     *       {@value #INTERVALO_AUTOGUARDADO_MS} ms mientras se juega.</li>
     * </ul>
     */
    public void guardarProgreso() {
        ultimoGuardado = generarInstantanea();
        System.out.println("[Progreso guardado en memoria]");
        if (registro.guardarEnArchivo(historial, true)) {
            System.out.println("[Combate agregado al historial " + historial.getArchivo().getPath() + "]");
        }
        guardarPartida();
    }

    /**
     * Texto con el estado actual de la partida: perfil, gremio con las estadisticas de cada personaje, combate
     * en curso y registro del ultimo combate. Lo llama el hilo principal y tambien el daemon de auto-guardado,
     * por eso solo usa referencias volatile y metodos sincronizados.
     */
    public String generarInstantanea() {
        PerfilJugador p = perfil;
        Gremio g = gremio;
        Personaje jugador = jugadorEnCombate;
        Enemigo enemigo = enemigoEnCombate;
        String salto = System.lineSeparator();

        StringBuilder contenido = new StringBuilder();
        contenido.append("=== PARTIDA RPG ===").append(salto);
        contenido.append("Victorias: ").append(p.getVictorias()).append(salto);
        contenido.append("Derrotas: ").append(p.getDerrotas()).append(salto);
        contenido.append("Logros: ").append(String.join(", ", p.getLogros())).append(salto);
        List<Personaje> miembros = g.getMiembros();
        contenido.append("Gremio ").append(g.getNombre()).append(": ")
                .append(miembros.size()).append(" miembros").append(salto);
        for (Personaje miembro : miembros) {
            contenido.append("  - ").append(miembro.getTipo()).append(' ')
                    .append(FormatoCombate.resumen(miembro)).append(salto);
        }
        if (jugador != null && enemigo != null) {
            contenido.append("Combate en curso: ").append(FormatoCombate.resumen(jugador))
                    .append(" vs ").append(FormatoCombate.resumen(enemigo)).append(salto);
        }
        contenido.append("Enemigos derrotados: ").append(bestiario.resumenDerrotados()).append(salto);
        contenido.append(salto).append("--- Ultimo combate ---").append(salto);
        contenido.append(registro.getTexto());
        return contenido.toString();
    }

    public String getUltimoGuardado() {
        return ultimoGuardado;
    }

    // ---- TALLER ARCHIVOS: opciones de persistencia del menu

    /** Serializa la partida (perfil, gremio con sus personajes, bestiario y resumen) en partidas/partida.dat. */
    public void guardarPartida() {
        EstadoPartida estado = new EstadoPartida(perfil, gremio, bestiario.getDerrotados(), ultimoGuardado);
        if (gestorPartida.guardar(estado)) {
            System.out.printf("[Partida guardada en %s (%d bytes)]%n", gestorPartida.getArchivo().getPath(),
                    gestorPartida.getArchivo().length());
        }
    }

    /** Lee partidas/partida.dat con ObjectInputStream y continua el juego con ese estado. */
    public void cargarPartida() {
        EstadoPartida estado = gestorPartida.cargar();
        if (estado == null) {
            return; // GestorPartida ya explico el motivo (no existe, corrupto, version incompatible...)
        }
        PerfilJugador perfilCargado = estado.getPerfil();
        Gremio gremioCargado = estado.getGremio();
        perfil = perfilCargado;
        gremio = gremioCargado;
        bestiario.restaurarDerrotados(estado.getEnemigosDerrotados());
        ultimoGuardado = estado.getResumen();

        System.out.println("\n=== PARTIDA CARGADA ===");
        System.out.println("Guardada el: " + estado.getFechaGuardadoTexto());
        System.out.printf("Victorias: %d | Derrotas: %d | Logros: %s%n", perfilCargado.getVictorias(),
                perfilCargado.getDerrotas(), perfilCargado.getLogros());
        System.out.println("Enemigos derrotados: " + bestiario.resumenDerrotados());
        List<Personaje> miembros = gremioCargado.getMiembros();
        System.out.printf("Gremio %s (%d miembros):%n", gremioCargado.getNombre(), miembros.size());
        boolean cicloIntacto = true;
        for (Personaje miembro : miembros) {
            boolean apuntaAlGremio = miembro.getGremio() == gremioCargado;
            cicloIntacto &= apuntaAlGremio;
            System.out.printf("  - %s | arma: %s | pociones: %d | getGremio()==gremio: %b%n",
                    FormatoCombate.resumen(miembro),
                    miembro.getArma() != null ? miembro.getArma().getNombre() : "ninguna",
                    miembro.getInventario().tamanio(), apuntaAlGremio);
        }
        System.out.println("Relacion Gremio <-> Personaje intacta tras cargar: " + cicloIntacto);
        System.out.println("Campo transient PerfilJugador.combatesEnSesion tras cargar: "
                + perfilCargado.getCombatesEnSesion() + " (valor por defecto de int, no se guardo)");
        System.out.println("El juego continua con este estado: los proximos combates suman a este perfil.");
    }

    /** Muestra el historial de combates leido del .txt con BufferedReader. */
    public void verHistorial() {
        List<String> lineas = historial.leer();
        if (lineas.isEmpty()) {
            return; // HistorialCombates ya informo (archivo inexistente o vacio)
        }
        System.out.printf("%n=== HISTORIAL DE COMBATES (%s, %d lineas) ===%n", historial.getArchivo().getPath(),
                lineas.size());
        for (String linea : lineas) {
            System.out.println(linea);
        }
    }

    /** Demuestra FileWriter(file, false): sobrescribe el historial en lugar de agregar. */
    public void reiniciarHistorial() {
        if (!consola.leerConfirmacion("Esto SOBRESCRIBE el historial y pierde los combates anteriores. Seguir?")) {
            System.out.println("Operacion cancelada.");
            return;
        }
        if (historial.escribir("Historial reiniciado: se abrio con FileWriter(archivo, false), que sobrescribe.",
                false)) {
            System.out.println("Historial sobrescrito. Ahora solo contiene esta cabecera.");
        }
    }

    public void borrarHistorial() {
        if (consola.leerConfirmacion("Borrar el historial de combates (.txt)?")) {
            historial.borrar();
        } else {
            System.out.println("Operacion cancelada.");
        }
    }

    public void borrarPartida() {
        if (consola.leerConfirmacion("Seguro que quieres borrar la partida guardada?")) {
            gestorPartida.borrar();
        } else {
            System.out.println("Operacion cancelada.");
        }
    }

    public void mostrarInfoArchivos() {
        InfoArchivos.mostrar(gestorPartida.getArchivo(), historial.getArchivo(), archivoAutoGuardado.getArchivo());
    }

    /** Estado del daemon de auto-guardado: copia en memoria y copia leida del disco. */
    public void mostrarAutoGuardado() {
        System.out.println("\n=== AUTO-GUARDADO ===");
        System.out.printf("Hilo vivo: %b | daemon: %b | guardados: %d | ultimo: %s | intervalo: %d s%n",
                autoGuardado.estaVivo(), autoGuardado.esDaemon(), autoGuardado.getGuardadosRealizados(),
                autoGuardado.getHoraUltimoGuardado(), autoGuardado.getIntervaloMs() / 1000);
        System.out.println("Copias en memoria: " + autoGuardado.getHistorial().size());
        String enMemoria = autoGuardado.getUltimaCopia();
        if (enMemoria.isEmpty()) {
            System.out.println("Aun no hay auto-guardados (espera unos segundos).");
            return;
        }
        System.out.println("--- Ultima copia EN MEMORIA ---");
        imprimirLimitado(enMemoria.lines().toList());
        System.out.println("--- Copia EN DISCO (" + archivoAutoGuardado.getArchivo().getPath() + ") ---");
        imprimirLimitado(archivoAutoGuardado.leer());
    }

    private void imprimirLimitado(List<String> lineas) {
        int limite = Math.min(lineas.size(), LINEAS_MAXIMAS_A_MOSTRAR);
        for (int i = 0; i < limite; i++) {
            System.out.println(lineas.get(i));
        }
        if (lineas.size() > limite) {
            System.out.printf("... (%d lineas mas)%n", lineas.size() - limite);
        }
    }

    /**
     * Al salir del juego. TALLER ARCHIVOS (seccion 7): cerrar() del archivo de auto-guardado es synchronized,
     * asi que si el daemon estaba escribiendo, el hilo principal espera a que termine esa escritura y despues
     * impide nuevas. El daemon no se espera (la JVM lo corta al terminar main), pero nunca a mitad de archivo.
     */
    public void cerrar() {
        archivoAutoGuardado.cerrar();
        System.out.printf("[Auto-guardado] el hilo sigue vivo (%b) pero es daemon (%b): la JVM no lo espera.%n",
                autoGuardado.estaVivo(), autoGuardado.esDaemon());
    }
}
