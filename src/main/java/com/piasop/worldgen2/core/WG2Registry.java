package com.piasop.worldgen2.core;

import com.piasop.worldgen2.api.GenerationPhase;
import com.piasop.worldgen2.api.WG2Module;
import com.piasop.worldgen2.api.events.ModulesInitializedEvent;
import com.piasop.worldgen2.api.events.WG2LifecycleEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of pluggable WG2 generation modules, sorted by priority within each phase.
 */
public final class WG2Registry {
    private static final Map<String, WG2Module> MODULES = new LinkedHashMap<>();
    private static final EnumMap<GenerationPhase, List<WG2Module>> MODULES_BY_PHASE = new EnumMap<>(GenerationPhase.class);
    private static boolean initialized;

    private WG2Registry() {
    }

    public static void register(WG2Module module) {
        if (initialized) {
            throw new IllegalStateException("Cannot register module " + module.getId() + " after initialization");
        }
        MODULES.put(module.getId(), module);
        MODULES_BY_PHASE.clear();
    }

    public static Optional<WG2Module> get(String id) {
        return Optional.ofNullable(MODULES.get(id));
    }

    public static List<WG2Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(MODULES.values()));
    }

    public static List<WG2Module> byPhase(GenerationPhase phase) {
        return MODULES_BY_PHASE.computeIfAbsent(phase, WG2Registry::computeByPhase);
    }

    private static List<WG2Module> computeByPhase(GenerationPhase phase) {
        List<WG2Module> phaseModules = MODULES.values().stream()
                .filter(module -> module.getPhase() == phase)
                .sorted(Comparator.comparingInt(WG2Module::getPriority))
                .toList();
        return Collections.unmodifiableList(phaseModules);
    }

    public static void initializeAll(WG2Config config, WG2DataCache cache) {
        if (initialized) {
            return;
        }
        List<WG2Module> ordered = resolveInitializationOrder();
        for (WG2Module module : ordered) {
            module.initialize(config, cache);
        }
        initialized = true;
        WG2EventBus.fire(new ModulesInitializedEvent(ordered));
        WG2EventBus.fire(new WG2LifecycleEvent(WG2LifecycleEvent.Stage.READY));
    }

    private static List<WG2Module> resolveInitializationOrder() {
        List<WG2Module> modules = new ArrayList<>(MODULES.values());
        modules.sort(Comparator.comparingInt(WG2Module::getPriority).thenComparing(WG2Module::getId));

        List<WG2Module> ordered = new ArrayList<>();
        Map<String, WG2Module> byId = new LinkedHashMap<>();
        for (WG2Module module : modules) {
            byId.put(module.getId(), module);
        }

        Map<String, Integer> state = new LinkedHashMap<>();
        for (WG2Module module : modules) {
            visitModule(module, byId, state, ordered);
        }

        return ordered;
    }

    private static void visitModule(
            WG2Module module,
            Map<String, WG2Module> byId,
            Map<String, Integer> state,
            List<WG2Module> ordered) {
        String id = module.getId();
        Integer visitState = state.get(id);
        if (visitState != null && visitState == 1) {
            throw new IllegalStateException("Circular module dependency involving " + id);
        }
        if (visitState != null && visitState == 2) {
            return;
        }

        state.put(id, 1);
        for (Class<? extends WG2Module> dependencyType : module.getDependencies()) {
            WG2Module dependency = findModuleByType(dependencyType, byId);
            if (dependency != null) {
                visitModule(dependency, byId, state, ordered);
            }
        }
        state.put(id, 2);
        if (!ordered.contains(module)) {
            ordered.add(module);
        }
    }

    private static WG2Module findModuleByType(Class<? extends WG2Module> type, Map<String, WG2Module> byId) {
        for (WG2Module candidate : byId.values()) {
            if (type.isInstance(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static void resetForTesting() {
        MODULES.clear();
        MODULES_BY_PHASE.clear();
        initialized = false;
    }
}
