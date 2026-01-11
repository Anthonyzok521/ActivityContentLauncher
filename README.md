# ActivityContentLauncher (ACL)

**ActivityContentLauncher** es un launcher personalizado diseñado específicamente para entornos de **KiosKos y Vending Machines**. La aplicación actúa como un Shell de sistema que restringe el uso del dispositivo a aplicaciones autorizadas, proporcionando control total sobre el hardware y la interfaz de usuario.

## 🚀 Características Principales

* **Modo Kiosco Estricto:** Bloqueo de barra de navegación y notificaciones.
* **Administración de Dispositivo:** Capacidad de reiniciar, apagar y bloquear la pantalla mediante privilegios de *Device Owner*.
* **Interfaz Adaptable:** Reloj dinámico y grid de aplicaciones optimizado para diferentes resoluciones.
* **Seguridad:** Diseñado para operar en entornos desatendidos sin acceso al sistema operativo subyacente.

---

## 🛠️ Requisitos Previos

Antes de ejecutar o instalar la aplicación, asegúrate de tener:

1. **Android SDK API 26** o superior.
2. **Dispositivo Android** (preferiblemente con una instalación limpia para configurar el Device Owner).
3. **ADB (Android Debug Bridge)** instalado en tu PC.

---

## 🔧 Configuración de Device Owner

Para que las funciones de **Apagado, Reinicio y Bloqueo** funcionen, la aplicación DEBE ser establecida como el dueño del dispositivo (*Device Owner*). Este privilegio no se puede obtener mediante una instalación normal de APK.

### Pasos para habilitarlo:

1. **Elimina todas las cuentas de Google** del dispositivo (Ajustes > Cuentas). Si el dispositivo es nuevo, no inicies sesión en ninguna cuenta durante el setup.
2. Instala el APK en el dispositivo:
```bash
adb install app-release.apk

```


3. Ejecuta el siguiente comando desde la terminal de tu PC para otorgar los privilegios de administrador:
```bash
adb shell dpm set-device-owner com.acteam.acl/.DeviceAdminReceiver

```


> **Nota:** Si recibes un error indicando que ya existen cuentas en el dispositivo, debes borrarlas todas o realizar un Factory Reset y ejecutar el comando antes de configurar cualquier cuenta.



---

## 📂 Estructura del Proyecto

* `com.acteam.acl.MainActivity`: Punto de entrada principal y manejo de lógica de administración.
* `com.acteam.acl.ui.Home`: Interfaz de usuario construida con Jetpack Compose.
* `com.acteam.acl.DeviceAdminReceiver`: Receptor encargado de gestionar las políticas de seguridad.
* `com.acteam.acl.utils.Clock`: Componente de reloj con soporte para diferentes densidades de pantalla.

---

## 🛠️ Ejecución y Desarrollo

1. Clona el repositorio.
2. Abre el proyecto en **Android Studio (Ladybug o superior)**.
3. Sincroniza el proyecto con los archivos Gradle (`build.gradle.kts`).
4. Ejecuta la variante de `release` o `debug` en tu dispositivo.

### Comandos útiles de ADB para Kioskos:

* **Forzar parada del launcher:**
```bash
adb shell am force-stop com.acteam.acl

```


* **Verificar si es Device Owner:**
```bash
adb shell dpm list-owners

```

## ⚠️ Advertencia de Seguridad

Este software está diseñado para **vending machines**. Al activarse como *Device Owner*, la aplicación tiene control total sobre el ciclo de vida del hardware. No instales este software en dispositivos personales sin entender que el borrado de la aplicación puede requerir un restablecimiento de fábrica manual a través de Recovery.

---

**Desarrollado por:** AdvancedCommunity | 2026