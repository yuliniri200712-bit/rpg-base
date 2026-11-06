# Solucion del taller: Concurrencia e Hilos + Manejo de Archivos

Este documento explica, punto por punto, como se resolvio cada item de los dos checklists del README
(taller de concurrencia y taller de archivos) y donde esta cada cosa en el codigo. Los mensajes y
comentarios no llevan tildes a proposito, igual que el resto del proyecto.

## Como compilar y ejecutar

Con Maven (JDK 21):

```bash
mvn -q compile
java -cp target/classes com.rpg.Main
```

Solo con javac (desde la carpeta del proyecto):

```bash
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp out com.rpg.Main
```

Programas de demostracion (evidencias de las secciones 6 y de las investigaciones):

```bash
java -cp out com.rpg.concurrency.DemoCondicionCarrera
java -cp out com.rpg.concurrency.DemoHiloDaemon
java -cp out com.rpg.concurrency.DemoHiloDaemon usuario
java -cp out com.rpg.persistencia.DemoSerializacion
```

### Menu principal (nuevo)

```
1. Combate rapido                      (temporizador de turno + efectos en hilos)
2. Torneo                              (semifinales en paralelo con join)
3. Ver bestiario
4. Ver auto-guardado (memoria y disco) (estado del hilo daemon y sus copias)
5. Ver historial de combates (.txt)    (BufferedReader)
6. Guardar partida (.dat)              (ObjectOutputStream)
7. Cargar partida (.dat)               (ObjectInputStream)
8. Informacion de archivos (File)      (rutas, exists, length, getName, createNewFile, mkdirs)
9. Borrar partida guardada             (Files.deleteIfExists, con confirmacion)
10. Borrar historial de combates       (File.delete, con confirmacion)
11. Reiniciar historial (sobrescribir) (FileWriter(file, false) frente al append)
0. Salir
```

Todos los archivos se crean en la carpeta `partidas/` (relativa al directorio desde el que se lanza java):
`partida.dat`, `historial_combates.txt`, `autoguardado.txt` y `LEEME.txt`.

## Estructura final

```
com.rpg
  Main                          menu (opciones 0 a 11), arranca el auto-guardado, cierre ordenado
  core/
    MotorJuego                  temporizador en pedirAccion, fin de efectos, guardar/cargar, daemon
    TorneoManager               2 Thread + start + join, Thread.getState()
    TurnoCallback               interfaz onTiempoAgotado()                          (NUEVA)
    Arena                       duelo interrumpible, pausa entre turnos, sin procesarEfectos
    Consola                     hilo lector daemon + BlockingQueue (lectura interrumpible)
    RegistroCombate             synchronized, prefijo con el nombre del hilo, guardarEnArchivo
    Bestiario, Accion, FormatoCombate
  entities/
    Personaje                   synchronized, efectos en hilos, ReentrantLock, Serializable, transient
    EstadoEfecto, Arma, Gremio, PerfilJugador, Guerrero, Mago, Enemigo   Serializable + serialVersionUID
  items/  Inventario, Pocion    Serializable + serialVersionUID
  concurrency/                                                                       (NUEVAS)
    TemporizadorTurnoTask       Runnable con cuenta regresiva
    GestorTurno                 crea el Thread del temporizador y lo cancela con interrupt()
    EfectoContinuoTask          Runnable de cada efecto DoT/HoT
    AutoGuardadoDaemon          while(true) + sleep, setDaemon(true)
    DemoCondicionCarrera        reproduce la carrera y compara soluciones
    VidaAtomica                 reto extra: updateAndGet / compareAndSet
    DemoHiloDaemon              investigacion: hilos daemon
  persistencia/                                                                      (NUEVAS)
    RutasJuego                  rutas relativas y mkdirs
    InfoArchivos                File: relativa/absoluta, exists, length, getName, createNewFile
    HistorialCombates           BufferedWriter/FileWriter, BufferedReader/FileReader, File.delete
    EstadoPartida               objeto Serializable con toda la partida
    GestorPartida               ObjectOutputStream / ObjectInputStream / Files.deleteIfExists
    ArchivoAutoGuardado         archivo compartido daemon <-> hilo principal (synchronized)
    DemoSerializacion           NotSerializableException, transient, ciclo Gremio-Personaje
```

