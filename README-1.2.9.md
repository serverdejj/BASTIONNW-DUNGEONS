# BASTION DUNGEONS 1.2.9-dev

- `/dungeon setmobspawn`: estando en una instancia activa, OP guarda un punto de aparición exacto para el schematic. Se pueden guardar varios.
- `/dungeon clearmobspawns`: elimina los puntos del schematic actual y restaura la aparición aleatoria a 5-8 bloques.
- Los puntos se guardan en `plugins/BastionDungeons/mobspawns.yml` por nombre de schematic.
- En mapas sin puntos, los enemigos mantienen la aparición aleatoria anterior.
- Los enemigos alejados se recolocan también en puntos configurados cuando existan.
- El jefe usa puntos configurados cuando existan.
- Nuevo título: ☠ INICIANDO MAZMORRA ☠.
- Salud: Esqueleto Maldito 120 HP; Guardián Esquelético 300 HP; Rey Esqueleto 1200 HP efectivos (1024 vida + 176 absorción por límite vanilla).

## Instalación
Subir este código fuente al repositorio GitHub, ejecutar Actions y descargar el JAR generado. Hacer copia de seguridad antes de reemplazar la versión instalada.
