# Integración BastionMCDungeonRoulette

Se añadió API pública `startRouletteDungeon(Player, String, String)`.
Recibe el líder, ID de mapa (sin extensión) y dificultad `EASY`, `NORMAL`, `HARD`.
Valida grupo, acceso y evita sesiones simultáneas, luego empieza el conteo de 5 segundos directamente sin votación.

`/dungeon go` público indica entrar mediante los bloques. El comando manual OP `/dungeon go map <mapa>` permanece como prueba e inicia en NORMAL sin menú de votación.
