package com.proyecto;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * VISTA ADMINISTRATIVA PRINCIPAL - CORRECCIÓN DE VISIBILIDAD DE TEXTOS Y BOTONES
 * Se fuerza el color de la fuente en los headers y en los botones para evitar que
 * el Look and Feel del sistema operativo los oculte.
 */
public class MainAdmin extends JFrame {
    
    private AdminController controlador = new AdminController();

    // Paleta de Diseño Institucional Unificada
    private static final Color AZUL_OBSCURO = new Color(21, 67, 96);   // Encabezados y títulos principales
    private static final Color AZUL_MEDIO = new Color(41, 128, 185);   // Fondo de botones principales
    private static final Color FONDO_GRIS_LIGERO = new Color(245, 247, 250); // Fondo general de la ventana
    private static final Color AZUL_SELECCION = new Color(235, 245, 251);   // Filas seleccionadas
    private static final Color ROJO_ERROR = new Color(231, 76, 60);
    private static final Color VERDE_EXITO = new Color(39, 174, 96);
    
    private static final Font FUENTE_SANS = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FUENTE_NEGRITA = new Font("Segoe UI", Font.BOLD, 13);

    // Componentes del Formulario de Registro
    private JTextField txtMatricula, txtNombre, txtCarrera, txtHuella;
    private JComboBox<String> cbRol;
    private JTextField txtSalon1, txtMateria1, txtSalon2, txtMateria2, txtSalon3, txtMateria3, txtSalon4, txtMateria4;

    // Monitor en Tiempo Real
    private JPanel pnlMonitorContenedor;
    private JTable tablaMonitor;
    private DefaultTableModel modeloMonitor;
    private JScrollPane scrollMonitor;

    // Componentes de otras pestañas
    private DefaultTableModel modeloAlumnos, modeloHorarioAlumno, modeloFiltroSalon;
    private DefaultTableModel modeloMaestros, modeloHistorialMaestros; 
    private JTable tablaAlumnos, tablaMaestros;
    private JTextField txtBuscarSalon, txtBuscarFecha, txtFechaMaestros;
    private JComboBox<String> cbBuscarHora;

    public MainAdmin() {
        setTitle("Panel Escolar Administrativo - Arquitectura Decoupled MVC");
        setSize(1350, 780); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); 
        getContentPane().setBackground(FONDO_GRIS_LIGERO);

        JTabbedPane panelPestañas = new JTabbedPane();
        panelPestañas.setFont(FUENTE_NEGRITA);

        // Carga de los módulos en pestañas
        armarPestañaRegistro(panelPestañas);
        armarPestañaBaseAlumnos(panelPestañas);
        armarPestañaBaseMaestros(panelPestañas); 
        armarPestañaBuscadorSalones(panelPestañas);

        add(panelPestañas);

