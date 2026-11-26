# BASTION DUNGEONS 1.2.45 — Ajustes Dragón Rojo

- Ruta de vuelo: **4 s** por punto, parada **10 s**.
- Ruta aérea fijada a **12 bloques sobre Y del spawn**, configurable entre 10–15. No vuelve a buscar suelo debajo de los puntos (evita cuevas).
- Bolas de fuego: cada **8 s** en vez de 3; ataque de llama cada 6 s.
- Ambos ataques verifican bloqueo por bloques sólidos mediante rayTraceBlocks antes de alcanzar al jugador.
- Explosión de bolas de fuego sin romper bloques; retroceso ligero horizontal 0.20 y vertical 0.10, configurable.
- Mantiene los puntos de `adventure.yml` y el modelo V8.

## Instalación

Sube el proyecto a GitHub, compila, reemplaza solo el jar y conserva `adventure.yml` y loot.
Si usas configuración de servidor personalizada, usa `BASTION_DUNGEONS_config_1.2.45.yml` incluido al lado del ZIP.
No hay prueba de ejecución del servidor ni compilación local certificada; revisar GitHub Actions y la consola.
