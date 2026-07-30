#!/bin/bash
# Script de automatización para el Kiosco Biométrico

echo "Verificando conexión de red..."
# Espera 10 segundos a que la Raspberry Pi se conecte al Wi-Fi al encender
sleep 10 

echo "Iniciando la aplicación del Kiosco..."
# Navega a la carpeta donde está tu programa (ajusta la ruta según tu Raspberry)
cd /home/pi/Proyecto_Biometrico/kiosco

# Ejecuta el programa de Java automáticamente
java -jar kiosco-app.jar