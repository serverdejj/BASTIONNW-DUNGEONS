# Correccion /dungeon romper para OP

- El listener de rotura se ejecuta con prioridad HIGHEST y, para operadores con modo romper activo, elimina la cancelacion previa del evento SOLO en mundos de instancia de Dungeons.
- El modo sigue siendo individual y temporal. Para jugadores normales se mantiene la proteccion.
- Se muestra una advertencia si un OP activa el modo en Adventure o Spectator, donde Minecraft no permite romper normalmente.
- Si otro plugin cancela en MONITOR o modifica el mundo tras el evento, revisar los conflictos de proteccion.
- Los cambios en las instancias temporales no modifican el archivo `.schem` original.
- Reiniciar y compilar JAR mediante GitHub Actions antes de probar.
