package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import de.omegazirkel.risingworld.marketplace.MarketZone;
import de.omegazirkel.risingworld.marketplace.MarketCrier;
import de.omegazirkel.risingworld.marketplace.MarketCrierService;
import de.omegazirkel.risingworld.marketplace.MarketplacePlayerPreferences;
import de.omegazirkel.risingworld.marketplace.MarketplaceDatabase;
import de.omegazirkel.risingworld.marketplace.MarketplaceListing;
import de.omegazirkel.risingworld.marketplace.MarketplaceItemState;
import de.omegazirkel.risingworld.marketplace.MarketplaceCapacityShopIntegration;
import de.omegazirkel.risingworld.marketplace.MarketplaceResult;
import de.omegazirkel.risingworld.marketplace.MarketplaceSale;
import de.omegazirkel.risingworld.marketplace.MarketplaceService;
import de.omegazirkel.risingworld.marketplace.MarketplacePluginInfoStatusProvider;
import de.omegazirkel.risingworld.marketplace.PluginSettings;
import de.omegazirkel.risingworld.marketplace.PluginGUI;
import de.omegazirkel.risingworld.marketplace.WalletBridge;
import de.omegazirkel.risingworld.marketplace.ui.MarketplacePlayerPluginData;
import de.omegazirkel.risingworld.marketplace.ui.MarketplacePlayerPluginSettings;
import de.omegazirkel.risingworld.marketplace.ui.MarketplaceZoneIndicatorProvider;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.PlayerSettings;
import de.omegazirkel.risingworld.tools.db.SQLiteConnectionFactory;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import de.omegazirkel.risingworld.tools.ui.SharedIndicators;
import de.omegazirkel.risingworld.tools.bridge.MailBridge;
import net.risingworld.api.Plugin;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.events.player.PlayerNpcInteractionEvent;
import net.risingworld.api.events.player.ui.PlayerUITextFieldChangeEvent;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.Npc;
import net.risingworld.api.objects.Skin;
import net.risingworld.api.World;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Npcs;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

