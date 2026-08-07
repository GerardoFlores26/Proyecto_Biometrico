# 🏫 Sistema Biométrico de Control de Asistencia

Sistema de control de asistencia mediante reconocimiento de huellas dactilares, desarrollado como proyecto técnico para la Escuela CNCI. La propuesta integra un sensor biométrico AS608, aplicaciones de escritorio nativas en Java, un kiosco interactivo y una base de datos en la nube mediante Supabase.

---

## 🏢 1. Datos de la Institución

**Universidad CNCI (Campus Cumbres)**
Institución educativa privada enfocada en brindar servicios educativos accesibles y de alta calidad mediante modelos de aprendizaje innovadores.

- **Misión:** Formar personas con conocimientos, habilidades y actitudes utilizando procesos tecnológicos e innovadores, dirigidos por personal competente.
- **Visión:** Ser reconocida como la universidad privada más incluyente y accesible, referente por la efectividad de sus modelos de aprendizaje.
- **Ubicación:** Monterrey, Nuevo León, México.

---

## 🔍 2. Análisis de Requerimientos

### Problemática

El proceso tradicional de registro de asistencia mediante papel genera:

- Errores humanos durante el registro.
- Pérdida de tiempo valioso durante el pase de lista.
- Riesgos críticos relacionados con la suplantación de identidad.
- Dificultad para consultar información histórica y generar reportes.
- Duplicidad de actividades administrativas entre docentes y personal de coordinación.

### Requerimientos Funcionales (RF)

- **RF-01:** Registro de usuarios (alumnos y docentes).
- **RF-02:** Gestión y captura de huellas biométricas.
- **RF-03:** Gestión de horarios, materias y salones.
- **RF-04:** Asignación de materias a docentes y grupos.
- **RF-05:** Consulta de asistencias.
- **RF-06:** Generación de reportes.
- **RF-07:** Administración (CRUD) de usuarios.
- **RF-08:** Aceptación del aviso de privacidad.
- **RF-09:** Captura biométrica en el Kiosco.
- **RF-10:** Validación de identidad en tiempo real.
- **RF-11:** Registro automático de asistencia.
- **RF-12:** Identificación automática de materia por bloque horario.
- **RF-13:** Mensaje de bienvenida en pantalla interactiva.
- **RF-14:** Manejo de errores (huella no registrada, fallo de sensor, desconexión).

### Requerimientos No Funcionales (RNF)

- Seguridad de credenciales y protección de datos biométricos.
- Validación de huella en menos de **3 segundos**.
- Respuesta sin bloqueos gráficos (Multithreading) durante el registro.
- Arquitectura modular y multiplataforma (Windows y Raspberry Pi OS).
- Confiabilidad y tolerancia a fallos de red.

---

## 💡 3. Solución Propuesta

Implementar un sistema automatizado de asistencia basado en huellas dactilares que permita identificar a los usuarios, registrar su asistencia y centralizar la información para facilitar la administración.

### Arquitectura del Sistema

La arquitectura general está dividida en tres capas:

1. **Capa Cliente (Interfaces Java Swing / AWT):**
   - _Panel de Administración:_ Para gestión de datos maestros y enrolamiento en PC.
   - _Kiosko de Asistencia:_ Ejecutado en Raspberry Pi para el escaneo de entrada.
2. **Capa de Negocio (Backend / Lógica Java):**
   - Procesa la información enviada por el Kiosko.
   - Identifica al usuario, consulta el horario y aplica las reglas de registro (Asistencia/Retardo).
3. **Capa de Datos (Supabase / PostgreSQL):**
   - Almacenamiento seguro en la nube.
   - Comunicación mediante túnel JDBC (Java Database Connectivity).

### Tecnologías y Hardware Utilizado

## Tecnologías utilizadas

| Tecnología       | Uso                                                      |
| ---------------- | -------------------------------------------------------- |
| Java             | Desarrollo de la aplicación y lógica central del sistema |
| Java Swing / AWT | Desarrollo de interfaces gráficas interactivas           |
| Java JDBC        | Controlador nativo para comunicación con PostgreSQL      |
| PostgreSQL       | Sistema gestor de base de datos relacional               |
| Supabase         | Plataforma de servidor y alojamiento de datos en la nube |
| Raspberry Pi OS  | Sistema operativo del kiosco biométrico                  |
| Bash             | Automatización del despliegue                            |
| jlink            | Generación del runtime de Java optimizado                |
| GitHub           | Control de versiones y almacenamiento del código fuente  |

