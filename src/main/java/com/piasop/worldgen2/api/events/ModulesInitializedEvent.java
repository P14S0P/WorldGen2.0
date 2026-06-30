package com.piasop.worldgen2.api.events;

import com.piasop.worldgen2.api.WG2Module;

import java.util.List;

public record ModulesInitializedEvent(List<WG2Module> modules) {
}
