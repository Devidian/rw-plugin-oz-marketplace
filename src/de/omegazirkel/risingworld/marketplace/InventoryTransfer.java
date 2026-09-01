package de.omegazirkel.risingworld.marketplace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.Modifier;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
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
            String itemName = MarketplaceItemNames.storedItemName(item, candidateDefinition);
            if (itemName.isBlank()) {
                continue;
            }
            MarketplaceItemState itemState = snapshotState(item);
            String key = itemName + ":" + variant + ":" + itemState.durability() + ":" + itemState.status()
                    + ":" + itemState.modifier() + ":" + itemState.color();
            CandidateAccumulator accumulator = grouped.computeIfAbsent(key,
                    ignored -> new CandidateAccumulator(itemName,
                            MarketplaceItemNames.candidateLabel(item, candidateDefinition, variant, seller.getLanguage()),
                            variant, maxStackSize(item, candidateDefinition), itemState));
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
        return removeFromSeller(seller, itemName, itemVariant, amount, null);
    }

    public static MarketplaceResult removeFromSeller(Player seller, String itemName, int itemVariant, int amount,
            MarketplaceItemState state) {
        if (MarketplaceItemNames.definition(itemName) == null && MarketplaceItemNames.objectDefinition(itemName) == null
                && Definitions.getConstructionDefinition(itemName) == null
                && Definitions.getClothingDefinition(itemName) == null) {
            return MarketplaceResult.failKey("tc.market.result.item.unknown",
                    "Unknown item: PH_ITEM.", "PH_ITEM", itemName);
        }
        if (amount <= 0) {
            return MarketplaceResult.failKey("tc.market.result.amount.positive",
                    "Amount must be greater than 0.");
        }
        Inventory inventory = seller.getInventory();
        int remaining = amount;
        for (SlotType slotType : SlotType.values()) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (item == null || !item.isValid() || !MarketplaceItemNames.matches(item, itemName, itemVariant)
                        || (state != null && !matchesState(item, state))) {
                    continue;
                }
                int remove = Math.min(remaining, item.getStack());
                if (!inventory.removeItem(slot, slotType, remove)) {
                    return MarketplaceResult.failKey("tc.market.result.inventory.remove.failed",
                            "Could not remove the listed item from inventory.");
                }
                remaining -= remove;
                if (remaining == 0) {
                    inventory.syncWithClient();
                    return MarketplaceResult.okKey("tc.market.result.inventory.removed",
                            "Item removed from inventory.");
                }
            }
        }
        return MarketplaceResult.failKey("tc.market.result.items.not.matching",
                "You do not have enough matching items in one item state.");
    }

    public static MarketplaceResult addToBuyer(Player buyer, String itemName, int itemVariant, int amount) {
        return addToBuyer(buyer, itemName, itemVariant, amount, MarketplaceItemState.NEUTRAL);
    }

    public static MarketplaceResult addToBuyer(Player buyer, String itemName, int itemVariant, int amount,
            MarketplaceItemState state) {
        MarketplaceItemState effective = state == null ? MarketplaceItemState.NEUTRAL : state;
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        ObjectDefinition objectDefinition = MarketplaceItemNames.objectDefinition(itemName);
        ConstructionDefinition constructionDefinition = Definitions.getConstructionDefinition(itemName);
        ClothingDefinition clothingDefinition = Definitions.getClothingDefinition(itemName);
        if (definition == null && objectDefinition == null && constructionDefinition == null
                && clothingDefinition == null) {
            return MarketplaceResult.failKey("tc.market.result.item.unknown",
                    "Unknown item: PH_ITEM.", "PH_ITEM", itemName);
        }
        Item item = objectDefinition != null
                ? buyer.getInventory().addObjectItem(objectDefinition.id, itemVariant, amount)
                : constructionDefinition != null
                        ? buyer.getInventory().addConstructionItem(constructionDefinition.id, itemVariant,
                                effective.color(), amount)
                        : clothingDefinition != null
                                ? buyer.getInventory().addClothingItem(clothingDefinition.id, itemVariant, 0, amount,
                                        0L)
                                : buyer.getInventory().addItem(definition.id, itemVariant, amount);
        if (item == null || !item.isValid()) {
            return MarketplaceResult.failKey("tc.market.result.inventory.add.failed",
                    "Could not add the item to inventory.");
        }
        item.setDurability(effective.durability());
        item.setStatus(effective.status());
        if (!effective.modifier().isBlank()) {
            try { item.setModifier(Modifier.valueOf(effective.modifier())); } catch (IllegalArgumentException ignored) { }
        }
        buyer.getInventory().syncWithClient();
        return MarketplaceResult.okKey("tc.market.result.inventory.added",
                "Item added to inventory.");
    }

    public static MarketplaceItemState snapshotForSeller(Player seller, String itemName, int itemVariant, int amount) {
        if (seller == null || seller.getInventory() == null || amount <= 0) return null;
        for (SlotType type : SlotType.values()) for (int slot = 0; slot < seller.getInventory().getSlotCount(type); slot++) {
            Item item = seller.getInventory().getItem(slot, type);
            if (item != null && item.isValid() && MarketplaceItemNames.matches(item, itemName, itemVariant)
                    && item.getStack() >= amount) return snapshotState(item);
        }
        return null;
    }

    private static boolean matchesState(Item item, MarketplaceItemState state) {
        String modifier = item.getModifier() == null ? "" : item.getModifier().name();
        return item.getDurability() == state.durability() && item.getStatus() == state.status()
                && modifier.equals(state.modifier()) && constructionColor(item) == state.color();
    }

    private static MarketplaceItemState snapshotState(Item item) {
        return new MarketplaceItemState(item.getDurability(), item.getStatus(),
                item.getModifier() == null ? "" : item.getModifier().name(), constructionColor(item));
    }

    private static int constructionColor(Item item) {
        return item instanceof Item.ConstructionItem construction ? construction.getColor() : 0;
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
        private final MarketplaceItemState itemState;
        private int availableAmount;
        private int maxStackSize;

        private CandidateAccumulator(String itemName, String displayName, int variant, int maxStackSize,
                MarketplaceItemState itemState) {
            this.itemName = itemName;
            this.displayName = displayName;
            this.variant = variant;
            this.maxStackSize = maxStackSize;
            this.itemState = itemState;
        }

        private InventoryListingCandidate toCandidate() {
            return new InventoryListingCandidate(itemName, displayName, variant, availableAmount, maxStackSize, itemState);
        }
    }
}
