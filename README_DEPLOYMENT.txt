╔═════════════════════════════════════════════════════════════════════════════╗
║                      🚀 DESPLIEGUE LOCAL LISTO                             ║
║                  TravelBox Peru Backend - Perfil Production                ║
╚═════════════════════════════════════════════════════════════════════════════╝

✅ CONFIGURACIÓN COMPLETADA

Se han creado todos los archivos necesarios con los secretos de Azure KeyVault:
  ✓ Variables de entorno (.env.production.local)
  ✓ Scripts de compilación y ejecución
  ✓ Documentación completa
  ✓ Scripts de diagnóstico

═══════════════════════════════════════════════════════════════════════════════

🎯 PARA EJECUTAR AHORA MISMO:

Abre una terminal CMD o PowerShell en el directorio del proyecto:

    C:\Users\GianLH\Desktop\PROYECTI\TravelBox_Peru_Backend

Y ejecuta UNO de estos comandos:

    1. RUN_NOW.bat                 ← RECOMENDADO (compilar + ejecutar)
    
    2. deploy-simple.bat           ← Alternativa sin logs ocultos
    
    3. START.bat                   ← Con logging automático
    
    4. diagnose.bat                ← Verificación previa (opcional)

═══════════════════════════════════════════════════════════════════════════════

📋 QUÉ HACE RUN_NOW.bat:

1. Verifica Java está disponible
2. Verifica Maven está disponible
3. Carga variables de .env.production.local
4. Muestra configuración (BD, Firebase, etc.)
5. Limpia compilaciones anteriores
6. Compila el proyecto (mvnw.cmd clean package)
7. Genera el JAR en target/
8. Inicia la aplicación con:
   - spring.profiles.active=prod
   - Credenciales de Azure KeyVault
   - Configuración de producción local

═══════════════════════════════════════════════════════════════════════════════

✅ CUANDO FUNCIONA - DEBERÍAS VER:

    ...
    2026-03-22 23:XX:XX.XXX  INFO X XXX - Started StorageApplication in X.XXX seconds
    ...

Luego accede a:
    http://localhost:8080
    http://localhost:8080/actuator/health

═══════════════════════════════════════════════════════════════════════════════

🔍 SI ALGO FALLA:

1. Lee los errores que muestre en pantalla
2. Si necesitas ver logs después:
   
   type deployment-current.log      (logs completos)
   type deployment-current.err      (solo errores)

3. Si la ventana se cierra rápido:
   
   Ejecuta en PowerShell:
   & ".\deploy-simple.bat"

═══════════════════════════════════════════════════════════════════════════════

⚙️  CONFIGURACIÓN CARGADA (de Azure KeyVault):

Base de Datos:
  URL: jdbc:postgresql://34.151.246.247:5432/travelbox?sslmode=require
  Usuario: travelbox
  Servidor: GCP Cloud SQL

Azure KeyVault:
  Habilitado: true
  Endpoint: https://kvtravelboxpe.vault.azure.net/

Firebase:
  Proyecto: travelboxperu-f96ee
  Bucket: travelboxperu-f96ee.firebasestorage.app

SMTP/Email:
  Host: smtp-relay.brevo.com
  Puerto: 587

Aplicación:
  Perfil: prod
  Puerto: 8080
  URL: http://localhost:8080

═══════════════════════════════════════════════════════════════════════════════

🛑 REQUISITOS PREVIOS:

  ✓ Java 21+
    Verificar: java -version
    
  ✓ Maven (incluido como mvnw.cmd)
    
  ✓ Archivo .env.production.local (ya creado)
    
  ✓ Azure CLI (opcional pero recomendado)
    Si necesitas KeyVault dinámico: az login

═══════════════════════════════════════════════════════════════════════════════

📚 ARCHIVOS PRINCIPALES:

Script de inicio:
  RUN_NOW.bat                    ← EJECUTA ESTO
  deploy-simple.bat              ← Alternativa sin logs ocultos
  START.bat                       ← Con logging automático
  deploy-prod-local.bat           ← Script mejorado
  diagnose.bat                    ← Diagnóstico previo

Configuración:
  .env.production.local           ← Variables (NO commitear)
  src/main/resources/application-prod.yml  ← Config Spring Boot

Documentación:
  DEPLOYMENT_QUICK_START.md       ← Guía rápida
  DEPLOYMENT_LOCAL_PROD.md        ← Documentación completa
  README.md                        ← Readme general

═══════════════════════════════════════════════════════════════════════════════

🚀 LISTO PARA DESPLEGAR

Abre una terminal y ejecuta:

    RUN_NOW.bat

Eso es todo. La aplicación se compilará e iniciará.

═══════════════════════════════════════════════════════════════════════════════

¿Problemas?

1. Ejecuta: diagnose.bat
2. Lee: DEPLOYMENT_QUICK_START.md
3. Comparte: deployment-current.log + deployment-current.err

═══════════════════════════════════════════════════════════════════════════════

Generado: 2026-03-22 23:21
Versión: 0.0.1-SNAPSHOT
Perfil: prod
