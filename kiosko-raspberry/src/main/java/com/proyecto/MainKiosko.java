package com.proyecto;

import javax.swing.*;
import java.awt.*;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * VISTA DEL KIOSKO (GUI DE PANTALLA DE AULA)
 * Capa de presentación (UI) diseñada para ejecutarse en terminales físicas empotradas 
 * (ej. Raspberry Pi con pantalla táctil) ubicadas en los accesos de las aulas.
 * Funciona de manera desacoplada dentro del patrón de arquitectura MVC, reaccionando
 * a eventos de hardware en segundo plano.
 */
public class MainKiosko extends JFrame {
    private KioskoController controlador = new KioskoController();
    private String SALON_ACTUAL = "AULA GENERAL"; 
    private Map<String, List<String[]>> cacheHorarios; 

    private JLabel lblStatus, lblUser, lblLogo, lblConsejo;
    private SensorHuellaService sensor; 

    /**
     * Inicializa la interfaz de usuario del Kiosko escolar.
     * Carga la configuración local del entorno, descarga la matriz de horarios hacia
     * la memoria RAM y lanza el hilo daemon dedicado a la escucha activa del hardware biométrico.
     */
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
        // Implementación de renderizado bidimensional avanzado para redimensionar el logotipo
        // y prevenir la pérdida de resolución o pixelación en pantallas táctiles de alta densidad.
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