---

# TALLER DE CONCURRENCIA

## Antes de empezar

- [x] El proyecto compila (`javac --release 21`) y `Main` se ejecuta sin errores.
- [x] Se jugo un combate rapido y un torneo completo. Los 5 comentarios `TALLER CONCURRENCIA` originales se
  conservan (marcados como "resuelto") en: `MotorJuego.pedirAccion`, `MotorJuego.guardarProgreso`,
  `Personaje.recibirDanio`, `Arena.iniciarTurno` (donde se llamaba a `procesarEfectos`) y
  `TorneoManager.jugarTorneo`.
- [x] Conceptos de los talleres 1 a 8 revisados (se mantienen: switch moderno, do-while, StringBuilder,
  herencia, genericos, colecciones, excepciones propias...).

## Seccion 1: Temporizador de turno (Thread, Runnable, sleep)

| Item | Donde |
|---|---|
| Interfaz `TurnoCallback` con `void onTiempoAgotado()` | `core/TurnoCallback.java` |
| `TemporizadorTurnoTask implements Runnable`, constructor (segundos, callback) | `concurrency/TemporizadorTurnoTask.java` |
| Cuenta regresiva con `for` decreciente, un segundo por iteracion | `TemporizadorTurnoTask.run()` |
| `Thread.sleep(1000)` dentro de try-catch de `InterruptedException` | `TemporizadorTurnoTask.run()` |
| Al llegar a 0 llama a `callback.onTiempoAgotado()` | `TemporizadorTurnoTask.run()` |
| `GestorTurno` crea el Thread con el Runnable y llama a `start()` | `concurrency/GestorTurno.iniciar()` |
| Integrado en `MotorJuego.pedirAccion`: si se agota el tiempo, se pierde el turno | `MotorJuego.pedirAccion()` y `turnoJugador()` (accion `TIEMPO_AGOTADO`) |
| Si responde a tiempo se cancela con `interrupt()`, sin banderas booleanas | `GestorTurno.cancelar()` (llamado en el `finally` de `pedirAccion`) |

**Como funciona.** `Scanner.nextLine()` no responde a `interrupt()`, asi que no se podria cortar la espera
del teclado. Por eso `Consola` tiene un hilo daemon (`lector-consola`) que es el unico que usa el Scanner y
deja cada linea en una `BlockingQueue`. El hilo principal espera en la cola con `take()`, que **si** es
interrumpible. El callback del temporizador corre en el hilo del temporizador e interrumpe al hilo principal:
`take()` lanza `InterruptedException`, `pedirAccion` devuelve `Accion.TIEMPO_AGOTADO` y el jugador pierde el
turno. Si el jugador responde antes, el `finally` llama a `gestor.cancelar()`, que hace `interrupt()` al
temporizador: su `sleep` lanza la excepcion y la tarea termina **sin** llamar al callback. A los 5 segundos
restantes se avisa al jugador. Cada turno crea un `GestorTurno` y un `Thread` nuevos (15 s por turno).

Prueba realizada: se dejo pasar el tiempo sin responder y la salida fue
`[Temporizador] Quedan 5 segundos...`, `[Temporizador] Se acabo el tiempo!`, `Ana tardo demasiado y pierde el turno`.

## Seccion 2: Efectos continuos DoT / HoT (interrupt, InterruptedException)

| Item | Donde |
|---|---|
| `EfectoContinuoTask` (Runnable) con nombre del efecto e intervalo en ms | `concurrency/EfectoContinuoTask.java` |
| `while (!Thread.currentThread().isInterrupted())` aplicando dano o curacion | `EfectoContinuoTask.run()` / `aplicarTick()` |
| En el `catch (InterruptedException)`: limpia el estado, `interrupt()` y `break` | `EfectoContinuoTask.run()` |
| Cada efecto en su propio hilo; sin `Thread.stop()` ni banderas | `Personaje.aplicarEfecto()` crea un `Thread` por efecto |
| Reemplazada la logica por turnos de `Personaje.procesarEfectos` | `procesarEfectos` se elimino; `Arena.iniciarTurno` ya no la llama |
| Antidoto, purificacion o fin del combate interrumpen el hilo y termina limpio | `Personaje.quitarEfectosDanio()` (antidoto), `purificar()`, `detenerEfectos()` (fin del combate, usado en `MotorJuego.combateRapido` y `Arena.duelo`) |

