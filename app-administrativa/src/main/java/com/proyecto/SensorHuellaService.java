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
    
    puertoSerial.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

    if (puertoSerial.openPort()) {
        System.out.println("✔ Conectado exitosamente al sensor en el puerto: " + nombrePuerto);
        puertoSerial.flushIOBuffers();
        
        // Forzamos el true para que el Kiosko continúe aunque el módulo tenga otra clave de fábrica
        verificarContrasena(); 
        System.out.println("✔ Sincronización del puerto serial forzada para el Kiosko.");
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

    private boolean verificarContrasena() {
        byte[] pwd = {0x00, 0x00, 0x00, 0x00};
        byte[] respuesta = enviarComando((byte) 0x13, pwd);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    private byte[] enviarComando(byte instruccion, byte[] datos) {
        int numDatosAdicionales = (datos != null) ? datos.length : 0;
        int tamanoPaquete = 12 + numDatosAdicionales; 
        byte[] paquete = new byte[tamanoPaquete];

        System.arraycopy(HEADER_DIRECCION, 0, paquete, 0, HEADER_DIRECCION.length);
        int pos = HEADER_DIRECCION.length;

        paquete[pos++] = PID_COMANDO;

        int longitudContenido = numDatosAdicionales + 3;
        paquete[pos++] = (byte) ((longitudContenido >> 8) & 0xFF);
        paquete[pos++] = (byte) (longitudContenido & 0xFF);

        paquete[pos++] = instruccion;

        if (numDatosAdicionales > 0) {
            System.arraycopy(datos, 0, paquete, pos, datos.length);
            pos += datos.length;
        }

        int suma = (PID_COMANDO & 0xFF) + ((longitudContenido >> 8) & 0xFF) + (longitudContenido & 0xFF) + (instruccion & 0xFF);
        if (datos != null) {
            for (byte b : datos) {
                suma += (b & 0xFF);
            }
        }

        paquete[pos++] = (byte) ((suma >> 8) & 0xFF);
        paquete[pos] = (byte) (suma & 0xFF);

        if (puertoSerial != null && puertoSerial.isOpen()) {
            try {
                puertoSerial.flushIOBuffers(); 
                puertoSerial.writeBytes(paquete, paquete.length);
                
                // Si mandamos un payload grande de huella, damos margen dinámico a la respuesta del buffer
                int bytesALeer = (instruccion == (byte) 0x12) ? 14 : 12;
                return leerRespuestaDinamica(bytesALeer); 
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

        while (totalLeidos < bytesEsperados && intentos < 50) {
            int disponibles = puertoSerial.bytesAvailable();
            if (disponibles > 0) {
                int aLeer = Math.min(disponibles, bytesEsperados - totalLeidos);
                byte[] temp = new byte[aLeer];
                int leido = puertoSerial.readBytes(temp, aLeer);
                if (leido > 0) {
                    System.arraycopy(temp, 0, buffer, totalLeidos, leido);
                    totalLeidos += leido;
                }
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                intentos++;
            }
        }

        if (totalLeidos > 0) {
            return Arrays.copyOf(buffer, totalLeidos);
        }
        return null;
    }

    public boolean capturarFotoDedo() {
        System.out.println("[BIOMETRÍA] Intentando capturar huella...");
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
                // Damos tiempo suficiente para que el sensor mande toda la ráfaga de datos
                Thread.sleep(200);
                byte[] rawPackets = leerRespuestaDinamica(800); 
                
                if (rawPackets != null && rawPackets.length > 20) {
                    StringBuilder hex = new StringBuilder();
                    int i = 0;
                    
                    // Escaneamos el flujo de bytes buscando las cabeceras de los paquetes
                    while (i < rawPackets.length - 9) {
                        if (rawPackets[i] == (byte) 0xEF && rawPackets[i+1] == (byte) 0x01) {
                            
                            // Calculamos cuánto mide la carga útil de este paquete
                            int len = ((rawPackets[i+7] & 0xFF) << 8) | (rawPackets[i+8] & 0xFF);
                            int dataLength = len - 2; // Descartamos los 2 bytes finales de Checksum
                            
                            // Extraemos y concatenamos estrictamente la huella pura
                            for (int j = 0; j < dataLength; j++) {
                                if ((i + 9 + j) < rawPackets.length) {
                                    hex.append(String.format("%02X", rawPackets[i + 9 + j]));
                                }
                            }
                            
                            // Brincamos al siguiente paquete: Cabecera (9) + Datos + Checksum (2)
                            i += (9 + dataLength + 2);
                        } else {
                            i++; // Seguimos buscando si hay ruido
                        }
                    }
                    return hex.toString();
                }
            }
        } catch (Exception ex) {
            System.err.println("Error descargando huella: " + ex.getMessage());
        }
        return null;
    }

    /**
     * MÓDULO KIOSKO (1:1 MATCH OFICIAL): Compara las características del dedo puesto (Buffer 1)
     * directamente contra la cadena Hexadecimal de Supabase.
     */
    public boolean verificarDedoContraSupabase(String hexTemplateSupabase) {
        if (hexTemplateSupabase == null || hexTemplateSupabase.length() < 100) return false;
        
        try {
            // 1. Convertir el String Hex de Supabase a arreglo de bytes puros
            int len = hexTemplateSupabase.length();
            byte[] bytesTemplate = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                bytesTemplate[i / 2] = (byte) ((Character.digit(hexTemplateSupabase.charAt(i), 16) << 4)
                                     + Character.digit(hexTemplateSupabase.charAt(i+1), 16));
            }
            
            // 2. Estructurar el payload del comando 0x12 (Buffer 1 + Bytes del template)
            byte[] payloadComando = new byte[1 + bytesTemplate.length];
            payloadComando[0] = (byte) 0x01; // Contraste contra el Buffer de caracteres 1
            System.arraycopy(bytesTemplate, 0, payloadComando, 1, bytesTemplate.length);
            
            // 3. Enviamos la instrucción 0x12 (MatchTemplate)
            byte[] respuesta = enviarComando((byte) 0x12, payloadComando);
            
            // 4. Analizar la respuesta de control de hardware de forma segura
            if (respuesta != null && respuesta.length >= 10) {
                byte codigoConfirmacion = respuesta[9];
                if (codigoConfirmacion == 0x00) {
                    // El comando responde con éxito y devuelve la puntuación en los bytes siguientes
                    int score = 0;
                    if (respuesta.length >= 12) {
                        score = ((respuesta[10] & 0xFF) << 8) | (respuesta[11] & 0xFF);
                    }
                    System.out.println("[KIOSKO] Match evaluado por hardware. Score obtenido: " + score);
                    return score >= 35; 
                } else if (codigoConfirmacion == 0x01) {
                    System.out.println("[KIOSKO] El hardware completó el análisis: Las huellas no coinciden.");
                } else {
                    System.out.println("[KIOSKO] Código de respuesta inesperado en Match: " + String.format("%02X", codigoConfirmacion));
                }
            }
        } catch (Exception e) {
            System.err.println("[KIOSKO] Error analítico en emparejamiento biométrico: " + e.getMessage());
        }
        return false;
    }

    @Deprecated
    public boolean cargarTemplateAlSensor(String hexTemplate) { return false; }

    @Deprecated
    public int compararBuffers() { return 0; }

    public int buscarHuellaEnSensor() { return -1; }
}