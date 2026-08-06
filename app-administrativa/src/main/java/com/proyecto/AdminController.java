package com.proyecto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CONTROLADOR ADMINISTRATIVO (MVC - CAPA DE NEGOCIO)
 * Intermediario que gestiona las transacciones entre la Interfaz Gráfica (MainAdmin)
 * y la Base de Datos en la nube (Supabase / PostgreSQL).
 * * Responsabilidades:
 * - Sanitización de datos provenientes de la vista.
 * - Ejecución de transacciones SQL atómicas (Commit/Rollback).
 * - Generación de reportes cruzados y manejo de zonas horarias.
 */
public class AdminController {

    /**
     * Normaliza los bloques de texto de la interfaz gráfica a formato SQL TIME (HH:mm:ss).
     * Esto previene errores de persistencia al intentar insertar rangos (ej. "06:30 - 07:10")
     * o formatos con sufijos en la base de datos.
     *
     * @param horaOriginal Cadena de texto proveniente de la vista (Ej. "06:30 hs" o "06:30 - 07:10").
     * @param rol Parámetro auxiliar para contexto.
     * @return Cadena formateada estrictamente como "HH:mm:ss" compatible con PostgreSQL.
     */
    private String normalizarHora24(String horaOriginal, String rol) {
        if (horaOriginal == null || horaOriginal.isEmpty()) return "00:00:00";
        
        // Limpieza de sufijos estéticos de la UI
        String horaLimpia = horaOriginal.replace(" hs", "").trim();
        
        // Extracción del bloque de inicio si la cadena contiene un rango
        if (horaLimpia.contains("-")) {
            horaLimpia = horaLimpia.split("-")[0].trim();
        }
        
        if (!horaLimpia.contains(":")) return "00:00:00";
        
        String[] partes = horaLimpia.split(":");
        int horaInt = Integer.parseInt(partes[0]);
        int minutos = Integer.parseInt(partes[1].substring(0, 2));
        
        return String.format("%02d:%02d:00", horaInt, minutos);
    }

    /**
     * Inserta o actualiza un usuario y su matriz completa de horarios escolares en una sola transacción atómica.
     * Utiliza la cláusula ON CONFLICT (Upsert) para evitar duplicidad de matrículas.
     * Si la inserción de algún bloque de horario falla, toda la transacción hace Rollback para
     * evitar registros huérfanos o parciales en la base de datos.
     *
     * @param mat Matrícula única del usuario.
     * @param nom Nombre completo del usuario.
     * @param rol Rol institucional (ALUMNO/MAESTRO).
     * @param car Carrera o departamento.
     * @param hue Cadena hexadecimal de la huella dactilar.
     * @param bloques Matriz bidimensional con los horarios (Bloque, Salón, Materia).
     * @return true si la transacción fue exitosa.
     * @throws Exception Si ocurre un fallo de conexión o violación de restricciones SQL.
     */
    public boolean guardarUsuarioYHorarios(String mat, String nom, String rol, String car, String hue, String[][] bloques) throws Exception {
        // UPSERT de Usuario: Protege el template biométrico existente si la actualización viene con una huella vacía.
        String sqlUser = "INSERT INTO usuarios (matricula, nombre, rol, carrera, huella_template) VALUES (?, ?, ?, ?, ?) " +
                         "ON CONFLICT (matricula) DO UPDATE SET " +
                         "nombre = COALESCE(NULLIF(EXCLUDED.nombre, ''), usuarios.nombre), " +
                         "rol = EXCLUDED.rol, " +
                         "carrera = COALESCE(NULLIF(EXCLUDED.carrera, ''), usuarios.carrera), " +
                         "huella_template = CASE " +
                         "    WHEN EXCLUDED.huella_template IS NOT NULL AND EXCLUDED.huella_template <> '' THEN EXCLUDED.huella_template " +
                         "    ELSE usuarios.huella_template " +
                         "END";

        // UPSERT de Horario: Actualiza salón y materia si ya existe un registro para esa matrícula a esa hora específica.
        String sqlHorario = "INSERT INTO horarios (matricula, hora_inicio, salon, materia) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT (matricula, hora_inicio) DO UPDATE SET salon = EXCLUDED.salon, materia = EXCLUDED.materia";

        try (Connection con = ConexionSupabase.obtenerConexion()) { 
            con.setAutoCommit(false); // Inicia bloque de transacción atómica
            
            try (PreparedStatement psUser = con.prepareStatement(sqlUser);
                 PreparedStatement psHorario = con.prepareStatement(sqlHorario)) {
                
                psUser.setString(1, mat); 
                psUser.setString(2, nom); 
                psUser.setString(3, rol); 
                psUser.setString(4, car); 
                
                // Validación estricta del payload biométrico para evitar corromper la BD con cadenas cortas o nulas
                if (hue == null || hue.trim().isEmpty() || hue.length() < 50) {
                    psUser.setString(5, null);
                } else {
                    psUser.setString(5, hue.trim());
                }
                psUser.executeUpdate();

                // Procesamiento secuencial de la matriz de horarios
                for (String[] b : bloques) {
                    if (b[0] == null || b[0].isEmpty()) continue;
                    
                    psHorario.setString(1, mat);
                    String horaConvertida24 = normalizarHora24(b[0], rol);
                    psHorario.setTime(2, Time.valueOf(horaConvertida24)); 
                    psHorario.setString(3, b[1].isEmpty() ? "LIBRE" : b[1].toUpperCase());
                    psHorario.setString(4, b[2].isEmpty() ? "NINGUNA" : b[2].toUpperCase());
                    psHorario.executeUpdate();
                }
                
                con.commit(); // Consolidación de datos
                return true;
            } catch (Exception ex) { 
                con.rollback(); // Reversión de cambios ante cualquier excepción
                throw ex; 
            }
        }
    }

