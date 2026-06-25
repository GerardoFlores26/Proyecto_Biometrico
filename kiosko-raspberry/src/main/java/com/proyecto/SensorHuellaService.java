package com.proyecto;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;

/**
 * SERVICIO DE CONTROL PARA EL SENSOR DE HUELLA AS608 (Protocolo Serial)
 */
public class SensorHuellaService {

    private SerialPort puertoSerial;
    private final String nombrePuerto;
    private final int BAUD_RATE = 57600; 

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
        
        // Configuración de timeouts simple para lectura dinámica síncrona
        puertoSerial.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (puertoSerial.openPort()) {
            System.out.println("✔ Conectado exitosamente al sensor en el puerto: " + nombrePuerto);
            puertoSerial.flushIOBuffers();
            
            // PASO CRÍTICO ESTILO SFGDEMO: Autenticar el sensor con su contraseña por defecto
            if (verificarContrasena()) {
                System.out.println("✔ Sincronización y Handshake con el AS608 exitoso.");
                return true;
            } else {
                System.err.println("⚠ El sensor se conectó pero rechazó el Handshake. Revisa la alimentación.");
                return true; // Forzamos true para permitir reintentos mecánicos
            }
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

    /**
     * Handshake obligatorio del protocolo AS608 (Comando 0x13 - VfyPwd)
     * Envía la contraseña por defecto de 4 bytes: 0x00, 0x00, 0x00, 0x00
     */
    private boolean verificarContrasena() {
        byte[] pwd = {0x00, 0x00, 0x00, 0x00};
        byte[] respuesta = enviarComando((byte) 0x13, pwd);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    private byte[] enviarComando(byte instruccion, byte[] datos) {
        // La longitud que pide el protocolo (2 bytes de checksum + 1 byte de instrucción + N bytes de datos)
        int longitudDatos = (datos != null ? datos.length : 0) + 3; 
        
        // Calculamos el tamaño total real sumando el Header (6), PID (1), Longitud (2) y la longitud de datos calculada
        int tamanoPaquete = HEADER_DIRECCION.length + 1 + 2 + longitudDatos;
        byte[] paquete = new byte[tamanoPaquete];

        // 1. Copiar Header y Dirección (EF 01 FF FF FF FF)
        System.arraycopy(HEADER_DIRECCION, 0, paquete, 0, HEADER_DIRECCION.length);
        int pos = HEADER_DIRECCION.length;

        // 2. Tipo de paquete (PID = 0x01)
        paquete[pos++] = PID_COMANDO;

        // 3. Longitud (High byte y Low byte)
        paquete[pos++] = (byte) ((longitudDatos >> 8) & 0xFF);
        paquete[pos++] = (byte) (longitudDatos & 0xFF);

        // 4. Código de instrucción (Ej: 0x01 o 0x13)
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

        // Guardar Checksum de 2 bytes de forma exacta al final del arreglo
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

    private byte[] leerRespuestaDinamica(int bytesEsperados) {
        byte[] buffer = new byte[bytesEsperados];
        int totalLeidos = 0;
        int intentos = 0;

        while (totalLeidos < bytesEsperados && intentos < 40) {
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
                try { Thread.sleep(15); } catch (InterruptedException ignored) {}
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
        return null;
    }

    /**
     * Intenta capturar la foto del dedo con una pausa prudente simulando la espera activa.
     */
    public boolean capturarFotoDedo() {
        System.out.println("[BIOMETRÍA] Intentando capturar huella a 128 bytes...");
        
        if (puertoSerial != null && puertoSerial.isOpen()) {
            puertoSerial.flushIOBuffers(); 
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
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    public boolean crearModeloHuella() {
        byte[] respuesta = enviarComando((byte) 0x05, null);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    public String descargarTemplateDesdeSensor() {
        try {
            byte[] datos = {(byte) 0x01}; 
            byte[] respuesta = enviarComando((byte) 0x08, datos);
            
            if (respuesta != null && respuesta.length >= 10 && respuesta[9] == 0x00) {
                Thread.sleep(150);
                byte[] dataHuella = leerRespuestaDinamica(144);
                
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

    /**
     * MÉTODO NUEVO PARA EL KIOSCO: Captura el dedo del usuario y busca si coincide 
     * con alguna huella guardada en la base de datos interna o memoria.
     * Retorna el ID del usuario (si lo encuentra) o -1 si no hubo coincidencia.
     */
    public int buscarHuellaEnSensor() {
        System.out.println("[KIOSCO] Coloque su dedo para verificar asistencia...");
        
        if (puertoSerial != null && puertoSerial.isOpen()) {
            puertoSerial.flushIOBuffers();
        }
        
        byte[] respuestaFoto = enviarComando((byte) 0x01, null);
        if (respuestaFoto == null || respuestaFoto.length < 10 || respuestaFoto[9] != 0x00) {
            System.out.println("[KIOSCO] No se detectó un dedo válido en el lector.");
            return -1;
        }
        
        byte[] slotBuffer = {(byte) 0x01};
        byte[] respuestaChar = enviarComando((byte) 0x02, slotBuffer);
        if (respuestaChar == null || respuestaChar.length < 10 || respuestaChar[9] != 0x00) {
            System.out.println("[KIOSCO] Error al procesar las características de la huella.");
            return -1;
        }
        
        byte[] paramsBusqueda = {
            (byte) 0x01,              // Usar características del Buffer 1
            (byte) 0x00, (byte) 0x00, // ID inicial de búsqueda (Página 0)
            (byte) 0x01, (byte) 0x2C  // Cantidad de páginas (300 en total -> 0x012C)
        };
        
        byte[] respuestaBusqueda = enviarComando((byte) 0x0C, paramsBusqueda);
        
        if (respuestaBusqueda != null && respuestaBusqueda.length >= 14) {
            byte codigoConfirmacion = respuestaBusqueda[9];
            
            if (codigoConfirmacion == 0x00) {
                // Se encontró coincidencia perfecta
                int idEncontrado = ((respuestaBusqueda[10] & 0xFF) << 8) | (respuestaBusqueda[11] & 0xFF);
                int puntajeCoincidencia = ((respuestaBusqueda[12] & 0xFF) << 8) | (respuestaBusqueda[13] & 0xFF);
                
                System.out.println("✔ ¡Usuario Identificado! ID: " + idEncontrado + " (Puntaje: " + puntajeCoincidencia + ")");
                return idEncontrado; 
            } else if (codigoConfirmacion == 0x09) {
                System.out.println("❌ Huella no encontrada en el sistema.");
            } else {
                System.out.println("⚠ Error en la búsqueda. Código: " + String.format("%02X", codigoConfirmacion));
            }
        }
        
        return -1;
    }
}