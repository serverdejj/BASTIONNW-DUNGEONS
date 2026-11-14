# BASTION DUNGEONS 1.2.15-dev

Corrige la carga bloqueante de chunks al ejecutar `/dungeon go`.

- Crea el mundo temporal con generador interno, sin DeepVoid.
- Carga los chunks de la estructura asincronamente en lotes de 8.
- Pega el schematic con WorldEdit tras completar la carga.
- Informa de errores y limpia el mundo temporal si falla la preparacion.
- Conserva las mecanicas de la version anterior.

**Instalacion:** compilar con GitHub Actions, colocar el JAR en `plugins/` y reiniciar. Hacer copia de seguridad antes de probar. No se ha validado la compilacion en este entorno.
