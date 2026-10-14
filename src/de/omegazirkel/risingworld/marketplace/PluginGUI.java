package de.omegazirkel.risingworld.marketplace;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.marketplace.MarketCrier;
import de.omegazirkel.risingworld.marketplace.ui.MarketplaceOverlay;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.MessageBoxButtons;
import net.risingworld.api.ui.UITarget;

public class PluginGUI {
    private static final String OVERLAY_ATTRIBUTE = "oz.marketplace.ui.overlay";
    private static PluginGUI instance = null;
    private Marketplace plugin;

    private PluginGUI() {
    }

    public static PluginGUI getInstance(Marketplace plugin) {
        AssetManager.loadIconFromPlugin(plugin, "oz-marketplace");
        AssetManager.loadIconFromPlugin(plugin, "zone-marketplace-indicator");
        AssetManager.loadIconFromPlugin(plugin, "marketplace-capacity");
        AssetManager.loadIconFromPlugin(plugin, "market-crier-male");
        AssetManager.loadIconFromPlugin(plugin, "market-crier-female");
        AssetManager.loadIconFromPlugin(plugin, "market-crier-faction-male");
        AssetManager.loadIconFromPlugin(plugin, "market-crier-faction-female");
        PluginGUI gui = getInstance();
        gui.plugin = plugin;
        PluginMenuManager.registerPluginMenu(new MenuItem(Marketplace.name, "oz-marketplace",
                "Marketplace", gui::openMainMenu));
        return gui;
    }

    public static PluginGUI getInstance() {
        if (instance == null) {
            instance = new PluginGUI();
        }
        return instance;
    }

    public void openMainMenu(Player uiPlayer) {
        if (plugin == null) {
            uiPlayer.hideRadialMenu(false);
            return;
        }
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem("oz-marketplace", "Marketplace",
                player -> {
                    player.hideRadialMenu(true);
                    openMarketplaceOverlay(player);
                }));
        menuItems.add(MenuItem.iconKey("market-crier-male", text(uiPlayer, "tc.market.crier.menu.personal.male"),
                player -> createCrier(player, false, true)));
        menuItems.add(MenuItem.iconKey("market-crier-female", text(uiPlayer, "tc.market.crier.menu.personal.female"),
                player -> createCrier(player, false, false)));
        try {
            if (plugin.canCreateFactionMarketCrier(uiPlayer)) {
                menuItems.add(MenuItem.iconKey("market-crier-faction-male", text(uiPlayer, "tc.market.crier.menu.faction.male"),
                        player -> createFactionCrier(player, true)));
                menuItems.add(MenuItem.iconKey("market-crier-faction-female", text(uiPlayer, "tc.market.crier.menu.faction.female"),
                        player -> createFactionCrier(player, false)));
            }
        } catch (java.sql.SQLException ex) { Marketplace.logger().error("Faction crier menu capacity check failed: " + ex.getMessage()); }
        if (uiPlayer.isAdmin()) {
            menuItems.add(MenuItem.iconKey("market-crier-male", text(uiPlayer, "tc.market.crier.menu.global.male"),
                    player -> createCrier(player, true, true)));
            menuItems.add(MenuItem.iconKey("market-crier-female", text(uiPlayer, "tc.market.crier.menu.global.female"),
                    player -> createCrier(player, true, false)));
        }
        menuItems.add(PluginInfoStatusProviders.menuItem("Info / Status", Marketplace.name));
        menuItems.add(MenuItem.closeMenu(uiPlayer));
        PluginMenuManager.showMenu(uiPlayer, menuItems);
    }

    private void createCrier(Player player, boolean global, boolean male) {
        player.hideRadialMenu(true);
        boolean conversion = plugin.safeCurrentMarketZone(player).isPresent();
        showCrierCreationConfirmation(player, global, male, conversion);
    }

    private void createFactionCrier(Player player, boolean male) {
        player.hideRadialMenu(true);
        showCrierCreationConfirmation(player, false, male, false, true);
    }

    /** Keep NPC creation behind an explicit player-visible confirmation. */
    private void showCrierCreationConfirmation(Player player, boolean global, boolean male, boolean conversion) {
        showCrierCreationConfirmation(player, global, male, conversion, false);
    }

    private void showCrierCreationConfirmation(Player player, boolean global, boolean male, boolean conversion,
            boolean faction) {
        String kind = text(player, faction ? (male ? "tc.market.crier.menu.faction.male" : "tc.market.crier.menu.faction.female") : global
                ? (male ? "tc.market.crier.menu.global.male" : "tc.market.crier.menu.global.female")
                : (male ? "tc.market.crier.menu.personal.male" : "tc.market.crier.menu.personal.female"));
        String title = text(player, conversion ? "tc.market.crier.convert.ui.title"
                : "tc.market.crier.create.confirm.title");
        String message = text(player, conversion ? "tc.market.crier.convert.ui.text"
                : "tc.market.crier.create.confirm.text").replace("PH_KIND", kind);
        player.closeAllActiveUIWindows();
        OZTools.runAfterModalClose(player, () -> player.showMessageBox(MessageBoxButtons.Yes_No,
                title, message, 0, answer -> plugin.enqueue(() -> {
                    if (answer == null || answer != 0 || !player.isConnected()) return;
                    if (faction) plugin.createFactionMarketCrier(player, male);
                    else if (conversion) plugin.convertMarketZoneToCrier(player, global, male);
                    else plugin.createMarketCrier(player, global, male);
                })));
    }

    private String text(Player player, String key) {
        return plugin.i18n().get(key, player);
    }

    public void openMarketplaceOverlay(Player player) {
        player.deleteAttribute("oz.marketplace.crier.endpoint");
        openMarketplaceOverlayInternal(player);
    }

    public void openMarketplaceOverlay(Player player, MarketCrier crier) {
        player.setAttribute("oz.marketplace.crier.endpoint", crier);
        openMarketplaceOverlayInternal(player);
    }

    /** Reopens the modal after the native item selector closed it. */
    public void openMarketplaceWantedDialog(Player player, String itemName, int variant) {
        openMarketplaceOverlayInternal(player);
        MarketplaceOverlay overlay = (MarketplaceOverlay) player.getAttribute(OVERLAY_ATTRIBUTE);
        if (overlay != null && itemName != null && !itemName.isBlank()) {
            overlay.showWantedCreateDialog(itemName, variant);
        }
    }

    private void openMarketplaceOverlayInternal(Player player) {
        UIElement existing = (UIElement) player.getAttribute(OVERLAY_ATTRIBUTE);
        if (existing != null) {
            // Escape closes native modal UI client-side, so the attribute can be stale.
            // Do not send a late removal command here: it may close the new modal instead.
            player.deleteAttribute(OVERLAY_ATTRIBUTE);
        }
        MarketplaceOverlay overlay = new MarketplaceOverlay(plugin, player);
        player.setAttribute(OVERLAY_ATTRIBUTE, overlay);
        player.addUIElement(overlay, UITarget.Modal);
    }

    public void closeMarketplaceOverlay(Player player) {
        UIElement existing = (UIElement) player.getAttribute(OVERLAY_ATTRIBUTE);
        if (existing != null) {
            player.removeUIElement(existing);
            player.deleteAttribute(OVERLAY_ATTRIBUTE);
            player.deleteAttribute("oz.marketplace.crier.endpoint");
            player.closeAllActiveUIWindows();
        }
    }
}
