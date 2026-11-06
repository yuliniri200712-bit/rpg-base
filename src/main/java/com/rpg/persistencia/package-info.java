/**
 * TALLER ARCHIVOS: persistencia del simulador (todo dentro de la carpeta partidas/).
 * <ul>
 *   <li>{@link com.rpg.persistencia.RutasJuego} e {@link com.rpg.persistencia.InfoArchivos}: clase File, rutas, mkdirs, createNewFile (seccion 1).</li>
 *   <li>{@link com.rpg.persistencia.HistorialCombates}: log de combate con BufferedWriter/BufferedReader y File.delete (secciones 2, 3 y 4).</li>
 *   <li>{@link com.rpg.persistencia.EstadoPartida} y {@link com.rpg.persistencia.GestorPartida}: serializacion con ObjectOutputStream/ObjectInputStream y Files.deleteIfExists (secciones 4, 5 y 6).</li>
 *   <li>{@link com.rpg.persistencia.ArchivoAutoGuardado}: archivo compartido por el daemon y el hilo principal, protegido con synchronized (seccion 7).</li>
 *   <li>{@link com.rpg.persistencia.DemoSerializacion}: NotSerializableException, transient y ciclo Gremio-Personaje.</li>
 * </ul>
 */
package com.rpg.persistencia;
