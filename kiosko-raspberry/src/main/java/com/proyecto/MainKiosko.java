package com.proyecto;

import javax.swing.*;
import java.awt.*;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * VISTA DEL KIOSKO (GUI DE PANTALLA DE AULA)
 * Ventana diseñada para terminales empotradas (ej. Raspberry Pi con pantalla táctil).
 */
public class MainKiosko extends JFrame {
    private KioskoController controlador = new KioskoController();
    private String SALON_ACTUAL = "AULA GENERAL"; 
    private Map<String, List<String[]>> cacheHorarios; 

    private JLabel lblStatus, lblUser, lblLogo, lblConsejo;
    private SensorHuellaService sensor; 

    public MainKiosko() {
        cargarConfiguracionArchivoLocal();
        cacheHorarios = controlador.descargarMatrizHorarios();
        System.out.println("[KIOSKO] Caché cargada desde Supabase con " + (cacheHorarios != null ? cacheHorarios.size() : 0) + " registros.");

        setTitle("Terminal Kiosko Escolar - Aula " + SALON_ACTUAL);
        setSize(800, 580); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);
        setLayout(new BorderLayout(10, 10));

        // LOGO INSTITUCIONAL
        lblLogo = new JLabel("", SwingConstants.CENTER);
        try {
            ImageIcon icono = new ImageIcon("logo.png");
            if (icono.getIconWidth() > 0) {
                int nAncho = 260; int nAlto = (icono.getIconHeight() * nAncho) / icono.getIconWidth();
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(nAncho, nAlto, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(icono.getImage(), 0, 0, nAncho, nAlto, null); g2.dispose();
                lblLogo.setIcon(new ImageIcon(img));
            }
        } catch (Exception e) { lblLogo.setText("[ LOGO UNIVERSIDAD ]"); }
        add(lblLogo, BorderLayout.NORTH);

        // Paneles Informativos Centrales
        JPanel pnlCentro = new JPanel(new GridLayout(2, 1)); pnlCentro.setOpaque(false);
        JLabel lblMeta = new JLabel("Chequeo de Asistencia - " + SALON_ACTUAL, SwingConstants.CENTER);
        lblMeta.setFont(new Font("Segoe UI", Font.PLAIN, 18)); lblMeta.setForeground(Color.GRAY);
        lblUser = new JLabel("", SwingConstants.CENTER);
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblUser.setForeground(new Color(20, 80, 160));
        pnlCentro.add(lblMeta); pnlCentro.add(lblUser);
        add(pnlCentro, BorderLayout.CENTER);

        // Sección Inferior de Mensajería y Alertas
        JPanel pnlInferior = new JPanel(new GridLayout(2, 1, 5, 5)); pnlInferior.setOpaque(false);
        lblStatus = new JLabel("POR FAVOR COLOQUE SU HUELLA", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblStatus.setForeground(new Color(20, 80, 160));
        
        lblConsejo = new JLabel("💡 Recomendación preventiva: Si el lector falla, limpia suavemente la superficie e intenta de nuevo.", SwingConstants.CENTER);
        lblConsejo.setFont(new Font("Segoe UI", Font.ITALIC, 13)); lblConsejo.setForeground(Color.DARK_GRAY);
        
        pnlInferior.add(lblStatus); pnlInferior.add(lblConsejo);
        add(pnlInferior, BorderLayout.SOUTH);

        // HILO DE RENDIMIENTO ESTABLE
        new Thread(() -> {
            sensor = new SensorHuellaService("COM7");

            while (true) {
                try {
                    if (sensor.conectar()) {
                        lblConsejo.setText("💡 Lector AS608 en línea. Coloque su dedo sobre el sensor para pasar asistencia...");
                        lblConsejo.setForeground(new Color(39, 174, 96)); 

                        if (sensor.capturarFotoDedo()) {
                            if (sensor.generarCaracteristicas(1)) {
                                SwingUtilities.invokeLater(() -> {
                                    lblStatus.setText("PROCESANDO HUELLA...");
                                    lblStatus.setForeground(new Color(211, 84, 0));
                                });
                                
                                evaluarAccesoBiometrico();
                            }
                            Thread.sleep(3500); 
                        }
                    } else {
                        lblConsejo.setText("❌ Error: No se detecta el lector de huellas en el puerto asignado.");
                        lblConsejo.setForeground(Color.RED);
                    }
                    Thread.sleep(250); 
                } catch (Exception e) {
                    System.err.println("Error crítico en el hardware: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void cargarConfiguracionArchivoLocal() {
        Properties prop = new Properties();
        try (FileInputStream input = new FileInputStream("config.properties")) {
            prop.load(input);
            String salonProp = prop.getProperty("salon");
            if (salonProp != null) this.SALON_ACTUAL = salonProp.trim().toUpperCase();
        } catch (Exception ex) { 
            System.out.println("No se detectó config.properties. Usando Aula General por defecto."); 
        }
    }

    private void evaluarAccesoBiometrico() {
        String huellaHolograficaMatch = null;
        List<String[]> agendaAsignada = null;
        
        System.out.println("[KIOSKO] Buscando correspondencia de cadena en caché...");

        if (cacheHorarios == null || cacheHorarios.isEmpty()) {
            actualizarPantalla("ACCESO DENEGADO ", "SISTEMA SIN DATOS LOCALES", new Color(231, 76, 60));
            return;
        }

        for (Map.Entry<String, List<String[]>> entrada : cacheHorarios.entrySet()) {
            String hexBase = entrada.getKey();
            
            if (hexBase == null || hexBase.trim().length() < 10) continue; 

            try {
                if (sensor.verificarDedoContraSupabase(hexBase.trim())) {
                    huellaHolograficaMatch = hexBase;
                    agendaAsignada = entrada.getValue();
                    break; 
                }
            } catch (Exception e) {
                System.err.println("[ERROR KIOSKO] Fallo al contrastar fila: " + e.getMessage());
            }
        }

        // EVALUACIÓN DE HORARIOS ESTRICTA
        if (huellaHolograficaMatch != null && agendaAsignada != null && !agendaAsignada.isEmpty()) {
            String bloqueActual = controlador.obtenerBloqueHorarioActual(); 
            String matInercial = agendaAsignada.get(0)[0]; // Matrícula del usuario identificado
            
            // 1. REGLA DE BLOQUEO: Si el reloj está fuera del horario escolar
            if (bloqueActual.equals("FUERA_DE_HORARIO")) {
                actualizarPantalla("FUERA DE TURNO", "NO HAY CLASES A ESTA HORA", new Color(231, 76, 60));
                controlador.registrarLogAcceso(matInercial, false, "Intento de acceso fuera del horario escolar.", SALON_ACTUAL);
                return;
            }

            // 2. BUSCAR MATERIA EN EL HORARIO ACTUAL
            String[] bloqueEncontrado = null;
            for (String[] h : agendaAsignada) {
                if (h[1].startsWith(bloqueActual)) { 
                    bloqueEncontrado = h; 
                    break; 
                }
            }

            // 3. DECISIÓN FINAL DE ACCESO
            if (bloqueEncontrado != null) {
                String salonAsignado = bloqueEncontrado[2]; 
                String materiaNombre = bloqueEncontrado[3];

                if (salonAsignado.equalsIgnoreCase(SALON_ACTUAL)) {
                    actualizarPantalla("ASISTENCIA REGISTRADA ", "MATRÍCULA: " + matInercial + " (" + materiaNombre + ")", new Color(46, 204, 113));
                    controlador.registrarLogAcceso(matInercial, true, "Clase: " + materiaNombre + " en " + SALON_ACTUAL, SALON_ACTUAL);
                } else if (salonAsignado.equals("LIBRE")) {
                    actualizarPantalla("HORA LIBRE", "MATRÍCULA: " + matInercial, new Color(20, 80, 160));
                    controlador.registrarLogAcceso(matInercial, false, "El usuario cuenta con Hora Libre.", SALON_ACTUAL);
                } else {
                    actualizarPantalla("SALÓN EQUIVOCADO ", "DEBES IR A: " + salonAsignado, new Color(231, 76, 60));
                    controlador.registrarLogAcceso(matInercial, false, "Debió asistir a " + salonAsignado, SALON_ACTUAL);
                }
            } else {
                actualizarPantalla("FUERA DE TURNO", "SIN MATERIAS ASIGNADAS AHORA", new Color(231, 76, 60));
                controlador.registrarLogAcceso(matInercial, false, "El usuario no tiene clases registradas en este bloque.", SALON_ACTUAL);
            }
        } else {
            actualizarPantalla("ACCESO DENEGADO ", "HUELLA DESCONOCIDA", new Color(231, 76, 60));
            controlador.registrarLogAcceso(null, false, "Intento con huella no registrada o rechazada.", SALON_ACTUAL);
        }
    }

    private void actualizarPantalla(String status, String usuario, Color colorContexto) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(status); 
            lblStatus.setForeground(colorContexto);
            lblUser.setText(usuario); 
            lblUser.setForeground(colorContexto);
        });
        
        Timer t = new Timer(3500, e -> {
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("POR FAVOR COLOQUE SU HUELLA"); 
                lblStatus.setForeground(new Color(20, 80, 160));
                lblUser.setText("");
                lblUser.setForeground(Color.LIGHT_GRAY); 
            });
        });
        t.setRepeats(false);
        t.start();
    }

    public static void main(String[] args) { 
        SwingUtilities.invokeLater(() -> new MainKiosko().setVisible(true)); 
    }
}