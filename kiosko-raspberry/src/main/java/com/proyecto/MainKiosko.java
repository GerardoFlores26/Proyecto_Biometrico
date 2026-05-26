package com.proyecto;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class MainKiosko extends JFrame {
    private JLabel lblStatus, lblMessage, lblUser;
    private JPanel pnlMain;
    private Map<String, String> cache = new HashMap<>();
    private Color supabaseGreen = new Color(62, 207, 142);
    private Color accessDenied = new Color(255, 75, 75);

    public MainKiosko() {
        setTitle("Terminal de Acceso Kiosko");
        setSize(800, 480); // Tamaño típico de pantalla Raspberry
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        pnlMain = new JPanel(new BorderLayout());
        pnlMain.setBackground(new Color(23, 23, 23));
        
        lblStatus = new JLabel("BIENVENIDO", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 48));
        lblStatus.setForeground(Color.WHITE);
        pnlMain.add(lblStatus, BorderLayout.NORTH);

        JPanel pnlCenter = new JPanel(new GridLayout(2, 1));
        pnlCenter.setOpaque(false);
        lblMessage = new JLabel("Coloque su huella en el sensor", SwingConstants.CENTER);
        lblMessage.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        lblMessage.setForeground(Color.GRAY);
        
        lblUser = new JLabel("", SwingConstants.CENTER);
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 36));
        lblUser.setForeground(supabaseGreen);
        
        pnlCenter.add(lblMessage);
        pnlCenter.add(lblUser);
        pnlMain.add(pnlCenter, BorderLayout.CENTER);

        add(pnlMain);
        descargarCache();

        // Hilo de escucha (Simulado con input)
        new Thread(() -> {
            while(true) {
                String huellaInput = JOptionPane.showInputDialog(this, "Símula el sensor (Ingresa ID de huella):");
                if (huellaInput != null) procesar(huellaInput);
            }
        }).start();
    }

    private void descargarCache() {
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement("SELECT matricula, huella_template FROM usuarios");
             ResultSet rs = ps.executeQuery()) {
            cache.clear();
            while(rs.next()) cache.put(rs.getString("huella_template"), rs.getString("matricula"));
        } catch (Exception e) { System.err.println("Error cache: " + e.getMessage()); }
    }

    private void procesar(String huella) {
        if (cache.containsKey(huella)) {
            String mat = cache.get(huella);
            mostrarResultado("ACCESO AUTORIZADO", mat, supabaseGreen);
            subirAcceso(mat, true, "Validación Biométrica Correcta");
        } else {
            // TRUCO: Si no la encuentra, vuelve a revisar la nube por si es un usuario nuevo
            descargarCache(); 
            
            // Intentamos revisar otra vez con la caché actualizada
            if (cache.containsKey(huella)) {
                String mat = cache.get(huella);
                mostrarResultado("ACCESO AUTORIZADO", mat, supabaseGreen);
                subirAcceso(mat, true, "Validación Biométrica Correcta (Caché Actualizada)");
            } else {
                mostrarResultado("ACCESO DENEGADO", "DESCONOCIDO", accessDenied);
                subirAcceso(null, false, "Huella no registrada");
            }
        }
    }

    private void mostrarResultado(String msg, String user, Color c) {
        lblStatus.setText(msg);
        lblStatus.setForeground(c);
        lblUser.setText("MAT: " + user);
        Timer t = new Timer(3000, e -> {
            lblStatus.setText("BIENVENIDO"); lblStatus.setForeground(Color.WHITE);
            lblUser.setText("");
        });
        t.setRepeats(false); t.start();
    }

    private void subirAcceso(String mat, boolean ok, String mot) {
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement("INSERT INTO registro_accesos (matricula, permitido, motivo_rechazo) VALUES (?, ?, ?)")) {
            if(mat == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, mat);
            ps.setBoolean(2, ok); ps.setString(3, mot);
            ps.executeUpdate();
        } catch (Exception e) {}
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new MainKiosko().setVisible(true)); }
}