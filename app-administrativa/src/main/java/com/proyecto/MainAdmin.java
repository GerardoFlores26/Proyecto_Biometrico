package com.proyecto;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainAdmin extends JFrame {
    private JTextField txtMatricula, txtNombre, txtHuella;
    private JComboBox<String> cbRol;
    private JTable tablaAccesos;
    private DefaultTableModel modeloTabla;
    private Color supabaseGreen = new Color(62, 207, 142);
    private Color darkBg = new Color(28, 28, 28);

    public MainAdmin() {
        setTitle("Panel de Control Administrativo - Biometría");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(darkBg);
        setLayout(new BorderLayout(20, 20));

        // --- PANEL SUPERIOR (TÍTULO) ---
        JPanel pnlHeader = new JPanel();
        pnlHeader.setBackground(new Color(23, 23, 23));
        JLabel lblHeader = new JLabel("GESTIÓN DE ACCESO ESCOLAR");
        lblHeader.setForeground(supabaseGreen);
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 24));
        pnlHeader.add(lblHeader);
        add(pnlHeader, BorderLayout.NORTH);

        // --- PANEL IZQUIERDO (FORMULARIO) ---
        JPanel pnlForm = new JPanel(new GridBagLayout());
        pnlForm.setBackground(darkBg);
        pnlForm.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblM = new JLabel("Matrícula:"); lblM.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 0; pnlForm.add(lblM, gbc);
        txtMatricula = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtMatricula, gbc);

        JLabel lblN = new JLabel("Nombre:"); lblN.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 1; pnlForm.add(lblN, gbc);
        txtNombre = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtNombre, gbc);

        JLabel lblR = new JLabel("Rol:"); lblR.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 2; pnlForm.add(lblR, gbc);
        cbRol = new JComboBox<>(new String[]{"ALUMNO", "MAESTRO"}); gbc.gridx = 1; pnlForm.add(cbRol, gbc);

        JLabel lblH = new JLabel("ID Huella:"); lblH.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 3; pnlForm.add(lblH, gbc);
        txtHuella = new JTextField(15); gbc.gridx = 1; pnlForm.add(txtHuella, gbc);

        JButton btnSave = new JButton("REGISTRAR USUARIO");
        btnSave.setBackground(supabaseGreen);
        btnSave.setForeground(Color.BLACK);
        btnSave.setFocusPainted(false);
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; pnlForm.add(btnSave, gbc);

        add(pnlForm, BorderLayout.WEST);

        // --- PANEL CENTRAL (TABLA) ---
        String[] cols = {"ID", "Matrícula", "Hora", "Estatus", "Motivo"};
        modeloTabla = new DefaultTableModel(cols, 0);
        tablaAccesos = new JTable(modeloTabla);
        tablaAccesos.setBackground(new Color(40, 40, 40));
        tablaAccesos.setForeground(Color.WHITE);
        tablaAccesos.setGridColor(new Color(60, 60, 60));
        JScrollPane sp = new JScrollPane(tablaAccesos);
        sp.getViewport().setBackground(darkBg);
        add(sp, BorderLayout.CENTER);

        btnSave.addActionListener(e -> registrar());

        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(this::actualizarTabla, 0, 3, TimeUnit.SECONDS);
    }

    private void registrar() {
        String sql = "INSERT INTO usuarios (matricula, nombre, rol, huella_template) VALUES (?, ?, ?, ?)";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, txtMatricula.getText());
            ps.setString(2, txtNombre.getText());
            ps.setString(3, cbRol.getSelectedItem().toString());
            ps.setString(4, txtHuella.getText());
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Usuario en la nube!");
            txtMatricula.setText(""); txtNombre.setText(""); txtHuella.setText("");
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }

    private void actualizarTabla() {
        String sql = "SELECT * FROM registro_accesos ORDER BY fecha_hora DESC LIMIT 10";
        try (Connection con = ConexionSupabase.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            modeloTabla.setRowCount(0);
            while (rs.next()) {
                modeloTabla.addRow(new Object[]{ rs.getInt("id"), rs.getString("matricula"),
                        rs.getTimestamp("fecha_hora"), rs.getBoolean("permitido") ? "OK" : "NO", rs.getString("motivo_rechazo")});
            }
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new MainAdmin().setVisible(true)); }
}