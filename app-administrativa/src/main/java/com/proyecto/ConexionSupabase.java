package com.proyecto;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * CLASE DE CONFIGURACIÓN Y ENLACE DE RED
 * Proporciona el mecanismo de conectividad centralizado entre la aplicación Java 
 * y la base de datos PostgreSQL alojada en la infraestructura de Supabase.
 * Utiliza el estándar JDBC (Java Database Connectivity) para la comunicación.
 */
public class ConexionSupabase {
  
    // Cadena de conexión (Connection String) que apunta al servidor remoto en la nube.
    // Especifica el protocolo (postgresql), el host de Supabase, el puerto seguro (5432) y la base de datos destino.
    private static final String URL = "jdbc:postgresql://db.sngahtlwvzemqxpwxayl.supabase.co:5432/postgres";
    
    // Credenciales de autenticación del administrador de la base de datos.
    private static final String USUARIO = "postgres";
    private static final String PASSWORD = "Los5deITC-2026"; 

    /**
     * Establece y retorna una conexión activa con el servidor de base de datos.,
     * Este método debe ser invocado dentro de bloques try-with-resources por los 
     * controladores (Controllers) para garantizar el cierre adecuado de los puertos de red.
     *
     * @return Un objeto genérico Connection activo, listo para ejecutar transacciones SQL.
     * @throws SQLException Si el servidor rechaza las credenciales, si no hay acceso a internet,
     * o si el sistema no logra instanciar el driver org.postgresql.
     */
    public static Connection obtenerConexion() throws SQLException {
        try {
            // Carga dinámica del Driver JDBC de PostgreSQL en la Máquina Virtual de Java (JVM).
            // Obligatorio para asegurar que Java sepa cómo traducir los comandos SQL a la red.
            Class.forName("org.postgresql.Driver");
            
            // Apertura del socket de comunicación TCP/IP hacia Supabase.
            return DriverManager.getConnection(URL, USUARIO, PASSWORD);
            
        } catch (ClassNotFoundException e) {
            // Se encapsula el error de clase faltante en una SQLException para mantener
            // una firma de método estandarizada y notificar si falta la dependencia en el pom.xml.
            throw new SQLException("Error crítico de dependencias: No se encontró el driver JDBC de PostgreSQL en el Classpath", e);
        }
    }
}