package com.proyecto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CONTROLADOR ADMINISTRATIVO (MVC - PATRÓN DE AISLAMIENTO)
 *  almacenamiento efectivo de huellas dactilares en Supabase

 */
public class AdminController {

    /**
     * Convierte una cadena de hora al formato estricto "HH:mm:ss" de 24 horas.
     * Mantiene los bloques fijos de la interfaz sin alterarlos por la hora del sistema.
     */
    private String normalizarHora24(String horaOriginal, String rol) {
        if (horaOriginal == null || horaOriginal.isEmpty()) return "00:00:00";
        
        // Limpiar espacios o textos basura como " hs"
        String horaLimpia = horaOriginal.replace(" hs", "").trim();
        
        // Si el bloque viene como rango (ej: "08:30 - 09:10"), nos quedamos solo con la hora de inicio
        if (horaLimpia.contains("-")) {
            horaLimpia = horaLimpia.split("-")[0].trim();
        }
        
        if (!horaLimpia.contains(":")) return "00:00:00";
        
        String[] partes = horaLimpia.split(":");
        int horaInt = Integer.parseInt(partes[0]);
        int minutos = Integer.parseInt(partes[1].substring(0, 2)); // Evita segundos pegados si los hay
        
        return String.format("%02d:%02d:00", horaInt, minutos);
    }

    /**
     * Guarda de forma atómica un usuario y su matriz completa de 4 horas académicas.
     
     */
    public boolean guardarUsuarioYHorarios(String mat, String nom, String rol, String car, String hue, String[][] bloques) throws Exception {
        // CORRECCIÓN: Optimizamos la actualización para asegurar que la huella se escriba correctamente 
        // si se proporciona un template válido, y que no se machuque con strings vacíos.
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
                
                // Si la huella viene nula o contiene textos de pruebas erróneos (como longitudes 9 o 10), mandamos vacío
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

    /**
     * Consulta y extrae colecciones de usuarios filtrados por Rol.
     */
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

    /**
     * Recupera el desglose del itinerario académico diario de un estudiante.
     */
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

    /**
     * Ejecuta una purga de datos por Matrícula de forma segura.
     */
    public void eliminarUsuario(String matricula) throws Exception {
        String sql = "DELETE FROM usuarios WHERE matricula = ?";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, matricula);
            ps.executeUpdate();
        }
    }

    /**
     * BUSCADOR COMPUESTO: Filtra asistencias convirtiendo la hora de consulta a un String plano.
     */
    public List<Object[]> filtrarAsistenciasPorSalon(String salon, String hora, String fecha) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        
        String sql = "SELECT h.matricula, h.materia, " +
                     "COALESCE((SELECT 'ASISTIÓ ✔' FROM registro_accesos r WHERE r.matricula = h.matricula AND r.salon_kiosko = h.salon AND r.permitido = true AND r.fecha_hora::DATE = ?::DATE LIMIT 1), 'AUSENTE ❌') as estatus, " +
                     "COALESCE((SELECT CAST(r.fecha_hora AS VARCHAR) FROM registro_accesos r WHERE r.matricula = h.matricula AND r.salon_kiosko = h.salon AND r.fecha_hora::DATE = ?::DATE ORDER BY r.fecha_hora DESC LIMIT 1), 'Sin registros') as momento " +
                     "FROM horarios h WHERE h.salon LIKE ? AND CAST(h.hora_inicio AS VARCHAR) LIKE ?";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fecha); 
            ps.setString(2, fecha); 
            ps.setString(3, "%" + salon + "%"); 
            
            String horaConsulta24 = normalizarHora24(hora, "BUSQUEDA");
            ps.setString(4, horaConsulta24 + "%"); 
            
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    lista.add(new Object[]{ 
                        rs.getString("matricula"), 
                        horaConsulta24.substring(0,5) + " hs", 
                        rs.getString("materia"), 
                        rs.getString("estatus"), 
                        rs.getString("momento") 
                    });
                }
            }
        }
        return lista;
    }

    /**
     * VISTA DE AUDITORÍA UNIFICADA PARA DOCENTES
     */
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

    /**
     * MONITOR DE HISTORIAL EN TIEMPO REAL
     */
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
        } catch (Exception ex) {
            System.err.println("Fallo crítico en lectura de ráfaga de monitoreo: " + ex.getMessage());
            throw ex;
        }
        return lista; 
    }
}