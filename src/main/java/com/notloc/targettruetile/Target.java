package com.notloc.targettruetile;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.NPC;

public class Target {
    @Getter
    private final NPC npc;

    @Getter @Setter
    private long lastTargetedAt;

    public Target(NPC npc, long lastTargetedAt) {
        this.npc = npc;
        this.lastTargetedAt = lastTargetedAt;
    }

    public boolean isVisible() {
        return npc.getComposition() != null && !npc.isDead();
    }
}
