package com.assetiq.services;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Guards self-referencing trees (locations, categories) against cycles.
 */
public final class HierarchyGuard {

    private HierarchyGuard() {
    }

    /**
     * Refuses a parent that is the node itself or sits below it. Walks up from the
     * proposed parent; an A -> B -> A cycle would otherwise be saved and break every
     * tree walk. Stops quietly on a pre-existing cycle so the walk always ends.
     *
     * @param proposedParent the parent being assigned (not null)
     * @param selfId         the id of the node being edited
     * @param parentOf       returns a node's parent, or null at the root
     * @param idOf           returns a node's id
     * @param message        the error when the parent is the node or a descendant
     */
    public static <T> void assertNotDescendant(T proposedParent, UUID selfId,
            Function<T, T> parentOf, Function<T, UUID> idOf, String message) {
        if (selfId == null) return; // a node being created has no descendants
        Set<UUID> seen = new HashSet<>();
        for (T cursor = proposedParent; cursor != null; cursor = parentOf.apply(cursor)) {
            UUID id = idOf.apply(cursor);
            if (selfId.equals(id)) {
                throw new IllegalArgumentException(message);
            }
            if (!seen.add(id)) return;
        }
    }
}
