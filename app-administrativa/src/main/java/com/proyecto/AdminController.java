package com.proyecto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CONTROLADOR ADMINISTRATIVO (MVC - PATRÓN DE AISLAMIENTO)
 * Esta clase centraliza toda la lógica de persistencia y consultas pesadas hacia 
 * Supabase (PostgreSQL), manteniendo la interfaz MainAdmin.java libre de código espagueti.
 */
public class AdminController {

    /**
     * Guarda de forma atómica un usuario y su matriz completa de 4 horas académicas.
     * Utiliza un bloque transaccional (ACID) para garantizar que si el registro del usuario 
     * o cualquiera de sus 4 horas falla, el sistema no quede con datos huérfanos o corruptos.
     */
    public boolean guardarUsuarioYHorarios(String mat, String nom, String rol, String car, String hue, String[][] bloques) throws Exception {
        // UPSERT para Usuarios: Evita excepciones por llaves duplicadas. Si la matrícula ya existe,
        // actualiza los campos usando COALESCE para no destruir datos existentes con cadenas vacías.
        String sqlUser = "INSERT INTO usuarios (matricula, nombre, rol, carrera, huella_template) VALUES (?, ?, ?, ?, ?) " +
                         "ON CONFLICT (matricula) DO UPDATE SET " +
                         "nombre = COALESCE(NULLIF(EXCLUDED.nombre, ''), usuarios.nombre), " +
                         "rol = EXCLUDED.rol, " +
                         "carrera = COALESCE(NULLIF(EXCLUDED.carrera, ''), usuarios.carrera), " +
                         "huella_template = COALESCE(NULLIF(EXCLUDED.huella_template, ''), usuarios.huella_template)";

        // UPSERT para Horarios: La tabla tiene una restricción UNIQUE compuesta por (matricula, hora_inicio).
        // Si se intenta registrar una materia en un bloque de hora ya ocupado por esa matrícula, lo sobrescribe de inmediato.
        String sqlHorario = "INSERT INTO horarios (matricula, hora_inicio, salon, materia) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT (matricula, hora_inicio) DO UPDATE SET salon = EXCLUDED.salon, materia = EXCLUDED.materia";

        // Establecemos la conexión usando el pool de la clase ConexionSupabase
        try (Connection con = ConexionSupabase.obtenerConexion()) { 
            // CRITICAL: Desactivamos el AutoCommit para iniciar manualmente la transacción. 
            // Esto evita que las sentencias se guarden de forma aislada a mitad de la ejecución.
            con.setAutoCommit(false); 
            
            try (PreparedStatement psUser = con.prepareStatement(sqlUser);
                 PreparedStatement psHorario = con.prepareStatement(sqlHorario)) {
                
                // Vinculación segura de parámetros en posiciones estrictas para mitigar SQL Injection
                psUser.setString(1, mat); 
                psUser.setString(2, nom); 
                psUser.setString(3, rol); 
                psUser.setString(4, car); 
                psUser.setString(5, hue); // El hash o string plano simulado de la huella biométrica
                psUser.executeUpdate();

                // Iteración lineal sobre la matriz de 4 bloques de tiempo enviados desde la Vista
                for (String[] b : bloques) {
                    psHorario.setString(1, mat);
                    // Conversión explícita de String "HH:MM:SS" a un objeto java.sql.Time compatible con PostgreSQL
                    psHorario.setTime(2, Time.valueOf(b[0])); 
                    // Operador ternario integrado: Si el campo de la interfaz llegó vacío, por sanidad se inserta "LIBRE"
                    psHorario.setString(3, b[1].isEmpty() ? "LIBRE" : b[1].toUpperCase());
                    psHorario.setString(4, b[2].isEmpty() ? "NINGUNA" : b[2].toUpperCase());
                    // Ejecución por lotes individuales dentro de la misma transacción activa
                    psHorario.executeUpdate();
                }
                
                // Si todo el bucle se ejecutó sin excepciones, disparamos el commit definitivo en la nube
                con.commit(); 
                return true;
            } catch (Exception ex) { 
                // Mecanismo de seguridad: Si algo falló (ej. red caída o tipo de dato inválido), 
                // deshacemos todos los cambios parciales para que la base de datos vuelva a su estado original.
                con.rollback(); 
                throw ex; // Re-lanzamos la excepción para que sea capturada por el JOptionPane de la Vista
            }
        }
    }

