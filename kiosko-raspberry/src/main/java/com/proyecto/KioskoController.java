package com.proyecto;

import java.sql.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CONTROLADOR DEL KIOSKO BIOMÉTRICO (MVC)
 * Clase autónoma que corre en la Raspberry Pi. Mitiga el retardo de red 
 * descargando los horarios escolares a la memoria RAM.
 */
public class KioskoController {

    public Map<String, List<String[]>> descargarMatrizHorarios() {
        Map<String, List<String[]>> cache = new HashMap<>();
        String sql = "SELECT u.huella_template, u.matricula, h.hora_inicio, h.salon, h.materia " +
                     "FROM usuarios u LEFT JOIN horarios h ON u.matricula = h.matricula";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while(rs.next()) {
                String huella = rs.getString("huella_template");
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
     * MÓDULO DE REGLAS DE TIEMPO ESTRICTO (DOBLE TURNO):
     * Mapea el reloj en formato 24h para el Turno Nocturno (18:30 a 21:10)
     * y el Turno Matutino, traduciéndolo al formato "06:30" de la base de datos.
     */
    public String obtenerBloqueHorarioActual() {
        LocalTime ahora = LocalTime.now();
        
        // --- TURNO NOCTURNO (Tus clases reales de 6:30 PM a 9:10 PM) ---
        if (!ahora.isBefore(LocalTime.of(18,30)) && ahora.isBefore(LocalTime.of(19,10))) return "06:30";
        if (!ahora.isBefore(LocalTime.of(19,10)) && ahora.isBefore(LocalTime.of(19,50))) return "07:10";
        if (!ahora.isBefore(LocalTime.of(19,50)) && ahora.isBefore(LocalTime.of(20,30))) return "07:50";
        if (!ahora.isBefore(LocalTime.of(20,30)) && ahora.isBefore(LocalTime.of(21,10))) return "08:30";

        // --- TURNO MATUTINO (Por si acaso lo usan en la mañana de 6:30 AM a 9:10 AM) ---
        if (!ahora.isBefore(LocalTime.of(6,30)) && ahora.isBefore(LocalTime.of(7,10))) return "06:30";
        if (!ahora.isBefore(LocalTime.of(7,10)) && ahora.isBefore(LocalTime.of(7,50))) return "07:10";
        if (!ahora.isBefore(LocalTime.of(7,50)) && ahora.isBefore(LocalTime.of(8,30))) return "07:50";
        if (!ahora.isBefore(LocalTime.of(8,30)) && ahora.isBefore(LocalTime.of(9,10))) return "08:30";
        
        // Si no está en ninguna ventana, niega el paso
        return "FUERA_DE_HORARIO"; 
    }

    public void registrarLogAcceso(String matricula, boolean permitido, String motivo, String salonKiosko) {
        String sql = "INSERT INTO registro_accesos (matricula, permitido, motivo_rechazo, salon_kiosko) VALUES (?, ?, ?, ?)";
        try (Connection con = ConexionSupabase.obtenerConexion(); 
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            if(matricula == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, matricula);
            
            ps.setBoolean(2, permitido); 
            ps.setString(3, motivo); 
            ps.setString(4, salonKiosko); 
            ps.executeUpdate();
        } catch (Exception ex) { 
            System.err.println("Fallo al escribir log en Supabase: " + ex.getMessage()); 
        }
    }
}