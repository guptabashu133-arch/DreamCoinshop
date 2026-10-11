package org.mydreamduels.dreamcoinshop.data;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class PlayerProfile {
    private final UUID uuid;
    private final Set<String> unlockedChatColors = new LinkedHashSet<>();
    private final Set<String> unlockedGlows = new LinkedHashSet<>();
    private final Set<String> unlockedPerks = new LinkedHashSet<>();
    private final Set<String> unlockedTags = new LinkedHashSet<>();
    private final Set<String> unlockedGradients = new LinkedHashSet<>();
    /** head hunt id -> head numbers this player has already claimed (each one only ever counts once) */
    private final Map<String, Set<Integer>> foundHuntHeads = new HashMap<>();
    private String activeChatColor;
    private String activeGlow;
    private String activeTag;
    private String activeGradient;
    private int sellwandInventoryCount;

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public Set<String> getUnlockedChatColors() {
        return this.unlockedChatColors;
    }

    public Set<String> getUnlockedGlows() {
        return this.unlockedGlows;
    }

    public Set<String> getUnlockedPerks() {
        return this.unlockedPerks;
    }

    public Set<String> getUnlockedTags() {
        return this.unlockedTags;
    }

    public Set<String> getUnlockedGradients() {
        return this.unlockedGradients;
    }

    public Set<Integer> getFoundHuntHeads(String huntId) {
        return this.foundHuntHeads.computeIfAbsent(huntId, k -> new TreeSet<>());
    }

    public Map<String, Set<Integer>> getAllFoundHuntHeads() {
        return this.foundHuntHeads;
    }

    public String getActiveChatColor() {
        return this.activeChatColor;
    }

    public void setActiveChatColor(String activeChatColor) {
        this.activeChatColor = activeChatColor;
    }

    public String getActiveGlow() {
        return this.activeGlow;
    }

    public void setActiveGlow(String activeGlow) {
        this.activeGlow = activeGlow;
    }

    public String getActiveTag() {
        return this.activeTag;
    }

    public void setActiveTag(String activeTag) {
        this.activeTag = activeTag;
    }

    public String getActiveGradient() {
        return this.activeGradient;
    }

    public void setActiveGradient(String activeGradient) {
        this.activeGradient = activeGradient;
    }
}
