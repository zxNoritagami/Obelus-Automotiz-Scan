# 🏎️ Obelus Automotriz Scan

Scanner OBD2 profesional para Android. Conecta con tu vehículo vía Bluetooth, lee sensores en tiempo real, diagnostica códigos de error y guarda historial de viajes.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg?style=flat&logo=android)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange.svg?style=flat)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat)](LICENSE)

## ✨ Características

- 📊 **Dashboard en Tiempo Real**: Gauges visuales para RPM, velocidad, temperatura, carga del motor y posición del acelerador
- 🔍 **Diagnóstico DTC**: Lee y borra códigos de error (check engine)
- 💾 **Historial de Viajes**: Guarda sesiones completas con estadísticas
- 📈 **Gráficos Detallados**: Analiza el comportamiento de tu vehículo sesión por sesión
- 📤 **Exportación CSV**: Comparte datos con tu mecánico
- 🔌 **Compatible ELM327**: Funciona con adaptadores Bluetooth estándar

## 🛠️ Tecnologías

- **Lenguaje**: Kotlin + Coroutines
- **UI**: Jetpack Compose (Material3)
- **Inyección de Dependencias**: Hilt
- **Persistencia**: Room Database
- **Conectividad**: Bluetooth Classic (RFCOMM/SPP)
- **Arquitectura**: MVVM Clean Architecture

## 📋 Requisitos

- Android 8.0+ (API 26)
- Adaptador ELM327 Bluetooth (v1.5 o v2.1)
- Permisos de Bluetooth (Connect/Scan) y Ubicación (para legacy scannning)

## 🚀 Instalación

1. Clonar repositorio:
   ```bash
   git clone https://github.com/tu-usuario/obelus.git
   ```
2. Abrir en Android Studio Hedgehog o superior.
3. Esperar sincronización de Gradle (Sync Gradle).
4. Ejecutar en dispositivo físico (el emulador no soporta Bluetooth Classic nativamente):
   - Activa "Opciones de Desarrollador" y "USB Debugging" en tu teléfono.
   - Conecta vía USB y dale Play ▶️.

## 📸 Screenshots

| Dashboard | DTCs | Historial | Gráficos |
|:---:|:---:|:---:|:---:|
| ![Dashboard](docs/screenshots/dashboard.png) | ![DTCs](docs/screenshots/dtcs.png) | ![History](docs/screenshots/history.png) | ![Charts](docs/screenshots/charts.png) |
*(Screenshots pendientes de añadir)*

## 🏗️ Arquitectura

El proyecto sigue una arquitectura **MVVM (Model-View-ViewModel)** con principios de Clean Architecture:

- **presentation**: UI (Compose) y ViewModels.
- **domain**: Casos de uso y modelos de negocio puramente Kotlin (en desarrollo).
- **data**: Repositorios, fuentes de datos (Local/Remote/Bluetooth) y Mappers.
- **protocol**: Lógica de bajo nivel para comunicación ELM327 y decodificación OBD2.

## 🤝 Contribuciones

PRs son bienvenidos. Por favor, abrir un issue primero para discutir cambios mayores o nuevas funcionalidades.

1. Fork el proyecto
2. Crea tu rama de feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📄 Licencia

Este proyecto está bajo la Licencia MIT - ver el archivo [LICENSE](LICENSE) para más detalles.

## 🙏 Agradecimientos

- Protocolo OBD2 estándar (ISO 9141-2, ISO 14230-4, ISO 15765-4)
- Documentación de comandos ELM327
- Comunidad Open Source de Android
