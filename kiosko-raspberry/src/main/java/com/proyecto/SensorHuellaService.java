package com.proyecto;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;

/**
 * SERVICIO DE CONTROL PARA EL SENSOR DE HUELLA AS608 (Protocolo Serial)
 */
public class SensorHuellaService {

    private SerialPort puertoSerial;
    private final String nombrePuerto;
    private final int BAUD_RATE = 57600; // Volvemos a los 57600 por defecto que te funcionaron

    private final byte[] HEADER_DIRECCION = {(byte) 0xEF, (byte) 0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private final byte PID_COMANDO = (byte) 0x01;

    public SensorHuellaService(String nombrePuerto) {
        this.nombrePuerto = nombrePuerto;
    }

    public boolean conectar() {
        if (puertoSerial != null && puertoSerial.isOpen()) {
            return true;
        }

        puertoSerial = SerialPort.getCommPort(nombrePuerto);
        puertoSerial.setBaudRate(BAUD_RATE);
        puertoSerial.setNumDataBits(8);
        puertoSerial.setNumStopBits(SerialPort.ONE_STOP_BIT);
        puertoSerial.setParity(SerialPort.NO_PARITY);
        
        // CAMBIO CRÍTICO ESTILO SCRIPT DE PRUEBAS: 
        // Desactivamos timeouts rígidos y dejamos que lea de forma síncrona según disponibilidad (Non-blocking / Simple)
        puertoSerial.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (puertoSerial.openPort()) {
            System.out.println("✔ Conectado exitosamente al sensor en el puerto: " + nombrePuerto);
            puertoSerial.flushIOBuffers();
            return true;
        } else {
            System.err.println("❌ No se pudo abrir el puerto: " + nombrePuerto);
            return false;
        }
    }

    public void desconectar() {
        if (puertoSerial != null && puertoSerial.isOpen()) {
            puertoSerial.closePort();
            System.out.println("🔌 Puerto serial cerrado.");
        }
    }

    private byte[] enviarComando(byte instruccion, byte[] datos) {
    // Si no hay datos adicionales, la longitud oficial del paquete es de 3 bytes (Instrucción + 2 bytes de Checksum)
    int longitudDatos = (datos != null ? datos.length : 0) + 3; 
    
    int tamanoPaquete = HEADER_DIRECCION.length + 1 + 2 + (datos != null ? datos.length : 0) + 2;
    byte[] paquete = new byte[tamanoPaquete];

    // 1. Copiar Header y Dirección (EF 01 FF FF FF FF)
    System.arraycopy(HEADER_DIRECCION, 0, paquete, 0, HEADER_DIRECCION.length);
    int pos = HEADER_DIRECCION.length;

    // 2. Tipo de paquete (PID = 0x01)
    paquete[pos++] = PID_COMANDO;

    // 3. Longitud (High byte y Low byte)
    paquete[pos++] = (byte) ((longitudDatos >> 8) & 0xFF);
    paquete[pos++] = (byte) (longitudDatos & 0xFF);

    // 4. Código de instrucción (Ej: 0x01 para capturar)
    paquete[pos++] = instruccion;

    // 5. Datos adicionales (si existen)
    if (datos != null && datos.length > 0) {
        System.arraycopy(datos, 0, paquete, pos, datos.length);
        pos += datos.length;
    }

    // 6. Calcular Checksum (Suma desde PID hasta el último dato)
    int suma = PID_COMANDO + ((longitudDatos >> 8) & 0xFF) + (longitudDatos & 0xFF) + (instruccion & 0xFF);
    if (datos != null) {
        for (byte b : datos) {
            suma += (b & 0xFF);
        }
    }

    // Guardar Checksum de 2 bytes al final del arreglo
    paquete[pos++] = (byte) ((suma >> 8) & 0xFF);
    paquete[pos] = (byte) (suma & 0xFF);

    // Enviar por el puerto serial
    if (puertoSerial != null && puertoSerial.isOpen()) {
        try {
            puertoSerial.flushIOBuffers(); 
            puertoSerial.writeBytes(paquete, paquete.length);
            
            // Esperamos la respuesta estándar de 12 bytes
            return leerRespuestaDinamica(12); 
        } catch (Exception e) {
            System.err.println("[ERROR EN ENVÍO]: " + e.getMessage());
        }
    }
    return null;
}

    /**
     * Lee el puerto byte por byte esperando a que el hardware responda.
     */
    private byte[] leerRespuestaDinamica(int bytesEsperados) {
        byte[] buffer = new byte[bytesEsperados];
        int totalLeidos = 0;
        int intentos = 0;

        while (totalLeidos < bytesEsperados && intentos < 30) {
            if (puertoSerial.bytesAvailable() > 0) {
                byte[] unByte = new byte[1];
                int leido = puertoSerial.readBytes(unByte, 1);
                if (leido > 0) {
                    if (totalLeidos < buffer.length) {
                        buffer[totalLeidos++] = unByte[0];
                    } else {
                        break;
                    }
                }
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                intentos++;
            }
        }

        if (totalLeidos > 0) {
            System.out.print("[DEBUG SERIAL] Recibidos: ");
            for (int i = 0; i < totalLeidos; i++) {
                System.out.print(String.format("%02X ", buffer[i]));
            }
            System.out.println();
            return Arrays.copyOf(buffer, totalLeidos);
        }
        
        System.out.println("[DEBUG SERIAL] Silencio total del sensor.");
        return null;
    }

    /**
     * MÉTODO OPTIMIZADO: Captura directa de la huella digital.
     * Toma la muestra de forma síncrona inmediata con el dedo previamente colocado.
     */
  public boolean capturarFotoDedo() {
    System.out.println("[BIOMETRÍA] Intentando capturar huella a 128 bytes...");
    
    if (puertoSerial != null && puertoSerial.isOpen()) {
        puertoSerial.flushIOBuffers(); // Limpia cualquier residuo en el puerto
    }

    byte[] datosVacios = new byte[0]; 
    byte[] respuesta = enviarComando((byte) 0x01, datosVacios);
    
    if (respuesta != null && respuesta.length >= 10) {
        byte codigoConfirmacion = respuesta[9];
        System.out.println("[BIOMETRÍA] El sensor respondió código: " + String.format("%02X", codigoConfirmacion));
        
        if (codigoConfirmacion == 0x00) {
            System.out.println("✔ ¡Captura exitosa!");
            return true;
        }
    }
    return false;
}

    public boolean generarCaracteristicas(int numeroBuffer) {
        byte[] datos = {(byte) numeroBuffer};
        byte[] respuesta = enviarComando((byte) 0x02, datos);
        if (respuesta != null && respuesta.length > 0) {
            if (respuesta.length >= 10) {
                return respuesta[9] == 0x00;
            }
            return true;
        }
        return false;
    }

    public boolean crearModeloHuella() {
        byte[] respuesta = enviarComando((byte) 0x05, null);
        if (respuesta != null && respuesta.length > 0) {
            if (respuesta.length >= 10) {
                return respuesta[9] == 0x00;
            }
            return true;
        }
        return false;
    }

    public String descargarTemplateDesdeSensor() {
        try {
            byte[] datos = {(byte) 0x01}; 
            byte[] respuesta = enviarComando((byte) 0x08, datos);
            
            if (respuesta != null && respuesta.length >= 10 && respuesta[9] == 0x00) {
                Thread.sleep(150);
                
                // Para el flujo masivo del template (la huella), leemos dinámicamente 568 bytes
                byte[] dataHuella = leerRespuestaDinamica(144);
                
                // VALIDACIÓN CRÍTICA: Asegurar que tenga bytes suficientes para extraer el HEX
                if (dataHuella != null && dataHuella.length > 12) {
                    StringBuilder hex = new StringBuilder();
                    int limiteSuperior = dataHuella.length - 2;
                    for (int i = 9; i < limiteSuperior; i++) { 
                        hex.append(String.format("%02X", dataHuella[i]));
                    }
                    return hex.toString(); 
                }
            }
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        } finally {
            desconectar();
        }
        return null;
    }
}