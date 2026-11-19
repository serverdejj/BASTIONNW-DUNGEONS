# BASTION DUNGEONS - Reglas individuales por mapa

Se añadieron reglas configurables al final de `plugins/BastionDungeons/config.yml`.

Para `netherybreis.schem` (ajusta la clave si el nombre real del archivo es distinto):
- Radio horizontal desde el spawn de la partida: **67 bloques**.
- Si un participante llega a **20 bloques por debajo de la Y del spawn configurado**, recibe daño letal (1000 puntos). Si otro plugin lo cancela o reduce, se fuerza la muerte.
- Se conserva el manejo existente de muerte: objetos caen al suelo, espectador 5 segundos, y regreso al spawn.
- Se aplica tanto si el mapa se selecciona al azar como con `/dungeon go map <nombre>`.

Los demás mapas siguen usando un radio de **33 bloques** y no tienen muerte por caída de altura relativa al spawn, salvo que añadas sus propias reglas.

## Para personalizar otros mapas
Duplica la sección de `netherybreis` dentro de `map-rules.maps`, cambia su clave por el nombre del archivo `.schem` sin extensión (minúsculas), y cambia los valores. `below-spawn.enabled: false` desactiva la zona mortal.

No cambia la altura máxima del bloque `height-protection`, ni el límite de los OP con `/dungeon libre`. El daño por bajar 20 bloques sigue aplicándose a todos los participantes, incluso OP, pero no a espectadores.

**Importante:** preserva `config.yml` existente en Holy Hosting. Añade solo el bloque `map-rules` del nuevo config, ya que `saveDefaultConfig()` no actualiza automáticamente uno existente. Reinicia el servidor o usa el comando de recarga del plugin si la recarga lee config.yml.
