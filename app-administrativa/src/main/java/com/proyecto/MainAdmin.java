package com.proyecto;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * VISTA ADMINISTRATIVA PRINCIPAL - INTEGRACIÓN BIOMÉTRICA AS608 Y SUPABASE
 * Capa de presentación (UI) de la Arquitectura Decoupled MVC.
 * Responsabilidades:
 * - Proveer la interfaz gráfica para el enrolamiento, gestión y auditoría escolar.
 * - Gestionar la concurrencia delegando operaciones de hardware (AS608) y red (Supabase) 
 *   a hilos secundarios (Background Threads) para evitar el bloqueo del Event Dispatch Thread (EDT).
 * - Renderizar en tiempo real el tráfico de acceso mediante un monitor de sondeo (Polling).
 */
public class MainAdmin extends JFrame {
    
    private AdminController controlador = new AdminController();

    // Paleta de Diseño Institucional Unificada
    // Centraliza los colores corporativos para facilitar futuras actualizaciones de UI/UX.
    private static final Color AZUL_OBSCURO = new Color(21, 67, 96);   
    private static final Color AZUL_MEDIO = new Color(41, 128, 185);   
    private static final Color FONDO_GRIS_LIGERO = new Color(245, 247, 250); 
    private static final Color AZUL_SELECCION = new Color(235, 245, 251);   
    private static final Color ROJO_ERROR = new Color(231, 76, 60);
    private static final Color VERDE_EXITO = new Color(39, 174, 96);
    private static final Color VERDE_EXCEL = new Color(33, 115, 70);
    
    private static final Font FUENTE_SANS = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FUENTE_NEGRITA = new Font("Segoe UI", Font.BOLD, 13);

    // Componentes del Formulario de Registro
    private JTextField txtMatricula, txtNombre, txtCarrera, txtHuella;
    private JComboBox<String> cbRol;
    private JTextField txtSalon1, txtMateria1, txtSalon2, txtMateria2, txtSalon3, txtMateria3, txtSalon4, txtMateria4;

    // Estructuras de Datos Visuales para el Monitor en Tiempo Real
    private JPanel pnlMonitorContenedor;
    private JTable tablaMonitor;
    private DefaultTableModel modeloMonitor;
    private JScrollPane scrollMonitor;

    // Componentes de visualización y filtrado de datos maestros
    private DefaultTableModel modeloAlumnos, modeloHorarioAlumno, modeloFiltroSalon;
    private DefaultTableModel modeloMaestros, modeloHistorialMaestros; 
    private JTable tablaAlumnos, tablaMaestros, tablaFiltro;
    private JTextField txtBuscarSalon, txtBuscarFecha, txtFechaMaestros;
    private JComboBox<String> cbBuscarHora;

    /**
     * Constructor principal de la interfaz administrativa.
     * Inicializa los contenedores Swing, aplica las configuraciones de Layout
     * y arranca el demonio de monitoreo en tiempo real.
     */
    public MainAdmin() {
        setTitle("Panel Escolar Administrativo - Arquitectura Decoupled MVC");
        setSize(1350, 780); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); 
        getContentPane().setBackground(FONDO_GRIS_LIGERO);

        JTabbedPane panelPestañas = new JTabbedPane();
        panelPestañas.setFont(FUENTE_NEGRITA);

        armarPestañaRegistro(panelPestañas);
        armarPestañaBaseAlumnos(panelPestañas);
        armarPestañaBaseMaestros(panelPestañas); 
        armarPestañaBuscadorSalones(panelPestañas);

        add(panelPestañas);

