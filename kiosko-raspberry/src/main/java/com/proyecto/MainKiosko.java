package com.proyecto;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class MainKiosko extends JFrame {
    private String SALON_ACTUAL = "AULA GENERAL"; 

    private JLabel lblStatus, lblMessage, lblUser, lblLogo, lblConsejo;
    private JPanel pnlMain;
    
    // Guardamos: huella -> [matricula, hora_inicio, salon, materia]
    private Map<String, java.util.List<String[]>> cacheHorarios = new HashMap<>(); 
    
    private Color azulPrincipal = new Color(20, 80, 160);
    private Color fondoBlanco = new Color(255, 255, 255);
    private Color verdeExito = new Color(46, 204, 113);
    private Color rojoError = new Color(231, 76, 60);

    public MainKiosko() {
        cargarConfiguracionLocal();

        setTitle("Terminal Kiosko de Aula - " + SALON_ACTUAL);
        setSize(800, 580); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        pnlMain = new JPanel(new BorderLayout(10, 10));
        pnlMain.setBackground(fondoBlanco);
        
        // --- LOGO EN ALTA DEFINICIÓN ---
        lblLogo = new JLabel("", SwingConstants.CENTER);
        try {
            ImageIcon iconoOriginal = new ImageIcon("logo.png");
            Image imgOriginal = iconoOriginal.getImage();
            int anchoOriginal = iconoOriginal.getIconWidth();
            int altoOriginal = iconoOriginal.getIconHeight();
            if (anchoOriginal > 0) {
                int nuevoAncho = 260; 
                int nuevoAlto = (altoOriginal * nuevoAncho) / anchoOriginal;
                java.awt.image.BufferedImage imgModificada = new java.awt.image.BufferedImage(nuevoAncho, nuevoAlto, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = imgModificada.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(imgOriginal, 0, 0, nuevoAncho, nuevoAlto, null);
                g2.dispose();
                lblLogo.setIcon(new ImageIcon(imgModificada));
                lblLogo.setBorder(BorderFactory.createEmptyBorder(15, 0, 10, 0)); 
            }
        } catch (Exception e) { lblLogo.setText("[ LOGO ]"); }
        
        lblStatus = new JLabel("POR FAVOR COLOQUE SU HUELLA", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 30));
        lblStatus.setForeground(azulPrincipal);

        JPanel pnlCenter = new JPanel(new GridLayout(3, 1));
        pnlCenter.setOpaque(false);
        
        lblMessage = new JLabel("Control de Horarios Dinámicos - " + SALON_ACTUAL, SwingConstants.CENTER);
        lblMessage.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        lblMessage.setForeground(Color.GRAY);
        
        lblUser = new JLabel("", SwingConstants.CENTER);
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblUser.setForeground(azulPrincipal);
        
        pnlCenter.add(lblMessage);
        pnlCenter.add(lblUser);

        // NUEVO: Recomendación de limpieza de huella
        lblConsejo = new JLabel("💡 Nota: Si el sensor no reconoce tu huella, límpiala suavemente e inténtalo de nuevo.", SwingConstants.CENTER);
        lblConsejo.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblConsejo.setForeground(Color.DARK_GRAY);

        JPanel pnlInferior = new JPanel(new GridLayout(2, 1, 5, 5));
        pnlInferior.setOpaque(false);
        pnlInferior.add(lblStatus);
        pnlInferior.add(lblConsejo);

        pnlMain.add(lblLogo, BorderLayout.NORTH);
        pnlMain.add(pnlCenter, BorderLayout.CENTER);
        pnlMain.add(pnlInferior, BorderLayout.SOUTH);

        add(pnlMain);
        descargarCacheHorarios();

        new Thread(() -> {
            while(true) {
                String huellaInput = JOptionPane.showInputDialog(this, "Lector Biométrico (" + SALON_ACTUAL + "):");
                if (huellaInput != null) procesarAccesoDinamico(huellaInput.trim());
            }
        }).start();
    }

    private void cargarConfiguracionLocal() {
        java.util.Properties prop = new java.util.Properties();
        try (java.io.FileInputStream input = new java.io.FileInputStream("config.properties")) {
            prop.load(input);
            String salonProp = prop.getProperty("salon");
            if (salonProp != null) this.SALON_ACTUAL = salonProp.trim().toUpperCase();
        } catch (Exception ex) { System.out.println("Usando aula por defecto."); }
    }

    private void descargarCacheHorarios() {
        String sql = "SELECT u.huella_template, h.matricula, h.hora_inicio, h.salon, h.materia " +
                     "FROM horarios h JOIN usuarios u ON h.matricula = u.matricula";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            cacheHorarios.clear();
            while(rs.next()) {
                String huella = rs.getString("huella_template");
                String[] datos = { rs.getString("matricula"), rs.getString("hora_inicio"), rs.getString("salon"), rs.getString("materia") };
                cacheHorarios.computeIfAbsent(huella, k -> new java.util.ArrayList<>()).add(datos);
            }
        } catch (Exception e) { System.err.println("Error actualizando horarios: " + e.getMessage()); }
    }

    private void procesarAccesoDinamico(String huella) {
        if (!cacheHorarios.containsKey(huella)) {
            descargarCacheHorarios();
        }

        if (cacheHorarios.containsKey(huella)) {
            java.util.List<String[]> listaHorarios = cacheHorarios.get(huella);
            
            // Determinar la hora real de la petición
            LocalTime ahora = LocalTime.now();
            String bloqueActual = "06:30"; // Bloque por defecto de pruebas si está fuera del turno
            
            if (ahora.isAfter(LocalTime.of(6,30)) && ahora.isBefore(LocalTime.of(7,10))) bloqueActual = "06:30";
            else if (ahora.isAfter(LocalTime.of(7,10)) && ahora.isBefore(LocalTime.of(7,50))) bloqueActual = "07:10";
            else if (ahora.isAfter(LocalTime.of(7,50)) && ahora.isBefore(LocalTime.of(8,30))) bloqueActual = "07:50";
            else if (ahora.isAfter(LocalTime.of(8,30)) && ahora.isBefore(LocalTime.of(9,15))) bloqueActual = "08:30";

            String[] horarioEncontrado = null;
            for (String[] h : listaHorarios) {
                if (h[1].startsWith(bloqueActual)) {
                    horarioEncontrado = h;
                    break;
                }
            }

            if (horarioEncontrado != null) {
                String matricula = horarioEncontrado[0];
                String salonAsignado = horarioEncontrado[2];
                String materia = horarioEncontrado[3];

                if (salonAsignado.equalsIgnoreCase(SALON_ACTUAL)) {
                    mostrarResultado("ASISTENCIA REGISTRADA", "MAT: " + matricula + " (" + materia + ")", verdeExito);
                    subirAcceso(matricula, true, "Clase actual: " + materia + " en " + SALON_ACTUAL);
                } else if (salonAsignado.equals("LIBRE")) {
                    mostrarResultado("HORA LIBRE", "MAT: " + matricula, rojoError);
                    subirAcceso(matricula, false, "El alumno tiene hora libre en este bloque.");
                } else {
                    mostrarResultado("SALON EQUIVOCADO", "DEBE IR A: " + salonAsignado, rojoError);
                    subirAcceso(matricula, false, "Debió asistir a " + salonAsignado + " para la materia " + materia);
                }
            } else {
                mostrarResultado("SIN CLASE ASIGNADA", "HORA FUERA DE TURNO", rojoError);
            }
        } else {
            mostrarResultado("ACCESO DENEGADO", "HUELLA DESCONOCIDA", rojoError);
            subirAcceso(null, false, "Huella no parametrizada en el sistema.");
        }
    }

    private void mostrarResultado(String msg, String user, Color color) {
        lblStatus.setText(msg); lblStatus.setForeground(color);
        lblUser.setText(user); lblUser.setForeground(color);
        Timer t = new Timer(3500, e -> {
            lblStatus.setText("POR FAVOR COLOQUE SU HUELLA"); lblStatus.setForeground(azulPrincipal);
            lblUser.setText(""); lblUser.setForeground(azulPrincipal);
        });
        t.setRepeats(false); t.start();
    }

    private void subirAcceso(String mat, boolean ok, String mot) {
        String sql = "INSERT INTO registro_accesos (matricula, permitido, motivo_rechazo, salon_kiosko) VALUES (?, ?, ?, ?)";
        try (Connection con = ConexionSupabase.obtenerConexion(); PreparedStatement ps = con.prepareStatement(sql)) {
            if(mat == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, mat);
            ps.setBoolean(2, ok); ps.setString(3, mot); ps.setString(4, SALON_ACTUAL);
            ps.executeUpdate();
        } catch (Exception e) {}
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new MainKiosko().setVisible(true)); }
}