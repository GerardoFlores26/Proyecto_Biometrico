package com.proyecto;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class MainKiosko extends JFrame {
    private String SALON_ACTUAL = "AULA GENERAL"; 

    private JLabel lblStatus, lblMessage, lblUser, lblLogo;
    private JPanel pnlMain;
    private Map<String, String[]> cacheUsuarios = new HashMap<>(); 
    
    private Color azulPrincipal = new Color(20, 80, 160);
    private Color fondoBlanco = new Color(255, 255, 255);
    private Color verdeExito = new Color(46, 204, 113);
    private Color rojoError = new Color(231, 76, 60);

    public MainKiosko() {
        cargarConfiguracionLocal();

        setTitle("Terminal Kiosko - " + SALON_ACTUAL);
        setSize(800, 520); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        pnlMain = new JPanel(new BorderLayout(10, 10));
        pnlMain.setBackground(fondoBlanco);
        
        // --- LOGO DE ALTA DEFINICIÓN CON PROPORCIÓN CORRECTA ---
        lblLogo = new JLabel("", SwingConstants.CENTER);
        try {
            ImageIcon iconoOriginal = new ImageIcon("logo.png");
            Image imgOriginal = iconoOriginal.getImage();
            
            int anchoOriginal = iconoOriginal.getIconWidth();
            int altoOriginal = iconoOriginal.getIconHeight();
            
            if (anchoOriginal > 0) {
                int nuevoAncho = 280; 
                int nuevoAlto = (altoOriginal * nuevoAncho) / anchoOriginal;
                
                java.awt.image.BufferedImage imgModificada = new java.awt.image.BufferedImage(
                    nuevoAncho, nuevoAlto, java.awt.image.BufferedImage.TYPE_INT_ARGB
                );
                
                Graphics2D g2 = imgModificada.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                
                g2.drawImage(imgOriginal, 0, 0, nuevoAncho, nuevoAlto, null);
                g2.dispose();
                
                lblLogo.setIcon(new ImageIcon(imgModificada));
                lblLogo.setBorder(BorderFactory.createEmptyBorder(25, 0, 15, 0)); 
            } else {
                lblLogo.setText("[ LOGO NO ENCONTRADO ]");
                lblLogo.setForeground(Color.LIGHT_GRAY);
            }
        } catch (Exception e) {
            lblLogo.setText("[ ERROR AL CARGAR LOGO ]");
            lblLogo.setForeground(Color.LIGHT_GRAY);
        }
        
        lblStatus = new JLabel("POR FAVOR COLOQUE SU HUELLA", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblStatus.setForeground(azulPrincipal);

        JPanel pnlCenter = new JPanel(new GridLayout(3, 1));
        pnlCenter.setOpaque(false);
        
        lblMessage = new JLabel("Chequeo de Asistencia - " + SALON_ACTUAL, SwingConstants.CENTER);
        lblMessage.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        lblMessage.setForeground(Color.GRAY);
        
        lblUser = new JLabel("", SwingConstants.CENTER);
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 36));
        lblUser.setForeground(azulPrincipal);
        
        pnlCenter.add(lblMessage);
        pnlCenter.add(lblUser);

        pnlMain.add(lblLogo, BorderLayout.NORTH);
        pnlMain.add(pnlCenter, BorderLayout.CENTER);
        pnlMain.add(lblStatus, BorderLayout.SOUTH);

        add(pnlMain);
        descargarCache();

        new Thread(() -> {
            while(true) {
                String huellaInput = JOptionPane.showInputDialog(this, "Simulador de Huella para " + SALON_ACTUAL + ":");
                if (huellaInput != null) procesarAcceso(huellaInput.trim());
            }
        }).start();
    }

    private void cargarConfiguracionLocal() {
        java.util.Properties prop = new java.util.Properties();
        try (java.io.FileInputStream input = new java.io.FileInputStream("config.properties")) {
            prop.load(input);
            String salonProp = prop.getProperty("salon");
            if (salonProp != null && !salonProp.trim().isEmpty()) {
                this.SALON_ACTUAL = salonProp.trim().toUpperCase();
            }
        } catch (java.io.IOException ex) {
            System.out.println("[CONFIG] No se encontró config.properties, usando aula por defecto.");
        }
    }

    private void descargarCache() {
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement("SELECT matricula, salon, huella_template FROM usuarios");
             ResultSet rs = ps.executeQuery()) {
            cacheUsuarios.clear();
            while(rs.next()) {
                cacheUsuarios.put(rs.getString("huella_template"), new String[]{rs.getString("matricula"), rs.getString("salon")});
            }
        } catch (Exception e) { System.err.println("Error al actualizar caché: " + e.getMessage()); }
    }

    private void procesarAcceso(String huella) {
        if (!cacheUsuarios.containsKey(huella)) {
            descargarCache();
        }

        if (cacheUsuarios.containsKey(huella)) {
            String[] datos = cacheUsuarios.get(huella);
            String matricula = datos[0];
            String salonAsignado = datos[1];

            // VALIDACIÓN INTELIGENTE: Compara si son iguales, o si uno contiene al otro 
            // (Evita errores si uno dice "303" y el otro "AULA 303")
            if (salonAsignado.equalsIgnoreCase(SALON_ACTUAL) || 
                SALON_ACTUAL.contains(salonAsignado) || 
                salonAsignado.contains(SALON_ACTUAL)) {
                
                // Quitamos el emoji '✔' para evitar el cuadro blanco
                mostrarResultado("ASISTENCIA REGISTRADA", "MAT: " + matricula, verdeExito);
                subirAcceso(matricula, true, "Llegó a su salón correcto: " + SALON_ACTUAL);
            } else {
                // Quitamos el emoji '❌' para evitar el cuadro blanco
                mostrarResultado("SALON EQUIVOCADO", "ASIGNADO A: " + salonAsignado, rojoError);
                subirAcceso(matricula, false, "Intentó entrar a " + SALON_ACTUAL + " pero pertenece a " + salonAsignado);
            }
        } else {
            // Quitamos el emoji '❌' para evitar el cuadro blanco
            mostrarResultado("ACCESO DENEGADO", "HUELLA DESCONOCIDA", rojoError);
            subirAcceso(null, false, "Huella no registrada en el sistema");
        }
    }

    private void mostrarResultado(String msg, String user, Color colorEstatus) {
        lblStatus.setText(msg);
        lblStatus.setForeground(colorEstatus);
        lblUser.setText(user);
        
        Timer t = new Timer(3500, e -> {
            lblStatus.setText("POR FAVOR COLOQUE SU HUELLA"); 
            lblStatus.setForeground(azulPrincipal);
            lblUser.setText("");
        });
        t.setRepeats(false); 
        t.start();
    }

    private void subirAcceso(String mat, boolean ok, String mot) {
        String sql = "INSERT INTO registro_accesos (matricula, permitido, motivo_rechazo, salon_kiosko) VALUES (?, ?, ?, ?)";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if(mat == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, mat);
            ps.setBoolean(2, ok); 
            ps.setString(3, mot);
            ps.setString(4, SALON_ACTUAL); 
            ps.executeUpdate();
        } catch (Exception e) {}
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new MainKiosko().setVisible(true)); }
}