Detalles que pide la guia: hay un `finally` que libera recursos (quita el efecto y el registro del hilo en el
Personaje) y se consulta `isInterrupted()` justo despues de despertar, donde no hay `sleep` que lance la
excepcion. El tick comprueba que el efecto siga activo y aplica el dano dentro de un mismo bloque
`synchronized (objetivo)`, asi un antidoto no puede colarse entre la comprobacion y el dano. Si el mismo
efecto se vuelve a aplicar mientras sigue activo, solo se renueva su duracion (no se crea otro hilo).
`detenerEfectos()` interrumpe y espera con `join(1000)` a cada hilo **sin** tener el candado del personaje
tomado (el hilo lo necesita en su `finally`; si no, habria interbloqueo).

Prueba realizada (veneno 4 x 5 ticks + antidoto tras 2 ticks):
```
[Veneno@Thor] Thor sufre 4 de dano por Veneno (vida 116/120)
[Veneno@Thor] Thor sufre 4 de dano por Veneno (vida 112/120)
[Veneno@Thor] Thor: Veneno cancelado (hilo interrumpido)
Thor usa Antidoto (+0 de vida) y purifica 1 efecto(s) de dano
vida=112 estado=VIVO hilos=0
```
Tambien se probo: fin de combate con HoT y DoT a la vez, efecto que termina solo, renovacion y DoT mortal
("Thor cae derrotado por Veneno"). Al final no queda ningun hilo `Efecto@Personaje` vivo.

## Seccion 3: Torneo en paralelo (join)

| Item | Donde |
|---|---|
| Dos `Thread`, uno por semifinal, cada uno con su Runnable (lambda) | `TorneoManager.jugarTorneo()` |
| `start()` en ambos para que corran a la vez | idem |
| `join()` sobre la semifinal A y luego sobre la B | idem |
| `join()` protegidos con try-catch de `InterruptedException` | idem: cancela las semifinales, restaura la bandera y devuelve null |
| Ganadores consultados solo despues de los dos `join()`; luego la final | arreglo `finalistas[]` leido despues de los `join()` |
| `Thread.getState()` antes de start, durante y despues del join | `TorneoManager.mostrarEstados()` / `observarDuranteEjecucion()` |

Salida real:
```
[Estado hilos antes de start()] Semifinal-A=NEW | Semifinal-B=NEW
[Estado hilos justo despues de start()] Semifinal-A=RUNNABLE | Semifinal-B=RUNNABLE
[Semifinal-A] === SEMIFINAL: Maximo vs Circe ===
[Semifinal-B] === SEMIFINAL: Aquiles vs Merlina ===
[Estado hilos durante la ejecucion] Semifinal-A=TIMED_WAITING | Semifinal-B=TIMED_WAITING
...
[Estado hilos despues de join()] Semifinal-A=TERMINATED | Semifinal-B=TERMINATED
=== FINAL: Maximo vs Aquiles ===
```
Relacion con el ciclo de vida: **NEW** = el objeto Thread existe pero no se llamo a `start()`;
**RUNNABLE** = ejecutando (o listo para ejecutarse) un turno; **TIMED_WAITING** = dormido en
`Thread.sleep(250)` entre turnos (`Arena.pausar`), que es donde pasa casi todo el tiempo; **BLOCKED** podria
verse si un hilo espera el candado `synchronized` del registro; **TERMINATED** = `run()` termino y por eso
`join()` retorno. Las lineas de cada semifinal salen intercaladas (con el nombre del hilo delante), lo que
demuestra que corren a la vez. `join()` crea una relacion *happens-before*: lo que escribio cada hilo en
`finalistas[]` es visible para el hilo principal sin `synchronized`.

## Seccion 4: Auto-guardado en segundo plano (hilo daemon)

