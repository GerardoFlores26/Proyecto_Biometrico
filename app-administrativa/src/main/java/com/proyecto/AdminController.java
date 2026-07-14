package com.proyecto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CONTROLADOR ADMINISTRATIVO (MVC - CAPA DE NEGOCIO)
 * Intermediario que gestiona las transacciones entre la Interfaz Gráfica (MainAdmin)
 * y la Base de Datos en la nube (Supabase / PostgreSQL).
 */
public class AdminController {

    private String normalizarHora24(String horaOriginal, String rol) {
        if (horaOriginal == null || horaOriginal.isEmpty()) return "00:00:00";
        
        String horaLimpia = horaOriginal.replace(" hs", "").trim();
        
        if (horaLimpia.contains("-")) {
            horaLimpia = horaLimpia.split("-")[0].trim();
        }
        
        if (!horaLimpia.contains(":")) return "00:00:00";
        
        String[] partes = horaLimpia.split(":");
        int horaInt = Integer.parseInt(partes[0]);
        int minutos = Integer.parseInt(partes[1].substring(0, 2));
        
        return String.format("%02d:%02d:00", horaInt, minutos);
    }

    public boolean guardarUsuarioYHorarios(String mat, String nom, String rol, String car, String hue, String[][] bloques) throws Exception {
        String sqlUser = "INSERT INTO usuarios (matricula, nombre, rol, carrera, huella_template) VALUES (?, ?, ?, ?, ?) " +
                         "ON CONFLICT (matricula) DO UPDATE SET " +
                         "nombre = COALESCE(NULLIF(EXCLUDED.nombre, ''), usuarios.nombre), " +
                         "rol = EXCLUDED.rol, " +
                         "carrera = COALESCE(NULLIF(EXCLUDED.carrera, ''), usuarios.carrera), " +
                         "huella_template = CASE " +
                         "    WHEN EXCLUDED.huella_template IS NOT NULL AND EXCLUDED.huella_template <> '' THEN EXCLUDED.huella_template " +
                         "    ELSE usuarios.huella_template " +
                         "END";

        String sqlHorario = "INSERT INTO horarios (matricula, hora_inicio, salon, materia) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT (matricula, hora_inicio) DO UPDATE SET salon = EXCLUDED.salon, materia = EXCLUDED.materia";

        try (Connection con = ConexionSupabase.obtenerConexion()) { 
            con.setAutoCommit(false); 
            
            try (PreparedStatement psUser = con.prepareStatement(sqlUser);
                 PreparedStatement psHorario = con.prepareStatement(sqlHorario)) {
                
                psUser.setString(1, mat); 
                psUser.setString(2, nom); 
                psUser.setString(3, rol); 
                psUser.setString(4, car); 
                
                if (hue == null || hue.trim().isEmpty() || hue.length() < 50) {
                    psUser.setString(5, null);
                } else {
                    psUser.setString(5, hue.trim());
                }
                psUser.executeUpdate();

                for (String[] b : bloques) {
                    if (b[0] == null || b[0].isEmpty()) continue;
                    
                    psHorario.setString(1, mat);
                    String horaConvertida24 = normalizarHora24(b[0], rol);
                    psHorario.setTime(2, Time.valueOf(horaConvertida24)); 
                    psHorario.setString(3, b[1].isEmpty() ? "LIBRE" : b[1].toUpperCase());
                    psHorario.setString(4, b[2].isEmpty() ? "NINGUNA" : b[2].toUpperCase());
                    psHorario.executeUpdate();
                }
                
                con.commit(); 
                return true;
            } catch (Exception ex) { 
                con.rollback(); 
                throw ex; 
            }
        }
    }

