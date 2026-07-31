package de.omegazirkel.risingworld.marketplace;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Plugin;
import net.risingworld.api.objects.Player;

/** Optional reflection bridge for the player-owned Marketplace listing capacity offer. */
public final class MarketplaceCapacityShopIntegration {
    private static final String OFFER_ID = "ozmarketplace.capacity";
    private final Plugin owner;

    public MarketplaceCapacityShopIntegration(Plugin owner) { this.owner = owner; }

    public void register(PluginSettings settings) {
        Plugin shop = owner.getPluginByName("OZ - Shop");
        if (shop == null) return;
        try {
            Class<?> callbackType = Class.forName("de.omegazirkel.risingworld.shop.ShopPurchaseCallback");
            Class<?> priceType = Class.forName("de.omegazirkel.risingworld.shop.ShopPriceResolver");
            Class<?> localizationType = Class.forName("de.omegazirkel.risingworld.shop.ShopOfferLocalization");
            Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[] { callbackType },
                    (proxy, method, args) -> complete(method, args));
            Object price = Proxy.newProxyInstance(priceType.getClassLoader(), new Class<?>[] { priceType },
                    (proxy, method, args) -> price(method, args, settings));
            Object localization = Proxy.newProxyInstance(localizationType.getClassLoader(), new Class<?>[] { localizationType },
                    (proxy, method, args) -> localization(proxy, method, args));
            Method register = shop.getClass().getMethod("registerOffer", String.class, String.class, String.class,
                    long.class, String.class, String.class, String.class, callbackType, priceType, localizationType);
            register.invoke(shop, OFFER_ID, "Marketplace capacity", "Increases active listing capacity by its base limit.",
                    settings.marketCapacityBasePrice, "", "marketplace-capacity", Marketplace.name, callback, price, localization);
        } catch (ReflectiveOperationException ex) {
            Marketplace.logger().debug("Marketplace capacity offer will retry after Shop/settings reload: " + ex.getMessage());
        }
    }

    private Object localization(Object proxy, Method method, Object[] args) {
        Player player = args != null && args.length > 0 && args[0] instanceof Player p ? p : null;
        I18n translations = I18n.getInstance(owner);
        return switch (method.getName()) {
            case "title" -> translations.get("TC_MARKET_SHOP_CAPACITY_TITLE", player);
            case "description" -> translations.get("TC_MARKET_SHOP_CAPACITY_DESC", player);
            case "toString" -> "MarketplaceCapacityOffer";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> false;
            default -> null;
        };
    }

    private Object price(Method method, Object[] args, PluginSettings settings) {
        if (!"price".equals(method.getName())) return null;
        Player player = args != null && args.length > 0 && args[0] instanceof Player p ? p : null;
        double factor = MarketplacePlayerPreferences.capacityFactor(player);
        int purchases = Math.max(0, (int) Math.round(factor - 1.0d));
        return Math.max(0L, Math.min(Long.MAX_VALUE,
                Math.round(settings.marketCapacityBasePrice * Math.pow(settings.marketCapacityPriceIncreaseFactor, purchases))));
    }

    private Object complete(Method method, Object[] args) {
        if (!"complete".equals(method.getName())) return null;
        Player player = args != null && args.length > 0 && args[0] instanceof Player p ? p : null;
        Object offer = args != null && args.length > 1 ? args[1] : null;
        try {
            double factor = MarketplacePlayerPreferences.increaseCapacityFactor(player);
            Class<?> result = Class.forName("de.omegazirkel.risingworld.shop.ShopPurchaseResult");
            Class<?> offerType = Class.forName("de.omegazirkel.risingworld.shop.ShopOffer");
            String message = I18n.getInstance(owner).get("TC_MARKET_SHOP_CAPACITY_PURCHASED", player)
                    .replace("PH_FACTOR", String.valueOf((int) Math.round(factor)));
            return result.getMethod("success", String.class, offerType).invoke(null,
                    message, offer);
        } catch (ReflectiveOperationException ex) { return null; }
    }
}
