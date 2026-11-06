package com.rpg.entities;

import com.rpg.concurrency.EfectoContinuoTask;
import com.rpg.entities.enums.EstadoPersonaje;
import com.rpg.entities.enums.TipoPersonaje;
import com.rpg.exceptions.SinManaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Personaje base del juego.
 *
 * <h2>TALLER CONCURRENCIA - Sincronizacion (seccion 6)</h2>
 * La vida, el estado y el mapa de efectos son estado mutable COMPARTIDO: los modifican el hilo del combate
 * (ataques, pociones) y los hilos de cada efecto DoT/HoT ({@link EfectoContinuoTask}). Ademas son variables
 * DEPENDIENTES entre si: si la vida llega a 0 el estado pasa a MUERTO; si no quedan DoT el estado vuelve
 * de ENVENENADO a VIVO; curar consulta el estado antes de tocar la vida.
 * <p>
 * Por eso todos los metodos que leen o escriben vida/estado/efectos son {@code synchronized} sobre el
 * propio personaje (su monitor, {@code this}). Se eligio synchronized (unidad 4.2) porque:
 * <ul>
 *   <li>Hace atomica la secuencia leer-calcular-escribir de {@link #recibirDanio} y {@link #curar}.</li>
 *   <li>Protege varias variables a la vez con un unico candado (vida + estado + efectos), algo que un
 *       AtomicInteger no puede hacer.</li>
 *   <li>Garantiza visibilidad: lo que un hilo escribe dentro del monitor lo ve el siguiente que entra.</li>
 *   <li>Es reentrante (recibirDanio llama a aplicarVida, curar llama a estaVivo) y el candado se libera solo,
 *       incluso si se lanza una excepcion: no hay riesgo de olvidarse un unlock().</li>
 * </ul>
 * <b>Por que AtomicInteger.addAndGet(-danio) no basta:</b> addAndGet es atomico para UNA variable, pero
 * setVida tambien acota la vida a 0..vidaMaxima (addAndGet podria dejarla en negativo o por encima del
 * maximo) y cambia {@code estado} a MUERTO: son dos variables que deben cambiar juntas y ningun atomico
 * suelto las actualiza a la vez. Ver {@link com.rpg.concurrency.VidaAtomica} para el reto extra con
 * updateAndGet / compareAndSet y {@link com.rpg.concurrency.DemoCondicionCarrera} para la demostracion.
 *
 * <h2>TALLER ARCHIVOS - Serializacion</h2>
 * Personaje es Serializable para guardar la partida con ObjectOutputStream. Todo lo que referencia debe serlo
 * tambien (Inventario, Pocion, Arma, Gremio, EstadoEfecto y los enums, que lo son de serie), o se obtiene
 * NotSerializableException. Gremio y Personaje se referencian entre si: la serializacion de Java guarda cada
 * objeto una sola vez y usa referencias para los repetidos, asi que el ciclo se reconstruye bien.
 * Los campos transient (hilos, consumidor del registro, ReentrantLock) no se guardan y se recrean en
 * {@link #readObject(ObjectInputStream)}.
 */
// TALLER ARCHIVOS (resuelto): Serializable + serialVersionUID; los campos transient estan marcados abajo.
public abstract class Personaje implements ICombatiente, Serializable {

    /**
     * serialVersionUID: numero de version de la clase serializada. Al leer, Java compara el numero guardado
     * en el archivo con el de la clase actual; si no coinciden lanza InvalidClassException en lugar de
     * reconstruir un objeto con campos que ya no encajan. Declararlo a mano evita que el compilador calcule
     * uno distinto ante cualquier cambio menor (lo que invalidaria todas las partidas guardadas).
     */
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int VIDA_BASE = 100;
    private static final int ATAQUE_BASE = 10;
    private static final int DEFENSA_BASE = 5;
    private static final int CAPACIDAD_INVENTARIO = 3;
    private static final int DANIO_MINIMO = 1;
    private static final double PORCENTAJE_TOTAL = 100.0;
    /** Cada cuantos milisegundos aplica un tick un efecto DoT/HoT (cada efecto corre en su propio hilo). */
    public static final long INTERVALO_EFECTO_MS = 1500;
    /** Tiempo maximo que se espera a que termine cada hilo de efecto al cerrar un combate. */
    private static final long ESPERA_FIN_EFECTO_MS = 1000;

    private String nombre;
    private final TipoPersonaje tipo;
    private int vidaMaxima;
    private int vida;
    private final int ataqueBase;
    private final int defensaBase;
    private EstadoPersonaje estado = EstadoPersonaje.VIVO;

    private Arma arma;                  // asociacion 0..1
    private Gremio gremio;              // asociacion bidireccional 0..1
    private final Inventario<Pocion> inventario = new Inventario<>(CAPACIDAD_INVENTARIO); // composicion
    private final Set<String> habilidades = new HashSet<>();
    private final Map<String, EstadoEfecto> efectos = new LinkedHashMap<>();

    // ---- campos transient (TALLER ARCHIVOS): no forman parte de la partida guardada.
    // OJO: al deserializar NO se ejecuta el constructor de Personaje ni estos inicializadores, por eso vuelven
    // como null y readObject(...) los vuelve a crear.

    /** Hilo de cada efecto activo. Thread NO es Serializable (y un hilo no tiene sentido fuera de esta JVM). */
    private transient Map<String, Thread> hilosEfectos = new HashMap<>();
    /** A donde envian sus mensajes los hilos de efectos (por defecto la consola). Una lambda no es Serializable. */
    private transient Consumer<String> registroEfectos = System.out::println;
    /** INVESTIGACION OBLIGATORIA: candado explicito usado por {@link #recibirDanioConLock(int)}. */
    private transient ReentrantLock candadoVida = new ReentrantLock();

    protected Personaje(String nombre, TipoPersonaje tipo) {
        this(nombre, tipo, VIDA_BASE, ATAQUE_BASE, DEFENSA_BASE);
    }

    protected Personaje(String nombre, TipoPersonaje tipo, int vidaMaxima, int ataqueBase, int defensaBase) {
        validarNombre(nombre);
        if (vidaMaxima <= 0 || ataqueBase < 0 || defensaBase < 0) {
            throw new IllegalArgumentException("Estadisticas invalidas para " + nombre);
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.vidaMaxima = vidaMaxima;
        this.vida = vidaMaxima;
        this.ataqueBase = ataqueBase;
        this.defensaBase = defensaBase;
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
    }

    // ---- combate

    /**
     * Ataque especial propio de cada clase.
     * @return descripcion de lo ocurrido, para el registro de combate
     */
    public abstract String realizarAtaqueEspecial(Personaje objetivo) throws SinManaException;

    /** Hook que se ejecuta al comenzar el turno (por ejemplo, el mago regenera mana). */
    public void alIniciarTurno() {
    }

    public int calcularDanio() {
        double bruto = ataqueBase + tipo.getBonificadorAtaque();
        if (arma != null) {
            bruto += arma.getDanioEfectivo();
        }
        return (int) Math.round(bruto);
    }

    @Override
    public void defender(int danoRecibido) {
        // Con enteros, 100 / 123 daria 0: por eso se divide con un double
        double factorMitigacion = PORCENTAJE_TOTAL / (PORCENTAJE_TOTAL + getDefensa());
        int danioFinal = (int) Math.round(danoRecibido * factorMitigacion);
        recibirDanio(Math.max(DANIO_MINIMO, danioFinal));
    }

    // ---- vida

    public synchronized void recibirDanio(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("El danio no puede ser negativo");
        }
        // TALLER CONCURRENCIA (resuelto): leer la vida, calcular la nueva y escribirla (y cambiar el estado
        // a MUERTO si llega a 0) ocurre ahora DENTRO del monitor de este personaje. Mientras un hilo esta
        // aqui, ningun otro (un veneno, una quemadura, un ataque o una curacion) puede intercalarse, asi que
        // ya no se pierden actualizaciones. DemoCondicionCarrera reproduce el fallo de la version anterior.
        aplicarVida(vida - cantidad);
    }

    /**
     * INVESTIGACION OBLIGATORIA - ReentrantLock: la misma operacion que {@link #recibirDanio(int)}, pero con
     * un candado explicito. El unlock() va SIEMPRE en el finally: si se lanzara una excepcion entre lock() y
     * unlock() sin finally, el candado quedaria tomado para siempre y cualquier otro hilo que llamara a lock()
     * se bloquearia indefinidamente.
     * <p>
     * Cuando preferir ReentrantLock a synchronized: cuando se necesita algo que synchronized no ofrece, como
     * tryLock() (intentar sin bloquearse) o tryLock(tiempo) para no esperar eternamente, lockInterruptibly()
     * para poder cancelar la espera con interrupt(), un candado "justo" (fair) que respete el orden de llegada,
     * o varias Condition por candado. Para exclusion mutua simple, synchronized es mas corto y menos propenso
     * a errores porque libera el candado automaticamente.
     * <p>
     * Importante: un ReentrantLock y el monitor de synchronized son candados DISTINTOS, no se excluyen entre
     * si. Para usar esta version en el juego, todos los metodos que tocan vida/estado deberian pasar al mismo
     * ReentrantLock. Por eso el juego usa synchronized y este metodo solo lo usa DemoCondicionCarrera, donde
     * es el unico que modifica la vida.
     */
    public void recibirDanioConLock(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("El danio no puede ser negativo");
        }
        candadoVida.lock();
        try {
            aplicarVida(vida - cantidad);
        } finally {
            candadoVida.unlock();
        }
    }

    /** @return vida realmente recuperada */
    public synchronized int curar(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La curacion no puede ser negativa");
        }
        // La comprobacion del estado y la escritura de la vida forman una sola operacion atomica: un DoT no
        // puede matar al personaje "entre medias" y que luego la curacion lo resucite.
        if (!estaVivo()) {
            return 0;
        }
        int vidaAntes = vida;
        aplicarVida(vida + cantidad);
        return vida - vidaAntes;
    }

    /** Vida al maximo y sin efectos (los hilos de los efectos se interrumpen y se espera a que terminen). */
    public void restaurarVida() {
        detenerEfectos();
        synchronized (this) {
            estado = EstadoPersonaje.VIVO;
            vida = vidaMaxima;
        }
    }

    @Override
    public synchronized boolean estaVivo() {
        return estado != EstadoPersonaje.MUERTO;
    }

    // ---- efectos DoT / HoT: cada efecto corre en su propio hilo (EfectoContinuoTask)

    /**
     * Aplica un efecto y arranca un hilo NUEVO que lo ejecuta en segundo plano. Si el mismo efecto ya esta
     * activo, solo renueva su duracion (no se crea un segundo hilo para el mismo efecto).
     */
    public synchronized void aplicarEfecto(EstadoEfecto efecto) {
        if (efecto == null || !estaVivo()) {
            return;
        }
        String nombreEfecto = efecto.getNombre();
        EstadoEfecto actual = efectos.get(nombreEfecto);
        Thread hiloActual = hilosEfectos.get(nombreEfecto);
        if (actual != null && !actual.haTerminado() && hiloActual != null && hiloActual.isAlive()) {
            actual.renovar(efecto.getTurnosRestantes());
            return;
        }
        EstadoEfecto copia = efecto.copia();
        efectos.put(nombreEfecto, copia);
        if (copia.esDanio() && estado == EstadoPersonaje.VIVO) {
            estado = EstadoPersonaje.ENVENENADO;
        }
        // TALLER CONCURRENCIA (seccion 2): un Thread nuevo por efecto. Nunca se reutiliza un Thread ya
        // iniciado (start() dos veces lanza IllegalThreadStateException) y nunca se llama a run() directamente.
        Thread hilo = new Thread(new EfectoContinuoTask(this, copia, INTERVALO_EFECTO_MS), nombreEfecto + "@" + nombre);
        // Red de seguridad: si algun efecto quedara vivo por error, no impide que la JVM cierre.
        hilo.setDaemon(true);
        hilosEfectos.put(nombreEfecto, hilo);
        hilo.start();
    }

    /**
     * Quita un efecto y cancela su hilo con interrupt() (cancelacion cooperativa: el hilo captura la
     * InterruptedException, limpia y termina). Nunca se usa Thread.stop() ni banderas booleanas.
     */
    public boolean quitarEfecto(String nombreEfecto) {
        Thread hilo;
        synchronized (this) {
            if (efectos.remove(nombreEfecto) == null) {
                return false;
            }
            hilo = hilosEfectos.remove(nombreEfecto);
            normalizarEstado();
        }
        if (hilo != null) {
            hilo.interrupt();
        }
        return true;
    }

    /** Antidoto: quita solo los DoT (veneno, quemadura...). @return cuantos efectos se quitaron */
    public int quitarEfectosDanio() {
        List<String> nombresDanio = new ArrayList<>();
        synchronized (this) {
            for (EstadoEfecto efecto : efectos.values()) {
                if (efecto.esDanio()) {
                    nombresDanio.add(efecto.getNombre());
                }
            }
        }
        int quitados = 0;
        for (String nombreEfecto : nombresDanio) {
            if (quitarEfecto(nombreEfecto)) {
                quitados++;
            }
        }
        return quitados;
    }

    /** Purificacion: elimina TODOS los efectos (DoT y HoT) interrumpiendo sus hilos, sin esperarlos. */
    public void purificar() {
        cancelarTodosLosEfectos();
    }

    /**
     * Fin del combate: interrumpe todos los hilos de efectos y espera (con join y tiempo maximo) a que
     * terminen limpios. No se mantiene el candado del personaje mientras se espera: el hilo del efecto
     * necesita ese candado en su finally para limpiar, y si lo tuvieramos tomado habria un interbloqueo.
     */
    public void detenerEfectos() {
        for (Thread hilo : cancelarTodosLosEfectos()) {
            if (hilo == Thread.currentThread()) {
                continue;
            }
            try {
                hilo.join(ESPERA_FIN_EFECTO_MS);
            } catch (InterruptedException e) {
                // Nos piden parar mientras esperabamos: restauramos la bandera y dejamos de esperar.
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private List<Thread> cancelarTodosLosEfectos() {
        List<Thread> hilos;
        synchronized (this) {
            hilos = new ArrayList<>(hilosEfectos.values());
            hilosEfectos.clear();
            efectos.clear();
            normalizarEstado();
        }
        for (Thread hilo : hilos) {
            hilo.interrupt();
        }
        return hilos;
    }

    // ---- metodos que usa EfectoContinuoTask desde el hilo de cada efecto

    /** @return true si ese efecto concreto (misma instancia) sigue activo sobre el personaje */
    public synchronized boolean tieneEfectoActivo(EstadoEfecto efecto) {
        return efectos.get(efecto.getNombre()) == efecto;
    }

    /** Consume un tick del efecto. @return true si el efecto ya termino */
    public synchronized boolean consumirTickEfecto(EstadoEfecto efecto) {
        efecto.consumirTurno();
        return efecto.haTerminado();
    }

    /** Quita el efecto (solo si sigue siendo esa misma instancia) y recalcula el estado. Es idempotente. */
    public synchronized void limpiarEfecto(EstadoEfecto efecto) {
        if (efectos.get(efecto.getNombre()) == efecto) {
            efectos.remove(efecto.getNombre());
        }
        normalizarEstado();
    }

    /** Olvida el hilo de un efecto que ya termino (solo si el registrado sigue siendo ese hilo). */
    public synchronized void liberarHiloEfecto(String nombreEfecto, Thread hilo) {
        hilosEfectos.remove(nombreEfecto, hilo);
    }

    /** @return cuantos hilos de efectos siguen registrados (util para comprobar que terminan limpios) */
    public synchronized int contarHilosEfectos() {
        return hilosEfectos.size();
    }

    /** Envia un mensaje de un hilo de efecto al registro (fuera de cualquier candado del personaje). */
    public void registrarEventoEfecto(String mensaje) {
        Consumer<String> destino = registroEfectos;
        if (destino != null) {
            destino.accept(mensaje);
        }
    }

    public void setRegistroEfectos(Consumer<String> registroEfectos) {
        this.registroEfectos = registroEfectos != null ? registroEfectos : System.out::println;
    }

    /** Debe llamarse con el candado tomado. */
    private boolean tieneEfectoDanio() {
        for (EstadoEfecto efecto : efectos.values()) {
            if (efecto.esDanio()) {
                return true;
            }
        }
        return false;
    }

    /** Si ya no queda ningun DoT, un personaje ENVENENADO vuelve a estar VIVO. Debe llamarse con el candado. */
    private void normalizarEstado() {
        if (estado == EstadoPersonaje.ENVENENADO && !tieneEfectoDanio()) {
            estado = EstadoPersonaje.VIVO;
        }
    }

    // ---- aturdimiento

    public synchronized void aturdir() {
        if (estaVivo()) {
            estado = EstadoPersonaje.ATURDIDO;
        }
    }

    /** @return true si estaba aturdido (y por tanto pierde este turno) */
    public synchronized boolean consumirAturdimiento() {
        boolean estabaAturdido = estado == EstadoPersonaje.ATURDIDO;
        if (estabaAturdido) {
            estado = tieneEfectoDanio() ? EstadoPersonaje.ENVENENADO : EstadoPersonaje.VIVO;
        }
        return estabaAturdido;
    }

    // ---- inventario, arma, gremio, habilidades

    public String usarPocion(Pocion pocion) {
        int curado = curar(pocion.getCuracionReal());
        if (pocion.getEfecto() != null) {
            aplicarEfecto(pocion.getEfecto());
        }
        String extra = "";
        if (pocion.isPurifica()) {
            // Antidoto: interrumpe los hilos de los DoT activos
            int quitados = quitarEfectosDanio();
            extra = String.format(" y purifica %d efecto(s) de dano", quitados);
        }
        return String.format("%s usa %s (+%d de vida)%s", nombre, pocion.getNombre(), curado, extra);
    }

    public void equiparArma(Arma nuevaArma) {
        this.arma = nuevaArma;
    }

    public void desequiparArma() {
        this.arma = null;
    }

    public void unirseAGremio(Gremio nuevoGremio) {
        if (nuevoGremio == null || nuevoGremio == gremio) {
            return;
        }
        if (gremio != null) {
            gremio.removerMiembro(this);
        }
        gremio = nuevoGremio;
        nuevoGremio.agregarMiembro(this);
    }

    public void abandonarGremio() {
        Gremio anterior = gremio;
        if (anterior == null) {
            return;
        }
        gremio = null;
        anterior.removerMiembro(this);
    }

    /** @return false si la habilidad ya estaba aprendida (el Set rechaza duplicados) */
    public boolean aprenderHabilidad(String habilidad) {
        return habilidades.add(habilidad);
    }

    // ---- getters y setters validados

    public synchronized void setVida(int nuevaVida) {
        aplicarVida(nuevaVida);
    }

    /** Acota la vida a 0..vidaMaxima y pasa a MUERTO al llegar a 0. Quien la llama debe tener el candado. */
    private void aplicarVida(int nuevaVida) {
        vida = Math.max(0, Math.min(nuevaVida, vidaMaxima));
        if (vida == 0) {
            estado = EstadoPersonaje.MUERTO;
        }
    }

    public void setNombre(String nombre) {
        validarNombre(nombre);
        this.nombre = nombre;
    }

    public synchronized void setVidaMaxima(int vidaMaxima) {
        if (vidaMaxima <= 0) {
            throw new IllegalArgumentException("La vida maxima debe ser positiva");
        }
        this.vidaMaxima = vidaMaxima;
        this.vida = Math.min(vida, vidaMaxima);
    }

    public int getDefensa() {
        return defensaBase + tipo.getBonificadorDefensa();
    }

    public String getNombre() {
        return nombre;
    }

    public TipoPersonaje getTipo() {
        return tipo;
    }

    public synchronized int getVida() {
        return vida;
    }

    public synchronized int getVidaMaxima() {
        return vidaMaxima;
    }

    public synchronized EstadoPersonaje getEstado() {
        return estado;
    }

    public Arma getArma() {
        return arma;
    }

    public Gremio getGremio() {
        return gremio;
    }

    public Inventario<Pocion> getInventario() {
        return inventario;
    }

    public Set<String> getHabilidades() {
        return Collections.unmodifiableSet(habilidades);
    }

    /** Copia del momento: se puede recorrer aunque los hilos de efectos sigan modificando el mapa. */
    public synchronized Collection<EstadoEfecto> getEfectos() {
        return List.copyOf(efectos.values());
    }

    // ---- serializacion (TALLER ARCHIVOS)

    /**
     * Se ejecuta al deserializar con ObjectInputStream. Primero lee los campos normales y despues recrea los
     * transient, que llegan con su valor por defecto (null). Los efectos guardados se descartan porque sus
     * hilos no existen en esta ejecucion (un efecto sin hilo nunca volveria a hacer tick).
     */
    @Serial
    private void readObject(ObjectInputStream entrada) throws IOException, ClassNotFoundException {
        entrada.defaultReadObject();
        hilosEfectos = new HashMap<>();
        registroEfectos = System.out::println;
        candadoVida = new ReentrantLock();
        efectos.clear();
        normalizarEstado();
    }
}