### Flujo General de Funcionamiento

Usuario ➔ Coloca el dedo en el sensor AS608
➔ Kiosco (Raspberry Pi) captura minucias
➔ Lógica Java valida reglas de negocio (Horario/Materia)
➔ Conexión JDBC envía a Supabase (PostgreSQL)
➔ Supabase retorna confirmación
➔ Kiosco muestra mensaje visual (Verde/Rojo).

###📦 4. Alcance del Proyecto
Incluye:

- Aplicación de escritorio administrativa multiplataforma.
- Kiosco físico interactivo para validación biométrica.
- Sincronización de registros de asistencia en la nube.
- Extracción purificada de minucias (sin almacenar imágenes físicas de las huellas).

No incluye:

-Cálculo de nóminas o pagos a docentes.
-Control de calificaciones o portal de tareas del alumno.
-Integración con torniquetes físicos o chapas magnéticas (el control es 100% por software)

### 🎯 5. Objetivos

Objetivo General:
Desarrollar e implementar un sistema multiplataforma que automatice y controle el registro de asistencia mediante hardware biométrico, centralizando la información en la nube para eliminar el pase de lista manual.

Objetivos Específicos:

- Reducir el tiempo de pase de lista en las aulas a menos de 3 segundos por alumno.
- Erradicar al 100% la suplantación de identidad mediante tecnología dactilar.
- Garantizar la disponibilidad del sistema implementando caché en RAM para que el kiosco funcione sin latencia.
- Generar reportes históricos de asistencia exportables a Excel sin errores de codificación.

### 👥 6. Usuarios o Actores

| Rol                                | Descripción de Permisos                                    | Principales                                                         |
| ---------------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------- |
| Administrador<br>(Control Escolar) | Personal encargado de la gestión académica y del hardware. | CRUD total de usuarios, horarios y huellas. Generación de reportes. |
| Alumno                             | Estudiante inscrito en el campus.                          | Solo lectura/validación biométrica en el kiosco.                    |
| Docente                            | Profesor titular de las materias.                          | Validación de asistencia en el kiosco y auditorías de sus aulas.    |

### 📄 7. Casos de Uso Principales

- CU01: Enrolamiento Biométrico: El administrador captura los datos del alumno y escanea su huella 2 veces en el sensor AS608 para generar una plantilla matemática.
- CU02: Registro de Asistencia: El alumno coloca su dedo en el Kiosco; el sistema valida la huella, revisa el reloj del sistema y marca asistencia, retardo o falta.
- CU03: Exportación de Reportes: El administrador filtra un salón y semana específicos para descargar un documento CSV/Excel (UTF-8 con BOM) de asistencias.
- CU04: Manejo de Falla de Red (Offline): El sistema del Kiosco detecta intermitencia, evalúa desde la Caché y previene el congelamiento de la aplicación.

### 📊 8. Criterios de Éxito

- Velocidad: Validación de identidad y actualización gráfica de pantalla en < 3 segundos.
- Seguridad: Tasa de Falsa Aceptación (FAR) mitigada utilizando un nivel de seguridad 4 en el AS608.
- Integridad: 100% de éxito en el algoritmo purificador de bytes (eliminación de ruido serial antes de persistir en PostgreSQL).
- Usabilidad: Interfaz interactiva donde el alumno no requiere tocar teclado ni ratón.

### 🛠️ 9. Plan de Trabajo (Metodología Ágil)

- Sprint 1 — Aprovisionamiento y Hardware: Creación del esquema relacional DDL, configuración de Supabase y comunicación inicial por puerto serial.
- Sprint 2 — Cliente Java y Lógica: Desarrollo del cliente JDBC, manejo de hilos (Multithreading) y procesamiento de la información del lector biométrico.
- Sprint 3 — Interfaz Visual (UI): Desarrollo de las vistas en Java Swing y pruebas de comunicación entre el sensor, Java y la interfaz.
- Sprint 4 — Reglas y Despliegue: Desarrollo del motor de reglas para horarios, empaquetado del software y configuración del despliegue en Raspberry Pi.

### ⚠️ 10. Riesgos Técnicos y Seguridad

