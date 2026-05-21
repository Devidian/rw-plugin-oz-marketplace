package de.omegazirkel.risingworld.marketplace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Inventory.SlotType;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;

public final class InventoryTransfer {
    private InventoryTransfer() {
    }

    public static List<InventoryListingCandidate> listingCandidates(Player seller) {
        if (seller == null || seller.getInventory() == null) {
            return List.of();
        }
        Map<String, CandidateAccumulator> grouped = new LinkedHashMap<>();
        Inventory inventory = seller.getInventory();
        Item[] items = inventory.getAllItems();
        if (items == null) {
            return List.of();
        }
        for (Item item : items) {
            if (item == null || !item.isValid() || item.getStack() <= 0) {
                continue;
            }
            ItemDefinition definition = item.getDefinition();
            if (definition == null || definition.name == null || definition.name.isBlank()) {
                definition = Definitions.getItemDefinition(item.getTypeID());
            }
            if (definition == null || definition.name == null || definition.name.isBlank()) {
                continue;
            }
            int variant = item.getVariant();
            ItemDefinition candidateDefinition = definition;
            String key = candidateDefinition.name + ":" + variant;
            CandidateAccumulator accumulator = grouped.computeIfAbsent(key,
                    ignored -> new CandidateAccumulator(candidateDefinition.name,
                            displayName(item, candidateDefinition, variant),
                            variant, maxStackSize(item, candidateDefinition)));
            accumulator.availableAmount += item.getStack();
            accumulator.maxStackSize = Math.max(accumulator.maxStackSize, maxStackSize(item, candidateDefinition));
        }
        List<InventoryListingCandidate> candidates = new ArrayList<>();
        for (CandidateAccumulator accumulator : grouped.values()) {
            candidates.add(accumulator.toCandidate());
        }
        candidates.sort(Comparator
                .comparing(InventoryListingCandidate::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(InventoryListingCandidate::variant));
        return candidates;
    }

    public static MarketplaceResult removeFromSeller(Player seller, String itemName, int itemVariant, int amount) {
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        if (definition == null) {
            return MarketplaceResult.fail("Unknown item: " + itemName);
        }
        if (amount <= 0) {
            return MarketplaceResult.fail("Amount must be greater than 0.");
        }
        Inventory inventory = seller.getInventory();
        int remaining = amount;
        for (SlotType slotType : SlotType.values()) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (item == null || !item.isValid() || item.getTypeID() != definition.id
                        || item.getVariant() != itemVariant) {
                    continue;
                }
                int remove = Math.min(remaining, item.getStack());
                if (!inventory.removeItem(slot, slotType, remove)) {
                    return MarketplaceResult.fail("Could not remove listed item from inventory.");
                }
                remaining -= remove;
                if (remaining == 0) {
                    inventory.syncWithClient();
                    return MarketplaceResult.ok("Item removed from seller inventory.");
                }
            }
        }
        return MarketplaceResult.fail("You do not have enough matching items in your inventory.");
    }

    public static MarketplaceResult addToBuyer(Player buyer, String itemName, int itemVariant, int amount) {
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        if (definition == null) {
            return MarketplaceResult.fail("Unknown item: " + itemName);
        }
        Item item = buyer.getInventory().addItem(definition.id, itemVariant, amount);
        if (item == null || !item.isValid()) {
            return MarketplaceResult.fail("Could not add item to buyer inventory.");
        }
        buyer.getInventory().syncWithClient();
        return MarketplaceResult.ok("Item added to buyer inventory.");
    }

    private static String displayName(Item item, ItemDefinition definition, int variant) {
        String itemName = item.getName();
        if (itemName != null && !itemName.isBlank()) {
            return itemName;
        }
        if (definition.getVariant(variant) != null && definition.getVariant(variant).name != null
                && !definition.getVariant(variant).name.isBlank()) {
            return definition.getVariant(variant).name;
        }
        return definition.name;
    }

    private static int maxStackSize(Item item, ItemDefinition definition) {
        int itemMax = item.getMaxStackSize();
        if (itemMax > 0) {
            return itemMax;
        }
        return definition.stacksize > 0 ? definition.stacksize : 1;
    }

    private static final class CandidateAccumulator {
        private final String itemName;
        private final String displayName;
        private final int variant;
        private int availableAmount;
        private int maxStackSize;

        private CandidateAccumulator(String itemName, String displayName, int variant, int maxStackSize) {
            this.itemName = itemName;
            this.displayName = displayName;
            this.variant = variant;
            this.maxStackSize = maxStackSize;
        }

        private InventoryListingCandidate toCandidate() {
            return new InventoryListingCandidate(itemName, displayName, variant, availableAmount, maxStackSize);
        }
    }
}
