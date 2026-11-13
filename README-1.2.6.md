# BASTION DUNGEONS 1.2.6-dev

## Cambios
- Título de victoria amarillo HEX `#FFF800` en negrita: `¡VICTORIA!`.
- Subtítulo blanco: `¡Han derrotado a todos los enemigos!`.
- Horario global: 30 minutos cerrado y 5 minutos abierto; comienza cerrado al instalar esta versión por primera vez.
- Avisos globales al abrir y cerrar; los jugadores que ya entraron pueden terminar su partida.
- Cada jugador normal puede iniciar una partida por ventana de apertura; si no entra, espera la siguiente.
- OP o permiso `bastiondungeons.bypass` ignoran la restricción.
- `/dungeon party leave`, `/dungeon leave`, `/dungeon party list` siguen disponibles para todos.
- Horario y participaciones se guardan en `plugins/BastionDungeons/schedule.yml` y sobreviven reinicios.

## Instalación
Subir archivos fuente al repositorio, compilar con GitHub Actions, hacer respaldo del servidor y reemplazar el JAR anterior con el servidor apagado. **No borrar** la carpeta `plugins/BastionDungeons`.

## Notas
Los 5 minutos son una ventana de acceso, no un límite para acabar una partida. El ciclo se repite cada 35 minutos. Esta versión requiere prueba en servidor; no se ha compilado localmente.
