package com.rpg;

import com.rpg.core.Consola;
import com.rpg.core.MotorJuego;

import java.util.NoSuchElementException;
import java.util.Scanner;

public class Main {

    private static final int OPCION_MAXIMA = 11;

    public static void main(String[] args) {
        // El Scanner de System.in no se cierra a mano: lo usa el hilo daemon "lector-consola" (ver Consola), que
        // puede estar bloqueado leyendo. Cerrar System.in mientras otro hilo lee puede colgar la salida en
        // algunas consolas; al terminar la JVM el sistema operativo libera la entrada estandar.
        Consola consola = new Consola(new Scanner(System.in));
        MotorJuego motor = new MotorJuego(consola);
        motor.iniciarAutoGuardado();
        try {
            ejecutarMenu(consola, motor);
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada. Hasta luego.");
        } finally {
            motor.cerrar();
        }
        // Al salir de main solo quedan hilos daemon (auto-guardado, lector-consola): la JVM termina ya,
        // sin esperar al auto-guardado.
    }

    private static void ejecutarMenu(Consola consola, MotorJuego motor) {
        int opcion;
        do {
            mostrarMenu();
            // TALLER ARCHIVOS (resuelto): el menu ofrece guardar, cargar, ver y borrar archivos (opciones 5 a 11).
            opcion = consola.leerEntero("Opcion: ", 0, OPCION_MAXIMA);
            switch (opcion) {
                case 1 -> motor.combateRapido();
                case 2 -> motor.jugarTorneo();
                case 3 -> motor.mostrarBestiario();
                case 4 -> motor.mostrarAutoGuardado();
                case 5 -> motor.verHistorial();
                case 6 -> motor.guardarPartida();
                case 7 -> motor.cargarPartida();
                case 8 -> motor.mostrarInfoArchivos();
                case 9 -> motor.borrarPartida();
                case 10 -> motor.borrarHistorial();
                case 11 -> motor.reiniciarHistorial();
                default -> System.out.println("Hasta luego.");
            }
        } while (opcion != 0);
    }

    private static void mostrarMenu() {
        System.out.println("\n=== SIMULADOR DE BATALLA RPG ===");
        System.out.println("1. Combate rapido");
        System.out.println("2. Torneo");
        System.out.println("3. Ver bestiario");
        System.out.println("4. Ver auto-guardado (memoria y disco)");
        System.out.println("5. Ver historial de combates (.txt)");
        System.out.println("6. Guardar partida (.dat)");
        System.out.println("7. Cargar partida (.dat)");
        System.out.println("8. Informacion de archivos (File)");
        System.out.println("9. Borrar partida guardada");
        System.out.println("10. Borrar historial de combates");
        System.out.println("11. Reiniciar historial (sobrescribir)");
        System.out.println("0. Salir");
    }
}