| Item | Donde |
|---|---|
| `AutoGuardadoDaemon` con `while (true)` y `Thread.sleep(intervalo)` | `concurrency/AutoGuardadoDaemon.run()` |
| Registra periodicamente estadisticas de los personajes e historial en memoria | `guardarAhora()`: `getUltimaCopia()` + lista con las ultimas 5 copias; el texto lo genera `MotorJuego.generarInstantanea()` (perfil, cada personaje del gremio con su vida/estado, combate en curso, registro) |
| `setDaemon(true)` antes de `start()` | `AutoGuardadoDaemon.iniciar()` |
| La aplicacion cierra de inmediato al salir | comprobado: el juego termina en ~0,5 s con el daemon vivo (`[Auto-guardado] el hilo sigue vivo (true) pero es daemon (true)`) |

El intervalo es de 10 s. La opcion 4 del menu muestra el hilo (vivo, daemon, numero de guardados, hora) y la
ultima copia. Prueba: a mitad de un combate la copia incluia
`Combate en curso: Morgana 80/80 VIVO vs Dragon Joven 160/160 VIVO`. Para que el daemon pueda leer el estado
mientras el hilo principal juega: `perfil`, `gremio`, `jugadorEnCombate`... son `volatile` en `MotorJuego`, y
`PerfilJugador`, `Gremio`, `Bestiario` y `RegistroCombate` tienen metodos `synchronized` que devuelven copias
(sin ellos el daemon podria recibir `ConcurrentModificationException`).

## Seccion 5: Reto integrador

- [x] Los cuatro modulos funcionan juntos: en un combate rapido corren a la vez el hilo principal, el
  temporizador, los hilos de efectos y el daemon de auto-guardado; el torneo usa join.
- [x] Estructura esperada: `com.rpg.core` (MotorJuego, TorneoManager, TurnoCallback), `com.rpg.entities`
  (Personaje, EstadoEfecto), `com.rpg.concurrency` (TemporizadorTurnoTask, EfectoContinuoTask,
  AutoGuardadoDaemon) y `Main`.
- [x] Combate y torneo se juegan completos sin bloqueos ni excepciones sin controlar.

## Seccion 6: Depuracion y condiciones de carrera

**Reproduccion** (`DemoCondicionCarrera`): dos hilos "Veneno" y "Quemadura" golpean 200 000 veces cada uno a
un personaje con la version original de `recibirDanio` (leer, calcular, escribir):
```
1) SIN PROTECCION: vida final 603031 -> 3031 actualizaciones PERDIDAS
2) CON synchronized (Personaje.recibirDanio): vida final 600000 -> CORRECTO
3) CON ReentrantLock (lock/unlock en try-finally): vida final 600000 -> CORRECTO
4) AtomicInteger.addAndGet: vida final -1000 (no se acota a 0) y ningun estado pasa a MUERTO
5) RETO EXTRA updateAndGet: vida final 0, vivo=false
```
Dato interesante: en la version sin proteccion `getVida()` y `setVida()` ya son `synchronized` cada uno, y
aun asi se pierden actualizaciones. Dos operaciones atomicas seguidas **no** forman una operacion atomica.

**Correccion con synchronized** (`Personaje`): `recibirDanio`, `curar`, `setVida`, `estaVivo`,
`aplicarEfecto`, `aturdir`, `consumirAturdimiento`, getters de vida/estado, etc. son `synchronized` sobre el
propio personaje. Se eligio `synchronized` porque:
1. hace atomica la secuencia leer-calcular-escribir;
2. protege con **un solo candado** varias variables dependientes (vida, estado y mapa de efectos);
3. garantiza la visibilidad entre hilos;
4. es reentrante y libera el candado solo, incluso si hay una excepcion.

**Estado y efectos protegidos**: `estado` y el mapa `efectos` solo se tocan con el candado tomado;
`getEfectos()` devuelve una copia (ya no hay `ConcurrentModificationException` al recorrerlo mientras un
hilo lo modifica); `curar` comprueba `estaVivo()` y escribe la vida dentro del mismo monitor.

**Por que `AtomicInteger.addAndGet(-danio)` no basta**: es atomico para una sola variable, pero `setVida`
ademas acota la vida a `0..vidaMaxima` (addAndGet la deja en negativo, como muestra la demo: -1000) y cambia
`estado` a MUERTO, que es otra variable. Ningun atomico suelto actualiza dos variables a la vez.
**Reto extra** (`concurrency/VidaAtomica`): `updateAndGet(v -> max(0, min(v - d, max)))` acota de forma
atomica y `curar` usa un bucle `compareAndSet`. Funciona porque ahi el estado se **deriva** de la vida; en
`Personaje` el estado tambien depende de los efectos, por eso se mantiene `synchronized`.