    /**
     * Consulta y extrae colecciones de usuarios filtrados por Rol (ALUMNO/MAESTRO).
     * Devuelve una estructura desacoplada (List de Object[]) para evitar que la interfaz visual 
     * manipule objetos del driver de base de datos como un ResultSet.
     */
    public List<Object[]> obtenerUsuariosPorRol(String rol) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT matricula, nombre, carrera, huella_template FROM usuarios WHERE rol = ? ORDER BY matricula ASC";
        
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rol);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    // Mapeo manual de tipos: Transformamos el estado de la huella en un indicador visual intuitivo para la tabla Swing
                    String estatusHuella = (rs.getString("huella_template") == null || rs.getString("huella_template").isEmpty()) ? "Sin Registrar" : "ACTIVA ✔";
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
     * Formatea el objeto Time de la base de datos a un String amigable de 5 caracteres ("HH:MM").
     */
    public List<Object[]> obtenerHorarioIndividual(String matricula) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT hora_inicio, salon, materia FROM horarios WHERE matricula = ? ORDER BY hora_inicio ASC";
        
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, matricula);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    // Extraemos el Time, lo pasamos a String "06:30:00" y aplicamos un substring para truncar los segundos ":00"
                    String horaFormateada = rs.getTime("hora_inicio").toString().substring(0, 5) + " hs";
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
     * Ejecuta una purga de datos por Matrícula. 
     * Nota de Arquitectura: Debido a que las tablas en Supabase están enlazadas con 
     * llaves foráneas en modo 'ON DELETE CASCADE', al borrar al usuario aquí,
     * la base de datos borra automáticamente sus horarios asociados de forma interna.
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
     * BUSCADOR COMPUESTO (Subconsultas Correlacionadas):
     * Muestra de forma reactiva quién debía estar en un salón y cruza datos con logs de accesos reales.
     * Resuelve el problema espagueti de hacer cruces de datos con filtros complejos desde Java.
     */
    public List<Object[]> filtrarAsistenciasPorSalon(String salon, String hora, String fecha) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT h.matricula, h.materia, " +
                     "COALESCE((SELECT 'ASISTIÓ ✔' FROM registro_accesos r WHERE r.matricula = h.matricula AND r.salon_kiosko = h.salon AND r.permitido = true AND DATE(r.fecha_hora) = ? LIMIT 1), 'AUSENTE ❌') as estatus, " +
                     "COALESCE((SELECT CAST(r.fecha_hora AS VARCHAR) FROM registro_accesos r WHERE r.matricula = h.matricula AND r.salon_kiosko = h.salon AND DATE(r.fecha_hora) = ? ORDER BY r.fecha_hora DESC LIMIT 1), 'Sin registros') as momento " +
                     "FROM horarios h WHERE h.salon LIKE ? AND h.hora_inicio = ?";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fecha); 
            ps.setString(2, fecha);
            ps.setString(3, "%" + salon + "%"); 
            ps.setTime(4, Time.valueOf(hora));
            
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    lista.add(new Object[]{ 
                        rs.getString("matricula"), 
                        hora.substring(0,5), 
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
     * VISTA DE AUDITORÍA UNIFICADA: 
     * Realiza un INNER JOIN entre accesos y usuarios para extraer en un solo viaje de red
     * las asistencias efectivas únicamente del personal con rol 'MAESTRO' filtrado por día.
     */
    public List<Object[]> consultarAsistenciaMaestrosPorFecha(String fecha) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT u.matricula, u.nombre, r.salon_kiosko, r.fecha_hora " +
                     "FROM registro_accesos r INNER JOIN usuarios u ON r.matricula = u.matricula " +
                     "WHERE u.rol = 'MAESTRO' AND DATE(r.fecha_hora) = ? AND r.permitido = true ORDER BY r.fecha_hora ASC";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fecha);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    lista.add(new Object[]{
                        rs.getString("matricula"), 
                        rs.getString("nombre"),
                        rs.getString("salon_kiosko"), 
                        rs.getTimestamp("fecha_hora").toString() 
                    });
                }
            }
        }
        return lista;
    }

    /**
     * MONITOR DE HISTORIAL EN TIEMPO REAL (CORE FIX - RAFAGA DE 15 INTENTOS)
     * Extrae de forma descendente los últimos 15 marcajes registrados en la base de datos,
     * cruzando con la tabla usuarios mediante un LEFT JOIN para jalar el nombre y rol.
     * Alimenta directamente a la tabla estilo terminal de la pestaña principal.
     */
    public List<Object[]> obtenerUltimos15Ingresos() throws Exception {
        List<Object[]> lista = new ArrayList<>();
        
        // El 'LIMIT 15' nos asegura traer únicamente la ráfaga de eventos más frescos de la nube
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

                // Extracción del bloque de tiempo (HH:MM:SS) quitándole la fecha y milisegundos de Postgres
                String horaFormateada = "--:--:--";
                if (horaCompleta != null && horaCompleta.length() >= 19) {
                    horaFormateada = horaCompleta.substring(11, 19);
                }

                // Homologamos los textos de salida para limpiar las columnas de la interfaz
                String estatus = permitido ? "OK" : "NO";
                String motivoFinal = (motivo != null && !motivo.isEmpty()) ? motivo : (permitido ? "Acceso Correcto" : "Hora Inválida");

                // Añadimos el renglón estructurado al paquete final de la lista
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