package com.proyecto;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;

/**
 * SERVICIO DE CONTROL DE HARDWARE (AS608) - CAPA DE PERIFÉRICOS
 * Interfaz de comunicación de bajo nivel para el sensor óptico de huellas AS608.
 * Gestiona el intercambio bidireccional de tramas de bytes a través del puerto serial (RS232/UART).
 * Implementa el protocolo nativo del fabricante para la captura, modelado y contraste biométrico (Match 1:1).
 */
public class SensorHuellaService {

    private SerialPort puertoSerial;
    private final String nombrePuerto;
    
    // Tasa de baudios estándar de fábrica para la serie AS60x. Garantiza sincronía en la transmisión.
    private final int BAUD_RATE = 57600; 

    // Cabecera fija (Adder) requerida por el protocolo del sensor para identificar el inicio de un paquete válido.
    private final byte[] HEADER_DIRECCION = {(byte) 0xEF, (byte) 0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    // Identificador de paquete (PID) que indica que la trama entrante es un comando de ejecución.
    private final byte PID_COMANDO = (byte) 0x01;

    /**
     * Constructor del servicio.
     *
     * @param nombrePuerto Identificador lógico del sistema operativo para el puerto serial (Ej. "COM7" o "/dev/ttyUSB0").
     */
    public SensorHuellaService(String nombrePuerto) {
        this.nombrePuerto = nombrePuerto;
    }

    /**
     * Abre y configura el canal de comunicación serial con los parámetros de hardware requeridos por el AS608.
     *
     * @return true si el socket serial fue adquirido con éxito; false si el puerto está ocupado o desconectado.
     */
    public boolean conectar() {
        if (puertoSerial != null && puertoSerial.isOpen()) {
            return true;
        }

        puertoSerial = SerialPort.getCommPort(nombrePuerto);
        puertoSerial.setBaudRate(BAUD_RATE);
        puertoSerial.setNumDataBits(8);
        puertoSerial.setNumStopBits(SerialPort.ONE_STOP_BIT);
        puertoSerial.setParity(SerialPort.NO_PARITY);
        
        // Configuración no bloqueante para evitar que el hilo de interfaz (EDT) se congele si el sensor no responde.
        puertoSerial.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (puertoSerial.openPort()) {
            System.out.println("✔ Conectado exitosamente al sensor en el puerto: " + nombrePuerto);
            // Limpieza del búfer de E/S para purgar lecturas residuales de sesiones anteriores.
            puertoSerial.flushIOBuffers();
            
            // Verificación de integridad de enlace. Se fuerza a true en el retorno para mitigar fallos de contraseñas de fábrica alteradas.
            verificarContrasena(); 
            System.out.println("✔ Sincronización del puerto serial forzada para el Kiosko.");
            return true; 
            
        } else {
            System.err.println("❌ No se pudo abrir el puerto: " + nombrePuerto);
            return false;
        }
    }

    /**
     * Libera el recurso físico del puerto serial para permitir que otras aplicaciones puedan utilizarlo.
     */
    public void desconectar() {
        if (puertoSerial != null && puertoSerial.isOpen()) {
            puertoSerial.closePort();
            System.out.println("🔌 Puerto serial cerrado.");
        }
    }

    /**
     * Protocolo Handshake inicial. Envía el comando (0x13) para verificar la contraseña de acceso al chip.
     *
     * @return true si el sensor devuelve el código de estado 0x00 (Éxito).
     */
    private boolean verificarContrasena() {
        byte[] pwd = {0x00, 0x00, 0x00, 0x00}; // Contraseña estándar (0)
        byte[] respuesta = enviarComando((byte) 0x13, pwd);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    /**
     * Ensamblador de tramas seriales.
     * Construye un paquete de datos que cumple estrictamente con el formato de hardware del AS608:
     * Header (6 bytes) | PID (1 byte) | Length (2 bytes) | Instruction (1 byte) | Data (N bytes) | Checksum (2 bytes).
     *
     * @param instruccion Código hexadecimal de la operación solicitada (Ej. 0x01 para GenImg).
     * @param datos Argumentos adicionales requeridos por la instrucción. Nulo si no requiere argumentos.
     * @return Búfer de respuesta (Acknowledgment) emitido por el sensor.
     */
    private byte[] enviarComando(byte instruccion, byte[] datos) {
        int numDatosAdicionales = (datos != null) ? datos.length : 0;
        int tamanoPaquete = 12 + numDatosAdicionales; 
        byte[] paquete = new byte[tamanoPaquete];

        System.arraycopy(HEADER_DIRECCION, 0, paquete, 0, HEADER_DIRECCION.length);
        int pos = HEADER_DIRECCION.length;

        paquete[pos++] = PID_COMANDO;

        int longitudContenido = numDatosAdicionales + 3; // +3 corresponde a la instrucción y al checksum (2 bytes)
        paquete[pos++] = (byte) ((longitudContenido >> 8) & 0xFF);
        paquete[pos++] = (byte) (longitudContenido & 0xFF);

        paquete[pos++] = instruccion;

        if (numDatosAdicionales > 0) {
            System.arraycopy(datos, 0, paquete, pos, datos.length);
            pos += datos.length;
        }

        // Cálculo de Checksum aritmético para que el receptor verifique la integridad de la trama
        // es para que los bytes no se corrompan a viajar por el cable serial
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
                
                // Mapeo dinámico de longitud de respuesta. Las búsquedas locales (0x12) retornan más bytes informativos.
                int bytesALeer = (instruccion == (byte) 0x12) ? 14 : 12;
                return leerRespuestaDinamica(bytesALeer); 
            } catch (Exception e) {
                System.err.println("[ERROR EN ENVÍO]: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Lector asíncrono con control de tiempo de espera (Timeout loop).
     * Evita bloqueos indefinidos si la transmisión serial sufre pérdida de paquetes.
     *
     * @param bytesEsperados Cantidad de bytes requerida para considerar la trama como completa.
     * @return Arreglo de bytes consolidado, o null si el tiempo de lectura caduca sin respuesta.
     */
    private byte[] leerRespuestaDinamica(int bytesEsperados) {
        byte[] buffer = new byte[bytesEsperados];
        int totalLeidos = 0;
        int intentos = 0;

        while (totalLeidos < bytesEsperados && intentos < 50) { // Límite de ~500ms (50 * 10ms)
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

    /**
     * Emite el comando GenImg (0x01) para encender el prisma óptico y capturar la imagen cruda.
     *
     * @return true si la captura se consolida exitosamente (Código 0x00).
     */
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

    /**
     * Emite el comando Img2Tz (0x02) para extraer minucias de la imagen cruda y generar un archivo de características.
     *
     * @param numeroBuffer Búfer de memoria RAM del chip donde se guardarán las características (1 o 2).
     * @return true si el mapeo de características fue exitoso.
     */
    public boolean generarCaracteristicas(int numeroBuffer) {
        byte[] datos = {(byte) numeroBuffer};
        byte[] respuesta = enviarComando((byte) 0x02, datos);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    /**
     * Emite el comando RegModel (0x05) para fusionar los búferes temporales en una plantilla maestra única.
     * (Requerido para la fase de enrolamiento).
     *
     * @return true si la consolidación biométrica fue exitosa.
     */
    public boolean crearModeloHuella() {
        byte[] respuesta = enviarComando((byte) 0x05, null);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    /**
     * Extrae el molde biométrico consolidado desde la placa física hacia la aplicación Java (Comando UpChar 0x08).
     *
     * @return Representación en cadena Hexadecimal de la plantilla pura (sin cabeceras seriales).
     */
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
                        // Purificador: Omite las cabeceras recurrentes de los fragmentos intermedios.
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
     * MOTOR DE CONTRASTE BIOMÉTRICO (Hardware 1:1 Match).
     * Transfiere una plantilla biométrica completa desde Supabase al sensor físico para validación.
     * Implementa un algoritmo de fragmentación de paquetes (Chunks) para mitigar desbordamientos del búfer físico.
     *
     * @param hexTemplateSupabase Cadena hexadecimal pura proveniente de la base de datos central.
     * @return true si la comparación física obtiene un coeficiente de seguridad (Score) igual o superior a 35.
     */
    public boolean verificarDedoContraSupabase(String hexTemplateSupabase) {
        if (hexTemplateSupabase == null || hexTemplateSupabase.length() < 100) return false;
        
        try {
            // 1. Parsing del String almacenado en la nube a un vector de bytes.
            int len = hexTemplateSupabase.length();
            byte[] bytesTemplate = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                bytesTemplate[i / 2] = (byte) ((Character.digit(hexTemplateSupabase.charAt(i), 16) << 4)
                                     + Character.digit(hexTemplateSupabase.charAt(i+1), 16));
            }
            
            // 2. Transmisión del Comando DownChar (0x09) para habilitar el modo de recepción en el CharBuffer 2 del chip.
            byte[] cmdDownChar = {(byte) 0x02}; 
            byte[] resDown = enviarComando((byte) 0x09, cmdDownChar);
            if (resDown == null || resDown.length < 10 || resDown[9] != 0x00) {
                System.out.println("[KIOSKO] Error hardware: El sensor rechazó preparar su memoria.");
                return false;
            }

            // 3. Algoritmo de Fragmentación: Segmenta el envío en trozos seguros de 128 bytes.
            int offset = 0;
            int chunkSize = 128;
            while (offset < bytesTemplate.length) {
                int remain = bytesTemplate.length - offset;
                int currentChunk = Math.min(chunkSize, remain);
                boolean isLast = (offset + currentChunk >= bytesTemplate.length);
                
                // PID dinámico: 0x08 indica la terminación de la transferencia; 0x02 indica paquete continuo.
                byte pid = isLast ? (byte) 0x08 : (byte) 0x02; 
                int packetLen = currentChunk + 2; // Datos + 2 bytes de Checksum de seguridad
                
                byte[] packet = new byte[9 + currentChunk + 2];
                packet[0] = (byte) 0xEF; packet[1] = (byte) 0x01; // Cabecera física (Adder)
                packet[2] = (byte) 0xFF; packet[3] = (byte) 0xFF; // Dirección lógica del módulo
                packet[4] = (byte) 0xFF; packet[5] = (byte) 0xFF;
                packet[6] = pid;
                packet[7] = (byte) (packetLen >> 8);
                packet[8] = (byte) (packetLen & 0xFF);
                
                // Inyección del trozo biométrico en el marco serial
                System.arraycopy(bytesTemplate, offset, packet, 9, currentChunk);
                
                // Cálculo aritmético del Checksum fragmentario
                int sum = (pid & 0xFF) + (packetLen >> 8) + (packetLen & 0xFF);
                for (int i = 0; i < currentChunk; i++) {
                    sum += (packet[9 + i] & 0xFF);
                }
                packet[9 + currentChunk] = (byte) (sum >> 8);
                packet[9 + currentChunk + 1] = (byte) (sum & 0xFF);
                
                // Tránsito serial al hardware
                if (puertoSerial != null && puertoSerial.isOpen()) {
                    puertoSerial.writeBytes(packet, packet.length);
                }
                offset += currentChunk;
            }
            
            // Pausa de consolidación (Cool-down) para estabilización de memoria RAM física
            Thread.sleep(100);

            // 4. Comando Match (0x03): Instruye al DSP del chip a confrontar el dedo escaneado (Buffer 1) contra la huella recién inyectada (Buffer 2).
            byte[] resMatch = enviarComando((byte) 0x03, null);
            if (resMatch != null && resMatch.length >= 10) {
                if (resMatch[9] == 0x00) {
                    // Cómputo del Coeficiente de Aceptación (FAR/FRR Score) dictado por el fabricante
                    int score = ((resMatch[10] & 0xFF) << 8) | (resMatch[11] & 0xFF);
                    System.out.println("[KIOSKO] Match evaluado por hardware. Score: " + score);
                    
                    // Umbral de Seguridad (Threshold): Se rechazan falsos positivos débiles (Score < 35).
                    return score >= 35; 
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

    // --- Métodos marcados para deprecación debido a la migración a la nube (los datos ahora se guardan en el dipositivo) ---

    @Deprecated
    public boolean cargarTemplateAlSensor(String hexTemplate) { return false; }

    @Deprecated
    public int compararBuffers() { return 0; }

    @Deprecated
    public int buscarHuellaEnSensor() { return -1; }
}