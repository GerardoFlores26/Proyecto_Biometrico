package com.proyecto;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainAdmin extends JFrame {
    private JTextField txtMatricula, txtNombre, txtHuella, txtSalon, txtCarrera;
    private JComboBox<String> cbRol;
    private JTable tablaAccesos;
    private DefaultTableModel modeloTabla;
    
    // Paleta de Colores: Azul y Blanco
    private Color azulPrincipal = new Color(20, 80, 160);
    private Color fondoBlanco = new Color(245, 247, 250);

    public MainAdmin() {
        setTitle("Panel de Control Administrativo - Control de Asistencia");
        setSize(1050, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(fondoBlanco);
        setLayout(new BorderLayout(20, 20));

        // --- PANEL SUPERIOR (TÍTULO) ---
        JPanel pnlHeader = new JPanel();
        pnlHeader.setBackground(azulPrincipal);
        JLabel lblHeader = new JLabel("SISTEMA DE ASISTENCIA ESCOLAR (ADMIN)");
        lblHeader.setForeground(Color.WHITE);
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 22));
        pnlHeader.add(lblHeader);
        add(pnlHeader, BorderLayout.NORTH);

        // --- PANEL IZQUIERDO (FORMULARIO) ---
        JPanel pnlForm = new JPanel(new GridBagLayout());
        pnlForm.setBackground(Color.WHITE);
        pnlForm.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(azulPrincipal), " Asignación y Registro "));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; pnlForm.add(new JLabel("Matrícula:"), gbc);
        txtMatricula = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtMatricula, gbc);

        gbc.gridx = 0; gbc.gridy = 1; pnlForm.add(new JLabel("Nombre:"), gbc);
        txtNombre = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtNombre, gbc);

        gbc.gridx = 0; gbc.gridy = 2; pnlForm.add(new JLabel("Rol:"), gbc);
        cbRol = new JComboBox<>(new String[]{"ALUMNO", "MAESTRO"}); gbc.gridx = 1; pnlForm.add(cbRol, gbc);

        // Campo: Salón Asignado
        gbc.gridx = 0; gbc.gridy = 3; pnlForm.add(new JLabel("Salón Asignado:"), gbc);
        txtSalon = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtSalon, gbc);

        // NUEVO CAMPO: Carrera
        gbc.gridx = 0; gbc.gridy = 4; pnlForm.add(new JLabel("Carrera / Depto:"), gbc);
        txtCarrera = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtCarrera, gbc);

        gbc.gridx = 0; gbc.gridy = 5; pnlForm.add(new JLabel("ID Huella:"), gbc);
        txtHuella = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtHuella, gbc);

        JButton btnSave = new JButton("GUARDAR / ACTUALIZAR");
        btnSave.setBackground(azulPrincipal);
        btnSave.setForeground(Color.WHITE);
        btnSave.setFocusPainted(false);
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; pnlForm.add(btnSave, gbc);

        add(pnlForm, BorderLayout.WEST);

        // --- PANEL CENTRAL (TABLA DE ASISTENCIAS) ---
        String[] cols = {"ID", "Matrícula", "Hora", "Salón Aula", "Estatus", "Motivo"};
        modeloTabla = new DefaultTableModel(cols, 0);
        tablaAccesos = new JTable(modeloTabla);
        tablaAccesos.setBackground(Color.WHITE);
        tablaAccesos.setGridColor(Color.LIGHT_GRAY);
        JScrollPane sp = new JScrollPane(tablaAccesos);
        add(sp, BorderLayout.CENTER);

        btnSave.addActionListener(e -> guardarOActualizarUsuario());

        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(this::actualizarTabla, 0, 3, TimeUnit.SECONDS);
    }

    private void guardarOActualizarUsuario() {
        // SQL con COALESCE para soportar actualizaciones parciales sin machacar datos existentes
        String sql = "INSERT INTO usuarios (matricula, nombre, rol, salon, carrera, huella_template) VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (matricula) DO UPDATE SET " +
                     "nombre = COALESCE(NULLIF(EXCLUDED.nombre, ''), usuarios.nombre), " +
                     "rol = EXCLUDED.rol, " +
                     "salon = COALESCE(NULLIF(EXCLUDED.salon, ''), usuarios.salon), " +
                     "carrera = COALESCE(NULLIF(EXCLUDED.carrera, ''), usuarios.carrera), " +
                     "huella_template = COALESCE(NULLIF(EXCLUDED.huella_template, ''), usuarios.huella_template)";
                     
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            String matricula = txtMatricula.getText().trim();
            String nombre = txtNombre.getText().trim();
            String rol = cbRol.getSelectedItem().toString();
            String salon = txtSalon.getText().trim().toUpperCase();
            String carrera = txtCarrera.getText().trim().toUpperCase();
            String huella = txtHuella.getText().trim();
            
            if (matricula.isEmpty()) {
                JOptionPane.showMessageDialog(this, "La matrícula es obligatoria para identificar al usuario.");
                return;
            }
            
            ps.setString(1, matricula);
            ps.setString(2, nombre);
            ps.setString(3, rol);
            ps.setString(4, salon);
            ps.setString(5, carrera);
            ps.setString(6, huella);
            ps.executeUpdate();
            
            JOptionPane.showMessageDialog(this, "Datos procesados correctamente en el sistema.");
            
            // Limpiar cajas de texto
            txtMatricula.setText(""); txtNombre.setText(""); txtHuella.setText(""); txtSalon.setText(""); txtCarrera.setText("");
        } catch (Exception ex) { 
            JOptionPane.showMessageDialog(this, "Error al actualizar: " + ex.getMessage()); 
        }
    }

    private void actualizarTabla() {
        String sql = "SELECT id, matricula, fecha_hora, salon_kiosko, permitido, motivo_rechazo FROM registro_accesos ORDER BY fecha_hora DESC LIMIT 15";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            modeloTabla.setRowCount(0);
            while (rs.next()) {
                modeloTabla.addRow(new Object[]{ 
                    rs.getInt("id"), 
                    rs.getString("matricula"),
                    rs.getTimestamp("fecha_hora"), 
                    rs.getString("salon_kiosko"),
                    rs.getBoolean("permitido") ? "PRESENTE ✔" : "DENEGADO ❌", 
                    rs.getString("motivo_rechazo")
                });
            }
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new MainAdmin().setVisible(true)); }
}