    public List<Object[]> obtenerUsuariosPorRol(String rol) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT matricula, nombre, carrera, huella_template FROM usuarios WHERE rol = ? ORDER BY matricula ASC";
        
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rol);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    String ht = rs.getString("huella_template");
                    String estatusHuella = (ht == null || ht.trim().isEmpty() || ht.length() < 100) ? "Sin Registrar" : "ACTIVA ✔";
                    
                    lista.add(new Object[]{
                        rs.getString("matricula"), 
                        rs.getString("nombre"), 
                        rs.getString("carrera"),
                        estatusHuella
                    });
                }
            }
        }
        return lista;
    }

    public List<Object[]> obtenerHorarioIndividual(String matricula) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT hora_inicio, salon, materia FROM horarios WHERE matricula = ? ORDER BY hora_inicio ASC";
        
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, matricula);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    Time hora = rs.getTime("hora_inicio");
                    String horaFormateada = (hora != null) ? hora.toString().substring(0, 5) + " hs" : "00:00 hs";
                    lista.add(new Object[]{
                        horaFormateada, 
                        rs.getString("salon"), 
                        rs.getString("materia")
                    });
                }
            }
        }
        return lista;
    }

    public void eliminarUsuario(String matricula) throws Exception {
        String sql = "DELETE FROM usuarios WHERE matricula = ?";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, matricula);
            ps.executeUpdate();
        }
    }

    /**
     * Reporte Semanal (Versión Blindada): 
     * - Calcula las fechas desde Java para evadir fallos de zona horaria en la Nube.
     * - Resta 6 horas de compensación para turnos nocturnos.
     * - Reemplaza Emojis por Texto Plano (PRESENTE/FALTA) para evitar corrupción de caracteres en Java y Excel.
     */
    public List<Object[]> generarReporteSemanalSalon(String salon, String fechaReferencia, String bloqueSeleccionado) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        
        // 1. Calculamos los días exactos de Lunes a Viernes de esa semana en Java
        java.time.LocalDate fechaBase = java.time.LocalDate.parse(fechaReferencia);
        java.time.LocalDate lunes = fechaBase.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        
        String dLun = lunes.toString();
        String dMar = lunes.plusDays(1).toString();
        String dMie = lunes.plusDays(2).toString();
        String dJue = lunes.plusDays(3).toString();
        String dVie = lunes.plusDays(4).toString();
        
        // 2. Consulta SQL extrema para emparejar la asistencia (Usamos texto PRESENTE y FALTA)
        String sql = "SELECT h.matricula, h.materia, " +
                     "  COALESCE((SELECT 'PRESENTE' FROM registro_accesos r WHERE UPPER(TRIM(r.matricula)) = UPPER(TRIM(h.matricula)) AND TRIM(r.salon_kiosko) = TRIM(h.salon) AND r.permitido = true AND (r.fecha_hora - INTERVAL '6 hours')::DATE = ?::DATE LIMIT 1), 'FALTA') as lun, " +
                     "  COALESCE((SELECT 'PRESENTE' FROM registro_accesos r WHERE UPPER(TRIM(r.matricula)) = UPPER(TRIM(h.matricula)) AND TRIM(r.salon_kiosko) = TRIM(h.salon) AND r.permitido = true AND (r.fecha_hora - INTERVAL '6 hours')::DATE = ?::DATE LIMIT 1), 'FALTA') as mar, " +
                     "  COALESCE((SELECT 'PRESENTE' FROM registro_accesos r WHERE UPPER(TRIM(r.matricula)) = UPPER(TRIM(h.matricula)) AND TRIM(r.salon_kiosko) = TRIM(h.salon) AND r.permitido = true AND (r.fecha_hora - INTERVAL '6 hours')::DATE = ?::DATE LIMIT 1), 'FALTA') as mie, " +
                     "  COALESCE((SELECT 'PRESENTE' FROM registro_accesos r WHERE UPPER(TRIM(r.matricula)) = UPPER(TRIM(h.matricula)) AND TRIM(r.salon_kiosko) = TRIM(h.salon) AND r.permitido = true AND (r.fecha_hora - INTERVAL '6 hours')::DATE = ?::DATE LIMIT 1), 'FALTA') as jue, " +
                     "  COALESCE((SELECT 'PRESENTE' FROM registro_accesos r WHERE UPPER(TRIM(r.matricula)) = UPPER(TRIM(h.matricula)) AND TRIM(r.salon_kiosko) = TRIM(h.salon) AND r.permitido = true AND (r.fecha_hora - INTERVAL '6 hours')::DATE = ?::DATE LIMIT 1), 'FALTA') as vie " +
                     "FROM horarios h WHERE TRIM(h.salon) LIKE ? AND CAST(h.hora_inicio AS VARCHAR) LIKE ? " +
                     "ORDER BY h.matricula";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, dLun);
            ps.setString(2, dMar);
            ps.setString(3, dMie);
            ps.setString(4, dJue);
            ps.setString(5, dVie);
            ps.setString(6, "%" + salon.trim() + "%"); 
            
            String horaSQL = normalizarHora24(bloqueSeleccionado, "BUSQUEDA").substring(0, 5) + "%";
            ps.setString(7, horaSQL); 
            
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    lista.add(new Object[]{ 
                        rs.getString("matricula"), 
                        rs.getString("materia"), 
                        rs.getString("lun"), 
                        rs.getString("mar"),
                        rs.getString("mie"),
                        rs.getString("jue"),
                        rs.getString("vie")
                    });
                }
            }
        }
        return lista;
    }

    public List<Object[]> consultarAsistenciaMaestrosPorFecha(String fecha) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT u.matricula, u.nombre, r.salon_kiosko, r.fecha_hora " +
                     "FROM registro_accesos r INNER JOIN usuarios u ON r.matricula = u.matricula " +
                     "WHERE u.rol = 'MAESTRO' AND r.fecha_hora::DATE = ?::DATE AND r.permitido = true ORDER BY r.fecha_hora ASC";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fecha);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    Object fechaHoraObj = rs.getTimestamp("fecha_hora");
                    String fechaHoraStr = (fechaHoraObj != null) ? fechaHoraObj.toString() : "Sin fecha";
                    lista.add(new Object[]{
                        rs.getString("matricula"), 
                        rs.getString("nombre"),
                        rs.getString("salon_kiosko"), 
                        fechaHoraStr
                    });
                }
            }
        }
        return lista;
    }

    public List<Object[]> obtenerUltimos15Ingresos() throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT r.matricula, COALESCE(u.nombre, 'NO RECONOCIDO') as nombre, " +
                     "COALESCE(u.rol, 'DESCONOCIDO') as rol, CAST(r.fecha_hora AS VARCHAR) as hora, " +
                     "r.permitido, r.motivo_rechazo " +
                     "FROM registro_accesos r " +
                     "LEFT JOIN usuarios u ON r.matricula = u.matricula " +
                     "ORDER BY r.id DESC LIMIT 15";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                String matricula = rs.getString("matricula");
                String nombre = rs.getString("nombre");
                String rol = rs.getString("rol");
                String horaCompleta = rs.getString("hora");
                boolean permitido = rs.getBoolean("permitido");
                String motivo = rs.getString("motivo_rechazo");

                String horaFormateada = "--:--:--";
                if (horaCompleta != null && horaCompleta.length() >= 19) {
                    horaFormateada = horaCompleta.substring(11, 19);
                }

                String estatus = permitido ? "OK" : "NO";
                String motivoFinal = (motivo != null && !motivo.isEmpty()) ? motivo : (permitido ? "Acceso Correcto" : "Hora Inválida");

                lista.add(new Object[]{ 
                    matricula != null ? matricula : "S/M", 
                    nombre, 
                    rol, 
                    horaFormateada, 
                    estatus, 
                    motivoFinal 
                });
            }
        }
        return lista; 
    }
}