**Reglas comprobadas** (revisadas con grep sobre todo el codigo):
- [x] Ningun `catch (InterruptedException)` esta vacio ni solo imprime la traza: todos restauran la bandera
  con `Thread.currentThread().interrupt()` y/o actuan (salir del bucle, cancelar, perder el turno).
- [x] Nunca se llama dos veces a `start()` sobre el mismo Thread: temporizador, efectos y semifinales crean
  un Thread nuevo cada vez; `AutoGuardadoDaemon.iniciar()` lanza `IllegalStateException` si se repite.
- [x] Nunca se llama a `run()` directamente (no hay ninguna llamada `.run()` en el codigo).
- [x] Los 9 `Thread.sleep()` estan dentro de try-catch que gestiona `InterruptedException`.
- [x] Todo hilo de auto-guardado o monitoreo usa `setDaemon(true)` antes de `start()`: auto-guardado,
  temporizador de turno, lector de consola (y los efectos, como red de seguridad).
- [x] El `join()` del torneo espera a ambas semifinales antes de leer los ganadores.

## Investigacion obligatoria (concurrencia)

**Hilos daemon** (`concurrency/DemoHiloDaemon`, con la explicacion en su comentario):
- Hilo de **usuario** (el tipo por defecto; `main` es uno): la JVM no termina mientras quede alguno vivo.
- Hilo **daemon**: hilo de servicio; cuando solo quedan daemons la JVM termina y los corta en seco, sin
  ejecutar su `finally`. Se usa para tareas de fondo (auto-guardado, monitoreo, recolector de basura).
- `setDaemon(true)` despues de `start()` lanza `IllegalThreadStateException` (prueba 1 de la demo).
- Evidencia: con un daemon de bucle infinito la JVM se cierra a los ~1544 ms (al terminar main) con el hilo
  aun vivo y sin ejecutar su `finally`; con el mismo hilo como usuario la JVM espera hasta ~4018 ms y si se
  ejecuta el `finally`.

**ReentrantLock** (`Personaje.recibirDanioConLock`): `lock()` y `unlock()` dentro de `try-finally`. Si el
`unlock()` no estuviera en el `finally`, una excepcion dejaria el candado tomado para siempre y todos los
demas hilos quedarian bloqueados. Cuando preferirlo a `synchronized`: cuando hace falta `tryLock()` (no
bloquearse si esta ocupado) o `tryLock(tiempo)`, `lockInterruptibly()` (poder cancelar la espera con
`interrupt()`), un candado justo (*fair*) o varias `Condition`. Para exclusion mutua simple, `synchronized` es
mas corto y seguro. Nota: un `ReentrantLock` y el monitor de `synchronized` son candados distintos y no se
excluyen entre si; por eso en el juego se usa `synchronized` y este metodo solo lo usa la demo.

---

# TALLER DE ARCHIVOS

## Antes de empezar

- [x] Compila y se ejecuta sin errores.
- [x] Comentarios `TALLER ARCHIVOS` localizados y resueltos (`Main`, `MotorJuego`, `RegistroCombate`,
  `Personaje`, `Arma`, `Gremio`, `EstadoEfecto`, `PerfilJugador`, `Inventario`, `Pocion`).

## Seccion 1: Clase File (`persistencia/InfoArchivos`, `RutasJuego`, opcion 8 del menu)

- [x] `File` con ruta relativa (`partidas/partida.dat`) y otro con la absoluta (`getAbsolutePath()`).
  Diferencia: la relativa depende del directorio de trabajo (`user.dir`); la absoluta empieza en la raiz del
  disco y siempre apunta al mismo sitio. Se muestra `isAbsolute()` de ambas.
- [x] `exists()`, `length()` y `getName()` de partida, historial, auto-guardado y LEEME.
- [x] `createNewFile()` (crea `partidas/LEEME.txt` solo si no existe) con su `IOException` controlada.
- [x] `mkdirs()` para crear `partidas/` sin fallar si ya existe (`RutasJuego.asegurarCarpeta`, synchronized
  porque la usan el daemon y el hilo principal).

