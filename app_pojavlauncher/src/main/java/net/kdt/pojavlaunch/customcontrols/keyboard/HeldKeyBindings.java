package net.kdt.pojavlaunch.customcontrols.keyboard;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/** Pins a mapping until key-up and reference-counts keys shared by several sources. */
public final class HeldKeyBindings {
    private final Map<Long, Integer> held = new HashMap<>();
    private final Map<Integer, Integer> counts = new HashMap<>();

    public int press(int device, int source, int target) {
        long id = id(device, source);
        if (held.containsKey(id)) return -1;
        held.put(id, target);
        int count = counts.containsKey(target) ? counts.get(target) : 0;
        counts.put(target, count + 1);
        return count == 0 ? target : -1;
    }

    public boolean isHeld(int device, int source) { return held.containsKey(id(device, source)); }

    public int release(int device, int source) {
        Integer target = held.remove(id(device, source));
        if (target == null) return -1;
        int count = counts.get(target) - 1;
        if (count > 0) { counts.put(target, count); return -1; }
        counts.remove(target);
        return target;
    }

    public void releaseAll(IntConsumer release) {
        for (int target : counts.keySet()) release.accept(target);
        counts.clear();
        held.clear();
    }

    public void releaseDevice(int device, IntConsumer release) {
        ArrayList<Integer> sources = new ArrayList<>();
        for (long key : held.keySet()) if ((int) (key >> 32) == device) sources.add((int) key);
        for (int source : sources) {
            int target = release(device, source);
            if (target >= 0) release.accept(target);
        }
    }

    private static long id(int device, int source) {
        return ((long) device << 32) | (source & 0xffffffffL);
    }
}