        // HILO DE RENDIMIENTO ESTABLE (BACKGROUND THREAD DE SONDEO HARWARE)
        // Se ejecuta un bucle infinito en un hilo secundario para evitar congelar el Event Dispatch 
        // Thread (EDT) de Swing durante las lecturas físicas síncronas del puerto serial COM.
        // HILO DE RENDIMIENTO ESTABLE (BACKGROUND THREAD DE SONDEO HARWARE)
        // Se ejecuta un bucle infinito en un hilo secundario para evitar congelar el Event Dispatch 
        // Thread (EDT) de Swing durante las lecturas físicas síncronas del puerto serial COM.
        new Thread(() -> {
            // RUTA DE LINUX PARA LA RASPBERRY PI
            sensor = new SensorHuellaService("/dev/ttyUSB0");

            while (true) {
                try {
                    if (sensor.conectar()) {
                        lblConsejo.setText("💡 Lector AS608 en línea. Coloque su dedo sobre el sensor para pasar asistencia...");
                        lblConsejo.setForeground(new Color(39, 174, 96)); 

                        // Paso 1: Monitoreo y captura de la imagen física del dedo
                        if (sensor.capturarFotoDedo()) {
                            // Paso 2: Generación del molde matemático de rasgos en el búfer temporal 1 del chip
                            if (sensor.generarCaracteristicas(1)) {
                                // Delegación segura al EDT para actualizar etiquetas textuales durante el proceso
                                SwingUtilities.invokeLater(() -> {
                                    lblStatus.setText("PROCESANDO HUELLA...");
                                    lblStatus.setForeground(new Color(211, 84, 0));
                                });
                                
                                // Paso 3: Disparo del motor de contraste biométrico y validación horaria
                                evaluarAccesoBiometrico();
                            }
                            // Retraso de seguridad (Cool down) para evitar registros duplicados por el mismo marcaje
                            Thread.sleep(3500); 
                        }
                    } else {
                        lblConsejo.setText(" Error: No se detecta el lector de huellas en el puerto asignado.");
                        lblConsejo.setForeground(Color.RED);
                    }
                    Thread.sleep(250); // Frecuencia de muestreo ante estados inactivos
                } catch (Exception e) {
                    System.err.println("Error crítico en el hardware: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Carga las variables dinámicas del entorno desde un archivo local estructurado.
     * Permite modificar el identificador físico de la terminal en el campus sin necesidad
     * de recompilar o modificar el código fuente de la aplicación.
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
     * Motor de Reglas Biométricas e Institucionales.
     * Itera sobre el mapa de memoria RAM transmitiendo las cadenas hexadecimales almacenadas
     * hacia el segundo búfer del sensor físico para ejecutar comparaciones 1:1 por hardware.
     * Tras la validación dactilar, cruza el resultado con el reloj del sistema y las restricciones
     * de aula para determinar el veredicto final.
     */
    private void evaluarAccesoBiometrico() {
        String huellaHolograficaMatch = null;
        List<String[]> agendaAsignada = null;
        
        System.out.println("[KIOSKO] Buscando correspondencia de cadena en caché...");

        if (cacheHorarios == null || cacheHorarios.isEmpty()) {
            actualizarPantalla("ACCESO DENEGADO ", "SISTEMA SIN DATOS LOCALES", new Color(231, 76, 60));
            return;
        }

        // Bucle de búsqueda en caché: Transmite las plantillas de Supabase hacia el chip AS608
        for (Map.Entry<String, List<String[]>> entrada : cacheHorarios.entrySet()) {
            String hexBase = entrada.getKey();
            
            // Filtro defensivo: Ignora cadenas incompletas o nulas para mitigar excepciones seriales
            if (hexBase == null || hexBase.trim().length() < 10) continue; 

            try {
                // Ejecución directa en el chip físico: Contrapone el dedo puesto (Búfer 1) contra la cadena (Búfer 2)
                if (sensor.verificarDedoContraSupabase(hexBase.trim())) {
                    huellaHolograficaMatch = hexBase;
                    agendaAsignada = entrada.getValue();
                    break; // Coincidencia exitosa. Se rompe el ciclo para optimizar procesamiento
                }
            } catch (Exception e) {
                System.err.println("[ERROR KIOSKO] Fallo al contrastar fila: " + e.getMessage());
            }
        }

        // EVALUACIÓN DE REGLAS ESCOLARES Y HORARIOS ESTRICTOS
        if (huellaHolograficaMatch != null && agendaAsignada != null && !agendaAsignada.isEmpty()) {
            String bloqueActual = controlador.obtenerBloqueHorarioActual(); 
            String matInercial = agendaAsignada.get(0)[0]; // Extracción de la matrícula ligada al molde dactilar
            
            // 1. REGLA BARRERA: Rechazo automático si el reloj se encuentra en períodos inhábiles
            if (bloqueActual.equals("FUERA_DE_HORARIO")) {
                actualizarPantalla("FUERA DE TURNO", "NO HAY CLASES A ESTA HORA", new Color(231, 76, 60));
                controlador.registrarLogAcceso(matInercial, false, "Intento de acceso fuera del horario escolar.", SALON_ACTUAL);
                return;
            }

            // 2. FILTRADO DE LA CLASE ASIGNADA PARA EL BLOQUE EN CURSO
            String[] bloqueEncontrado = null;
            for (String[] h : agendaAsignada) {
                if (h[1].startsWith(bloqueActual)) { 
                    bloqueEncontrado = h; 
                    break; 
                }
            }

            // 3. MATRIZ DE DECISIONES DE ACCESO
            if (bloqueEncontrado != null) {
                String salonAsignado = bloqueEncontrado[2]; 
                String materiaNombre = bloqueEncontrado[3];

                if (salonAsignado.equalsIgnoreCase(SALON_ACTUAL)) {
                    // Caso A: Ubicación y hora correctas. Registro de asistencia válido.
                    actualizarPantalla("ASISTENCIA REGISTRADA ", "MATRÍCULA: " + matInercial + " (" + materiaNombre + ")", new Color(46, 204, 113));
                    controlador.registrarLogAcceso(matInercial, true, "Clase: " + materiaNombre + " en " + SALON_ACTUAL, SALON_ACTUAL);
                } else if (salonAsignado.equals("LIBRE")) {
                    // Caso B: El alumno tiene una hora libre configurada por control escolar.
                    actualizarPantalla("HORA LIBRE", "MATRÍCULA: " + matInercial, new Color(20, 80, 160));
                    controlador.registrarLogAcceso(matInercial, false, "El usuario cuenta con Hora Libre.", SALON_ACTUAL);
                } else {
                    // Caso C: El alumno está en el aula equivocada. Se le notifica la locación correcta.
                    actualizarPantalla("SALÓN EQUIVOCADO ", "DEBES IR A: " + salonAsignado, new Color(231, 76, 60));
                    controlador.registrarLogAcceso(matInercial, false, "Debió asistir a " + salonAsignado, SALON_ACTUAL);
                }
            } else {
                // Caso D: El alumno no tiene registrada carga académica para este período de tiempo.
                actualizarPantalla("FUERA DE TURNO", "SIN MATERIAS ASIGNADAS AHORA", new Color(231, 76, 60));
                controlador.registrarLogAcceso(matInercial, false, "El usuario no tiene clases registradas en este bloque.", SALON_ACTUAL);
            }
        } else {
            // Caso E: Falla en el reconocimiento biométrico. El template no existe en la base de datos centralizada.
            actualizarPantalla("ACCESO DENEGADO ", "HUELLA DESCONOCIDA", new Color(231, 76, 60));
            controlador.registrarLogAcceso(null, false, "Intento con huella no registrada o rechazada.", SALON_ACTUAL);
        }
    }

    /**
     * Coordina de forma asíncrona los refrescos gráficos sobre los contenedores Swing.
     * Modifica las etiquetas mediante invokeLater para mantener el principio de aislamiento de hilos
     * e instancia un temporizador de un solo uso para reestablecer la vista al estado base.
     *
     * @param status Leyenda principal a desplegar en el contenedor de alerta.
     * @param usuario Datos informativos adicionales sobre la matrícula procesada.
     * @param colorContexto Tonalidad cromática adaptada a la naturaleza de la respuesta (Éxito/Falla/Aviso).
     */
    private void actualizarPantalla(String status, String usuario, Color colorContexto) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(status); 
            lblStatus.setForeground(colorContexto);
            lblUser.setText(usuario); 
            lblUser.setForeground(colorContexto);
        });
        
        // Temporizador de restauración automática (Duración establecida en 3.5 segundos)
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

    /**
     * Punto de entrada ejecutable de la terminal empotrada.
     *
     * @param args Argumentos base de la máquina de ejecución.
     */
    public static void main(String[] args) { 
        SwingUtilities.invokeLater(() -> new MainKiosko().setVisible(true)); 
    }
}