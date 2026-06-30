# WorldGen2 — Estado del proyecto
Ultima actualizacion: 2026-06-30

## Modulos completados ✓
- [x] Core infrastructure (WG2Module, WG2Registry, WG2ThreadPool, WG2DataCache, WG2EventBus, WG2Config)
- [x] TerrainModule (Fase 1 base funcional)
- [x] TerrainModule (Fase 1: Domain Warp + OpenSimplex2S + RidgedFBM)
- [x] ClimateModule (temperatura, precipitacion, Koppen, efecto orografico)
- [x] BiomeModule (tabla clima->bioma + blending)
- [x] Benchmark Fase 1 vs vanilla-like
- [x] Integracion runtime de mixins activos (NoiseBasedChunkGenerator + StructureManagerAccessor)
- [x] CaveModule prototipo (Fase 2)
- [x] RiverModule prototipo (Fase 2)
- [x] OceanModule prototipo (Fase 2)
- [x] Integration tests rio->oceano funcionales
- [x] RiverModule carve real de cauce en bloques (hook runtime en doFill)
- [x] CaveModule carve real 3D en bloques (hook runtime en doFill)
- [x] OceanModule integrado al chunk final (piso oceanico + columna de agua en doFill)
- [x] VegetationModule base (Fase 3: tabla clima+altitud, densidad/diversidad determinista)
- [x] TreeModule base (Fase 3: potencial arboreo determinista + paleta parametrica)
- [x] StructureModule baseline (Fase 3: mapa de oportunidad determinista + prototipos base)
- [x] RuinsModule baseline (Fase 3: oportunidad de ruinas + degradacion determinista)
- [x] MineralModule baseline (Fase 3: estratigrafia inicial + riqueza mineral determinista)
- [x] Integracion runtime Fase 3 baseline (dispatch NOISE + FEATURES por chunk)
- [x] Primer efecto FEATURES en runtime (anclas estructurales + degradacion de ruinas)
- [x] Primer efecto NOISE en runtime para minerales (estratos ligeros deterministas)

## Modulo actual en desarrollo
- Ajuste de balance/performance de efectos Fase 3 — fase: pendiente

## Archivos existentes
- WG2_SESSION_LOG.md
- src/main/java/com/piasop/worldgen2/core/WG2Mod.java
- src/main/java/com/piasop/worldgen2/mixin/NoiseChunkMixin.java
- src/main/java/com/piasop/worldgen2/mixin/NoiseBasedChunkGeneratorMixin.java
- src/main/java/com/piasop/worldgen2/mixin/StructureManagerAccessor.java
- src/main/java/com/piasop/worldgen2/modules/phase1/OpenSimplex2S.java
- src/main/java/com/piasop/worldgen2/modules/phase1/TerrainModule.java
- src/main/java/com/piasop/worldgen2/modules/phase1/ClimateModule.java
- src/main/java/com/piasop/worldgen2/modules/phase1/BiomeModule.java
- src/main/java/com/piasop/worldgen2/modules/phase2/CaveModule.java
- src/main/java/com/piasop/worldgen2/modules/phase2/RiverModule.java
- src/main/java/com/piasop/worldgen2/modules/phase2/OceanModule.java
- src/test/java/com/piasop/worldgen2/modules/phase1/Phase1BenchmarkTest.java
- src/test/java/com/piasop/worldgen2/modules/phase2/CaveModuleTest.java
- src/test/java/com/piasop/worldgen2/modules/phase2/RiverModuleTest.java
- src/test/java/com/piasop/worldgen2/modules/phase2/OceanModuleTest.java
- src/test/java/com/piasop/worldgen2/modules/phase2/RiverOceanIntegrationTest.java

## Bugs conocidos
- Sin bugs criticos bloqueantes reportados en Fase 2 tras hardening final

## Proxima sesion: hacer
1. Ajustar balance de frecuencia de anclas/degradacion por bioma
2. Ajustar densidad/frecuencia de estratos minerales por perfil
3. Ejecutar validacion integrada de avance Fase 3 (tests + runClient)

## Notas tecnicas importantes
- Trabajar un modulo por sesion.
- No avanzar al siguiente modulo hasta validar runClient.
- Seed global real del mundo conectada en hooks runtime activos (`NoiseBasedChunkGeneratorMixin` + `StructureManagerAccessor`).
- River/Cave/Ocean aplican cambios reales en bloques dentro de doFill.
- Muestreo river/ocean suavizado (bilinear por posicion intra-chunk) para reducir costuras visibles en ejes/chunk borders.
- TerrainModule usa OpenSimplex2S en dominio y macro para cumplir especificacion de Fase 1.
- Estado de performance actual: funcional pero con degradacion notable; requiere optimizacion en Fase 5.
- Integration tests rio->oceano ya estan en verde y forman parte de la base de regresion de Fase 2.
