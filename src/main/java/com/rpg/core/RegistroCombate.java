package com.rpg.core;

import com.rpg.persistencia.HistorialCombates;

/**
 * Imprime cada evento del combate y lo guarda para incluirlo en el guardado de la partida.
 * <p>
 * TALLER CONCURRENCIA: varios hilos escriben a la vez (hilo principal, semifinales del torneo, hilos de efectos
 * DoT/HoT) y el daemon de auto-guardado lee el texto. StringBuilder no es thread-safe, asi que todos los metodos
 * son synchronized: cada linea se imprime y se acumula entera, sin mezclarse con la de otro hilo.
 * Las lineas que no vienen del hilo principal llevan delante el nombre del hilo, para ver quien hizo que.
 */
public class RegistroCombate {

    private static final String HILO_PRINCIPAL = "main";

    private final StringBuilder texto = new StringBuilder();

    public synchronized void agregar(String linea) {
        String hilo = Thread.currentThread().getName();
        String salida = HILO_PRINCIPAL.equals(hilo) ? linea : "[" + hilo + "] " + linea;
        System.out.println(salida);
        texto.append(salida).append(System.lineSeparator());
    }

    public void agregar(String formato, Object... argumentos) {
        agregar(String.format(formato, argumentos));
    }

    public synchronized void limpiar() {
        texto.setLength(0);
    }

    public synchronized String getTexto() {
        return texto.toString();
    }

    /**
     * TALLER ARCHIVOS (resuelto): vuelca el registro de combate a un .txt con BufferedWriter + FileWriter.
     * @param agregar true = append (conserva los combates anteriores); false = sobrescribe el archivo
     * @return true si se escribio correctamente
     */
    public boolean guardarEnArchivo(HistorialCombates historial, boolean agregar) {
        return historial.escribir(getTexto(), agregar);
    }
}
