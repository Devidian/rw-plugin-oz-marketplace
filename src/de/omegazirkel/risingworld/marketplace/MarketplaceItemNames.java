package de.omegazirkel.risingworld.marketplace;

import java.util.Locale;

import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.definitions.Plants.PlantDefinition;
import net.risingworld.api.objects.Item;

public final class MarketplaceItemNames {
    private MarketplaceItemNames() {
    }

    public static String listingLabel(String itemName, int variant) {
        ObjectDefinition objectDefinition = objectDefinition(itemName, variant);
        int labelVariant = objectDefinition == null ? variant : objectVariant(itemName, variant, objectDefinition);
        ItemDefinition itemDefinition = definition(itemName);
        if (objectDefinition == null && itemDefinition != null) {
            Variant itemVariant = itemDefinition.getVariant(variant);
            String itemDisplayName = itemDefinition.name == null || itemDefinition.name.isBlank()
                    ? itemName
                    : itemDefinition.name;
            if (itemVariant != null) {
                if (!isDefaultVariantName(itemVariant.name)) {
                    return derivedBaseName(itemDisplayName) + " " + derivedBaseName(itemVariant.name);
                }
                return derivedBaseName(itemDisplayName);
            }
        }
        String baseName = objectDisplayName(itemName, variant);
        if (baseName.isBlank() && objectDefinition == null) {
            baseName = objectVariantDisplayName(itemName, variant);
        }
        if (baseName.isBlank()) {
            baseName = itemVariantDisplayName(itemName, variant);
        }
        if (baseName.isBlank()) {
            baseName = directDefinitionDisplayName(itemName);
        }
        if (baseName.isBlank()) {
            baseName = derivedBaseName(itemName);
        }
        return labelVariant == 0 ? derivedBaseName(baseName) : derivedBaseName(baseName) + "-" + labelVariant;
    }

    public static String candidateLabel(Item item, ItemDefinition definition, int variant) {
        String name = constructionItemName(item);
        if (name.isBlank()) {
            name = clothingItemName(item);
        }
        if (name.isBlank()) {
            name = objectItemName(item);
        }
        if (name.isBlank()) {
            name = item == null ? "" : item.getName();
        }
        if (name == null || name.isBlank()) {
            Variant itemVariant = definition == null ? null : definition.getVariant(variant);
            name = itemVariant != null && itemVariant.name != null && !itemVariant.name.isBlank()
                    ? itemVariant.name
                    : definition == null ? "" : definition.name;
        }
        return listingLabel(name, variant);
    }

    public static ItemDefinition definition(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        return Definitions.getItemDefinition(itemName);
    }

    public static int durabilityPercent(String itemName, MarketplaceItemState state) {
        ItemDefinition definition = definition(itemName);
        if (definition == null || definition.durability <= 0 || state == null) {
            return -1;
        }
        return Math.max(0, Math.min(100, (int) Math.floor(state.durability() * 100.0d / definition.durability)));
    }

    public static ObjectDefinition objectDefinition(String itemName) {
        return objectDefinition(itemName, 0);
    }

