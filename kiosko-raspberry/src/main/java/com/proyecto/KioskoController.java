package com.proyecto;

import java.sql.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CONTROLADOR DEL KIOSKO BIOMÉTRICO (MVC - CAPA DE LÓGICA EMPOTRADA)
 * Clase autónoma diseñada para ejecutarse en terminales físicas (ej. Raspberry Pi).
 * Responsabilidades:
 * - Sincronizar y almacenar en caché (RAM) los datos biométricos para permitir búsquedas locales de latencia cero.
 * - Evaluar reglas de tiempo (ventanas de acceso) basadas en el reloj del hardware local.
 * - Registrar transacciones de auditoría de forma asíncrona hacia la nube.
 */
public class KioskoController {

    /**
     * Descarga y estructura la matriz completa de usuarios y horarios desde PostgreSQL.
     * Utiliza un enfoque de "Caché Activa" almacenando los datos en un mapa Hash.
     * La llave del mapa es la huella digital para permitir búsquedas instantáneas en tiempo O(1)
     * cuando el hardware detecta un evento, sin requerir una solicitud de red HTTP/TCP por cada alumno.
     *
     * @return Mapa estructurado donde la clave es el template hexadecimal de la huella, 
     * y el valor es una lista de arreglos conteniendo la matrícula, hora, salón y materia.
     */
    public Map<String, List<String[]>> descargarMatrizHorarios() {
        Map<String, List<String[]>> cache = new HashMap<>();
        
        // Se utiliza LEFT JOIN intencionalmente para cargar a todos los usuarios enrolados.
        // Si un alumno no tiene horario asignado, de igual forma debe cargarse en memoria 
        // para poder identificarlo y registrar formalmente su intento de acceso denegado.
        String sql = "SELECT u.huella_template, u.matricula, h.hora_inicio, h.salon, h.materia " +
                     "FROM usuarios u LEFT JOIN horarios h ON u.matricula = h.matricula";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while(rs.next()) {
                String huella = rs.getString("huella_template");
                
                // Filtro de integridad: Se descartan registros incompletos o corruptos para no ensuciar la RAM.
                if (huella == null || huella.trim().isEmpty()) continue;

                // Normalización de valores nulos para usuarios sin agenda asignada.
                String[] datos = { 
                    rs.getString("matricula"), 
                    rs.getString("hora_inicio") != null ? rs.getString("hora_inicio") : "SIN_HORARIO", 
                    rs.getString("salon") != null ? rs.getString("salon") : "LIBRE", 
                    rs.getString("materia") != null ? rs.getString("materia") : "SIN_MATERIA" 
                };
                
                // Si la huella no existe en el mapa, crea la lista y añade el horario. 
                // Si ya existe, simplemente añade el nuevo bloque horario a la lista existente del alumno.
                cache.computeIfAbsent(huella, k -> new ArrayList<>()).add(datos);
            }
        } catch (Exception e) { 
            System.err.println("CRITICAL: Caída de enlace de red al sincronizar caché: " + e.getMessage()); 
        }
        return cache;
    }

   /**
     * Evalúa el reloj local del hardware para determinar el bloque escolar vigente.
     * Implementa soporte para doble turno (Matutino y Nocturno), traduciendo el
     * horario físico real (ej. 19:50) a la nomenclatura normalizada de la base de datos (ej. "07:50").
     *
     * @return Cadena representativa del bloque horario normalizado (Ej. "06:30"). 
     * Retorna "FUERA_DE_HORARIO" si el reloj no coincide con ninguna ventana permitida.
     */
    public String obtenerBloqueHorarioActual() {
        LocalTime ahora = LocalTime.now();
        
        // --- TURNO NOCTURNO (Clases operativas de 18:30 a 21:10) ---
        // Se utilizan validaciones inclusivas (!isBefore) para el inicio de la ventana y exclusivas para el fin.
        if (!ahora.isBefore(LocalTime.of(18,30)) && ahora.isBefore(LocalTime.of(19,10))) return "06:30";
        if (!ahora.isBefore(LocalTime.of(19,10)) && ahora.isBefore(LocalTime.of(19,50))) return "07:10";
        if (!ahora.isBefore(LocalTime.of(19,50)) && ahora.isBefore(LocalTime.of(20,30))) return "07:50";
        if (!ahora.isBefore(LocalTime.of(20,30)) && ahora.isBefore(LocalTime.of(21,10))) return "08:30";

        // --- TURNO MATUTINO (Clases operativas de 06:30 a 09:10) ---
        if (!ahora.isBefore(LocalTime.of(6,30)) && ahora.isBefore(LocalTime.of(7,10))) return "06:30";
        if (!ahora.isBefore(LocalTime.of(7,10)) && ahora.isBefore(LocalTime.of(7,50))) return "07:10";
        if (!ahora.isBefore(LocalTime.of(7,50)) && ahora.isBefore(LocalTime.of(8,30))) return "07:50";
        if (!ahora.isBefore(LocalTime.of(8,30)) && ahora.isBefore(LocalTime.of(9,10))) return "08:30";
        
        // Barrera de seguridad estricta para evitar la inyección de asistencia en tiempos inactivos.
        return "FUERA_DE_HORARIO"; 
    }

    /**
     * Inserta un registro de auditoría inmutable en la base de datos central.
     * Diseñado para trazar tanto accesos exitosos como intentos de vulneración física al Kiosko.
     *
     * @param matricula Identificador del alumno/maestro. Puede ser nulo si el sensor lee una huella intrusa.
     * @param permitido Bandera booleana que indica si el motor de reglas aprobó el acceso.
     * @param motivo Descripción técnica de la resolución (Ej. "Clase de ESPAÑOL" o "Huella desconocida").
     * @param salonKiosko Identificador físico del aula donde se encuentra empotrado el hardware.
     */
    public void registrarLogAcceso(String matricula, boolean permitido, String motivo, String salonKiosko) {
        String sql = "INSERT INTO registro_accesos (matricula, permitido, motivo_rechazo, salon_kiosko) VALUES (?, ?, ?, ?)";
        try (Connection con = ConexionSupabase.obtenerConexion(); 
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            // Tratamiento defensivo para "Intrusos": Si la huella no está registrada, la matrícula llega nula.
            // Se utiliza setNull para evitar excepciones de tipo NullPointerException o violaciones de Foreign Key.
            if(matricula == null) {
                ps.setNull(1, Types.VARCHAR); 
            } else {
                ps.setString(1, matricula);
            }
            
            ps.setBoolean(2, permitido); 
            ps.setString(3, motivo); 
            ps.setString(4, salonKiosko); 
            ps.executeUpdate();
            
        } catch (Exception ex) { 
            // Falla silenciosa en consola. Se prioriza mantener el Kiosko operativo aunque falle la red.
            System.err.println("Fallo al escribir log en Supabase: " + ex.getMessage()); 
        }
    }
}