## Seccion 2: Escritura (`persistencia/HistorialCombates.escribir`, `RegistroCombate.guardarEnArchivo`)

- [x] El log de `RegistroCombate` se vuelca a `historial_combates.txt` con `BufferedWriter` + `FileWriter`
  al terminar cada combate o torneo.
- [x] Se escribe linea a linea con `newLine()`.
- [x] `FileWriter(file, true)` agrega (append) sin borrar los combates anteriores (comprobado: tras 3 partidas
  el archivo tenia 3 cabeceras). La opcion 11 usa `FileWriter(file, false)`, el modo por defecto, que
  sobrescribe: despues el archivo solo contiene la cabecera nueva.
- [x] `flush()` explicito y cierre automatico con try-with-resources.

## Seccion 3: Lectura (`HistorialCombates.leer`, opcion 5 del menu)

- [x] `BufferedReader` + `FileReader` con `while ((linea = lector.readLine()) != null)`.
- [x] Opcion nueva del menu que muestra el historial.
- [x] `FileNotFoundException` capturada (antes que `IOException`) con un mensaje claro sin tildes:
  `Aun no hay historial de combates: no existe el archivo partidas\historial_combates.txt. Juega un combate para crearlo.`

## Seccion 4: Borrado

- [x] `File.delete()` revisando el boolean (`HistorialCombates.borrar`, opcion 10).
- [x] `Files.deleteIfExists(Path)` (`GestorPartida.borrar`, opcion 9). Diferencia: `File.delete()` nunca
  lanza excepcion y devuelve `false` en cualquier fallo sin decir por que; `Files.deleteIfExists` devuelve
  `false` solo si el archivo no existia y, si no puede borrarlo, lanza una `IOException` que explica la causa
  (`AccessDeniedException`, `DirectoryNotEmptyException`, archivo en uso...).
- [x] Ambas opciones piden confirmacion `(s/n)` antes de borrar (`Consola.leerConfirmacion`).

## Seccion 5: Serializacion

- [x] `Serializable`: `Personaje`, `Guerrero`, `Mago`, `Enemigo`, `Inventario`, `Pocion`, `Arma`, `Gremio`,
  `EstadoEfecto`, `PerfilJugador` y `EstadoPartida` (los enums ya lo son de serie).
- [x] `serialVersionUID` en cada clase serializable (tambien en las excepciones, que heredan Serializable de
  Throwable). Sirve para versionar: al leer, Java compara el numero del archivo con el de la clase; si no
  coinciden lanza `InvalidClassException` en vez de cargar datos que ya no encajan. Declararlo a mano evita
  que cualquier cambio menor genere otro numero e invalide las partidas guardadas.
- [x] `transient`: `PerfilJugador.combatesEnSesion` vuelve con su valor por defecto **0** al cargar (se
  muestra en la opcion 7). En `Personaje` son transient los hilos de efectos (`Thread` no es Serializable),
  el consumidor del registro (una lambda) y el `ReentrantLock`; vuelven como `null` y `readObject` los recrea.
- [x] Guardado con `ObjectOutputStream.writeObject(...)` en `partidas/partida.dat` (`GestorPartida.guardar`).
- [x] Carga con `ObjectInputStream.readObject()` y cast `(EstadoPartida)` (`GestorPartida.cargar`).
- [x] `NotSerializableException` provocada y resuelta en `DemoSerializacion`: un objeto con un campo
  `RegistroCombate` (no Serializable) produce `NotSerializableException: com.rpg.core.RegistroCombate`; se
  resuelve marcando ese campo `transient` y guardando solo su texto (un `String`).
- [x] Tras cargar, el Gremio y sus Personaje siguen relacionados: la opcion 7 muestra
  `getGremio()==gremio: true` por cada miembro y `Relacion Gremio <-> Personaje intacta tras cargar: true`.
- [x] Opcion "Cargar partida": el juego continua con el estado guardado (probado: se cargo un gremio con
  2 miembros y 1 victoria, se jugo otro combate y el nuevo heroe paso a ser el miembro 3 del gremio cargado).

## Seccion 6: try-with-resources y excepciones

- [x] Todos los flujos (`BufferedWriter`, `BufferedReader`, `ObjectOutputStream`, `ObjectInputStream`) se
  abren con try-with-resources; no hay ningun `close()` manual (comprobado con grep).
