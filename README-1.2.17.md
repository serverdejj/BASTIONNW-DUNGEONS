# BASTION DUNGEONS 1.2.17-dev

- Mundo temporal y pegado del schematic sin DeepVoid (se mantiene el sistema anterior).
- Espera de 20 ticks antes del teleport al mundo, tras el pegado de FAWE.
- Teleport asíncrono con verificación del mundo de destino.
- Si falla, reintento síncrono único 10 ticks después.
- Diagnóstico de PlayerTeleportEvent al inicio y al final del evento.
- Se mantiene la limpieza diferida de mundos temporales.

**Nota:** Si otro plugin cancela el teletransporte, la versión registra el problema, pero no omite las protecciones de otros plugins. No se ha probado en servidor real.
