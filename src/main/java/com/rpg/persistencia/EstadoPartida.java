package com.rpg.persistencia;

import com.rpg.entities.Gremio;
import com.rpg.entities.PerfilJugador;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TALLER ARCHIVOS (seccion 5): todo lo que forma una partida guardada, en un unico objeto Serializable que se
 * escribe con ObjectOutputStream.writeObject y se lee con ObjectInputStream.readObject.
 * <p>
 * Grafo de objetos que se guarda: EstadoPartida -> PerfilJugador (logros en TreeSet) -> Gremio -> lista de
 * Personaje (Guerrero/Mago) -> Arma, Inventario -> Pocion -> EstadoEfecto... y cada Personaje vuelve a apuntar
 * a su Gremio (ciclo). Todas esas clases implementan Serializable y declaran su serialVersionUID.
 * <p>
 * Lo que NO se guarda es el RegistroCombate entero (no es Serializable: provocaria NotSerializableException,
 * ver DemoSerializacion); se guarda solo su texto dentro de {@code resumen}.
 */
public class EstadoPartida implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PerfilJugador perfil;
    private final Gremio gremio;
    private final LinkedHashMap<String, Integer> enemigosDerrotados;
    private final String resumen;
    private final LocalDateTime fechaGuardado;

    public EstadoPartida(PerfilJugador perfil, Gremio gremio, Map<String, Integer> enemigosDerrotados,
                         String resumen) {
        this.perfil = perfil;
        this.gremio = gremio;
        this.enemigosDerrotados = new LinkedHashMap<>(enemigosDerrotados);
        this.resumen = resumen;
        this.fechaGuardado = LocalDateTime.now();
    }

    public PerfilJugador getPerfil() {
        return perfil;
    }

    public Gremio getGremio() {
        return gremio;
    }

    public Map<String, Integer> getEnemigosDerrotados() {
        return new LinkedHashMap<>(enemigosDerrotados);
    }

    public String getResumen() {
        return resumen;
    }

    public LocalDateTime getFechaGuardado() {
        return fechaGuardado;
    }

    public String getFechaGuardadoTexto() {
        return fechaGuardado.format(FORMATO_FECHA);
    }
}
