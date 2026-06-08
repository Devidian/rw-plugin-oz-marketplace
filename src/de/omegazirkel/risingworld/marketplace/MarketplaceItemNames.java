package de.omegazirkel.risingworld.marketplace;

import java.util.Locale;

import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.objects.Item;

public final class MarketplaceItemNames {
    private MarketplaceItemNames() {
    }

    public static String listingLabel(String itemName, int variant) {
        String baseName = objectVariantDisplayName(itemName, variant);
        if (baseName.isBlank()) {
            baseName = objectDisplayName(itemName);
        }
        if (baseName.isBlank()) {
            baseName = derivedBaseName(itemName);
        }
        return variant == 0 ? baseName : baseName + "-" + variant;
    }

    public static String candidateLabel(Item item, ItemDefinition definition, int variant) {
        String name = objectItemName(item);
        if (name.isBlank()) {
            name = item == null ? "" : item.getName();
        }
        if (name == null || name.isBlank()) {
            Variant itemVariant = definition == null ? null : definition.getVariant(variant);
            name = itemVariant != null && itemVariant.name != null && !itemVariant.name.isBlank()
                    ? itemVariant.name
                    : definition == null ? "" : definition.name;
        }
        return variant == 0 ? derivedBaseName(name) : derivedBaseName(name) + "-" + variant;
    }

    public static ItemDefinition definition(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        return Definitions.getItemDefinition(itemName);
    }

    public static ObjectDefinition objectDefinition(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        return Definitions.getObjectDefinition(itemName);
    }

    public static String storedItemName(Item item, ItemDefinition definition) {
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

    private static String objectItemName(Item item) {
        if (item instanceof Item.ObjectItem object) {
            String name = object.getObjectName();
            return name == null ? "" : name.trim();
        }
        return "";
    }

    private static String objectDisplayName(String itemName) {
        ObjectDefinition definition = objectDefinition(itemName);
        if (definition == null || definition.name == null || definition.name.isBlank()) {
            return "";
        }
        return derivedBaseName(definition.name);
    }

    private static String objectVariantDisplayName(String itemName, int variant) {
        ObjectDefinition definition = objectDefinition(itemName);
        if (definition == null) {
            return "";
        }
        ObjectDefinition.Variant objectVariant = definition.getVariant(variant);
        if (objectVariant == null || objectVariant.name == null || objectVariant.name.isBlank()) {
            return "";
        }
        return derivedBaseName(objectVariant.name);
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
