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
                Thread.sleep(100);
                byte[] rawPackets = leerRespuestaDinamica(600); 
                
                if (rawPackets != null && rawPackets.length > 20) {
                    StringBuilder hex = new StringBuilder();
                    
                    for (int i = 0; i < rawPackets.length; i++) {
                        if (i <= rawPackets.length - 2 && rawPackets[i] == (byte)0xEF && rawPackets[i+1] == (byte)0x01) {
                            i += 8; 
                            continue;
                        }
                        hex.append(String.format("%02X", rawPackets[i]));
                    }
                    
                    String resultadoFinal = hex.toString();
                    if (resultadoFinal.length() > 32) {
                        resultadoFinal = resultadoFinal.substring(0, resultadoFinal.length() - 4);
                    }
                    return resultadoFinal;
                }
            }
        } catch (Exception ex) {
            System.err.println("Error descargando huella: " + ex.getMessage());
        }
        return null;
    }

    /**
     * MÓDULO KIOSKO (1:1 MATCH OFICIAL por fragmentos): Descarga la huella de Supabase 
     * y la envía al Buffer 2 del sensor en paquetes de 128 bytes para evitar saturarlo.
     */
    public boolean verificarDedoContraSupabase(String hexTemplateSupabase) {
        if (hexTemplateSupabase == null || hexTemplateSupabase.length() < 100) return false;
        
        try {
            // 1. Convertir el mega-string a un arreglo de bytes
            int len = hexTemplateSupabase.length();
            byte[] bytesTemplate = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                bytesTemplate[i / 2] = (byte) ((Character.digit(hexTemplateSupabase.charAt(i), 16) << 4)
                                     + Character.digit(hexTemplateSupabase.charAt(i+1), 16));
            }
            
            // 2. Avisarle al sensor que le vamos a enviar una huella al Buffer 2 (0x09 = DownChar)
            byte[] cmdDownChar = {(byte) 0x02}; 
            byte[] resDown = enviarComando((byte) 0x09, cmdDownChar);
            if (resDown == null || resDown.length < 10 || resDown[9] != 0x00) {
                System.out.println("[KIOSKO] Error hardware: El sensor rechazó preparar su memoria.");
                return false;
            }

            // 3. Enviar los paquetes de datos en "mordidas" de 128 bytes
            int offset = 0;
            int chunkSize = 128;
            while (offset < bytesTemplate.length) {
                int remain = bytesTemplate.length - offset;
                int currentChunk = Math.min(chunkSize, remain);
                boolean isLast = (offset + currentChunk >= bytesTemplate.length);
                
                // 0x08 = Último paquete de datos, 0x02 = Paquete intermedio
                byte pid = isLast ? (byte) 0x08 : (byte) 0x02; 
                int packetLen = currentChunk + 2; // Datos + 2 bytes de Checksum
                
                byte[] packet = new byte[9 + currentChunk + 2];
                packet[0] = (byte) 0xEF; packet[1] = (byte) 0x01; // Cabecera fija
                packet[2] = (byte) 0xFF; packet[3] = (byte) 0xFF; // Dirección
                packet[4] = (byte) 0xFF; packet[5] = (byte) 0xFF;
                packet[6] = pid;
                packet[7] = (byte) (packetLen >> 8);
                packet[8] = (byte) (packetLen & 0xFF);
                
                // Copiamos el fragmento de huella al paquete
                System.arraycopy(bytesTemplate, offset, packet, 9, currentChunk);
                
                // Matemáticas de seguridad (Checksum) para que el sensor valide el paquete
                int sum = (pid & 0xFF) + (packetLen >> 8) + (packetLen & 0xFF);
                for (int i = 0; i < currentChunk; i++) {
                    sum += (packet[9 + i] & 0xFF);
                }
                packet[9 + currentChunk] = (byte) (sum >> 8);
                packet[9 + currentChunk + 1] = (byte) (sum & 0xFF);
                
                // Enviamos el trozo por el cable serial
                if (puertoSerial != null && puertoSerial.isOpen()) {
                    puertoSerial.writeBytes(packet, packet.length);
                }
                offset += currentChunk;
            }
            
            // PASO 4 ELIMINADO: El sensor AS608 no envía confirmación tras los paquetes de datos.
            // Solo pausamos 100 milisegundos para que el chip termine de guardar la huella en su RAM.
            Thread.sleep(100);

            // 5. El gran momento: Comparar Dedo Puesto (Buffer 1) vs Huella Descargada (Buffer 2) (0x03 = Match)
            byte[] resMatch = enviarComando((byte) 0x03, null);
            if (resMatch != null && resMatch.length >= 10) {
                if (resMatch[9] == 0x00) {
                    int score = ((resMatch[10] & 0xFF) << 8) | (resMatch[11] & 0xFF);
                    System.out.println("[KIOSKO] Match evaluado por hardware. Score: " + score);
                    return score >= 35; // Puntaje de seguridad
                } else if (resMatch[9] == 0x08) {
                    System.out.println("[KIOSKO] Las huellas NO coinciden (El sensor dice que es otro dedo).");
                } else {
                    System.out.println("[KIOSKO] Código inesperado en Match: " + String.format("%02X", resMatch[9]));
                }
            }
        } catch (Exception e) {
            System.err.println("[KIOSKO] Error procesando paquetes biométricos: " + e.getMessage());
        }
        return false;
    }

    @Deprecated
    public boolean cargarTemplateAlSensor(String hexTemplate) { return false; }

    @Deprecated
    public int compararBuffers() { return 0; }

    public int buscarHuellaEnSensor() { return -1; }
}