- [x] Se manejan `FileNotFoundException` (antes que `IOException`), `NotSerializableException`,
  `InvalidClassException`, `StreamCorruptedException` / `EOFException`, `ClassNotFoundException` y
  `ClassCastException`, cada una con su mensaje.
- [x] Ningun catch esta vacio ni solo imprime la traza (no hay `printStackTrace` en el proyecto).
- [x] El juego no se cae si el archivo no existe, esta corrupto o no se puede escribir. Probado con un
  `partida.dat` de texto basura y otro truncado: `El archivo partidas\partida.dat esta corrupto o incompleto: no se puede cargar.`

## Seccion 7: Integracion con concurrencia

- [x] `AutoGuardadoDaemon` escribe a disco (`partidas/autoguardado.txt`) en cada intervalo, ademas de la
  copia en memoria.
- [x] Escritura y lectura del mismo archivo protegidas con `synchronized`: `ArchivoAutoGuardado.escribir`,
  `leer` y `cerrar` usan el monitor de la misma instancia, compartida por el daemon y el hilo principal
  (opcion 4 del menu).
- [x] Al cerrar el juego el daemon no deja un archivo a medio escribir: (1) se escribe en
  `autoguardado.txt.tmp` y luego se mueve con `Files.move(..., ATOMIC_MOVE)`, asi que el archivo definitivo
  siempre esta completo; (2) al salir, `Main` llama a `motor.cerrar()` -> `ArchivoAutoGuardado.cerrar()`,
  que es `synchronized`: espera a que termine una escritura en curso y bloquea las siguientes. Despues de
  todas las pruebas en `partidas/` no quedo ningun `.tmp`.

## Investigacion obligatoria (archivos)

**FileWriter vs Files.writeString** (comentario en `HistorialCombates`): `FileWriter` (con `BufferedWriter`)
es un flujo que abres, escribes por partes cuando quieras (linea a linea, en append) y cierras tu; conviene
para contenido grande o que crece, como un log. `Files.writeString(path, texto)` es una sola llamada de NIO
que abre, escribe todo el texto y cierra; conviene para volcar de golpe un texto pequeno ya construido.

**Seguridad de Serializable** (comentario en `GestorPartida`): `readObject()` reconstruye objetos de
cualquier clase que aparezca en el archivo y ejecuta su codigo de deserializacion. Un archivo manipulado
puede encadenar clases del classpath (*gadget chains*) para ejecutar codigo, o provocar un consumo enorme de
memoria/CPU al reconstruir estructuras gigantes. Por eso nunca se deben deserializar datos que no son de
confianza. Alternativas: JSON (Jackson, Gson), XML con esquema o un formato de texto propio (por ejemplo
`clave=valor`) que el programa interpreta campo a campo. Como mitigacion, `GestorPartida.cargar` aplica un
`ObjectInputFilter` con lista blanca (`com.rpg.**`, `java.lang.*`, `java.util.*`, `java.time.*`, resto
rechazado) y limites de profundidad, referencias y bytes.

---

## Adaptaciones y decisiones

- **Lectura de teclado en un hilo daemon** (`Consola`): imprescindible para poder cortar la espera del
  jugador cuando se agota el temporizador, porque `Scanner.nextLine()` no responde a `interrupt()`. Por la
  misma razon `Main` ya no cierra el `Scanner` de `System.in` con try-with-resources (cerrar `System.in`
  mientras otro hilo lee puede colgar la salida en algunas consolas).
- **Pausa de 250 ms entre turnos de los duelos automaticos** (`Arena.duelo`): sin ella un duelo dura
  milisegundos y los efectos (que hacen tick cada 1,5 s) no llegarian a actuar; ademas permite observar el
  estado `TIMED_WAITING` y ver las semifinales intercaladas.
- **Efectos en tiempo real**: un efecto dura sus ticks (cada 1,5 s) aunque el jugador tarde en elegir; es el
  comportamiento natural de un DoT/HoT en un hilo propio.
- **Accion `TIEMPO_AGOTADO`** anadida al enum `Accion` (no la elige el jugador, la produce el temporizador).
- **Los mensajes de los hilos** llevan el nombre del hilo delante (`[Semifinal-A]`, `[Veneno@Thor]`).
