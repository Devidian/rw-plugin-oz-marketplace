package de.omegazirkel.risingworld.marketplace.ui;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginDataPanel;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginData;
import net.risingworld.api.objects.Player;

public class MarketplacePlayerPluginData extends PlayerPluginData {
    public MarketplacePlayerPluginData(String pluginVersion) {
        this.pluginLabel = Marketplace.name;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public BasePlayerPluginDataPanel createPlayerPluginDataUIElement(Player uiPlayer) {
        return new BasePlayerPluginDataPanel(uiPlayer, pluginLabel) {
            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                flexWrapper.addChild(defaultEmptyStateLabel());
            }
        };
    }
}
