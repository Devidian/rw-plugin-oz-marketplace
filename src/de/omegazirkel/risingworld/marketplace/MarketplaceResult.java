package de.omegazirkel.risingworld.marketplace;

import java.util.List;

import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.objects.Player;

public record MarketplaceResult(boolean success, String message, String messageKey, List<String> replacements) {
    public MarketplaceResult {
        message = message == null ? "" : message;
        messageKey = messageKey == null ? "" : messageKey;
        replacements = replacements == null ? List.of() : List.copyOf(replacements);
    }

    /** Preserves the original public constructor for existing compiled consumers. */
    public MarketplaceResult(boolean success, String message) {
        this(success, message, "", List.of());
    }

    public static MarketplaceResult ok(String message) {
        return new MarketplaceResult(true, message);
    }

    public static MarketplaceResult fail(String message) {
        return new MarketplaceResult(false, message);
    }

    public static MarketplaceResult okKey(String messageKey, String fallback, String... replacements) {
        return keyed(true, messageKey, fallback, replacements);
    }

    public static MarketplaceResult failKey(String messageKey, String fallback, String... replacements) {
        return keyed(false, messageKey, fallback, replacements);
    }

    public boolean hasKey(String key) {
        return key != null && key.equals(messageKey);
    }

    public String localized(I18n translations, Player player) {
        String localized = message;
        if (translations != null && player != null && !messageKey.isBlank()) {
            String candidate = translations.get(messageKey, player);
            if (candidate != null && !candidate.isBlank() && !candidate.equals(messageKey)) {
                localized = candidate;
            }
        }
        for (int i = 0; i + 1 < replacements.size(); i += 2) {
            localized = localized.replace(replacements.get(i), replacements.get(i + 1));
        }
        return localized;
    }

    private static MarketplaceResult keyed(boolean success, String messageKey, String fallback,
            String... replacements) {
        return new MarketplaceResult(success, fallback, messageKey,
                replacements == null ? List.of() : List.of(replacements));
    }
}
