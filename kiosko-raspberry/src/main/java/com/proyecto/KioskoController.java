package com.proyecto;

import java.sql.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CONTROLADOR DEL KIOSKO BIOMÉTRICO (MVC)
 * Clase autónoma que corre en la Raspberry Pi. Su función principal es mitigar el retardo
 * de red descargando los horarios escolares a un Mapa en memoria RAM y procesando las marcas.
 */
public class KioskoController {

    /**
     * DESCARGA Y CACHÉ SÍNCRONA:
     * Hace una consulta unificada cruzando Horarios y Usuarios mediante un JOIN.
     * Almacena todo en un HashMap estructurado donde la LLAVE es la Huella Digital del usuario.
     * Esto permite búsquedas O(1) instantáneas cuando alguien pone el dedo en el sensor.
     */
   public Map<String, List<String[]>> descargarMatrizHorarios() {
    Map<String, List<String[]>> cache = new HashMap<>();
    // Cambiado a LEFT JOIN desde usuarios para traer a TODOS los alumnos con o sin clases agendadas
    String sql = "SELECT u.huella_template, u.matricula, h.hora_inicio, h.salon, h.materia " +
                 "FROM usuarios u LEFT JOIN horarios h ON u.matricula = h.matricula";
                 
    try (Connection con = ConexionSupabase.obtenerConexion();
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
        
        while(rs.next()) {
            String huella = rs.getString("huella_template");
            
            // Si la huella de la base de datos es nula o vacía, la ignoramos de la caché para evitar errores
            if (huella == null || huella.trim().isEmpty()) continue;

            String[] datos = { 
                rs.getString("matricula"), 
                rs.getString("hora_inicio") != null ? rs.getString("hora_inicio") : "SIN_HORARIO", 
                rs.getString("salon") != null ? rs.getString("salon") : "LIBRE", 
                rs.getString("materia") != null ? rs.getString("materia") : "SIN_MATERIA" 
            };
            
            cache.computeIfAbsent(huella, k -> new ArrayList<>()).add(datos);
        }
    } catch (Exception e) { 
        System.err.println("CRITICAL: Caída de enlace de red al sincronizar caché: " + e.getMessage()); 
    }
    return cache;
}

    /**
     * MÓDULO DE REGLAS DE TIEMPO REAL:
     * Lee el reloj interno de la Raspberry Pi (LocalTime.now()) y mapea en qué bloque rígido 
     * se encuentra el reloj del aula actual.
     */
    public String obtenerBloqueHorarioActual() {
        LocalTime ahora = LocalTime.now();
        
        // Ventana 1: 06:30 hs a 07:10 hs
        if (ahora.isAfter(LocalTime.of(6,30)) && ahora.isBefore(LocalTime.of(7,10))) return "06:30";
        // Ventana 2: 07:10 hs a 07:50 hs
        if (ahora.isAfter(LocalTime.of(7,10)) && ahora.isBefore(LocalTime.of(7,50))) return "07:10";
        // Ventana 3: 07:50 hs a 08:30 hs
        if (ahora.isAfter(LocalTime.of(7,50)) && ahora.isBefore(LocalTime.of(8,30))) return "07:50";
        // Ventana 4: 08:30 hs a 09:15 hs
        if (ahora.isAfter(LocalTime.of(8,30)) && ahora.isBefore(LocalTime.of(9,15))) return "08:30";
        
        // COMODÍN DE PRUEBAS ACADÉMICAS: Si estás exponiendo el proyecto por la tarde o noche,
        // este retorno base evita que la app truene, simulando por defecto que es la primera clase.
        return "06:30"; 
    }

    /**
     * AUDITORÍA EN TIEMPO REAL:
     * Inserta un log de auditoría directo en la nube. No importa si el acceso fue rechazado o aceptado,
     * guarda un registro inmutable con el veredicto para que el Administrador lo supervise.
     */
    public void registrarLogAcceso(String matricula, boolean permitido, String motivo, String salonKiosko) {
        String sql = "INSERT INTO registro_accesos (matricula, permitido, motivo_rechazo, salon_kiosko) VALUES (?, ?, ?, ?)";
        try (Connection con = ConexionSupabase.obtenerConexion(); 
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            // Manejo de Huellas Desconocidas: Si la matrícula es nula (intruso), seteamos NULL en SQL 
            // para no romper la integridad de llave foránea en el servidor de base de datos
            if(matricula == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, matricula);
            
            ps.setBoolean(2, permitido); 
            ps.setString(3, motivo); 
            ps.setString(4, salonKiosko); // Identifica desde qué aula física llegó la solicitud de acceso
            ps.executeUpdate();
        } catch (Exception ex) { 
            System.err.println("Fallo al escribir log en Supabase: " + ex.getMessage()); 
        }
    }
}