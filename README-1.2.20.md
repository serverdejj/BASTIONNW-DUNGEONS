# BASTION DUNGEONS 1.2.20-dev

Corrección: el listener `onMove(PlayerMoveEvent)` ya no modifica eventos `PlayerTeleportEvent`, evitando que la congelación del lobby reemplace el destino de teletransportes entre mundos.

Compilar mediante GitHub Actions. Mantener una copia de las configuraciones. La corrección requiere prueba en el servidor.
