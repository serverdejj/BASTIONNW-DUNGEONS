BASTION DUNGEONS 1.2.16-dev

- TeleportAsync secuencial de participantes, comprobando resultados.
- Diagnóstico en consola para eventos PlayerTeleportEvent cancelados hacia mundos bd_.
- Limpieza de mundo retrasada al menos 10 segundos para reducir carreras con FAWE.
- Conserva la carga asíncrona por lotes de 8 chunks de 1.2.15.

Nota: si otro plugin cancela el teletransporte, el diagnóstico lo muestra, pero no evita la cancelación.
