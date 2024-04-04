package com.notloc.targettruetile;

import lombok.Getter;
import net.runelite.api.NPC;

import java.util.*;

public class TargetMemory {

    @Getter
    private final Set<NPC> npcs = new HashSet<>();
    private final Map<NPC, Target> targets = new HashMap<>();

    public void acknowledgeTarget(NPC npc) {
        Target target = targets.get(npc);
        if (target == null) {
            target = add(npc);
        }
        target.setLastTargetedAt(now());
    }

    public void forgetTarget(NPC npc) {
        remove(npc);
    }

    public void forgetOldTargets(int threshold_s) {
        long now = now();
        List<NPC> npcsToForget = new ArrayList<>();

        for (Target target : targets.values()) {
            long delta = now - target.getLastTargetedAt();
            if (delta >= threshold_s || !target.isVisible()) {
                npcsToForget.add(target.getNpc());
            }
        }

        for (NPC npc : npcsToForget) {
            remove(npc);
        }
    }

    public void forgetAll() {
        npcs.clear();
        targets.clear();
    }

    private Target add(NPC npc) {
        Target target = new Target(npc, now());
        targets.put(npc, target);
        npcs.add(npc);
        return target;
    }

    private void remove(NPC npc) {
        targets.remove(npc);
        npcs.remove(npc);
    }

    private static long now() {
        return java.time.Instant.now().getEpochSecond();
    }
}
