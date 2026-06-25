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
 * Ejecuta hilos independientes en segundo plano para emular el escaneo constante del hardware.
 */
public class MainKiosko extends JFrame {
    // Vinculación directa con el procesador lógico del Kiosko
    private KioskoController controlador = new KioskoController();
    
    // Parámetro dinámico modificado mediante archivo externo local
    private String SALON_ACTUAL = "AULA GENERAL"; 
    // Memoria caché para evitar sobrecargar a Supabase con peticiones concurrentes por segundo
    private Map<String, List<String[]>> cacheHorarios; 

    private JLabel lblStatus, lblUser, lblLogo, lblConsejo;

    public MainKiosko() {
        // 1. Cargamos el archivo físico 'config.properties' para saber qué aula es esta terminal
        cargarConfiguracionArchivoLocal();
        // 2. Bajamos los horarios escolares completos a la RAM de la Raspberry Pi
        cacheHorarios = controlador.descargarMatrizHorarios();

        // Inicialización básica del entorno de interfaz
        setTitle("Terminal Kiosko Escolar - Aula " + SALON_ACTUAL);
        setSize(800, 580); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Color.WHITE);
        setLayout(new BorderLayout(10, 10));

        // CONSTRUCCIÓN RENDERIZADA DEL LOGO INSTITUCIONAL
        lblLogo = new JLabel("", SwingConstants.CENTER);
        try {
            ImageIcon icono = new ImageIcon("logo.png");
            if (icono.getIconWidth() > 0) {
                // Algoritmo matemático para escalar proporcionalmente la imagen usando interpolación bicúbica de alta calidad
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
        
        // REQUISITO PREVENTIVO: Leyenda inferior fija para mitigar atascos y fallos del sensor físico
        lblConsejo = new JLabel("💡 Recomendación preventiva: Si el lector falla, limpia suavemente la superficie e intenta de nuevo.", SwingConstants.CENTER);
        lblConsejo.setFont(new Font("Segoe UI", Font.ITALIC, 13)); lblConsejo.setForeground(Color.DARK_GRAY);
        
        pnlInferior.add(lblStatus); pnlInferior.add(lblConsejo);
        add(pnlInferior, BorderLayout.SOUTH);

        // HILO DE EJECUCIÓN AUTÓNOMO PARA HARDWARE REAL (AS608):
        // Reemplaza el simulador para hacer un pooling continuo sobre el puerto serial sin congelar la GUI.
        new Thread(() -> {
            // Instanciamos el servicio apuntando al puerto de tu adaptador USB
            SensorHuellaService sensor = new SensorHuellaService("COM7");

            while (true) {
                try {
                    // 1. Intentar conectar al sensor si no está abierto
                    if (sensor.conectar()) {
                        lblConsejo.setText("💡 Lector AS608 en línea. Coloque su dedo sobre el sensor para pasar asistencia...");
                        lblConsejo.setForeground(new Color(39, 174, 96)); // Verde indicador de hardware OK

                        // 2. Pooling continuo esperando que se pose un dedo en el cristal
                        if (sensor.capturarFotoDedo()) {
                            
                            // 3. Si detecta el dedo, procesa sus características en el buffer 1
                            if (sensor.generarCaracteristicas(1)) {
                                
                                // 4. Descarga la matriz de bytes convertida a cadena Hexadecimal
                                String inputHuellaReal = sensor.descargarTemplateDesdeSensor();
                                
                                if (inputHuellaReal != null && !inputHuellaReal.isEmpty()) {
                                    System.out.println("Huella capturada en tiempo real: " + inputHuellaReal);
                                    
                                    // Enviamos el Hexadecimal real extraído al motor de reglas del Kiosko
                                    evaluarAccesoBiometrico(inputHuellaReal.trim());
                                }
                            }
                            
                            // Pausa preventiva para evitar lecturas duplicadas inmediatas del mismo dedo
                            Thread.sleep(3000);
                        }
                    } else {
                        lblConsejo.setText("❌ Error: No se detecta el lector de huellas en el puerto asignado.");
                        lblConsejo.setForeground(Color.RED);
                    }
                    
                    // Frecuencia de muestreo del sensor en reposo (250 milisegundos)
                    Thread.sleep(250);
                    
                } catch (Exception e) {
                    System.err.println("Error en el bucle del hardware biométrico: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Carga el archivo config.properties ubicado en la raíz del ejecutable.
     * Permite cambiar la identidad del salón en caliente sin compilar código de nuevo.
     */
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

    /**
     * ALGORITMO COMPUESTO DE EVALUACIÓN BIOMÉTRICA:
     * Busca la huella en RAM, consulta el bloque horario del reloj del sistema y valida el aula.
     */
    private void evaluarAccesoBiometrico(String huella) {
        // Mecanismo de contingencia: Si entra una huella que no está en caché, refresca de inmediato 
        // la memoria consultando a Supabase para capturar registros creados recientemente en la administración.
        if (!cacheHorarios.containsKey(huella)) {
            cacheHorarios = controlador.descargarMatrizHorarios();
        }

        if (cacheHorarios.containsKey(huella)) {
            List<String[]> agenda = cacheHorarios.get(huella);
            String bloqueActual = controlador.obtenerBloqueHorarioActual(); // Revisa la hora del sistema
            String[] bloqueEncontrado = null;

            // Buscamos de manera lineal si el usuario tiene una materia asignada en este bloque horario
            for (String[] h : agenda) {
                if (h[1].startsWith(bloqueActual)) { 
                    bloqueEncontrado = h; 
                    break; 
                }
            }

            if (bloqueEncontrado != null) {
                String mat = bloqueEncontrado[0]; 
                String salonAsignado = bloqueEncontrado[2]; 
                String materiaNombre = bloqueEncontrado[3];

                // REGLA A: El estudiante está en el aula correcta y a la hora correcta
                if (salonAsignado.equalsIgnoreCase(SALON_ACTUAL)) {
                    actualizarPantalla("ASISTENCIA REGISTRADA ✔", "MATRÍCULA: " + mat + " (" + materiaNombre + ")", new Color(46, 204, 113));
                    controlador.registrarLogAcceso(mat, true, "Clase: " + materiaNombre + " en " + SALON_ACTUAL, SALON_ACTUAL);
                
                // REGLA B: El estudiante tiene hora libre marcada por administración
                } else if (salonAsignado.equals("LIBRE")) {
                    actualizarPantalla("HORA LIBRE", "MATRÍCULA: " + mat, new Color(20, 80, 160));
                    controlador.registrarLogAcceso(mat, false, "El usuario cuenta con Hora Libre.", SALON_ACTUAL);
                
                // REGLA C: El alumno está intentando colarse o se equivocó de aula física
                } else {
                    actualizarPantalla("SALÓN EQUIVOCADO ❌", "DEBES IR A: " + salonAsignado, new Color(231, 76, 60));
                    controlador.registrarLogAcceso(mat, false, "Debió asistir a " + salonAsignado + " (" + materiaNombre + ")", SALON_ACTUAL);
                }
            } else {
                actualizarPantalla("FUERA DE TURNO", "SIN MATERIAS ASIGNADAS AHORA", new Color(231, 76, 60));
            }
        } else {
            // REGLA D: Huella totalmente inexistente en la base de datos (Intruso / Desconocido)
            actualizarPantalla("ACCESO DENEGADO ❌", "HUELLA DESCONOCIDA", new Color(231, 76, 60));
            controlador.registrarLogAcceso(null, false, "Intento con huella no registrada.", SALON_ACTUAL);
        }
    }

    // LA ASISTENCIA EN PANTALLA
    private void actualizarPantalla(String status, String usuario, Color colorContexto) {
        lblStatus.setText(status); 
        lblStatus.setForeground(colorContexto);
        lblUser.setText(usuario); 
        lblUser.setForeground(colorContexto);
        
        Timer t = new Timer(3500, e -> {
            // El estado superior vuelve a invitar a poner la huella
            lblStatus.setText("POR FAVOR COLOQUE SU HUELLA"); 
            lblStatus.setForeground(new Color(20, 80, 160));
            
            // CONSERVACIÓN EN TIEMPO REAL: No borramos el texto del alumno, 
            // solo lo pintamos en gris claro para que el maestro vea quién fue el último en checar.
            lblUser.setForeground(Color.LIGHT_GRAY); 
        });
        t.setRepeats(false);
        t.start();
    }

    public static void main(String[] args) { 
        SwingUtilities.invokeLater(() -> new MainKiosko().setVisible(true)); 
    }
}