    public static ObjectDefinition objectDefinition(String itemName, int variant) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        ObjectDefinition direct = Definitions.getObjectDefinition(itemName);
        if (direct != null) {
            return direct;
        }
        ItemDefinition itemDefinition = definition(itemName);
        if (itemDefinition != null) {
            Variant itemVariant = itemDefinition.getVariant(variant);
            if (itemVariant != null && !isDefaultVariantName(itemVariant.name)) {
                ObjectDefinition byVariantName = Definitions.getObjectDefinition(itemVariant.name);
                if (byVariantName != null) {
                    return byVariantName;
                }
            }
        }
        return isObjectKit(itemName) ? Definitions.getObjectDefinition(variant) : null;
    }

    public static String storedItemName(Item item, ItemDefinition definition) {
        String constructionName = constructionItemName(item);
        if (!constructionName.isBlank()) {
            return constructionName;
        }
        String clothingName = clothingItemName(item);
        if (!clothingName.isBlank()) {
            return clothingName;
        }
        String objectName = objectItemName(item);
        if (!objectName.isBlank()) {
            return objectName;
        }
        return definition == null ? "" : definition.name;
    }

    public static boolean matches(Item item, String itemName, int variant) {
        if (item == null || itemName == null || itemName.isBlank() || item.getVariant() != variant) {
            return false;
        }
        String constructionName = constructionItemName(item);
        if (!constructionName.isBlank()) {
            return constructionName.equalsIgnoreCase(itemName);
        }
        String clothingName = clothingItemName(item);
        if (!clothingName.isBlank()) {
            return clothingName.equalsIgnoreCase(itemName);
        }
        String objectName = objectItemName(item);
        if (!objectName.isBlank()) {
            return objectName.equalsIgnoreCase(itemName);
        }
        ItemDefinition definition = item.getDefinition();
        if (definition == null || definition.name == null || definition.name.isBlank()) {
            definition = Definitions.getItemDefinition(item.getTypeID());
        }
        return definition != null && definition.name != null && definition.name.equalsIgnoreCase(itemName);
    }

    private static String constructionItemName(Item item) {
        if (item instanceof Item.ConstructionItem construction) {
            String name = construction.getConstructionName();
            return name == null ? "" : name.trim();
        }
        return "";
    }

    private static String clothingItemName(Item item) {
        if (item instanceof Item.ClothingItem clothing) {
            String name = clothing.getClothingName();
            return name == null ? "" : name.trim();
        }
        return "";
    }

    private static String objectItemName(Item item) {
        if (item instanceof Item.ObjectItem object) {
            String name = object.getObjectName();
            return name == null ? "" : name.trim();
        }
        return "";
    }

    private static String objectDisplayName(String itemName, int variant) {
        ObjectDefinition definition = objectDefinition(itemName, variant);
        if (definition == null || definition.name == null || definition.name.isBlank()) {
            return "";
        }
        return derivedBaseName(definition.name);
    }

    private static String objectVariantDisplayName(String itemName, int variant) {
        ObjectDefinition definition = objectDefinition(itemName, variant);
        if (definition == null) {
            return "";
        }
        ObjectDefinition.Variant objectVariant = definition.getVariant(objectVariant(itemName, variant, definition));
        if (objectVariant == null || isDefaultVariantName(objectVariant.name)) {
            return "";
        }
        return objectVariant.name;
    }

    private static int objectVariant(String itemName, int variant, ObjectDefinition definition) {
        return Definitions.getObjectDefinition(itemName) != null ? variant : 0;
    }

    private static String itemVariantDisplayName(String itemName, int variant) {
        ItemDefinition definition = definition(itemName);
        if (definition == null) {
            return "";
        }
        Variant itemVariant = definition.getVariant(variant);
        if (itemVariant == null || isDefaultVariantName(itemVariant.name)) {
            return definition.name == null ? "" : definition.name;
        }
        return itemVariant.name;
    }

    private static String directDefinitionDisplayName(String itemName) {
        ConstructionDefinition construction = Definitions.getConstructionDefinition(itemName);
        if (construction != null && construction.name != null && !construction.name.isBlank()) {
            return construction.name;
        }
        ClothingDefinition clothing = Definitions.getClothingDefinition(itemName);
        if (clothing != null && clothing.name != null && !clothing.name.isBlank()) {
            return clothing.name;
        }
        PlantDefinition plant = Definitions.getPlantDefinition(itemName);
        return plant != null && plant.name != null && !plant.name.isBlank() ? plant.name : "";
    }

    private static boolean isDefaultVariantName(String name) {
        return name == null || name.isBlank() || name.trim().equalsIgnoreCase("default");
    }

    private static boolean isObjectKit(String itemName) {
        return itemName != null && itemName.toLowerCase(Locale.ROOT).startsWith("objectkit");
    }

    private static String derivedBaseName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "Item";
        }
        normalized = normalized.replace('_', ' ').replace('-', ' ');
        String[] parts = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.isEmpty() ? "Item" : result.toString();
    }
}