        // Hilo de refresco del monitor (Cada 1.5 segundos)
        Timer timerIngresoTiempoReal = new Timer(1500, e -> refrescarHistorialMonitorEnVivo());
        timerIngresoTiempoReal.setRepeats(true);
        timerIngresoTiempoReal.start();
    }

    private void refrescarHistorialMonitorEnVivo() {
        try {
            List<Object[]> logsRecientes = controlador.obtenerUltimos15Ingresos();
            if (logsRecientes != null) {
                modeloMonitor.setRowCount(0);
                for (Object[] registro : logsRecientes) {
                    modeloMonitor.addRow(new Object[]{
                        registro[0], // Matrícula
                        registro[3], // Hora
                        registro[4], // Estatus (OK/NO)
                        registro[5]  // Motivo
                    });
                }
                pnlMonitorContenedor.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(AZUL_MEDIO, 1), " MONITOR DE ACCESOS EN LÍNEA ", 
                    0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));
            }
        } catch (Exception ex) {
            pnlMonitorContenedor.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ROJO_ERROR, 1), " MONITOR - ERROR DE CONEXIÓN ", 
                0, 0, FUENTE_NEGRITA, ROJO_ERROR));
        }
    }

    /**
     * PESTAÑA PRINCIPAL: REGISTRO Y MONITOR INTEGRADO
     */
    private void armarPestañaRegistro(JTabbedPane tabs) {
        JPanel panelPrincipal = new JPanel(new GridBagLayout());
        panelPrincipal.setBackground(FONDO_GRIS_LIGERO);
        GridBagConstraints gbcMaster = new GridBagConstraints();

        // PANEL IZQUIERDO: FORMULARIO DE CAPTURA
        JPanel pnlIzquierdo = new JPanel(new GridBagLayout());
        pnlIzquierdo.setBackground(Color.WHITE);
        pnlIzquierdo.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        GridBagConstraints gbcForm = new GridBagConstraints();
        gbcForm.insets = new Insets(5, 5, 5, 5);
        gbcForm.fill = GridBagConstraints.HORIZONTAL;

        // Sub-panel 1: Datos de Usuario
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
        gbcD.gridx = 0; gbcD.gridy = 4; pnlDatos.add(new JLabel("ID Huella:"), gbcD);
        txtHuella = new JTextField(15); gbcD.gridx = 1; pnlDatos.add(txtHuella, gbcD);

        gbcForm.gridx = 0; gbcForm.gridy = 0; gbcForm.weightx = 1.0;
        pnlIzquierdo.add(pnlDatos, gbcForm);

        // Sub-panel 2: Carga de Turnos
        JPanel pnlMatriz = new JPanel(new GridBagLayout());
        pnlMatriz.setBackground(Color.WHITE);
        pnlMatriz.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(210, 215, 225)), " 2. Carga del Turno Escolar (4 Bloques) ", 0, 0, FUENTE_NEGRITA, AZUL_OBSCURO));
        
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

        // Botón de Guardado con tamaño controlado y colores definidos explícitamente
        JButton btnGuardar = new JButton("GUARDAR MATRIZ ACADÉMICA");
        darEstiloBoton(btnGuardar, AZUL_MEDIO);
        
        gbcForm.gridy = 2;
        gbcForm.fill = GridBagConstraints.NONE; 
        gbcForm.anchor = GridBagConstraints.CENTER;
        gbcForm.insets = new Insets(20, 5, 5, 5);
        pnlIzquierdo.add(btnGuardar, gbcForm);

        // Ubicar formulario en el contenedor maestro
        gbcMaster.gridx = 0; gbcMaster.gridy = 0;
        gbcMaster.weightx = 0.45; gbcMaster.weighty = 1.0;
        gbcMaster.fill = GridBagConstraints.BOTH;
        gbcMaster.insets = new Insets(10, 10, 10, 5);
        panelPrincipal.add(pnlIzquierdo, gbcMaster);

        // PANEL DERECHO: MONITOR DE ACCESOS
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

        // Renderizador de celdas para pintar las alertas (OK / NO)
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

        btnGuardar.addActionListener(e -> accionGuardar());
        tabs.addTab("Matriz de Registro", panelPrincipal);
    }

    /**
     * PESTAÑA 2: BASE DE ESTUDIANTES
     */
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

    /**
     * PESTAÑA 3: BASE Y ASISTENCIA DE MAESTROS
     */
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

        tabs.addTab("Base y Asistencia Maestros", panel);
    }

    /**
     * PESTAÑA 4: BUSCADOR POR SALÓN
     */
    private void armarPestañaBuscadorSalones(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(FONDO_GRIS_LIGERO);

        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); pnlTop.setBackground(Color.WHITE);
        pnlTop.setBorder(BorderFactory.createLineBorder(new Color(220,225,230)));
        pnlTop.add(new JLabel("Salón:")); txtBuscarSalon = new JTextField(8); pnlTop.add(txtBuscarSalon);
        pnlTop.add(new JLabel("Bloque:")); cbBuscarHora = new JComboBox<>(new String[]{"06:30", "07:10", "07:50", "08:30"}); pnlTop.add(cbBuscarHora);
        pnlTop.add(new JLabel("Fecha:")); String hoy = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        txtBuscarFecha = new JTextField(hoy, 9); pnlTop.add(txtBuscarFecha);
        
        JButton btnFiltrar = new JButton("FILTRAR REGISTROS"); darEstiloBoton(btnFiltrar, AZUL_MEDIO);
        pnlTop.add(btnFiltrar); panel.add(pnlTop, BorderLayout.NORTH);

        modeloFiltroSalon = new DefaultTableModel(new String[]{"Matrícula", "Bloque", "Materia Asignada", "Estatus Presencia", "Hora Marcaje"}, 0);
        JTable tablaFiltro = new JTable(modeloFiltroSalon);
        estilizarTablaGeneral(tablaFiltro);
        panel.add(new JScrollPane(tablaFiltro), BorderLayout.CENTER);

        btnFiltrar.addActionListener(e -> accionFiltrarSalon());
        tabs.addTab("Buscador por Salón", panel);
    }

    // =========================================================================
    // LÓGICA DE CONTROLADOR Y EVENTOS
    // =========================================================================

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

    private void accionFiltrarSalon() {
        try {
            modeloFiltroSalon.setRowCount(0);
            String bloqueHora = cbBuscarHora.getSelectedItem().toString() + ":00";
            List<Object[]> datos = controlador.filtrarAsistenciasPorSalon(txtBuscarSalon.getText().trim().toUpperCase(), bloqueHora, txtBuscarFecha.getText().trim());
            for(Object[] row : datos) modeloFiltroSalon.addRow(row);
        } catch(Exception e) { JOptionPane.showMessageDialog(this, "Error analítico: " + e.getMessage()); }
    }

    // MÉTODOS AUXILIARES: PERSONALIZACIÓN ABSOLUTA DE COMPONENTES
    private void darEstiloBoton(JButton boton, Color colorFondo) {
        boton.setBackground(colorFondo); 
        boton.setForeground(Color.WHITE); // Fuerza el texto a Blanco
        boton.setFont(FUENTE_NEGRITA); 
        boton.setFocusPainted(false);
        boton.setOpaque(true);
        boton.setBorderPainted(false); // Elimina bordes nativos feos
        boton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void estilizarTablaGeneral(JTable tabla) {
        tabla.setFont(FUENTE_SANS);
        tabla.setRowHeight(24);
        tabla.setSelectionBackground(AZUL_SELECCION);
        tabla.setSelectionForeground(Color.BLACK);
        tabla.setGridColor(new Color(235, 238, 243));
        
        // CORRECCIÓN DE HEADERS INVISIBLES: Forzar renderizador propio con fondo azul y letras blancas
        JTableHeader header = tabla.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasFoc, row, col);
                lbl.setBackground(AZUL_OBSCURO);
                lbl.setForeground(Color.WHITE); // Texto siempre visible en blanco
                lbl.setFont(FUENTE_NEGRITA);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setOpaque(true);
                lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(40, 80, 110)));
                return lbl;
            }
        });
    }

    public static void main(String[] args) { 
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainAdmin().setVisible(true)); 
    }
}