- Conflictos de concurrencia en puerto serial: Múltiples procesos intentando acceder al sensor. Mitigación: Módulo Java con acceso exclusivo y sincronizado.
- Pérdida de datos por corte de energía: Corrupción de caché local. Mitigación: Escrituras síncronas y vaciado inmediato de búfer.
- Privacidad Biométrica: No se guardan imágenes de huellas, solo representaciones hexadecimales cifradas y se requiere aceptación de aviso de privacidad.

### 📂 11. Estructura y Despliegue

Proyecto_Biometrico/
│
├── admin/ ➔ Aplicación de administración (Windows)
├── kiosco/ ➔ Aplicación de asistencia (Raspberry Pi OS)
├── backend/ ➔ Lógica de negocio y controladores
├── database/ ➔ Scripts SQL / DDL (Supabase)
├── hardware/ ➔ Servicios seriales AS608 (USB-TTL)
└── deploy/ ➔ Scripts automatizados (Bash/jlink)

### 👨‍💻 12. Equipo de Desarrollo y Repositorio

Repositorio del proyecto:
https://github.com/GerardoFlores26/Proyecto_Biometrico

# Autores (Ingeniería en Tecnologías Computacionales):#

- Victor Hugo Solano Velazquez — E074
- Gerado Flores Tobias
- Emiliano Jimenez Reyes
- Jose Luiz Razon Leyva
- Mauricio De Jesus Silva Vazquez — E855

# Proyecto Académico:#

- Institución: Escuela CNCI (Monterrey, Nuevo León, México)
- Materias Integradas: Programación III, Ingeniería de Software, Desarrollo de Aplicaciones de Integración.
- Docente: Blanca Aracely Aranda Machorro

Fecha: 28 de Junio de 2026

Licencia: Proyecto desarrollado con fines académicos para la Universidad CNCI

# recomendaciones/anotaciones sobre el proyecto

- Este fragmento de codigo de cambia dentro del (MAINKIOSKO)para poder hacer pruebas dentro de windows; el puesto com7 cambia dependienmdo de la pc donde se use ,actualmente esta en la rama principal la opcion para usarse en la raspberry fuera de ahi para usarse en pc usar este fragmento que tiene el puerto diferente

new Thread(() -> {
sensor = new SensorHuellaService("COM7");

            while (true) {
                try {
                    if (sensor.conectar()) {
                        lblConsejo.setText("💡 Lector AS608 en línea. Coloque su dedo sobre el sensor para pasar asistencia...");
                        lblConsejo.setForeground(new Color(39, 174, 96));

                        // Paso 1: Monitoreo y captura de la imagen física del dedo
                        if (sensor.capturarFotoDedo()) {
                            // Paso 2: Generación del molde matemático de rasgos en el búfer temporal 1 del chip
                            if (sensor.generarCaracteristicas(1)) {
                                // Delegación segura al EDT para actualizar etiquetas textuales durante el proceso
                                SwingUtilities.invokeLater(() -> {
                                    lblStatus.setText("PROCESANDO HUELLA...");
                                    lblStatus.setForeground(new Color(211, 84, 0));
                                });

                                // Paso 3: Disparo del motor de contraste biométrico y validación horaria
                                evaluarAccesoBiometrico();
                            }
                            // Retraso de seguridad (Cool down) para evitar registros duplicados por el mismo marcaje
                            Thread.sleep(3500);
                        }
                    } else {
                        lblConsejo.setText(" Error: No se detecta el lector de huellas en el puerto asignado.");
                        lblConsejo.setForeground(Color.RED);
                    }
                    Thread.sleep(250); // Frecuencia de muestreo ante estados inactivos
                } catch (Exception e) {
                    System.err.println("Error crítico en el hardware: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();

# opciones a mejorar

- Arreglar el problema de desface de horarios de 6 horas
- Para ver las asistencias de maestros es muy tardado en boton puede tener alguna falla
- En el area de registo al alumnado agregar todos los dias de la semana para tener el horario por dia no por semana
- Para darle diseño al archivo de excel se puede instalar una librebria llamada apache poi es complicada pero funciona o plantillas usando JXLS
- Agregar turnos pues ahorita nada mas esta el nocturno pero esta la opcion de poner matutino y sabatino tanto en codigo como en en menu de registro poner el turno
- Agregar retardos pues se planeo pero no se implemento
- Opcion para directivos que funcione para todos no solo alumnos y maestros