        // Hilo de Sondeo (Polling) para el Monitor de Accesos.
        // Se establece una latencia de 5000 ms (5 segundos) para balancear la frescura 
        // de los datos visuales sin saturar el Connection Pool de la base de datos Supabase.
        Timer timerIngresoTiempoReal = new Timer(5000, e -> refrescarHistorialMonitorEnVivo());
        timerIngresoTiempoReal.setRepeats(true);
        timerIngresoTiempoReal.start();
    }

    /**
     * Tarea periódica invocada por el Timer del sistema.
     * Recupera la ráfaga de transacciones más recientes y actualiza la tabla lateral.
     * Implementa un diseño de retroalimentación visual (Bordes rojos) si falla la conexión.
     */
    private void refrescarHistorialMonitorEnVivo() {
        try {
            List<Object[]> logsRecientes = controlador.obtenerUltimos15Ingresos();
            if (logsRecientes != null) {
                modeloMonitor.setRowCount(0);
                for (Object[] registro : logsRecientes) {
                    modeloMonitor.addRow(new Object[]{
                        registro[0], 
                        registro[3], 
                        registro[4], 
                        registro[5]  
                    });
                }
                
                // Restablece el borde a su estado operativo (Azul)
                pnlMonitorContenedor.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(AZUL_MEDIO, 1), " MONITOR DE ACCESOS EN LÍNEA ", 
                    0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));
            }
        } catch (Exception ex) {
            System.err.println("=== 🚨 FALLA DE CONEXIÓN EN MONITOR Escolar 🚨 ===");
            ex.printStackTrace();
            
            // Alerta visual de hardware/red caído
            pnlMonitorContenedor.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ROJO_ERROR, 1), " MONITOR - ERROR DE CONEXIÓN ", 
                0, 0, FUENTE_NEGRITA, ROJO_ERROR));
        }
    }

    /**
     * Ensambla la pestaña de enrolamiento utilizando GridBagLayout.
     * Este Layout avanzado permite distribuir los campos de texto y botones en una cuadrícula
     * dinámica que mantiene sus proporciones independientemente de la resolución del monitor.
     */
    private void armarPestañaRegistro(JTabbedPane tabs) {
        JPanel panelPrincipal = new JPanel(new GridBagLayout());
        panelPrincipal.setBackground(FONDO_GRIS_LIGERO);
        GridBagConstraints gbcMaster = new GridBagConstraints();

        JPanel pnlIzquierdo = new JPanel(new GridBagLayout());
        pnlIzquierdo.setBackground(Color.WHITE);
        pnlIzquierdo.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        GridBagConstraints gbcForm = new GridBagConstraints();
        gbcForm.insets = new Insets(5, 5, 5, 5);
        gbcForm.fill = GridBagConstraints.HORIZONTAL;

        JPanel pnlDatos = new JPanel(new GridBagLayout());
        pnlDatos.setBackground(Color.WHITE);
        pnlDatos.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(210, 215, 225)), " 1. Datos del Usuario ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));
        
        GridBagConstraints gbcD = new GridBagConstraints();
        gbcD.insets = new Insets(6, 8, 6, 8); gbcD.fill = GridBagConstraints.HORIZONTAL;

        gbcD.gridx = 0; gbcD.gridy = 0; pnlDatos.add(new JLabel("Matrícula:"), gbcD);
        txtMatricula = new JTextField(15); gbcD.gridx = 1; pnlDatos.add(txtMatricula, gbcD);
        gbcD.gridx = 0; gbcD.gridy = 1; pnlDatos.add(new JLabel("Nombre:"), gbcD);
        txtNombre = new JTextField(15); gbcD.gridx = 1; pnlDatos.add(txtNombre, gbcD);
        gbcD.gridx = 0; gbcD.gridy = 2; pnlDatos.add(new JLabel("Rol:"), gbcD);
        cbRol = new JComboBox<>(new String[]{"ALUMNO", "MAESTRO"}); gbcD.gridx = 1; pnlDatos.add(cbRol, gbcD);
        gbcD.gridx = 0; gbcD.gridy = 3; pnlDatos.add(new JLabel("Carrera/Depto:"), gbcD);
        txtCarrera = new JTextField(15); gbcD.gridx = 1; pnlDatos.add(txtCarrera, gbcD);
        
        gbcD.gridx = 0; gbcD.gridy = 4; pnlDatos.add(new JLabel("Huella Hex:"), gbcD);
        
        JPanel pnlHuellaFisica = new JPanel(new BorderLayout(5, 0));
        pnlHuellaFisica.setOpaque(false);
        
        txtHuella = new JTextField(10);
        txtHuella.setEditable(false);
        txtHuella.setBackground(new Color(240, 244, 248));
        
        JButton btnEscanearHuella = new JButton("Escanear");
        darEstiloBoton(btnEscanearHuella, AZUL_MEDIO);
        btnEscanearHuella.setMargin(new Insets(2, 8, 2, 8));
        
        pnlHuellaFisica.add(txtHuella, BorderLayout.CENTER);
        pnlHuellaFisica.add(btnEscanearHuella, BorderLayout.EAST);
        
        gbcD.gridx = 1; pnlDatos.add(pnlHuellaFisica, gbcD);

        gbcForm.gridx = 0; gbcForm.gridy = 0; gbcForm.weightx = 1.0;
        pnlIzquierdo.add(pnlDatos, gbcForm);

        // Sub-panel de captura de horarios
        JPanel pnlMatriz = new JPanel(new GridBagLayout());
        pnlMatriz.setBackground(Color.WHITE);
        pnlMatriz.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(210, 215, 225)), " 2. Carga del Turno Escolar ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));
        
        GridBagConstraints gbcM = new GridBagConstraints();
        gbcM.insets = new Insets(4, 4, 4, 4); gbcM.fill = GridBagConstraints.HORIZONTAL;

        gbcM.gridy = 0;
        gbcM.gridx = 0; pnlMatriz.add(new JLabel("Bloque", SwingConstants.CENTER), gbcM);
        gbcM.gridx = 1; gbcM.weightx = 1.0; pnlMatriz.add(new JLabel("Salón", SwingConstants.CENTER), gbcM);
        gbcM.gridx = 2; gbcM.weightx = 1.0; pnlMatriz.add(new JLabel("Materia", SwingConstants.CENTER), gbcM);

        gbcM.gridy = 1; gbcM.weightx = 0; gbcM.gridx = 0; pnlMatriz.add(new JLabel("06:30 - 07:10 "), gbcM);
        txtSalon1 = new JTextField(); gbcM.gridx = 1; gbcM.weightx = 1.0; pnlMatriz.add(txtSalon1, gbcM);
        txtMateria1 = new JTextField(); gbcM.gridx = 2; pnlMatriz.add(txtMateria1, gbcM);

        gbcM.gridy = 2; gbcM.weightx = 0; gbcM.gridx = 0; pnlMatriz.add(new JLabel("07:10 - 07:50 "), gbcM);
        txtSalon2 = new JTextField(); gbcM.gridx = 1; gbcM.weightx = 1.0; pnlMatriz.add(txtSalon2, gbcM);
        txtMateria2 = new JTextField(); gbcM.gridx = 2; pnlMatriz.add(txtMateria2, gbcM);

        gbcM.gridy = 3; gbcM.weightx = 0; gbcM.gridx = 0; pnlMatriz.add(new JLabel("07:50 - 08:30 "), gbcM);
        txtSalon3 = new JTextField(); gbcM.gridx = 1; gbcM.weightx = 1.0; pnlMatriz.add(txtSalon3, gbcM);
        txtMateria3 = new JTextField(); gbcM.gridx = 2; pnlMatriz.add(txtMateria3, gbcM);

        gbcM.gridy = 4; gbcM.weightx = 0; gbcM.gridx = 0; pnlMatriz.add(new JLabel("08:30 - 09:10 "), gbcM);
        txtSalon4 = new JTextField(); gbcM.gridx = 1; gbcM.weightx = 1.0; pnlMatriz.add(txtSalon4, gbcM);
        txtMateria4 = new JTextField(); gbcM.gridx = 2; pnlMatriz.add(txtMateria4, gbcM);

        gbcForm.gridy = 1; gbcForm.insets = new Insets(10, 5, 5, 5);
        pnlIzquierdo.add(pnlMatriz, gbcForm);

        JButton btnGuardar = new JButton("GUARDAR");
        darEstiloBoton(btnGuardar, AZUL_MEDIO);
        
        gbcForm.gridy = 2;
        gbcForm.fill = GridBagConstraints.NONE; 
        gbcForm.anchor = GridBagConstraints.CENTER;
        gbcForm.insets = new Insets(20, 5, 5, 5);
        pnlIzquierdo.add(btnGuardar, gbcForm);

        gbcMaster.gridx = 0; gbcMaster.gridy = 0;
        gbcMaster.weightx = 0.45; gbcMaster.weighty = 1.0;
        gbcMaster.fill = GridBagConstraints.BOTH;
        gbcMaster.insets = new Insets(10, 10, 10, 5);
        panelPrincipal.add(pnlIzquierdo, gbcMaster);

        // Estructuración del Monitor de Accesos (Lado derecho de la pantalla)
        pnlMonitorContenedor = new JPanel(new BorderLayout());
        pnlMonitorContenedor.setBackground(Color.WHITE);
        pnlMonitorContenedor.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(AZUL_MEDIO, 1), " MONITOR DE ACCESOS EN LÍNEA ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));

        String[] columnasMonitor = {"Matrícula", "Hora", "Estatus", "Motivo de Acceso"};
        modeloMonitor = new DefaultTableModel(columnasMonitor, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };

        tablaMonitor = new JTable(modeloMonitor);
        estilizarTablaGeneral(tablaMonitor);

        // Modificador gráfico de celdas: Aplica colores condicionales (Rojo/Verde) a la columna Estatus
        tablaMonitor.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, val, isSel, hasFoc, row, col);
                c.setFont(FUENTE_SANS);
                if (col == 2 && val != null) {
                    if (val.toString().equals("OK")) {
                        c.setForeground(VERDE_EXITO);
                        c.setFont(FUENTE_NEGRITA);
                    } else {
                        c.setForeground(ROJO_ERROR);
                        c.setFont(FUENTE_NEGRITA);
                    }
                } else {
                    c.setForeground(Color.DARK_GRAY);
                }
                if (!isSel) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                }
                return c;
            }
        });

        scrollMonitor = new JScrollPane(tablaMonitor);
        scrollMonitor.getViewport().setBackground(Color.WHITE);
        scrollMonitor.setBorder(BorderFactory.createLineBorder(new Color(215, 220, 230)));
        pnlMonitorContenedor.add(scrollMonitor, BorderLayout.CENTER);

        gbcMaster.gridx = 1;
        gbcMaster.weightx = 0.55;
        gbcMaster.insets = new Insets(10, 5, 10, 10);
        panelPrincipal.add(pnlMonitorContenedor, gbcMaster);

        // Bindings de eventos
        btnEscanearHuella.addActionListener(e -> ejecutarEnrolamientoEnSegundoPlano(btnEscanearHuella));
        btnGuardar.addActionListener(e -> accionGuardar());
        tabs.addTab(" Registro", panelPrincipal);
    }

    private void armarPestañaBaseAlumnos(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(FONDO_GRIS_LIGERO);

        modeloAlumnos = new DefaultTableModel(new String[]{"Matrícula", "Nombre", "Carrera", "Huella"}, 0);
        tablaAlumnos = new JTable(modeloAlumnos);
        estilizarTablaGeneral(tablaAlumnos);
        JScrollPane spAlu = new JScrollPane(tablaAlumnos);
        spAlu.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(200,205,215)), " Alumnos Guardados ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));

        modeloHorarioAlumno = new DefaultTableModel(new String[]{"Bloque Horario", "Salón Asignado", "Materia / Clase"}, 0);
        JTable tabHor = new JTable(modeloHorarioAlumno);
        estilizarTablaGeneral(tabHor);
        JScrollPane spHor = new JScrollPane(tabHor);
        spHor.setPreferredSize(new Dimension(1100, 180));
        spHor.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(200,205,215)), " Detalle de Materias Asignadas ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));

        JPanel pnlCentro = new JPanel(new BorderLayout(10, 10)); pnlCentro.setOpaque(false);
        pnlCentro.add(spAlu, BorderLayout.CENTER); pnlCentro.add(spHor, BorderLayout.SOUTH);
        panel.add(pnlCentro, BorderLayout.CENTER);

        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT)); pnlBotones.setBackground(FONDO_GRIS_LIGERO);
        JButton btnRefrescar = new JButton("REFRESCAR TABLA"); darEstiloBoton(btnRefrescar, AZUL_MEDIO);
        JButton btnEliminar = new JButton("ELIMINAR ALUMNO"); darEstiloBoton(btnEliminar, ROJO_ERROR);
        pnlBotones.add(btnRefrescar); pnlBotones.add(btnEliminar); panel.add(pnlBotones, BorderLayout.SOUTH);

        // Listener reactivo: Actualiza la tabla inferior al seleccionar un alumno
        tablaAlumnos.getSelectionModel().addListSelectionListener(e -> {
            if(!e.getValueIsAdjusting() && tablaAlumnos.getSelectedRow() != -1) {
                String matriculaSeleccionada = tablaAlumnos.getValueAt(tablaAlumnos.getSelectedRow(), 0).toString();
                cargarHorarioEnPantalla(matriculaSeleccionada, modeloHorarioAlumno);
            }
        });

        btnRefrescar.addActionListener(e -> cargarUsuariosPorRol("ALUMNO", modeloAlumnos));
        btnEliminar.addActionListener(e -> accionEliminar(tablaAlumnos, modeloAlumnos));
        tabs.addTab("Base Estudiantes", panel);
    }

    private void armarPestañaBaseMaestros(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(FONDO_GRIS_LIGERO);

        modeloMaestros = new DefaultTableModel(new String[]{"Matrícula", "Nombre Docente", "Departamento", "Huella"}, 0);
        tablaMaestros = new JTable(modeloMaestros);
        estilizarTablaGeneral(tablaMaestros);
        JScrollPane spMae = new JScrollPane(tablaMaestros);
        spMae.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(200,205,215)), " Personal Docente Institucional ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));

        modeloHistorialMaestros = new DefaultTableModel(new String[]{"Matrícula", "Nombre", "Aula Visitada", "Fecha y Hora de Acceso"}, 0);
        JTable tabHist = new JTable(modeloHistorialMaestros);
        estilizarTablaGeneral(tabHist);
        JScrollPane spHist = new JScrollPane(tabHist);
        spHist.setPreferredSize(new Dimension(1100, 200));
        spHist.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(200,205,215)), " Asistencias Realizadas por Docentes ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));

        JPanel pnlFiltroFecha = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        pnlFiltroFecha.setBackground(Color.WHITE);
        pnlFiltroFecha.setBorder(BorderFactory.createLineBorder(new Color(220,225,230)));
        pnlFiltroFecha.add(new JLabel("Filtrar Día (AAAA-MM-DD):"));
        String hoy = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        txtFechaMaestros = new JTextField(hoy, 10); pnlFiltroFecha.add(txtFechaMaestros);
        
        JButton btnBuscarM = new JButton("CARGAR ASISTENCIA INTEGRAL");
        darEstiloBoton(btnBuscarM, AZUL_MEDIO);
        pnlFiltroFecha.add(btnBuscarM);

        JPanel pnlCentro = new JPanel(new BorderLayout(10, 10)); pnlCentro.setOpaque(false);
        pnlCentro.add(spMae, BorderLayout.CENTER); pnlCentro.add(spHist, BorderLayout.SOUTH);
        
        panel.add(pnlFiltroFecha, BorderLayout.NORTH);
        panel.add(pnlCentro, BorderLayout.CENTER);

        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT)); pnlBotones.setBackground(FONDO_GRIS_LIGERO);
        JButton btnRefrescar = new JButton("REFRESCAR LISTA"); darEstiloBoton(btnRefrescar, AZUL_MEDIO);
        JButton btnEliminar = new JButton("ELIMINAR DOCENTE"); darEstiloBoton(btnEliminar, ROJO_ERROR);
        pnlBotones.add(btnRefrescar); pnlBotones.add(btnEliminar); panel.add(pnlBotones, BorderLayout.SOUTH);

        btnBuscarM.addActionListener(e -> cargarAsistenciasMaestrosGlobal());
        btnRefrescar.addActionListener(e -> cargarUsuariosPorRol("MAESTRO", modeloMaestros));
        btnEliminar.addActionListener(e -> accionEliminar(tablaMaestros, modeloMaestros));

        tabs.addTab("BD,Asistencia Maestros", panel);
    }

    private void armarPestañaBuscadorSalones(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(FONDO_GRIS_LIGERO);

        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); 
        pnlTop.setBackground(Color.WHITE);
        pnlTop.setBorder(BorderFactory.createLineBorder(new Color(220,225,230)));
        
        pnlTop.add(new JLabel("Salón Escolar:")); 
        txtBuscarSalon = new JTextField(8); 
        pnlTop.add(txtBuscarSalon);
        
        pnlTop.add(new JLabel("Bloque:")); 
        cbBuscarHora = new JComboBox<>(new String[]{"06:30 - 07:10", "07:10 - 07:50", "07:50 - 08:30", "08:30 - 09:10"}); 
        pnlTop.add(cbBuscarHora);
        
        pnlTop.add(new JLabel("Semana del (AAAA-MM-DD):")); 
        String hoy = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        txtBuscarFecha = new JTextField(hoy, 9); 
        pnlTop.add(txtBuscarFecha);
        
        JButton btnFiltrar = new JButton("CARGAR SEMANA"); 
        darEstiloBoton(btnFiltrar, AZUL_MEDIO);
        pnlTop.add(btnFiltrar); 
        
        JButton btnExcel = new JButton("⬇ DESCARGAR EXCEL");
        darEstiloBoton(btnExcel, VERDE_EXCEL);
        pnlTop.add(btnExcel);
        
        panel.add(pnlTop, BorderLayout.NORTH);

        modeloFiltroSalon = new DefaultTableModel(new String[]{"Matrícula", "Materia", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"}, 0);
        tablaFiltro = new JTable(modeloFiltroSalon);
        estilizarTablaGeneral(tablaFiltro);
        
        panel.add(new JScrollPane(tablaFiltro), BorderLayout.CENTER);

        btnFiltrar.addActionListener(e -> accionFiltrarSalonSemanal());
        btnExcel.addActionListener(e -> exportarTablaAExcel());
        
        tabs.addTab("Buscador por Salón", panel);
    }

    /**
     * Puente con el controlador para la generación del pivote semanal.
     */
    private void accionFiltrarSalonSemanal() {
        try {
            modeloFiltroSalon.setRowCount(0);
            String bloqueSeleccionado = cbBuscarHora.getSelectedItem().toString();
            List<Object[]> datos = controlador.generarReporteSemanalSalon(txtBuscarSalon.getText().trim().toUpperCase(), txtBuscarFecha.getText().trim(), bloqueSeleccionado);
            for(Object[] row : datos) {
                modeloFiltroSalon.addRow(row);
            }
        } catch(Exception e) { 
            JOptionPane.showMessageDialog(this, "Error generando reporte semanal: " + e.getMessage()); 
        }
    }

    /**
     * Motor nativo de exportación a CSV.
     * Incorpora inyección de cabecera BOM (Byte Order Mark) en UTF-8 para evitar 
     * problemas de codificación de caracteres especiales (acentos/eñes) en Microsoft Excel.
     */
    private void exportarTablaAExcel() {
        if (tablaFiltro.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No hay datos para exportar. Busque un salón primero.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar Asistencia Semanal");
        fileChooser.setSelectedFile(new File("Reporte_Asistencia_" + txtBuscarSalon.getText().trim() + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File archivoGuardar = fileChooser.getSelectedFile();
            
            if (!archivoGuardar.getName().toLowerCase().endsWith(".csv")) {
                archivoGuardar = new File(archivoGuardar.getAbsolutePath() + ".csv");
            }

            // Uso de OutputStreamWriter forzando StandardCharsets.UTF_8 para integridad de acentos
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(archivoGuardar), StandardCharsets.UTF_8))) {
                
                // Inyección del BOM invisible (\ufeff)
                //para que el excel pueda leer las palabras con asentos 
                pw.print('\ufeff');

                StringBuilder sbHeaders = new StringBuilder();
                for (int i = 0; i < tablaFiltro.getColumnCount(); i++) {
                    sbHeaders.append(tablaFiltro.getColumnName(i));
                    if (i < tablaFiltro.getColumnCount() - 1) sbHeaders.append(",");
                }
                pw.println(sbHeaders.toString());

                for (int i = 0; i < tablaFiltro.getRowCount(); i++) {
                    StringBuilder sbRow = new StringBuilder();
                    for (int j = 0; j < tablaFiltro.getColumnCount(); j++) {
                        Object val = tablaFiltro.getValueAt(i, j);
                        sbRow.append(val != null ? val.toString() : "");
                        if (j < tablaFiltro.getColumnCount() - 1) sbRow.append(",");
                    }
                    pw.println(sbRow.toString());
                }

                JOptionPane.showMessageDialog(this, "¡Archivo Excel generado exitosamente!\n" + archivoGuardar.getAbsolutePath(), "Éxito", JOptionPane.INFORMATION_MESSAGE);
                Desktop.getDesktop().open(archivoGuardar);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al guardar el archivo: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * FLUJO ASÍNCRONO DE ENROLAMIENTO BIOMÉTRICO.
     * Desacopla la lógica de captura física del hilo principal (EDT).
     * Secuencia de hardware:
     * 1. Captura de la primera muestra fotográfica.
     * 2. Extracción de características hacia el Búfer 1.
     * 3. Captura de la segunda muestra fotográfica para validación cruzada.
     * 4. Extracción de características hacia el Búfer 2.
     * 5. Fusión de ambos búferes en un modelo maestro consolidado.
     *
     * @param btnOrigen Referencia al botón UI para deshabilitarlo preventivamente y evitar clicks dobles.
     */
    private void ejecutarEnrolamientoEnSegundoPlano(JButton btnOrigen) {
        new Thread(() -> {
            btnOrigen.setEnabled(false);
            btnOrigen.setText("Leyendo...");
            txtHuella.setText("Esperando dedo (Vez 1)...");
            
            SensorHuellaService sensor = new SensorHuellaService("COM7");
            
            try {
                if (!sensor.conectar()) {
                    JOptionPane.showMessageDialog(this, "No se pudo abrir el puerto COM7. Verifique el sensor.", "Hardware Error", JOptionPane.ERROR_MESSAGE);
                    txtHuella.setText("");
                    return;
                }
                
                // Muestreo Fase 1
                boolean captura1 = false;
                System.out.println("[BIOMETRÍA] Iniciando bucle de captura 1...");
                for (int i = 0; i < 60; i++) { // Timeout dinámico de ~15 segundos
                    if (sensor.capturarFotoDedo()) {
                        if (sensor.generarCaracteristicas(1)) {
                            captura1 = true;
                            break;
                        }
                    }
                    Thread.sleep(250);
                }
                
                if (!captura1) {
                    txtHuella.setText("Tiempo agotado / Error V1");
                    System.err.println("[BIOMETRÍA] Falló la primera captura por tiempo agotado.");
                    return;
                }
                
                txtHuella.setText("¡Retire el dedo!");
                System.out.println("[BIOMETRÍA] Captura 1 exitosa. Esperando liberación de sensor...");
                Thread.sleep(2000); // Ventana temporal obligatoria para evitar solapamiento fotográfico
                
                // Muestreo Fase 2
                txtHuella.setText("Coloque el mismo dedo (Vez 2)...");
                boolean captura2 = false;
                for (int i = 0; i < 60; i++) {
                    if (sensor.capturarFotoDedo()) {
                        if (sensor.generarCaracteristicas(2)) {
                            captura2 = true;
                            break;
                        }
                    }
                    Thread.sleep(250);
                }
                
                if (!captura2) {
                    txtHuella.setText("Error en confirmación V2");
                    System.err.println("[BIOMETRÍA] Falló la segunda captura.");
                    return;
                }
                
                // Síntesis y purificación del modelo final
                txtHuella.setText("Modelando huella...");
                System.out.println("[BIOMETRÍA] Creando modelo emparejado...");
                if (sensor.crearModeloHuella()) {
                    String templateHex = sensor.descargarTemplateDesdeSensor();
                    
                    if (templateHex != null && !templateHex.isEmpty()) {
                        txtHuella.setText(templateHex);
                        JOptionPane.showMessageDialog(this, "¡Huella digital modelada con éxito! Ya puede guardar al usuario.", "Éxito Biométrico", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        txtHuella.setText("Error al descargar buffer");
                    }
                } else {
                    txtHuella.setText("Las huellas no coinciden");
                    System.err.println("[BIOMETRÍA] Las huellas del buffer 1 y 2 no se pudieron consolidar.");
                }
                
            } catch (Exception ex) {
                txtHuella.setText("Fallo: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                try { sensor.desconectar(); } catch(Exception ignored){}
                
                // Retorno seguro al Event Dispatch Thread para reactivar los controles gráficos
                SwingUtilities.invokeLater(() -> {
                    btnOrigen.setText("Escanear");
                    btnOrigen.setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * Mapea los campos de texto del formulario hacia una estructura matricial 
     * bidimensional y solicita la persistencia al controlador.
     */
    private void accionGuardar() {
        String[][] bloques = { 
            {"06:30:00", txtSalon1.getText().trim(), txtMateria1.getText().trim()}, 
            {"07:10:00", txtSalon2.getText().trim(), txtMateria2.getText().trim()}, 
            {"07:50:00", txtSalon3.getText().trim(), txtMateria3.getText().trim()}, 
            {"08:30:00", txtSalon4.getText().trim(), txtMateria4.getText().trim()} 
        };
        try {
            controlador.guardarUsuarioYHorarios(txtMatricula.getText().trim(), txtNombre.getText().trim(), cbRol.getSelectedItem().toString(), txtCarrera.getText().trim(), txtHuella.getText().trim(), bloques);
            JOptionPane.showMessageDialog(this, "Matriz escolar sincronizada de forma integral en Supabase.");
            
            // Limpieza de UI post-inserción
            txtMatricula.setText(""); txtNombre.setText(""); txtCarrera.setText(""); txtHuella.setText("");
            txtSalon1.setText(""); txtMateria1.setText(""); txtSalon2.setText(""); txtMateria2.setText("");
            txtSalon3.setText(""); txtMateria3.setText(""); txtSalon4.setText(""); txtMateria4.setText("");
        } catch(Exception ex) { 
            JOptionPane.showMessageDialog(this, "Error de persistencia: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
        }
    }

    private void cargarUsuariosPorRol(String rol, DefaultTableModel modelo) {
        try {
            modelo.setRowCount(0);
            List<Object[]> datos = controlador.obtenerUsuariosPorRol(rol);
            for(Object[] fila : datos) modelo.addRow(fila);
        } catch(Exception ex) { 
            JOptionPane.showMessageDialog(this, "Fallo al refrescar: " + ex.getMessage()); 
        }
    }

    private void cargarHorarioEnPantalla(String mat, DefaultTableModel modelo) {
        try {
            modelo.setRowCount(0);
            List<Object[]> datos = controlador.obtenerHorarioIndividual(mat);
            for(Object[] fila : datos) modelo.addRow(fila);
        } catch(Exception ignored) {}
    }

    private void cargarAsistenciasMaestrosGlobal() {
        try {
            modeloHistorialMaestros.setRowCount(0);
            List<Object[]> datos = controlador.consultarAsistenciaMaestrosPorFecha(txtFechaMaestros.getText().trim());
            for(Object[] fila : datos) modeloHistorialMaestros.addRow(fila);
        } catch(Exception ex) { 
            JOptionPane.showMessageDialog(this, "Fallo en auditoría: " + ex.getMessage()); 
        }
    }

    /**
     * Intermediario de validación destructiva. Solicita confirmación explícita
     * antes de emitir una instrucción DELETE a la base de datos central.
     */
    private void accionEliminar(JTable tabla, DefaultTableModel modelo) {
        int fila = tabla.getSelectedRow();
        if(fila == -1) {
            JOptionPane.showMessageDialog(this, "Seleccione un registro.");
            return;
        }
        String mat = modelo.getValueAt(fila, 0).toString();
        if(JOptionPane.showConfirmDialog(this, "¿Eliminar matrícula [" + mat + "]?", "Confirmar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            try { 
                controlador.eliminarUsuario(mat); 
                modelo.removeRow(fila); 
                modeloHorarioAlumno.setRowCount(0); 
            } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
        }
    }

    /**
     * Motor de diseño universal para botones. Elimina los bordes del sistema operativo (Look&Feel nativo)
     * para aplicar un renderizado plano (Flat Design).
     */
    private void darEstiloBoton(JButton boton, Color colorFondo) {
        boton.setBackground(colorFondo); 
        boton.setForeground(Color.WHITE); 
        boton.setFont(FUENTE_NEGRITA); 
        boton.setFocusPainted(false);
        boton.setOpaque(true);
        boton.setBorderPainted(false); 
        boton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    /**
     * Inyector de propiedades gráficas para tablas (JTable).
     * Configura el renderizado de cabeceras oscuras y líneas de división atenuadas.
     */
    private void estilizarTablaGeneral(JTable tabla) {
        tabla.setFont(FUENTE_SANS);
        tabla.setRowHeight(24);
        tabla.setSelectionBackground(AZUL_SELECCION);
        tabla.setSelectionForeground(Color.BLACK);
        tabla.setGridColor(new Color(235, 238, 243));
        
        JTableHeader header = tabla.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasFoc, row, col);
                lbl.setBackground(AZUL_OBSCURO);
                lbl.setForeground(Color.WHITE); 
                lbl.setFont(FUENTE_NEGRITA);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setOpaque(true);
                lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(40, 80, 110)));
                return lbl;
            }
        });
    }

    /**
     * Hilo principal de ejecución (Main Thread).
     * Intenta forzar la adopción del tema visual del Sistema Operativo huésped antes 
     * de despachar la interfaz a la pila gráfica de Java.
     */
    public static void main(String[] args) { 
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainAdmin().setVisible(true)); 
    }
}