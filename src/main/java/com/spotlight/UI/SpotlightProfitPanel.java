package com.spotlight.UI;

import com.spotlight.SpotlightPlugin;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class SpotlightProfitPanel extends OverlayPanel {

    public int profit = 0;

    @Inject
    public SpotlightProfitPanel(SpotlightPlugin plugin) {
        super(plugin);

        setPosition(OverlayPosition.BOTTOM_LEFT);

        getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Clear","Profit"));
    }

    private String intToReadableString(int amount) {
        return (amount/1000) + "k";
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("+ " + intToReadableString(profit) + " GP")
                .color((profit > 10000000) ? Color.GREEN : Color.WHITE) //green cash baby!
                .build());

        return super.render(graphics);
    }
}
