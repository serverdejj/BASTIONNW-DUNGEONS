# BASTION DUNGEONS 1.2.42 - patrulla dragon / proyectiles

- Corrige la v1.2.41, que incluia fireballs en YAML sin crear proyectiles en Java.
- Ruta de vuelo por los puntos de adventure.yml, limitados a 30 bloques horizontales del spawn.
- 10 segundos de desplazamiento y 10 de parada (configurables).
- Bolas LargeFireball dirigidas al jugador vivo mas cercano, cada 3 segundos.
- Spawn por debajo de techo con margen; rutas tratan de conservar 5 bloques libres por encima.
- Explosiones sin romper bloques; damage/splash/fire configurables.
- Registro de inicio de ruta y aviso de teletransporte bloqueado en consola.

INSTALACION: Compilar proyecto en GitHub Actions, conservar adventure.yml y loot.yml, sustituir JAR.
Agregar flight.max-radius-blocks: 30.0 y flight.fallback-orbit-radius: 24.0 al config.
No se ha verificado una compilacion real ni probado en Purpur. Si sigue inmovil enviar latest.log.