    /**
     * Recupera la lista de usuarios filtrados por su rol institucional.
     * Evalúa la integridad de la huella dactilar para devolver un estatus visual a la tabla de la vista.
     *
     * @param rol "ALUMNO" o "MAESTRO".
     * @return Lista de arreglos (List<Object[]>) adaptada para DefaultTableModel.
     * @throws Exception En caso de error de lectura SQL.
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
                    
                    // Verificación de integridad: Templates menores a 100 caracteres se consideran inválidos/corruptos
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
     * Extrae el cronograma de clases de un estudiante particular.
     *
     * @param matricula Matrícula del usuario a consultar.
     * @return Lista de horarios parseados para la vista.
     * @throws Exception Error de conexión o lectura.
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
                    // Transformación del tipo TIME a String presentable ("HH:mm hs")
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
     * Ejecuta una purga de datos por matrícula de forma directa en la base de datos.
     * * @param matricula Identificador único del registro a borrar.
     * @throws Exception Si la eliminación compromete la integridad referencial.
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
     * Motor de Generación de Reporte Pivot Semanal.
     * Mapea de forma transversal las asistencias de la semana (Lunes a Viernes).
     * * Optimización Estética y de Compatibilidad:
     * - Utiliza los caracteres oficiales del plantel ('*' para asistencia y '/' para falta) 
     * en lugar de cadenas de texto largas. Esto reduce la carga visual en la hoja de cálculo
     * y emula la distribución del formato institucional.
     * - Implementa una compensación de huso horario (-6 horas) en las consultas SQL para 
     * neutralizar el desfase de los servidores en la nube (UTC) con el turno nocturno local.
     * - Aplica funciones defensivas TRIM() y UPPER() para prevenir pases de lista nulos por 
     * espacios accidentales en las matrículas o salones.
     *
     * @param salon Nombre del aula física a filtrar (Ej. "303").
     * @param fechaReferencia Fecha base seleccionada en la interfaz (YYYY-MM-DD).
     * @param bloqueSeleccionado Rango horario de la materia (Ej. "08:30 - 09:10").
     * @return Lista de arreglos de objetos conteniendo la matriz de asistencia semanal.
     * @throws Exception Si ocurre un fallo en la conexión TCP/IP con Supabase.
     */
    public List<Object[]> generarReporteSemanalSalon(String salon, String fechaReferencia, String bloqueSeleccionado) throws Exception {
        List<Object[]> lista = new ArrayList<>();
        
        // 1. Cálculo Aritmético de Fechas en la JVM
        java.time.LocalDate fechaBase = java.time.LocalDate.parse(fechaReferencia);
        java.time.LocalDate lunes = fechaBase.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        
        String dLun = lunes.toString();
        String dMar = lunes.plusDays(1).toString();
        String dMie = lunes.plusDays(2).toString();
        String dJue = lunes.plusDays(3).toString();
        String dVie = lunes.plusDays(4).toString();
        
        // 2. Subconsulta "Omni-Timezone" blindada contra errores de JDBC y Supabase
        // Valida el turno matutino y el turno nocturno (+12 hrs) tanto en hora cruda como compensada (-6 hrs).
        String subQuery = "(SELECT '*' FROM registro_accesos r " +
                          "WHERE UPPER(TRIM(r.matricula)) = UPPER(TRIM(h.matricula)) " +
                          "AND TRIM(r.salon_kiosko) = TRIM(h.salon) " +
                          "AND r.permitido = true " +
                          "AND (r.fecha_hora - INTERVAL '6 hours')::DATE = ?::DATE " +
                          "AND ( " +
                          "      (r.fecha_hora::TIME >= (h.hora_inicio::TIME - INTERVAL '15 minutes') AND r.fecha_hora::TIME <= (h.hora_inicio::TIME + INTERVAL '45 minutes')) " +
                          "   OR ((r.fecha_hora - INTERVAL '6 hours')::TIME >= (h.hora_inicio::TIME - INTERVAL '15 minutes') AND (r.fecha_hora - INTERVAL '6 hours')::TIME <= (h.hora_inicio::TIME + INTERVAL '45 minutes')) " +
                          "   OR (r.fecha_hora::TIME >= (h.hora_inicio::TIME + INTERVAL '11 hours 45 minutes') AND r.fecha_hora::TIME <= (h.hora_inicio::TIME + INTERVAL '12 hours 45 minutes')) " +
                          "   OR ((r.fecha_hora - INTERVAL '6 hours')::TIME >= (h.hora_inicio::TIME + INTERVAL '11 hours 45 minutes') AND (r.fecha_hora - INTERVAL '6 hours')::TIME <= (h.hora_inicio::TIME + INTERVAL '12 hours 45 minutes')) " +
                          ") LIMIT 1)";

        // 3. Consulta Transversal (Pivot) limpia
        String sql = "SELECT h.matricula, h.materia, " +
                     "  COALESCE(" + subQuery + ", '/') as lun, " +
                     "  COALESCE(" + subQuery + ", '/') as mar, " +
                     "  COALESCE(" + subQuery + ", '/') as mie, " +
                     "  COALESCE(" + subQuery + ", '/') as jue, " +
                     "  COALESCE(" + subQuery + ", '/') as vie " +
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
            
            // Extracción de la hora para validación
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

    /**
     * Consulta el log de auditoría histórico exclusivamente para el perfil 'MAESTRO'.
     * Utiliza INNER JOIN para asegurar que solo devuelva marcas válidas cruzando con la tabla de usuarios.
     *
     * @param fecha Cadena de fecha YYYY-MM-DD.
     * @return Log tabular de accesos.
     * @throws Exception Si ocurre un fallo en la conexión o sintaxis.
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
     * Alimentador de datos para el Monitor Visual en Tiempo Real.
     * Extrae una ráfaga con los últimos 15 registros globales, ordenados descendentemente.
     * Implementa LEFT JOIN porque los registros rechazados por hardware biométrico pueden no
     * poseer una matrícula válida en el sistema (ej. intentos de intrusos).
     *
     * @return Lista estructurada para el modelo JTable del monitor.
     * @throws Exception En caso de desconexión abrupta de red durante el hilo (timer).
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

                // Parseo de Timestamp ("YYYY-MM-DD HH:mm:ss.ms") para extraer solo el bloque "HH:mm:ss"
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