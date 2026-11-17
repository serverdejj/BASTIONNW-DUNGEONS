# BASTION DUNGEONS — corrección del retorno tras morir

Al salir de una mazmorra, el plugin ahora restaura SURVIVAL antes y después del teletransporte, y vuelve a comprobarlo 1 y 10 ticks más tarde. Las comprobaciones solo se aplican al jugador que regresó, siempre que esté fuera de una partida y en el mundo de destino. Si el teletransporte falla, se recupera el modo anterior y se reintenta el retorno.

Se mantiene la caída de objetos al morir. No se modifican las dificultades, votación, loot ni escalado de enemigos.

Compilar en GitHub Actions y probar en un servidor de pruebas antes de instalar en producción.
