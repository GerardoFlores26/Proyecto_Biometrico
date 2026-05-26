package com.proyecto;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionSupabase {
    
    private static final String URL = "jdbc:postgresql://db.sngahtlwvzemqxpwxayl.supabase.co:5432/postgres";
    
    
    private static final String USUARIO = "postgres";
    
    
    private static final String PASSWORD = "Los5deITC-2026"; 

    public static Connection obtenerConexion() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection(URL, USUARIO, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontró el driver de PostgreSQL", e);
        }
    }
}