class MarketplaceRuntime extends Plugin {
    private static final List<String> DEFAULT_CRIER_OUTFIT = List.of("medievalshirt", "medievalpants", "medievalshoes");
    private static final List<String> MALE_CRIER_NAMES = List.of(
            "Adrian", "Albrecht", "Benedikt", "Bruno", "Caspar", "Christian", "Daniel", "Elias", "Emil", "Fabian",
            "Felix", "Florian", "Gabriel", "Georg", "Hannes", "Henrik", "Jakob", "Jonas", "Julius", "Konrad",
            "Lars", "Leon", "Ludwig", "Magnus", "Marcel", "Markus", "Matthias", "Moritz", "Nico", "Oskar",
            "Pascal", "Paul", "Philipp", "Ralf", "Roman", "Samuel", "Stefan", "Theodor", "Thomas", "Uwe",
            "Valentin", "Viktor", "Vincent", "Walter", "Werner", "Wilhelm", "Yannik", "Zeno", "Armin", "Gregor");
    private static final List<String> FEMALE_CRIER_NAMES = List.of(
            "Ada", "Agnes", "Beate", "Carla", "Clara", "Diana", "Elena", "Elise", "Emilia", "Frida",
            "Greta", "Hannah", "Helena", "Ida", "Ines", "Johanna", "Julia", "Karla", "Klara", "Lena",
            "Leonie", "Liesel", "Luisa", "Mara", "Marie", "Marlene", "Nina", "Olivia", "Paula", "Rosa",
            "Sabine", "Sophie", "Theresa", "Ulrike", "Valerie", "Vera", "Victoria", "Wilma", "Yvonne", "Zoe",
            "Anja", "Birgit", "Christine", "Dorothea", "Eva", "Friederike", "Gabriele", "Heike", "Kerstin", "Monika");
    static final Colors c = Colors.getInstance();
    private static PluginSettings s;
    private static MarketplaceService service;
    private static Connection sqliteCon;
    private static PlayerSettings playerSettings;
    private static I18n t;
    private static MarketplaceDatabase database;
    private static MarketCrierService crierService;
    private MarketplaceCapacityShopIntegration capacityShop;
    public static String name;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Marketplace");
    }

    @Override
    public void onEnable() {
        name = getDescription("name");
        t = I18n.getInstance(this);
        s = PluginSettings.getInstance((Marketplace) this);
        s.initSettings();

        try {
            sqliteCon = SQLiteConnectionFactory.open(this);
            database = new MarketplaceDatabase(sqliteCon);
            playerSettings = new PlayerSettings(sqliteCon);
            service = new MarketplaceService(database, new WalletBridge(this), new MailBridge(this), s);
            crierService = new MarketCrierService(database, s);
            int repairedMarkets = service.repairLostPlayerMarkets();
            int cleanedCriers = cleanMissingCriersAfterEnable();
            logger().info("Marketplace startup repair completed; repaired player markets: " + repairedMarkets + ".");
            logger().info("Marketplace startup crier cleanup completed; removed records: " + cleanedCriers + ".");
        } catch (SQLException ex) {
            logger().error("Failed to initialize marketplace database: " + ex.getMessage());
            ex.printStackTrace();
        }

        PluginGUI.getInstance((Marketplace) this);
        capacityShop = new MarketplaceCapacityShopIntegration(this);
        capacityShop.register(s);
        PluginShortcutVisibility.register(name, MarketplacePlayerPreferences::shortcutVisible);
        SharedIndicators.registerProvider(name, new MarketplaceZoneIndicatorProvider((Marketplace) this));
        PlayerPluginSettingsOverlay
                .registerPlayerPluginSettings(new MarketplacePlayerPluginSettings(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new MarketplacePlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        PluginInfoStatusProviders
                .registerProvider(
                        new MarketplacePluginInfoStatusProvider((Marketplace) this, getDescription("version")));
        logger().info(getName() + " Plugin is enabled version:" + getDescription("version"));
    }

    /** Missing NPCs retain their endpoint while active listings still need manual settlement. */
    private int cleanMissingCriersAfterEnable() {
        if (database == null) return 0;
        int removed = 0;
        try {
            for (MarketCrier crier : database.listCriers()) {
                Npc npc = World.getNpc(crier.npcId());
                if (npc != null && !npc.isDead()) {
                    // Also repair criers created before delayed dummy initialization was introduced.
                    scheduleCrierInitialization(crier, npc);
                    continue;
                }
                MarketplaceDatabase.CrierDeleteResult result = database.deleteCrierIfEmpty(crier.npcId());
                if (!result.deleted()) {
                    if (result.activeListings() > 0 && rehydrateCrier(crier)) {
                        logger().info("Rehydrated missing market crier '" + crier.name() + "'.");
                    } else if (result.activeListings() > 0) logger().warn("Missing market crier '" + crier.name()
                            + "' retains " + result.activeListings() + " active listing(s); no saved location exists.");
                    continue;
                }
                if (crier.personal()) new WalletBridge(this).archiveSystemAccount(crier.accountId(), "OZ - Marketplace");
                removed++;
            }
        } catch (SQLException ex) {
            logger().error("Failed to clean missing market criers: " + ex.getMessage());
        }
        return removed;
    }

    private boolean rehydrateCrier(MarketCrier crier) {
        try {
            MarketplaceDatabase.CrierLocation location = database.findCrierLocation(crier.endpointId()).orElse(null);
            if (location == null) return false;
            var dummy = Definitions.getNpcDefinition("dummy");
            if (dummy == null) return false;
            Npc npc = World.spawnNpc(dummy.id, crier.male() ? 0 : 1, new Vector3f(location.x(), location.y(), location.z()),
                    new Quaternion(location.rx(), location.ry(), location.rz(), location.rw()));
            if (npc == null) return false;
            scheduleCrierInitialization(crier, npc);
            MarketCrier replacement = new MarketCrier(npc.getGlobalID(), crier.endpointId(), crier.name(), crier.ownerDbId(),
                    crier.ownerName(), crier.global(), crier.globalTradeEnabled(), crier.sharedListings(), crier.level(),
                    crier.male(), crier.createdAt(), crier.feePercent());
            database.replaceCrierNpcId(crier.npcId(), replacement);
            return true;
        } catch (SQLException ex) { logger().error("Failed to rehydrate market crier: " + ex.getMessage()); return false; }
    }

    @Override
    public void onDisable() {
        if (name != null) {
            PluginShortcutVisibility.unregister(name);
            PluginInfoStatusProviders.unregisterProvider(name);
        }
        SharedIndicators.unregisterProvider(name);
        if (sqliteCon != null) {
            try {
                sqliteCon.close();
            } catch (SQLException ex) {
                logger().error("Failed to close marketplace database connection: " + ex.getMessage());
            }
        }
    }

    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        if (service != null) {
            service.updateSettings(s);
        }
        if (crierService != null) crierService.updateSettings(s);
        if (capacityShop != null) capacityShop.register(s);
    }

    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        MarketplacePlayerPreferences.load(player);
        if (s.enableWelcomeMessage) {
            player.sendTextMessage(c.okay + tr(player, "tc.market.chat.welcome",
                    "PH_PLUGIN", getDescription("name"),
                    "PH_VERSION", getDescription("version"),
                    "PH_COMMAND", s.marketCommand));
        }
        if (player.isAdmin() && service != null && !service.walletAvailable()) {
            player.sendTextMessage(c.warning + tr(player, "tc.market.result.wallet.required"));
        }
    }

    public void onPlayerUITextFieldChangeEvent(PlayerUITextFieldChangeEvent event) {
        Player player = event.getPlayer();
        Object overlay = player.getAttribute("oz.marketplace.ui.overlay");
        if (overlay instanceof de.omegazirkel.risingworld.marketplace.ui.MarketplaceOverlay marketplaceOverlay) {
            marketplaceOverlay.onTextFieldChanged(event.getUITextField(), event.getNewText());
        }
    }

    public void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String[] parts = event.getCommand().split(" ");
        if (!parts[0].equals("/" + s.marketCommand)) {
            return;
        }
        if (parts.length > 1 && (parts[1].equalsIgnoreCase("status") || parts[1].equalsIgnoreCase("info"))) {
            PluginInfoStatusProviders.show(player, name);
            return;
        }
        if (service == null) {
            player.sendTextMessage(c.error + tr(player, "tc.market.result.database.unavailable"));
            return;
        }
        if (parts.length == 1) {
            PluginGUI.getInstance().openMainMenu(player);
            return;
        }
        if (parts[1].equalsIgnoreCase("list")) {
            sendListings(player);
            return;
        }
        switch (parts[1].toLowerCase()) {
            case "buy" -> buy(player, parts);
            case "wanted" -> wanted(player, parts);
            case "cancel" -> cancel(player, parts);
            case "sales" -> sales(player);
            case "zone" -> zone(player, parts);
            case "crier" -> crier(player, parts);
            case "help" -> usage(player);
            default -> usage(player);
        }
    }

    public void onPlayerNpcInteractionEvent(PlayerNpcInteractionEvent event) {
        if (event.getInteractionType() != Npcs.InteractionType.Default) return;
        if (database == null || event.getNpc() == null) return;
        try {
            MarketCrier crier = database.findCrier(event.getNpc().getGlobalID()).orElse(null);
            if (crier == null) return;
            Player player = event.getPlayer();
            player.closeAllActiveUIWindows();
            PluginGUI.getInstance().openMarketplaceOverlay(player, crier);
            event.setCancelled(true);
        } catch (SQLException ex) {
            logger().error("Failed to open market crier: " + ex.getMessage());
        }
    }

    public void createMarketCrier(Player player, boolean global, boolean male) {
        if (player == null) return;
        if (global && !player.isAdmin()) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.denied"));
            return;
        }
        createMarketCrierInternal(player, global, !global, male);
    }

    /** UI entry point; the same server-side conversion checks as the command apply. */
    public void convertMarketZoneToCrier(Player player, boolean global, boolean male) {
        convertCurrentMarketZoneToCrier(player, new String[] { "mp", "crier", "convert",
                global ? "global" : "personal", male ? "male" : "female", "confirm" });
    }

    private void crier(Player player, String[] parts) {
        if (parts.length >= 3 && parts[1].equalsIgnoreCase("crier") && parts[2].equalsIgnoreCase("convert")) {
            convertCurrentMarketZoneToCrier(player, parts);
            return;
        }
        if (parts.length == 5 && parts[1].equalsIgnoreCase("crier") && parts[2].equalsIgnoreCase("configure")) {
            configureCurrentCrier(player, parts[3], parts[4]);
            return;
        }
        if (parts.length == 4 && parts[1].equalsIgnoreCase("crier") && parts[2].equalsIgnoreCase("upgrade")) {
            upgradeCurrentCrier(player, parts[3]);
            return;
        }
        if (parts.length == 3 && parts[1].equalsIgnoreCase("crier") && parts[2].equalsIgnoreCase("position")) {
            sendResult(player, moveCurrentMarketCrier(player));
            return;
        }
        if (parts.length == 3 && parts[1].equalsIgnoreCase("crier") && parts[2].equalsIgnoreCase("appearance")) {
            sendResult(player, copyCurrentMarketCrierAppearance(player));
            return;
        }
        if ((parts.length == 5 || parts.length == 6) && parts[1].equalsIgnoreCase("crier")
                && parts[2].equalsIgnoreCase("account")) {
            transferCrierAccount(player, parts[3], parseLong(parts[4], 0L), parts.length == 6 ? parts[5] : "");
            return;
        }
        if (parts.length != 5 || !parts[1].equalsIgnoreCase("crier") || !parts[2].equalsIgnoreCase("create")) {
            usage(player);
            return;
        }
        boolean global = parts[3].equalsIgnoreCase("global");
        boolean personal = parts[3].equalsIgnoreCase("personal");
        boolean male = parts[4].equalsIgnoreCase("male");
        boolean female = parts[4].equalsIgnoreCase("female");
        if ((!global && !personal) || (!male && !female)) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.denied"));
            return;
        }
        createMarketCrier(player, global, male);
    }

    private void transferCrierAccount(Player player, String direction, long amount, String requestedCurrency) {
        Object endpoint = player.getAttribute("oz.marketplace.crier.endpoint");
        if (!(endpoint instanceof MarketCrier current) || !current.ownedBy(player.getDbID()) || amount <= 0L) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.account.invalid"));
            return;
        }
        WalletBridge wallet = new WalletBridge(this);
        if (!wallet.hasSystemAccountApi()) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.account.failed"));
            return;
        }
        String currency = requestedCurrency == null || requestedCurrency.isBlank()
                ? wallet.defaultCurrencyIdentifier() : requestedCurrency.trim();
        String correlation = "marketplace:crier:account:" + current.npcId() + ':' + System.nanoTime();
        WalletBridge.WalletTransferCallResult transfer;
        if (direction.equalsIgnoreCase("deposit")) {
            transfer = wallet.transferPlayerToSystemIdempotent(player.getDbID(), current.accountId(), amount,
                    "Marketplace crier deposit #" + current.npcId(), currency, "OZ - Marketplace", correlation);
        } else if (direction.equalsIgnoreCase("withdraw")) {
            transfer = wallet.transferSystemToPlayerIdempotent(current.accountId(), player.getDbID(), amount,
                    "Marketplace crier withdrawal #" + current.npcId(), currency, "OZ - Marketplace", correlation);
        } else {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.account.invalid"));
            return;
        }
        player.sendTextMessage((transfer.success() ? c.okay : c.error)
                + tr(player, transfer.success() ? "tc.market.crier.account.success" : "tc.market.crier.account.failed"));
    }

    private void upgradeCurrentCrier(Player player, String confirmation) {
        if (!confirmation.equalsIgnoreCase("confirm")) {
            player.sendTextMessage(c.info + tr(player, "tc.market.crier.upgrade.confirm"));
            return;
        }
        Object endpoint = player.getAttribute("oz.marketplace.crier.endpoint");
        if (!(endpoint instanceof MarketCrier current) || !current.ownedBy(player.getDbID()) || crierService == null) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.configure.denied"));
            return;
        }
        if (current.level() == Integer.MAX_VALUE) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.upgrade.failed"));
            return;
        }
        long cost = crierService.upgradeCost(current);
        WalletBridge wallet = new WalletBridge(this);
        String correlation = "marketplace:crier:upgrade:" + current.npcId() + ':' + System.nanoTime();
        if (cost > 0L) {
            if (!wallet.hasSystemAccountApi()) {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.upgrade.failed"));
                return;
            }
            WalletBridge.WalletTransferCallResult transfer = wallet.transferPlayerToSystemIdempotent(player.getDbID(),
                    current.accountId(), cost, "Marketplace crier upgrade #" + current.npcId(),
                    wallet.defaultCurrencyIdentifier(), "OZ - Marketplace", correlation);
            if (!transfer.success()) {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.upgrade.failed"));
                return;
            }
        }
        MarketCrier upgraded = new MarketCrier(current.npcId(), current.endpointId(), current.name(),
                current.ownerDbId(), current.ownerName(), false, false, current.sharedListings(), current.level() + 1,
                current.male(), current.createdAt(), current.feePercent());
        try {
            database.upsertCrier(upgraded);
            player.setAttribute("oz.marketplace.crier.endpoint", upgraded);
            player.sendTextMessage(c.okay + tr(player, "tc.market.crier.upgrade.success",
                    "PH_LEVEL", String.valueOf(upgraded.level())));
        } catch (SQLException ex) {
            if (cost > 0L) wallet.reverseAccountTransferIdempotent(correlation, correlation + ":refund",
                    "Marketplace crier upgrade refund #" + current.npcId(), "OZ - Marketplace");
            logger().error("Failed to upgrade market crier: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.upgrade.failed"));
        }
    }

    /** Configuration is bound to the interacted NPC endpoint, never a client-supplied NPC id. */
    private void configureCurrentCrier(Player player, String setting, String value) {
        Object endpoint = player.getAttribute("oz.marketplace.crier.endpoint");
        if (!(endpoint instanceof MarketCrier current)) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.configure.interact"));
            return;
        }
        boolean enabled;
        if (value.equalsIgnoreCase("on")) enabled = true;
        else if (value.equalsIgnoreCase("off")) enabled = false;
        else {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.configure.invalid"));
            return;
        }
        try {
            MarketCrier updated;
            if (setting.equalsIgnoreCase("share") && current.ownedBy(player.getDbID())) {
                updated = new MarketCrier(current.npcId(), current.endpointId(), current.name(), current.ownerDbId(),
                        current.ownerName(), false, false, enabled, current.level(), current.male(), current.createdAt(),
                        current.feePercent());
            } else if (setting.equalsIgnoreCase("global") && current.global() && player.isAdmin()) {
                updated = new MarketCrier(current.npcId(), current.endpointId(), current.name(), current.ownerDbId(),
                        current.ownerName(), true, enabled, current.sharedListings(), current.level(), current.male(),
                        current.createdAt(), current.feePercent());
            } else {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.configure.denied"));
                return;
            }
            database.upsertCrier(updated);
            player.setAttribute("oz.marketplace.crier.endpoint", updated);
            player.sendTextMessage(c.okay + tr(player, "tc.market.crier.configure.success"));
        } catch (SQLException ex) {
            logger().error("Failed to configure market crier: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.configure.failed"));
        }
    }

    /** Persists an endpoint fee independently from area-market zones. */
    public MarketplaceResult setCurrentMarketCrierFee(Player player, int feePercent) {
        MarketCrier current = currentEditableCrier(player);
        if (current == null) return MarketplaceResult.failKey("tc.market.crier.edit.denied",
                "You may not edit this market crier.");
        MarketCrier updated = new MarketCrier(current.npcId(), current.endpointId(), current.name(), current.ownerDbId(),
                current.ownerName(), current.global(), current.globalTradeEnabled(), current.sharedListings(), current.level(),
                current.male(), current.createdAt(), feePercent);
        try {
            database.upsertCrier(updated);
            player.setAttribute("oz.marketplace.crier.endpoint", updated);
            return MarketplaceResult.okKey("tc.market.crier.configure.success", "Market crier setting saved.");
        } catch (SQLException ex) {
            logger().error("Failed to update market crier fee: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.crier.configure.failed", "The market crier setting could not be saved.");
        }
    }

    public MarketplaceResult setCurrentMarketCrierSharing(Player player, boolean shared) {
        MarketCrier current = currentEditableCrier(player);
        if (current == null || !current.ownedBy(player.getDbID())) return MarketplaceResult.failKey(
                "tc.market.crier.edit.denied", "You may not edit this market crier.");
        MarketCrier updated = new MarketCrier(current.npcId(), current.endpointId(), current.name(), current.ownerDbId(),
                current.ownerName(), false, false, shared, current.level(), current.male(), current.createdAt(),
                current.feePercent());
        try {
            database.upsertCrier(updated);
            player.setAttribute("oz.marketplace.crier.endpoint", updated);
            return MarketplaceResult.okKey("tc.market.crier.configure.success", "Market crier setting saved.");
        } catch (SQLException ex) {
            logger().error("Failed to update market crier sharing: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.crier.configure.failed", "The market crier setting could not be saved.");
        }
    }

    /** Moves only the NPC selected by a server-side interaction attribute. */
    public MarketplaceResult moveCurrentMarketCrier(Player player) {
        MarketCrier current = currentEditableCrier(player);
        if (current == null) return MarketplaceResult.failKey("tc.market.crier.edit.denied",
                "You may not edit this market crier.");
        Npc npc = World.getNpc(current.npcId());
        if (npc == null) return MarketplaceResult.failKey("tc.market.crier.edit.missing",
                "This market crier is no longer available.");
        npc.setPosition(player.getPosition());
        npc.setRotation(player.getRotation());
        try { saveCrierLocation(current, npc); } catch (SQLException ex) { return MarketplaceResult.failKey("tc.market.crier.edit.position.failed", "Market crier position could not be saved."); }
        return MarketplaceResult.okKey("tc.market.crier.edit.position.success", "Market crier position updated.");
    }

    /** Small persistent transform increments mirror the useful movement controls from the native NPC editor. */
    public MarketplaceResult nudgeCurrentMarketCrier(Player player, float x, float y, float z) {
        MarketCrier current = currentEditableCrier(player);
        if (current == null) return MarketplaceResult.failKey("tc.market.crier.edit.denied",
                "You may not edit this market crier.");
        Npc npc = World.getNpc(current.npcId());
        if (npc == null) return MarketplaceResult.failKey("tc.market.crier.edit.missing",
                "This market crier is no longer available.");
        Vector3f position = npc.getPosition();
        npc.setPosition(position.x + x, position.y + y, position.z + z);
        try {
            saveCrierLocation(current, npc);
            return MarketplaceResult.okKey("tc.market.crier.edit.position.success", "Market crier position updated.");
        } catch (SQLException ex) {
            logger().error("Failed to save market crier transform: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.crier.edit.position.failed", "Market crier position could not be saved.");
        }
    }

    /** Rotates the selected Crier around the world Y axis without changing its pitch or roll. */
    public MarketplaceResult rotateCurrentMarketCrier(Player player, float degrees) {
        MarketCrier current = currentEditableCrier(player);
        if (current == null) return MarketplaceResult.failKey("tc.market.crier.edit.denied",
                "You may not edit this market crier.");
        Npc npc = World.getNpc(current.npcId());
        if (npc == null) return MarketplaceResult.failKey("tc.market.crier.edit.missing",
                "This market crier is no longer available.");
        Vector3f direction = npc.getViewDirection();
        float horizontalLength = (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalLength <= 0.0001f) direction = new Vector3f(0f, 0f, 1f);
        float radians = (float) Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        npc.setViewDirection(direction.x * cos - direction.z * sin, 0f,
                direction.x * sin + direction.z * cos);
        try {
            saveCrierLocation(current, npc);
            return MarketplaceResult.okKey("tc.market.crier.edit.position.success", "Market crier position updated.");
        } catch (SQLException ex) {
            logger().error("Failed to save market crier rotation: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.crier.edit.position.failed", "Market crier position could not be saved.");
        }
    }

    /** Removes a crier endpoint after its active listings have been settled or withdrawn. */
    public MarketplaceResult dissolveCurrentMarketCrier(Player player, boolean deleteNpc) {
        MarketCrier current = currentEditableCrier(player);
        if (current == null) return MarketplaceResult.failKey("tc.market.crier.edit.denied",
                "You may not edit this market crier.");
        try {
            MarketplaceDatabase.CrierDeleteResult result = database.deleteCrierIfEmpty(current.npcId());
            if (!result.deleted()) {
                return MarketplaceResult.failKey("tc.market.crier.delete.active",
                        "This market crier still has PH_COUNT active listing(s).",
                        "PH_COUNT", String.valueOf(result.activeListings()));
            }
            if (current.personal()) new WalletBridge(this).archiveSystemAccount(current.accountId(), "OZ - Marketplace");
            Npc npc = World.getNpc(current.npcId());
            if (deleteNpc && npc != null && !npc.isDead()) npc.delete();
            player.deleteAttribute("oz.marketplace.crier.endpoint");
            return MarketplaceResult.okKey("tc.market.crier.delete.success", "Market crier dissolved.");
        } catch (SQLException ex) {
            logger().error("Failed to dissolve market crier " + current.npcId() + ": " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.crier.delete.failed", "Market crier could not be dissolved.");
        }
    }

    /** Copies the owner's/admin's visible avatar appearance to the interacted crier only. */
    public MarketplaceResult copyCurrentMarketCrierAppearance(Player player) {
        MarketCrier current = currentEditableCrier(player);
        if (current == null) return MarketplaceResult.failKey("tc.market.crier.edit.denied",
                "You may not edit this market crier.");
        Npc npc = World.getNpc(current.npcId());
        if (npc == null) return MarketplaceResult.failKey("tc.market.crier.edit.missing",
                "This market crier is no longer available.");
        try {
            npc.getClothes().deserialize(player.getClothes().serialize());
            saveCrierAppearance(current, npc);
            return MarketplaceResult.okKey("tc.market.crier.edit.appearance.success",
                    "Market crier appearance updated.");
        } catch (Exception ex) {
            logger().error("Failed to copy market crier appearance: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.crier.edit.appearance.failed",
                    "Market crier appearance could not be updated.");
        }
    }

    private MarketCrier currentEditableCrier(Player player) {
        Object endpoint = player.getAttribute("oz.marketplace.crier.endpoint");
        if (!(endpoint instanceof MarketCrier current)) return null;
        return current.ownedBy(player.getDbID()) || (current.global() && player.isAdmin()) ? current : null;
    }

    private void saveCrierLocation(MarketCrier crier, Npc npc) throws SQLException {
        Vector3f position = npc.getPosition(); Quaternion rotation = npc.getRotation();
        database.upsertCrierLocation(crier.endpointId(), new MarketplaceDatabase.CrierLocation(position.x, position.y, position.z,
                rotation.x, rotation.y, rotation.z, rotation.w));
    }

    private void applySavedAppearance(MarketCrier crier, Npc npc) throws SQLException {
        MarketplaceDatabase.CrierAppearance appearance = database.findCrierAppearance(crier.endpointId()).orElse(null);
        if (appearance == null) return;
        npc.getSkin().setGender(Skin.Gender.valueOf(appearance.gender()));
        npc.getSkin().setSkinColor(appearance.skinColor()); npc.getSkin().setHairColor(appearance.hairColor());
        npc.getSkin().setEyeColor(appearance.eyeColor()); npc.getSkin().setHairstyle(appearance.hairstyle());
        npc.getSkin().setBeard(appearance.beard()); npc.getSkin().setVariation(appearance.variation());
        npc.getClothes().deserialize(appearance.clothes());
    }

    /** Dummy NPCs initialize asynchronously. Apply and verify their identity after that native setup completes. */
    private void scheduleCrierInitialization(MarketCrier crier, Npc npc) {
        executeDelayed(0.1f, () -> initializeCrier(npc, crier));
        executeDelayed(0.5f, () -> initializeCrier(npc, crier));
        executeDelayed(1.0f, () -> initializeCrier(npc, crier));
    }

    private void initializeCrier(Npc npc, MarketCrier crier) {
        if (npc == null || npc.isDead()) return;
        try {
            npc.setName(crier.name());
            npc.setLocked(true);
            // Keep the crier fixed by the plugin, but retain normal NPC animation and idle behaviour.
            npc.setStatic(false);
            npc.setInteractable(true);
            npc.setInvincible(true);
            if (database.findCrierAppearance(crier.endpointId()).isPresent()) {
                applySavedAppearance(crier, npc);
            } else {
                Skin skin = npc.getSkin();
                skin.setGender(crier.male() ? Skin.Gender.Male : Skin.Gender.Female);
                skin.setSkinColor(crier.male() ? 0xE0AC69 : 0xF1C27D);
                skin.setHairColor(crier.male() ? 0x3B2A1D : 0x5C3B24);
                skin.setEyeColor(0x4C7A4C);
                skin.setHairstyle(crier.male() ? 55 : 105);
                skin.setBeard((byte) (crier.male() ? 1 : -1));
                skin.setVariation((byte) 0);
                npc.getClothes().removeAll();
                for (String garment : DEFAULT_CRIER_OUTFIT) {
                    ClothingDefinition definition = Definitions.getClothingDefinition(garment);
                    if (definition != null) npc.getClothes().add((short) definition.id);
                }
                saveCrierAppearance(crier, npc);
            }
        } catch (Exception ex) {
            logger().error("Failed to initialize market crier " + crier.npcId() + ": " + ex.getMessage());
        }
    }

    private void saveCrierAppearance(MarketCrier crier, Npc npc) throws SQLException {
        Skin skin = npc.getSkin();
        database.upsertCrierAppearance(crier.endpointId(), new MarketplaceDatabase.CrierAppearance(
                skin.getGender().name(), skin.getSkinColor(), skin.getHairColor(), skin.getEyeColor(),
                skin.getHairstyle(), skin.getBeard(), skin.getVariation(), npc.getClothes().serialize()));
    }

    private String crierName(Player player, boolean male) {
        List<String> names = male ? MALE_CRIER_NAMES : FEMALE_CRIER_NAMES;
        String givenName = names.get(ThreadLocalRandom.current().nextInt(names.size()));
        return tr(player, male ? "tc.market.crier.name.male" : "tc.market.crier.name.female") + " " + givenName;
    }

    private void createMarketCrierInternal(Player player, boolean global, boolean personal, boolean male) {
        Npc npc = null;
        MarketCrier crier = null;
        boolean accountCreated = false;
        try {
            if (service.currentZone(player).isPresent()) {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.zone.exists"));
                return;
            }
            if (personal && (crierService == null || !crierService.canCreatePersonal(player.getDbID()))) {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.limit"));
                return;
            }
            var dummy = Definitions.getNpcDefinition("dummy");
            if (dummy == null) throw new SQLException("NPC definition dummy is unavailable");
            npc = World.spawnNpc(dummy.id, male ? 0 : 1, player.getPosition(), player.getRotation());
            if (npc == null) throw new SQLException("Could not spawn market crier NPC");
            String name = crierName(player, male);
            crier = new MarketCrier(npc.getGlobalID(), "crier-" + npc.getGlobalID(), name,
                    personal ? player.getDbID() : 0, personal ? player.getName() : "", global, false, false,
                    1, male, System.currentTimeMillis(), s.defaultLocalFeePercent);
            if (personal && !new WalletBridge(this).createSystemAccount(crier.accountId(), "MARKET_CRIER", name,
                    "OZ - Marketplace").success()) {
                npc.delete();
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.wallet.failed"));
                return;
            }
            accountCreated = personal;
            database.upsertCrier(crier);
            saveCrierLocation(crier, npc);
            scheduleCrierInitialization(crier, npc);
            player.sendTextMessage(c.okay + tr(player, "tc.market.crier.create.success", "PH_NAME", name));
        } catch (SQLException ex) {
            if (npc != null) npc.delete();
            if (accountCreated && crier != null) {
                new WalletBridge(this).archiveSystemAccount(crier.accountId(), "OZ - Marketplace");
            }
            logger().error("Failed to create market crier: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.failed"));
        }
    }

    /**
     * The explicit confirm token keeps an irreversible endpoint conversion out of accidental commands.
     * Wallet creation happens before the SQLite transaction and is compensated if that transaction fails.
     */
    private void convertCurrentMarketZoneToCrier(Player player, String[] parts) {
        if (parts.length != 6 || !parts[5].equalsIgnoreCase("confirm")) {
            player.sendTextMessage(c.info + tr(player, "tc.market.crier.convert.confirm"));
            return;
        }
        boolean global = parts[3].equalsIgnoreCase("global");
        boolean personal = parts[3].equalsIgnoreCase("personal");
        boolean male = parts[4].equalsIgnoreCase("male");
        boolean female = parts[4].equalsIgnoreCase("female");
        if ((!global && !personal) || (!male && !female)) {
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.denied"));
            return;
        }
        try {
            Optional<MarketZone> current = service.currentZone(player);
            if (current.isEmpty() || database.findCrierByEndpoint(current.get().id()).isPresent()) {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.convert.zone.required"));
                return;
            }
            MarketZone zone = current.get();
            if (global && (!player.isAdmin() || zone.playerOwned())) {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.convert.ownership"));
                return;
            }
            if (personal && (!zone.ownedBy(player.getDbID()) || crierService == null
                    || !crierService.canReplacePersonalMarket(player.getDbID()))) {
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.convert.ownership"));
                return;
            }
            var dummy = Definitions.getNpcDefinition("dummy");
            if (dummy == null) throw new SQLException("NPC definition dummy is unavailable");
            Npc npc = World.spawnNpc(dummy.id, male ? 0 : 1, player.getPosition(), player.getRotation());
            if (npc == null) throw new SQLException("Could not spawn market crier NPC");
            String name = crierName(player, male);
            MarketCrier crier = new MarketCrier(npc.getGlobalID(), "crier-" + npc.getGlobalID(), name,
                    personal ? player.getDbID() : 0, personal ? player.getName() : "", global,
                    global && zone.globalTradeAllowed(s.globalMarketplaceEnabled), false, 1, male,
                    System.currentTimeMillis(), zone.feePercent());
            WalletBridge wallet = new WalletBridge(this);
            if (personal && !wallet.createSystemAccount(crier.accountId(), "MARKET_CRIER", name,
                    "OZ - Marketplace").success()) {
                npc.delete();
                player.sendTextMessage(c.error + tr(player, "tc.market.crier.create.wallet.failed"));
                return;
            }
            try {
                int moved = database.convertZoneToCrier(zone.id(), crier);
                saveCrierLocation(crier, npc);
                scheduleCrierInitialization(crier, npc);
                player.sendTextMessage(c.okay + tr(player, "tc.market.crier.convert.success",
                        "PH_NAME", name, "PH_COUNT", String.valueOf(moved)));
            } catch (SQLException ex) {
                npc.delete();
                if (personal) wallet.archiveSystemAccount(crier.accountId(), "OZ - Marketplace");
                throw ex;
            }
        } catch (SQLException ex) {
            logger().error("Failed to convert market zone to crier: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "tc.market.crier.convert.failed"));
        }
    }

    private void buy(Player player, String[] parts) {
        if (parts.length < 3 || parts.length > 4) {
            usage(player);
            return;
        }
        MarketplaceResult result = service.buy(player, parseLong(parts[2], 0L),
                parts.length == 4 ? parseInt(parts[3], 0) : Integer.MAX_VALUE);
        sendResult(player, result);
    }

    private void wanted(Player player, String[] parts) {
        if (parts.length < 7 || parts.length > 8) {
            usage(player);
            return;
        }
        boolean global = parts[6].equalsIgnoreCase("global");
        MarketplaceResult result = service.createWantedListing(player, parts[2], parseInt(parts[3], -1),
                parseInt(parts[4], 0), parseLong(parts[5], 0L), parts.length == 8 ? parts[7] : "", global);
        sendResult(player, result);
    }

    private void cancel(Player player, String[] parts) {
        if (parts.length != 3) {
            usage(player);
            return;
        }
        MarketplaceResult result = service.cancel(player, parseLong(parts[2], 0L));
        sendResult(player, result);
    }

    private void sales(Player player) {
        try {
            List<MarketplaceSale> sales = service.listSales(player, 10);
            if (sales.isEmpty()) {
                player.sendTextMessage(c.info + tr(player, "tc.market.chat.no.sales"));
                return;
            }
            player.sendTextMessage(c.okay + tr(player, "tc.market.chat.latest.sales"));
            for (MarketplaceSale sale : sales) {
                player.sendTextMessage(c.info + tr(player, "tc.market.chat.sale.row",
                        "PH_SALE", String.valueOf(sale.id()),
                        "PH_LISTING", String.valueOf(sale.listingId()),
                        "PH_AMOUNT", String.valueOf(sale.amount()),
                        "PH_ITEM", sale.itemName() + ":" + sale.itemVariant(),
                        "PH_PAYOUT", String.valueOf(sale.sellerPayout()),
                        "PH_CURRENCY", currencyLabel(sale.currencyIdentifier()),
                        "PH_FEE", String.valueOf(sale.fee())));
            }
        } catch (SQLException ex) {
            logger().error("Failed to list sales: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "tc.market.chat.sales.failed"));
        }
    }

    private void zone(Player player, String[] parts) {
        if (!player.isAdmin()) {
            player.sendTextMessage(c.error + tr(player, "tc.market.chat.admin.only"));
            return;
        }
        if (parts.length >= 3 && parts[2].equalsIgnoreCase("list")) {
            sendZones(player);
            return;
        }
        if (parts.length >= 3 && parts[2].equalsIgnoreCase("delete")) {
            MarketplaceResult result = deleteCurrentMarketZone(player, parts.length >= 4 ? parts[3] : null);
            sendResult(player, result);
            return;
        }
        if (parts.length < 7 || !parts[2].equalsIgnoreCase("set")) {
            usage(player);
            return;
        }
        String id = parts[3];
        int radius = parseInt(parts[4], 0);
        int fee = parseInt(parts[5], s.defaultLocalFeePercent);
        int globalMode = parseGlobalMode(parts[6]);
        String label = parts.length >= 8 ? parts[7] : id;
        MarketplaceResult result = service.createZone(id, label, 0L, player.getChunkPosition(), radius, fee, globalMode);
        sendResult(player, result);
    }

    public MarketplaceResult createOrUpdateCurrentMarketZone(Player player) {
        return createOrUpdateCurrentMarketZone(player, false);
    }

    /** Administrators explicitly choose whether an area market is system-owned or player-owned. */
    public MarketplaceResult createOrUpdateCurrentMarketZone(Player player, boolean privateMarket) {
        if (s.onlyMarketCriers && !player.isAdmin() && safeCurrentMarketZone(player).isEmpty()) {
            return MarketplaceResult.failKey("tc.market.result.crier.only",
                    "New markets are available as market criers only.");
        }
        Area area = player.getCurrentArea();
        if (area == null || area.getID() <= 0L) {
            return MarketplaceResult.failKey("tc.market.result.area.required",
                    "Stand inside an existing area to create a market.");
        }
        Optional<MarketZone> existing = safeCurrentMarketZone(player);
        if (existing.isPresent() && !service.canManageZone(player, existing.get())) {
            return MarketplaceResult.failKey("tc.market.result.manage.own.only",
                    "You can only manage your own market.");
        }
        if (existing.isEmpty() && (!player.isAdmin() || privateMarket)) {
            return service.createPlayerMarket(player);
        }
        int globalMode = existing.map(MarketZone::globalTradeMode).orElse(MarketZone.GLOBAL_DEFAULT);
        return service.createAreaZone(currentZoneId(player), currentZoneName(player), area.getID(),
                existing.map(MarketZone::feePercent).orElse(s.defaultLocalFeePercent), globalMode);
    }

    public MarketplaceResult syncCurrentMarketZoneName(Player player) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("tc.market.result.not.in.market",
                        "You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            if (!service.canManageZone(player, current)) {
                return MarketplaceResult.failKey("tc.market.result.manage.own.only",
                        "You can only manage your own market.");
            }
            return service.updateZone(new MarketZone(current.id(), currentZoneName(player),
                    current.areaId(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), current.feePercent(), current.globalTradeMode(),
                    current.createdAt(), current.ownerDbId(), current.ownerName(), current.ownerAreaPermission()));
        } catch (SQLException ex) {
            logger().error("Failed to sync market zone name: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.result.zone.sync.failed",
                    "Could not synchronize the market zone name.");
        }
    }

    public MarketplaceResult toggleCurrentMarketZoneGlobal(Player player) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("tc.market.result.not.in.market",
                        "You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            if (!service.canManageZone(player, current)) {
                return MarketplaceResult.failKey("tc.market.result.manage.own.only",
                        "You can only manage your own market.");
            }
            if (current.playerOwned()) {
                return MarketplaceResult.failKey("tc.market.result.player.market.global.locked",
                        "Private markets cannot change global trading.");
            }
            int nextMode = nextGlobalTradeMode(current.globalTradeMode());
            return service.updateZone(new MarketZone(current.id(), current.name(),
                    current.areaId(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), current.feePercent(), nextMode,
                    current.createdAt(), current.ownerDbId(), current.ownerName(), current.ownerAreaPermission()));
        } catch (SQLException ex) {
            logger().error("Failed to update market zone global override: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.result.zone.global.failed",
                    "Could not update the market zone global mode.");
        }
    }

    public MarketplaceResult setCurrentMarketZoneFee(Player player, int feePercent) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("tc.market.result.not.in.market",
                        "You are not standing in a market zone.");
            }
            MarketZone current = zone.get();
            if (!service.canManageZone(player, current)) {
                return MarketplaceResult.failKey("tc.market.result.manage.own.only",
                        "You can only manage your own market.");
            }
            return service.updateZone(new MarketZone(current.id(), current.name(),
                    current.areaId(),
                    current.minChunkX(), current.maxChunkX(), current.minChunkY(), current.maxChunkY(),
                    current.minChunkZ(), current.maxChunkZ(), feePercent, current.globalTradeMode(),
                    current.createdAt(), current.ownerDbId(), current.ownerName(), current.ownerAreaPermission()));
        } catch (SQLException ex) {
            logger().error("Failed to update market zone fee: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.result.zone.fee.failed",
                    "Could not update the market zone fee.");
        }
    }

    public MarketplaceResult deleteCurrentMarketZone(Player player) {
        return deleteCurrentMarketZone(player, null);
    }

    private MarketplaceResult deleteCurrentMarketZone(Player player, String expectedZoneId) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return MarketplaceResult.failKey("tc.market.result.not.in.market",
                        "You are not standing in a market zone.");
            }
            if (!service.canManageZone(player, zone.get())) {
                return MarketplaceResult.failKey("tc.market.result.manage.own.only",
                        "You can only manage your own market.");
            }
            if (expectedZoneId != null && !expectedZoneId.isBlank() && !zone.get().id().equals(expectedZoneId)) {
                return MarketplaceResult.failKey("tc.market.result.delete.current.only",
                        "You can only delete the market zone you are currently standing in.");
            }
            return service.deleteZone(zone.get().id());
        } catch (SQLException ex) {
            logger().error("Failed to delete current market zone: " + ex.getMessage());
            return MarketplaceResult.failKey("tc.market.result.zone.delete.failed",
                    "Could not delete market zone.");
        }
    }

    public Optional<MarketZone> currentMarketZone(Player player) throws SQLException {
        if (service == null) {
            return Optional.empty();
        }
        return service.currentZone(player);
    }

    public Optional<MarketZone> safeCurrentMarketZone(Player player) {
        try {
            return currentMarketZone(player);
        } catch (SQLException ex) {
            logger().error("Failed to read current marketplace zone: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public PluginSettings marketplaceSettings() {
        return s;
    }

    public boolean showMarketplaceZoneIndicator() {
        return s != null && s.showMarketplaceZoneIndicator;
    }

    public boolean walletAvailable() {
        return service != null && service.walletAvailable();
    }

    public boolean marketplaceZoneIndicatorVisible(Player player, MarketZone zone) {
        if (!showMarketplaceZoneIndicator() || zone == null) {
            return false;
        }
        if (s.localMarketplaceEnabled || s.globalMarketplaceEnabled) {
            return true;
        }
        return zone.globalTradeMode() == MarketZone.GLOBAL_ALLOW;
    }

    public I18n i18n() {
        return t;
    }

    public List<MarketplaceListing> visibleMarketplaceListings(Player player) throws SQLException {
        if (service == null) {
            return List.of();
        }
        return service.listVisibleListings(player);
    }

    public List<MarketplaceSale> marketplaceSales(Player player, int limit) throws SQLException {
        if (service == null) {
            return List.of();
        }
        return service.listSales(player, limit);
    }

    public MarketplaceResult createMarketplaceListing(Player player, String itemName, int variant, int amount,
            long price, String currency, boolean global) {
        return createMarketplaceListing(player, itemName, variant, amount, price, currency, global, null);
    }

    public MarketplaceResult createMarketplaceListing(Player player, String itemName, int variant, int amount,
            long price, String currency, boolean global, MarketplaceItemState itemState) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.createListing(player, itemName, variant, amount, price, currency, global, itemState);
    }

    public MarketplaceResult buyMarketplaceListing(Player player, long listingId) {
        return buyMarketplaceListing(player, listingId, Integer.MAX_VALUE);
    }

    public MarketplaceResult buyMarketplaceListing(Player player, long listingId, int amount) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.buy(player, listingId, amount);
    }

    public MarketplaceResult cancelMarketplaceListing(Player player, long listingId) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.cancel(player, listingId);
    }

    public MarketplaceResult createWantedMarketplaceListing(Player player, String itemName, int variant, int amount,
            long price, String currency, boolean global) {
        if (service == null) return databaseUnavailable();
        return service.createWantedListing(player, itemName, variant, amount, price, currency, global);
    }

    public boolean marketplaceManagementAvailable(Player player) {
        if (player == null || service == null) return false;
        if (player.isAdmin()) return true;
        Optional<MarketZone> zone = safeCurrentMarketZone(player);
        return (!s.onlyMarketCriers && s.maxPlayerMarketplaces != 0 && player.getCurrentArea() != null)
                || zone.filter(current -> current.ownedBy(player.getDbID())).isPresent();
    }

    public boolean canCreatePrivateMarket(Player player) {
        return service != null && service.canCreatePlayerMarket(player);
    }

    public MarketplaceResult hideMarketplaceSale(Player player, long saleId) {
        if (service == null) {
            return databaseUnavailable();
        }
        return service.hideSale(player, saleId);
    }

    public String defaultCurrencyIdentifier() {
        if (service == null) {
            return "default";
        }
        return service.defaultCurrencyIdentifier();
    }

    public List<WalletBridge.CurrencyInfo> walletCurrencies() {
        if (service == null) {
            return List.of();
        }
        return service.availableCurrencies();
    }

    public long marketplaceBuyerFee(Player buyer, MarketplaceListing listing) {
        if (service == null) {
            return 0L;
        }
        return service.buyerFee(buyer, listing);
    }

    public int marketplaceBuyerFeePercent(Player buyer, MarketplaceListing listing) {
        if (service == null) {
            return 0;
        }
        return service.buyerFeePercent(buyer, listing);
    }

    public long defaultCurrencyBalance(Player player) {
        if (service == null || player == null) {
            return 0L;
        }
        return service.defaultCurrencyBalance(player);
    }

    public WalletBridge.BalanceInfo walletBalance(Player player, String currencyIdentifier) {
        if (service == null || player == null) {
            return new WalletBridge.BalanceInfo(false, 0L);
        }
        return service.balance(player, currencyIdentifier);
    }

    public static PlayerSettings playerSettings() {
        return playerSettings;
    }

    public boolean localListingAllowed(Player player) {
        return s != null && s.localMarketplaceEnabled && safeCurrentMarketZone(player).isPresent();
    }

    public boolean globalListingAllowed(Player player) {
        if (s == null) {
            return false;
        }
        Optional<MarketZone> zone = safeCurrentMarketZone(player);
        if (zone.isEmpty()) {
            return s.globalMarketplaceEnabled && !s.marketZoneOnlyMode;
        }
        return zone.get().globalTradeAllowed(s.globalMarketplaceEnabled);
    }

    public boolean sellingAllowed(Player player) {
        return localListingAllowed(player) || globalListingAllowed(player);
    }

    public String currentMarketZoneStatus(Player player) {
        try {
            Optional<MarketZone> zone = service.currentZone(player);
            if (zone.isEmpty()) {
                return tr(player, "tc.market.chat.no.current.zone");
            }
            MarketZone current = zone.get();
            return tr(player, "tc.market.chat.current.zone",
                    "PH_NAME", current.name(),
                    "PH_ZONE", current.id(),
                    "PH_FEE", String.valueOf(current.feePercent()),
                    "PH_GLOBAL", globalTradeModeLabel(player, current.globalTradeMode()));
        } catch (SQLException ex) {
            logger().error("Failed to read current market zone: " + ex.getMessage());
            return tr(player, "tc.market.chat.current.zone.failed");
        }
    }

    public void sendListings(Player player) {
        try {
            List<MarketplaceListing> listings = service.listVisibleListings(player);
            if (listings.isEmpty()) {
                player.sendTextMessage(c.info + tr(player, "tc.market.chat.no.listings"));
                player.sendTextMessage(c.text + tr(player, "tc.market.chat.open.hint",
                        "PH_COMMAND", s.marketCommand));
                return;
            }
            player.sendTextMessage(c.okay + tr(player, "tc.market.chat.listings"));
            for (MarketplaceListing listing : listings) {
                player.sendTextMessage(c.info + tr(player, "tc.market.chat.listing.row",
                        "PH_LISTING", String.valueOf(listing.id()),
                        "PH_AMOUNT", String.valueOf(listing.amount()),
                        "PH_ITEM", listing.itemName() + ":" + listing.itemVariant(),
                        "PH_SELLER", listing.sellerName(),
                        "PH_PRICE", String.valueOf(listing.price()),
                        "PH_CURRENCY", currencyLabel(listing.currencyIdentifier()),
                        "PH_MODE", listing.globalListing()
                                ? tr(player, "tc.market.ui.mode.global")
                                : tr(player, "tc.market.ui.mode.local") + ":" + listing.marketZoneId()));
            }
            player.sendTextMessage(c.text + tr(player, "tc.market.chat.buy.hint",
                    "PH_COMMAND", s.marketCommand));
        } catch (SQLException ex) {
            logger().error("Failed to list marketplace listings: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "tc.market.chat.listings.failed"));
        }
    }

    private void sendZones(Player player) {
        try {
            List<MarketZone> zones = service.listZones();
            if (zones.isEmpty()) {
                player.sendTextMessage(c.info + tr(player, "tc.market.chat.no.zones"));
                return;
            }
            player.sendTextMessage(c.okay + tr(player, "tc.market.chat.zones"));
            for (MarketZone zone : zones) {
                player.sendTextMessage(c.info + tr(player, "tc.market.chat.zone.row",
                        "PH_ZONE", zone.id(),
                        "PH_NAME", zone.name(),
                        "PH_FEE", String.valueOf(zone.feePercent()),
                        "PH_GLOBAL", globalTradeModeLabel(player, zone.globalTradeMode()),
                        "PH_AREA", String.valueOf(zone.areaId())));
            }
        } catch (SQLException ex) {
            logger().error("Failed to list market zones: " + ex.getMessage());
            player.sendTextMessage(c.error + tr(player, "tc.market.chat.zones.failed"));
        }
    }

    private void usage(Player player) {
        player.sendTextMessage(c.warning + tr(player, "tc.market.chat.usage",
                "PH_COMMAND", s.marketCommand));
        if (player.isAdmin()) {
            player.sendTextMessage(c.warning + tr(player, "tc.market.chat.usage.admin",
                    "PH_COMMAND", s.marketCommand));
        }
    }

    private void sendResult(Player player, MarketplaceResult result) {
        player.sendTextMessage((result.success() ? c.okay : c.error) + result.localized(t, player));
    }

    private String tr(Player player, String key, String... replacements) {
        String text = t.get(key, player);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    private MarketplaceResult databaseUnavailable() {
        return MarketplaceResult.failKey("tc.market.result.database.unavailable",
                "Marketplace database is not available.");
    }

    public String globalTradeModeLabel(Player player, int mode) {
        return switch (MarketZone.normalizeGlobalTradeMode(mode)) {
            case MarketZone.GLOBAL_DENY -> tr(player, "tc.market.chat.global.deny");
            case MarketZone.GLOBAL_ALLOW -> tr(player, "tc.market.chat.global.allow");
            default -> tr(player, "tc.market.chat.global.default");
        };
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String currencyLabel(String currency) {
        return currency == null || currency.isBlank() ? " " + defaultCurrencyIdentifier() : " " + currency;
    }

    private String t(String key, Player player) {
        return t.get(key, player);
    }

    private String currentZoneId(Player player) {
        Area area = player.getCurrentArea();
        if (area != null && area.getID() > 0L) {
            return "area-" + area.getID();
        }
        return "area-unknown";
    }

    private String currentZoneName(Player player) {
        Area area = player.getCurrentArea();
        if (area != null && area.getName() != null && !area.getName().isBlank()) {
            return area.getName();
        }
        return "Market Area";
    }

    private int parseGlobalMode(String value) {
        if (value == null) {
            return MarketZone.GLOBAL_DEFAULT;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "true", "allow", "allowed", "on", "2" -> MarketZone.GLOBAL_ALLOW;
            case "false", "deny", "denied", "off", "0" -> MarketZone.GLOBAL_DENY;
            default -> MarketZone.GLOBAL_DEFAULT;
        };
    }

    private int nextGlobalTradeMode(int currentMode) {
        return switch (MarketZone.normalizeGlobalTradeMode(currentMode)) {
            case MarketZone.GLOBAL_DEFAULT -> MarketZone.GLOBAL_ALLOW;
            case MarketZone.GLOBAL_ALLOW -> MarketZone.GLOBAL_DENY;
            default -> MarketZone.GLOBAL_DEFAULT;
        };
    }
}
