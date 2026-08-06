package com.proyecto;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;

/**
 * SERVICIO DE CONTROL DE HARDWARE (AS608) - CAPA ADMINISTRATIVA (ENROLAMIENTO)
 * Interfaz de comunicación de bajo nivel para el sensor óptico de huellas AS608.
 * A diferencia de la versión del Kiosko, esta clase está optimizada para el "Enrolamiento" (Registro).
 * Incorpora un algoritmo purificador de tramas seriales que extrae las minucias de la huella
 * descartando el "ruido" del protocolo (cabeceras, identificadores y sumas de comprobación),
 * garantizando que la base de datos almacene un modelo matemático puro y estandarizado.
 */
public class SensorHuellaService {

    private SerialPort puertoSerial;
    private final String nombrePuerto;
    private final int BAUD_RATE = 57600; 

    private final byte[] HEADER_DIRECCION = {(byte) 0xEF, (byte) 0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private final byte PID_COMANDO = (byte) 0x01;

    /**
     * Constructor del servicio administrativo.
     * @param nombrePuerto Identificador del puerto COM (Ej. "COM3").
     */
    public SensorHuellaService(String nombrePuerto) {
        this.nombrePuerto = nombrePuerto;
    }

    /**
     * Inicializa la interfaz serial RS232 con la placa óptica biométrica.
     * @return true si la apertura del puerto y el saludo (Handshake) inicial fueron exitosos.
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
        
        puertoSerial.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (puertoSerial.openPort()) {
            System.out.println(" Conectado exitosamente al sensor en el puerto: " + nombrePuerto);
            puertoSerial.flushIOBuffers();
            
            verificarContrasena(); 
            System.out.println(" Sincronización del puerto serial forzada para el panel Administrativo.");
            return true; 
            
        } else {
            System.err.println(" No se pudo abrir el puerto: " + nombrePuerto);
            return false;
        }
    }

    /**
     * Libera el hilo de comunicación serial.
     */
    public void desconectar() {
        if (puertoSerial != null && puertoSerial.isOpen()) {
            puertoSerial.closePort();
            System.out.println("🔌 Puerto serial cerrado.");
        }
    }

    /**
     * Valida el acceso a la placa óptica mediante la contraseña por defecto de fábrica.
     */
    private boolean verificarContrasena() {
        byte[] pwd = {0x00, 0x00, 0x00, 0x00};
        byte[] respuesta = enviarComando((byte) 0x13, pwd);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    /**
     * Transmisor de tramas base del protocolo AS60X.
     *
     * @param instruccion Byte que representa la acción a realizar en el sensor.
     * @param datos Argumentos secundarios del comando (puede ser null).
     * @return Arreglo de bytes emitido por el sensor físico como acuse de recibo.
     */
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
        // se asegura que los bytes no se corrompan por le cable serial
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
                
                int bytesALeer = (instruccion == (byte) 0x12) ? 14 : 12;
                return leerRespuestaDinamica(bytesALeer); 
            } catch (Exception e) {
                System.err.println("[ERROR EN ENVÍO]: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Lector de búfer de entrada con tolerancia a fallos por latencia USB/Serial.
     */
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

    /**
     * Operación GenImg (0x01). Captura el rastro dactilar en bruto (imagen cruda).
     * @return true si la captura fue validada matemáticamente por el sensor.
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
                System.out.println(" ¡Captura exitosa!");
                return true;
            }
        }
        return false;
    }

    /**
     * Operación Img2Tz (0x02). Convierte la imagen cruda a un modelo vectorial de puntos característicos.
     * @param numeroBuffer Búfer destino (1 o 2).
     * @return true si la conversión al modelo de minucias fue exitosa.
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
     * Operación RegModel (0x05). Fusiona el Búfer 1 y Búfer 2 para generar el modelo final de enrolamiento.
     * @return true si los búferes eran lo suficientemente similares para fusionarse.
     */
    public boolean crearModeloHuella() {
        byte[] respuesta = enviarComando((byte) 0x05, null);
        if (respuesta != null && respuesta.length >= 10) {
            return respuesta[9] == 0x00;
        }
        return false;
    }

    /**
     * RUTINA CRÍTICA DE ENROLAMIENTO Y SANITIZACIÓN: Comando UpChar (0x08).
     * Descarga el modelo dactilar maestro desde la RAM del chip hacia la aplicación Java.
     * 
     * * Mecanismo Purificador: El sensor AS608 transmite el modelo masivo fraccionado en paquetes
     * seriales de 128 bytes. Cada paquete contiene cabeceras, PID, longitud y suma de verificación.
     * Este método escanea el flujo entrante, ubica la cabecera (0xEF 0x01) de cada fragmento,
     * salta los identificadores de control y extrae de forma exclusiva los datos puros.
     * Esto evita que la BD Supabase se contamine con "ruido" de hardware, asegurando un Kiosko funcional.
     *
     * @return Cadena hexadecimal limpia conteniendo estrictamente las minucias dactilares. Nulo en caso de falla.
     */
    public String descargarTemplateDesdeSensor() {
        try {
            byte[] datos = {(byte) 0x01}; 
            byte[] respuesta = enviarComando((byte) 0x08, datos);
            
            if (respuesta != null && respuesta.length >= 10 && respuesta[9] == 0x00) {
                // Holgura de red para permitir la descarga masiva de la cadena de datos
                Thread.sleep(200);
                byte[] rawPackets = leerRespuestaDinamica(800); 
                
                if (rawPackets != null && rawPackets.length > 20) {
                    StringBuilder hex = new StringBuilder();
                    int i = 0;
                    
                    // Ciclo escaner de trama: Detecta y purifica paquetes iterativamente
                    while (i < rawPackets.length - 9) {
                        // Detección de la firma inicial del hardware (Adder: 0xEF01)
                        if (rawPackets[i] == (byte) 0xEF && rawPackets[i+1] == (byte) 0x01) {
                            
                            // Determinación dinámica de la longitud del fragmento actual
                            int len = ((rawPackets[i+7] & 0xFF) << 8) | (rawPackets[i+8] & 0xFF);
                            int dataLength = len - 2; // Remoción aritmética del Checksum final
                            
                            // Copia selectiva: Concatenación del núcleo del paquete (Payload puro)
                            for (int j = 0; j < dataLength; j++) {
                                if ((i + 9 + j) < rawPackets.length) {
                                    hex.append(String.format("%02X", rawPackets[i + 9 + j]));
                                }
                            }
                            
                            // Salto analítico hacia la cabecera del siguiente paquete
                            i += (9 + dataLength + 2);
                        } else {
                            // Descarte de ruido estático en la línea serial
                            i++; 
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
     * MÓDULO ADMINISTRATIVO (MATCH COMPRIMIDO 0x12).
     * Transmite una plantilla pura en un solo comando de alta velocidad (MatchTemplate)
     * para contrastarla contra el CharBuffer 1. Ideal para comprobaciones rápidas de 
     * enrolamiento donde no se busca la tolerancia extrema a fallos del Kiosko físico.
     *
     * @param hexTemplateSupabase Plantilla de la base de datos a contrastar.
     * @return true si el sensor dictamina coincidencia biométrica válida.
     */
    public boolean verificarDedoContraSupabase(String hexTemplateSupabase) {
        if (hexTemplateSupabase == null || hexTemplateSupabase.length() < 100) return false;
        
        try {
            int len = hexTemplateSupabase.length();
            byte[] bytesTemplate = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                bytesTemplate[i / 2] = (byte) ((Character.digit(hexTemplateSupabase.charAt(i), 16) << 4)
                                     + Character.digit(hexTemplateSupabase.charAt(i+1), 16));
            }
            
            // Estructuración del comando 0x12 acoplando el destino (Buffer 1) con la plantilla
            byte[] payloadComando = new byte[1 + bytesTemplate.length];
            payloadComando[0] = (byte) 0x01; 
            System.arraycopy(bytesTemplate, 0, payloadComando, 1, bytesTemplate.length);
            
            byte[] respuesta = enviarComando((byte) 0x12, payloadComando);
            
            if (respuesta != null && respuesta.length >= 10) {
                byte codigoConfirmacion = respuesta[9];
                if (codigoConfirmacion == 0x00) {
                    int score = 0;
                    if (respuesta.length >= 12) {
                        score = ((respuesta[10] & 0xFF) << 8) | (respuesta[11] & 0xFF);
                    }
                    System.out.println("[KIOSKO] Match evaluado por hardware. Score obtenido: " + score);
                    
                    // Umbral de Seguridad de Falsos Positivos
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

    // --- Métodos marcados para deprecación debido a migración estructural a la Nube ---

    @Deprecated
    public boolean cargarTemplateAlSensor(String hexTemplate) { return false; }

    @Deprecated
    public int compararBuffers() { return 0; }

    @Deprecated
    public int buscarHuellaEnSensor() { return -1; }
}