package com.proyecto;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainAdmin extends JFrame {
    // Componentes Pestaña 1 (Datos Base)
    private JTextField txtMatricula, txtNombre, txtCarrera, txtHuella;
    private JComboBox<String> cbRol;
    
    // Componentes Pestaña 1 (Matriz de 4 Horas)
    private JTextField txtSalon1, txtMateria1; // 06:30
    private JTextField txtSalon2, txtMateria2; // 07:10
    private JTextField txtSalon3, txtMateria3; // 07:50
    private JTextField txtSalon4, txtMateria4; // 08:30
    
    private JTable tablaAccesosEnVivo;
    private DefaultTableModel modeloEnVivo;

    // Componentes Pestaña 2 (Base de Datos + Visor de Horarios)
    private JTable tablaUsuariosBBDD;
    private DefaultTableModel modeloBBDD;
    private JTable tablaHorarioIndividual;
    private DefaultTableModel modeloHorarioIndividual;

    // Componentes Pestaña 3 (Buscador Avanzado)
    private JTextField txtBuscarSalon, txtBuscarFecha;
    private JComboBox<String> cbBuscarHora;
    private JTable tablaFiltroAsistencia;
    private DefaultTableModel modeloFiltro;

    // Paleta de Colores
    private Color azulPrincipal = new Color(20, 80, 160);
    private Color fondoBlanco = new Color(245, 247, 250);
    private Color rojoBorrar = new Color(192, 57, 43);

    public MainAdmin() {
        setTitle("Panel de Control Administrativo Escolar - Gestión de Horarios Completa");
        setSize(1200, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JTabbedPane panelPestañas = new JTabbedPane();
        panelPestañas.setFont(new Font("Segoe UI", Font.BOLD, 13));

        armarPestañaRegistroHorarios(panelPestañas);
        armarPestañaBaseDatos(panelPestañas);
        armarPestañaBuscadorAsistencias(panelPestañas);

        add(panelPestañas);

        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(this::actualizarTablaEnVivo, 0, 3, TimeUnit.SECONDS);
    }

    private void armarPestañaRegistroHorarios(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(fondoBlanco);

        // FORMULARIO IZQUIERDO PRINCIPAL
        JPanel pnlIzquierdo = new JPanel();
        pnlIzquierdo.setLayout(new BoxLayout(pnlIzquierdo, BoxLayout.Y_AXIS));
        pnlIzquierdo.setBackground(Color.WHITE);
        pnlIzquierdo.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(azulPrincipal, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Bloque A: Datos Personales
        JPanel pnlDatosPersonales = new JPanel(new GridBagLayout());
        pnlDatosPersonales.setOpaque(false);
        pnlDatosPersonales.setBorder(BorderFactory.createTitledBorder(" 1. Identificación del Usuario "));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; pnlDatosPersonales.add(new JLabel("Matrícula:"), gbc);
        txtMatricula = new JTextField(12); gbc.gridx = 1; pnlDatosPersonales.add(txtMatricula, gbc);

        gbc.gridx = 0; gbc.gridy = 1; pnlDatosPersonales.add(new JLabel("Nombre:"), gbc);
        txtNombre = new JTextField(12); gbc.gridx = 1; pnlDatosPersonales.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 2; pnlDatosPersonales.add(new JLabel("Rol:"), gbc);
        cbRol = new JComboBox<>(new String[]{"ALUMNO", "MAESTRO"}); gbc.gridx = 1; pnlDatosPersonales.add(cbRol, gbc);

        gbc.gridx = 0; gbc.gridy = 3; pnlDatosPersonales.add(new JLabel("Carrera:"), gbc);
        txtCarrera = new JTextField(12); gbc.gridx = 1; pnlDatosPersonales.add(txtCarrera, gbc);

        gbc.gridx = 0; gbc.gridy = 4; pnlDatosPersonales.add(new JLabel("ID Huella:"), gbc);
        txtHuella = new JTextField(12); gbc.gridx = 1; pnlDatosPersonales.add(txtHuella, gbc);

        pnlIzquierdo.add(pnlDatosPersonales);
        pnlIzquierdo.add(Box.createVerticalStrut(10));

        // Bloque B: Matriz de Horarios (Las 4 horas de corrido)
        JPanel pnlMatrizHorarios = new JPanel(new GridLayout(5, 3, 6, 6));
        pnlMatrizHorarios.setOpaque(false);
        pnlMatrizHorarios.setBorder(BorderFactory.createTitledBorder(" 2. Cronograma de Clases (4 Horas) "));

        pnlMatrizHorarios.add(new JLabel("Bloque Hora", SwingConstants.CENTER));
        pnlMatrizHorarios.add(new JLabel("Salón Aula", SwingConstants.CENTER));
        pnlMatrizHorarios.add(new JLabel("Materia / Cargo", SwingConstants.CENTER));

        pnlMatrizHorarios.add(new JLabel("06:30 - 07:10"));
        txtSalon1 = new JTextField(); pnlMatrizHorarios.add(txtSalon1);
        txtMateria1 = new JTextField(); pnlMatrizHorarios.add(txtMateria1);

        pnlMatrizHorarios.add(new JLabel("07:10 - 07:50"));
        txtSalon2 = new JTextField(); pnlMatrizHorarios.add(txtSalon2);
        txtMateria2 = new JTextField(); pnlMatrizHorarios.add(txtMateria2);

        pnlMatrizHorarios.add(new JLabel("07:50 - 08:30"));
        txtSalon3 = new JTextField(); pnlMatrizHorarios.add(txtSalon3);
        txtMateria3 = new JTextField(); pnlMatrizHorarios.add(txtMateria3);

        pnlMatrizHorarios.add(new JLabel("08:30 - 09:10"));
        txtSalon4 = new JTextField(); pnlMatrizHorarios.add(txtSalon4);
        txtMateria4 = new JTextField(); pnlMatrizHorarios.add(txtMateria4);

        pnlIzquierdo.add(pnlMatrizHorarios);
        pnlIzquierdo.add(Box.createVerticalStrut(15));

        JButton btnGuardar = new JButton("GUARDAR INTEGRALMENTE");
        btnGuardar.setBackground(azulPrincipal);
        btnGuardar.setForeground(Color.WHITE);
        btnGuardar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnGuardar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        pnlIzquierdo.add(btnGuardar);

        panel.add(pnlIzquierdo, BorderLayout.WEST);

        // TABLA DERECHA (MONITOR GLOBAL)
        String[] cols = {"ID", "Matrícula", "Fecha/Hora", "Kiosko Aula", "Estatus", "Motivo"};
        modeloEnVivo = new DefaultTableModel(cols, 0);
        tablaAccesosEnVivo = new JTable(modeloEnVivo);
        JScrollPane sp = new JScrollPane(tablaAccesosEnVivo);
        sp.setBorder(BorderFactory.createTitledBorder(" Bitácora de Accesos en Vivo (Global) "));
        panel.add(sp, BorderLayout.CENTER);

        btnGuardar.addActionListener(e -> guardarFormularioCompleto());
        tabs.addTab("Registro y Horarios Matriz", panel);
    }

    private void armarPestañaBaseDatos(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(fondoBlanco);

        // Tabla Superior: Lista Completa de Alumnos
        String[] colsBBDD = {"Matrícula", "Nombre", "Rol", "Carrera", "Huella Digital"};
        modeloBBDD = new DefaultTableModel(colsBBDD, 0);
        tablaUsuariosBBDD = new JTable(modeloBBDD);
        JScrollPane spUsuarios = new JScrollPane(tablaUsuariosBBDD);
        spUsuarios.setBorder(BorderFactory.createTitledBorder(" 1. Selecciona un Alumno/Maestro para ver su Horario "));

        // NUEVO - Tabla Inferior: Desglose de Horario del Alumno Seleccionado
        String[] colsHorario = {"Bloque Horario", "Salón Asignado", "Materia / Asignatura"};
        modeloHorarioIndividual = new DefaultTableModel(colsHorario, 0);
        tablaHorarioIndividual = new JTable(modeloHorarioIndividual);
        JScrollPane spHorario = new JScrollPane(tablaHorarioIndividual);
        spHorario.setPreferredSize(new Dimension(1100, 160));
        spHorario.setBorder(BorderFactory.createTitledBorder(" 2. Carga Académica Asignada (4 Horas del Turno) "));

        // Panel contenedor para organizar ambas tablas de arriba a abajo
        JPanel pnlTablasCentro = new JPanel(new BorderLayout(10, 10));
        pnlTablasCentro.setOpaque(false);
        pnlTablasCentro.add(spUsuarios, BorderLayout.CENTER);
        pnlTablasCentro.add(spHorario, BorderLayout.SOUTH);
        panel.add(pnlTablasCentro, BorderLayout.CENTER);

        // Panel de Control de Botones Inferiores
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlBotones.setBackground(fondoBlanco);

        JButton btnCargar = new JButton("CARGAR / REFRESCAR ALUMNOS");
        btnCargar.setBackground(azulPrincipal);
        btnCargar.setForeground(Color.WHITE);

        JButton btnEliminar = new JButton("ELIMINAR USUARIO TOTALMENTE");
        btnEliminar.setBackground(rojoBorrar);
        btnEliminar.setForeground(Color.WHITE);
        btnEliminar.setFont(new Font("Segoe UI", Font.BOLD, 12));

        pnlBotones.add(btnCargar);
        pnlBotones.add(btnEliminar);
        panel.add(pnlBotones, BorderLayout.SOUTH);

        // EVENTO CLIC: Al seleccionar un alumno, carga sus materias automáticamente
        tablaUsuariosBBDD.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int fila = tablaUsuariosBBDD.getSelectedRow();
                if (fila != -1) {
                    String matricula = modeloBBDD.getValueAt(fila, 0).toString();
                    cargarHorarioIndividual(matricula);
                }
            }
        });

        btnCargar.addActionListener(e -> {
            cargarUsuariosBBDD();
            modeloHorarioIndividual.setRowCount(0); // Limpiar visor de horario
        });
        btnEliminar.addActionListener(e -> {
            eliminarUsuarioSeleccionado();
            modeloHorarioIndividual.setRowCount(0);
        });

        tabs.addTab("Base de Datos Completa", panel);
    }

    private void armarPestañaBuscadorAsistencias(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(fondoBlanco);

        JPanel pnlBusqueda = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 12));
        pnlBusqueda.setBackground(Color.WHITE);
        pnlBusqueda.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        pnlBusqueda.add(new JLabel("Salón Kiosko:"));
        txtBuscarSalon = new JTextField(8);
        pnlBusqueda.add(txtBuscarSalon);

        pnlBusqueda.add(new JLabel("Bloque de Hora:"));
        cbBuscarHora = new JComboBox<>(new String[]{"06:30", "07:10", "07:50", "08:30"});
        pnlBusqueda.add(cbBuscarHora);

        pnlBusqueda.add(new JLabel("Fecha (AAAA-MM-DD):"));
        String fechaHoy = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        txtBuscarFecha = new JTextField(fechaHoy, 9);
        pnlBusqueda.add(txtBuscarFecha);

        JButton btnBuscar = new JButton("VER ASISTENCIA ESPECÍFICA");
        btnBuscar.setBackground(azulPrincipal);
        btnBuscar.setForeground(Color.WHITE);
        btnBuscar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pnlBusqueda.add(btnBuscar);

        panel.add(pnlBusqueda, BorderLayout.NORTH);

        String[] cols = {"Matrícula", "Bloque Horario", "Materia Esperada", "Estatus Asistencia", "Último Intento de Registro"};
        modeloFiltro = new DefaultTableModel(cols, 0);
        tablaFiltroAsistencia = new JTable(modeloFiltro);
        panel.add(new JScrollPane(tablaFiltroAsistencia), BorderLayout.CENTER);

        btnBuscar.addActionListener(e -> filtrarAsistenciasAvanzado());

        tabs.addTab("Historial de Asistencias por Salón", panel);
    }

    private void guardarFormularioCompleto() {
        String mat = txtMatricula.getText().trim();
        String nom = txtNombre.getText().trim();
        String rol = cbRol.getSelectedItem().toString();
        String car = txtCarrera.getText().trim().toUpperCase();
        String hue = txtHuella.getText().trim();

        if (mat.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Falta la matrícula para procesar la transacción.");
            return;
        }

        String sqlUser = "INSERT INTO usuarios (matricula, nombre, rol, carrera, huella_template) VALUES (?, ?, ?, ?, ?) " +
                         "ON CONFLICT (matricula) DO UPDATE SET " +
                         "nombre = COALESCE(NULLIF(EXCLUDED.nombre, ''), usuarios.nombre), " +
                         "rol = EXCLUDED.rol, " +
                         "carrera = COALESCE(NULLIF(EXCLUDED.carrera, ''), usuarios.carrera), " +
                         "huella_template = COALESCE(NULLIF(EXCLUDED.huella_template, ''), usuarios.huella_template)";

        String sqlHorario = "INSERT INTO horarios (matricula, hora_inicio, salon, materia) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT (matricula, hora_inicio) DO UPDATE SET salon = EXCLUDED.salon, materia = EXCLUDED.materia";

        String[][] bloques = {
            {"06:30:00", txtSalon1.getText().trim().toUpperCase(), txtMateria1.getText().trim().toUpperCase()},
            {"07:10:00", txtSalon2.getText().trim().toUpperCase(), txtMateria2.getText().trim().toUpperCase()},
            {"07:50:00", txtSalon3.getText().trim().toUpperCase(), txtMateria3.getText().trim().toUpperCase()},
            {"08:30:00", txtSalon4.getText().trim().toUpperCase(), txtMateria4.getText().trim().toUpperCase()}
        };

        try (Connection con = ConexionSupabase.obtenerConexion()) {
            con.setAutoCommit(false);
            
            try (PreparedStatement psUser = con.prepareStatement(sqlUser);
                 PreparedStatement psHorario = con.prepareStatement(sqlHorario)) {
                
                psUser.setString(1, mat); psUser.setString(2, nom); psUser.setString(3, rol); psUser.setString(4, car); psUser.setString(5, hue);
                psUser.executeUpdate();

                for (String[] b : bloques) {
                    psHorario.setString(1, mat);
                    psHorario.setTime(2, Time.valueOf(b[0]));
                    psHorario.setString(3, b[1].isEmpty() ? "LIBRE" : b[1]);
                    psHorario.setString(4, b[2].isEmpty() ? "NINGUNA" : b[2]);
                    psHorario.executeUpdate();
                }
                
                con.commit();
                JOptionPane.showMessageDialog(this, "Usuario y cronograma de 4 horas guardados exitosamente.");
                limpiarCamposHorarios();
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error en el guardado total: " + ex.getMessage());
        }
    }

    private void limpiarCamposHorarios() {
        txtMatricula.setText(""); txtNombre.setText(""); txtCarrera.setText(""); txtHuella.setText("");
        txtSalon1.setText(""); txtMateria1.setText(""); txtSalon2.setText(""); txtMateria2.setText("");
        txtSalon3.setText(""); txtMateria3.setText(""); txtSalon4.setText(""); txtMateria4.setText("");
    }

    private void cargarUsuariosBBDD() {
        String sql = "SELECT matricula, nombre, rol, carrera, huella_template FROM usuarios ORDER BY matricula ASC";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            modeloBBDD.setRowCount(0);
            while(rs.next()) {
                modeloBBDD.addRow(new Object[]{
                    rs.getString("matricula"), rs.getString("nombre"), rs.getString("rol"), rs.getString("carrera"),
                    rs.getString("huella_template").isEmpty() ? "Sin Registrar" : "ACTIVA ✔"
                });
            }
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Error: " + e.getMessage()); }
    }

    // NUEVO: Método que consulta el horario de un alumno específico y lo pinta abajo
    private void cargarHorarioIndividual(String matricula) {
        String sql = "SELECT hora_inicio, salon, materia FROM horarios WHERE matricula = ? ORDER BY hora_inicio ASC";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, matricula);
            try (ResultSet rs = ps.executeQuery()) {
                modeloHorarioIndividual.setRowCount(0);
                while(rs.next()) {
                    String horaFormateada = rs.getTime("hora_inicio").toString().substring(0, 5);
                    modeloHorarioIndividual.addRow(new Object[]{
                        horaFormateada + " hs",
                        rs.getString("salon"),
                        rs.getString("materia")
                    });
                }
            }
        } catch (Exception e) { System.err.println("Error al cargar materias: " + e.getMessage()); }
    }

    private void eliminarUsuarioSeleccionado() {
        int fila = tablaUsuariosBBDD.getSelectedRow();
        if (fila == -1) {
            JOptionPane.showMessageDialog(this, "Selecciona a un alumno de la lista para eliminarlo.");
            return;
        }
        String matricula = modeloBBDD.getValueAt(fila, 0).toString();
        int resp = JOptionPane.showConfirmDialog(this, "¿Deseas purgar la matrícula " + matricula + " del sistema escolar?", "Confirmación Crítica", JOptionPane.YES_NO_OPTION);
        
        if (resp == JOptionPane.YES_OPTION) {
            String sql = "DELETE FROM usuarios WHERE matricula = ?";
            try (Connection con = ConexionSupabase.obtenerConexion();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, matricula);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Registro borrado satisfactoriamente.");
                cargarUsuariosBBDD();
            } catch (Exception e) { JOptionPane.showMessageDialog(this, "Error: " + e.getMessage()); }
        }
    }

    private void filtrarAsistenciasAvanzado() {
        String salonBusqueda = txtBuscarSalon.getText().trim().toUpperCase();
        String horaBusqueda = cbBuscarHora.getSelectedItem().toString() + ":00";
        String fechaBusqueda = txtBuscarFecha.getText().trim();

        if(salonBusqueda.isEmpty() || fechaBusqueda.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Salón y Fecha son parámetros obligatorios.");
            return;
        }

        String sql = "SELECT h.matricula, h.hora_inicio, h.materia, " +
                     "COALESCE((SELECT 'ASISTIÓ ✔' FROM registro_accesos r WHERE r.matricula = h.matricula AND r.salon_kiosko = h.salon AND r.permitido = true AND DATE(r.fecha_hora) = ? LIMIT 1), 'AUSENTE ❌') as estatus, " +
                     "COALESCE((SELECT CAST(r.fecha_hora AS VARCHAR) FROM registro_accesos r WHERE r.matricula = h.matricula AND r.salon_kiosko = h.salon AND DATE(r.fecha_hora) = ? ORDER BY r.fecha_hora DESC LIMIT 1), 'Sin registros hoy') as momento " +
                     "FROM horarios h WHERE h.salon LIKE ? AND h.hora_inicio = ?";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fechaBusqueda);
            ps.setString(2, fechaBusqueda);
            ps.setString(3, "%" + salonBusqueda + "%");
            ps.setTime(4, Time.valueOf(horaBusqueda));
            ResultSet rs = ps.executeQuery();
            
            modeloFiltro.setRowCount(0);
            while(rs.next()) {
                modeloFiltro.addRow(new Object[]{
                    rs.getString("matricula"),
                    rs.getTime("hora_inicio"),
                    rs.getString("materia"),
                    rs.getString("estatus"),
                    rs.getString("momento")
                });
            }
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Error en el filtrado: " + e.getMessage()); }
    }

    private void actualizarTablaEnVivo() {
        String sql = "SELECT id, matricula, fecha_hora, salon_kiosko, permitido, motivo_rechazo FROM registro_accesos ORDER BY fecha_hora DESC LIMIT 15";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            modeloEnVivo.setRowCount(0);
            while (rs.next()) {
                modeloEnVivo.addRow(new Object[]{ 
                    rs.getInt("id"), rs.getString("matricula"), rs.getTimestamp("fecha_hora"), 
                    rs.getString("salon_kiosko"), rs.getBoolean("permitido") ? "ACCESO ✔" : "DENEGADO ❌", 
                    rs.getString("motivo_rechazo")
                });
            }
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new MainAdmin().setVisible(true)); }
}