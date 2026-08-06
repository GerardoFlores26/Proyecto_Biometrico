#!/bin/bash
# Script de automatización para el Kiosco Biométrico

echo "Verificando conexión de red..."
# Espera 10 segundos a que la Raspberry Pi se conecte al Wi-Fi al encender
sleep 10

echo "Iniciando la aplicación del Kiosco..."
# Navega a la carpeta real en tu Raspberry Pi
cd /home/admin/kiosko

# Ejecuta el programa de Java automáticamente con permisos de hardware
sudo java -jar kiosko-raspberry-1.0-SNAPSHOT-jar-with-dependencies.jar