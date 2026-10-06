package com.spotlight;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.spotlight.AccountManager.SpotlightAccountInfo;
import com.spotlight.AccountManager.SpotlightAccountManager;
import com.spotlight.AccountManager.SpotlightAccountManagerFrame;
import com.spotlight.Blackout.BlackoutQuad;
import com.spotlight.UI.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import org.apache.commons.lang3.ArrayUtils;


import java.awt.*;
import java.util.ArrayList;

@Slf4j
@PluginDescriptor(
		name = "Spotlight",
		internalName = "spotlight",
		// Moves an existing .runelite/spotlight folder (sounds, account files) into plugin-data on first start
		legacyDataDirectory = "spotlight"
)
public class SpotlightPlugin extends Plugin
{
	@Inject
	public Client client;
	@Inject
	public SpotlightConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	public ItemManager itemManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private AudioPlayer audioPlayer;

	private SpotlightAccountManager spotlightAccountManager;

	private SpotlightAccountManagerFrame currentManager = null;

	// .runelite/plugin-data/spotlight, set in startUp
	private Filepath spotlightDirectory;
	private Filepath lockFile;

	// NOTE: Accessed by SpotlightNearbyPanel
	public ArrayList<GroundItem> nearbyItems = new ArrayList<>();

	private SpotlightAltarOverlay spotlightAltarPanel = null;
	private SpotlightRSNOverlay spotlightRSNOverlay = null;
	private SpotlightProfitPanel spotlightProfitPanel = null;
	private SpotlightShoutOverlay spotlightShoutPanel = null;
	private SpotlightNearbyPanel spotlightNearbyPanel = null;
	private SpotlightSlotsLeftOverlay spotlightSlotsLeftOverlay = null;
	private SpotlightAlchsOverlay spotlightAlchsOverlay = null;
	private SpotlightBlackoutOverlay spotlightBlackoutOverlay = null;
	private SpotlightPrayerTimeOverlay spotlightPrayerTimeOverlay = null;

	private Item[] lastPlayerInventory = null;

	private static final String LOCK_FILE = "disable-blackout.lock";
	private static final String[] SOUND_FILES = {"yoink.wav", "shard.wav", "onyx.wav", "prayer.wav", "health.wav", "regularDrop.wav"};

	//GP/hr tracking
	public int GPhr = 0;


	private SpotlightSounds sounds;
	private final byte YOINK = 0;
	private final byte SHARD = 1;
	private final byte ONYX = 2;
	private final byte PRAYER = 3;
	private final byte HEALTH = 4;
	private final byte REGULAR_DROP = 5;

	int takingItem = 0;
	int ownLootTimer = 0;
	boolean playShardSoundNextTick = false;

	float timeElapsed = 0;

	private void updateAltar() {
		if(!config.altarBar()) {
			overlayManager.remove(spotlightAltarPanel);
			return;
		}
		overlayManager.add(spotlightAltarPanel);

		spotlightAltarPanel.maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
		spotlightAltarPanel.prayer = client.getBoostedSkillLevel(Skill.PRAYER);
		spotlightAltarPanel.gameWidth = client.getViewportWidth();
		spotlightAltarPanel.gameHeight = client.getViewportHeight();
	}
	private void updateRSN() {
		if(!config.rsnDisplay()) {
			overlayManager.remove(spotlightRSNOverlay);
			return;
		}
		overlayManager.add(spotlightRSNOverlay);
	}
	private void updateProfit() {
		timeElapsed += 0.6f;
		GPhr = Math.round((spotlightProfitPanel.profit / timeElapsed) * 60 * 60)/1000;
		if(!config.sessionTracker()) {
			overlayManager.remove(spotlightProfitPanel);
			return;
		}
		overlayManager.add(spotlightProfitPanel);
	}
	private void updateShout() {
		boolean[] thingsToBeFussedAbout = new boolean[]{false,false,false,false};
		boolean any = false;
		if(config.tooAFKIndicator() && tooAFK()) {
			thingsToBeFussedAbout[SpotlightShoutOverlay.TOO_AFK] = true;
			any = true;
		}
		if(config.treasureNear() && shardNear()) {
			thingsToBeFussedAbout[SpotlightShoutOverlay.SHARD_NEARBY] = true;
			any = true;
		}
		if(config.goBank() && inventoryFull()) {
			thingsToBeFussedAbout[SpotlightShoutOverlay.GO_BANK] = true;
			any = true;
		}
		if(config.goBank() && inventoryFull()) {
			thingsToBeFussedAbout[SpotlightShoutOverlay.GO_BANK] = true;
			any = true;
		}
		int boost = client.getBoostedSkillLevel(Skill.STRENGTH) - client.getRealSkillLevel(Skill.STRENGTH);
		if(boost < config.requireBoostedStrengthToBlackout()) {
			thingsToBeFussedAbout[SpotlightShoutOverlay.DRINK_STRENGTH] = true;
			any = true;
		}
		spotlightShoutPanel.thingsToBeFussedAbout = thingsToBeFussedAbout;
		spotlightShoutPanel.viewportWidth = client.getViewportWidth();
		spotlightShoutPanel.viewportHeight = client.getViewportHeight();
		if(any) {
			overlayManager.add(spotlightShoutPanel);
		} else {
			overlayManager.remove(spotlightShoutPanel);
		}
	}
	private void updateNearby() {
		if(!config.doNearbyDrops()) {
			overlayManager.remove(spotlightNearbyPanel);
			return;
		}
		overlayManager.add(spotlightNearbyPanel);
	}
	private void updateSlotsLeft() {
		if(!config.slotsLeft()) {
			overlayManager.remove(spotlightSlotsLeftOverlay);
			return;
		}
		overlayManager.add(spotlightSlotsLeftOverlay);
		spotlightSlotsLeftOverlay.slotsLeft = getSlotsLeft();
	}
	private void updateBlackout() {
		if(!config.blackoutOverlay() || lockFile.exists()) {
			overlayManager.remove(spotlightBlackoutOverlay);
			return;
		}
		boolean belowWidgets = config.drawBelowWidgets();
		if(belowWidgets != spotlightBlackoutOverlay.belowWidgetsWas) {
			overlayManager.remove(spotlightBlackoutOverlay);
			spotlightBlackoutOverlay.updateBelowWidgets(belowWidgets);
			overlayManager.add(spotlightBlackoutOverlay);
		}
		overlayManager.add(spotlightBlackoutOverlay);


		spotlightBlackoutOverlay.gameHeight = client.getViewportHeight();
		spotlightBlackoutOverlay.gameWidth = client.getViewportWidth();

		spotlightBlackoutOverlay.quads.clear();

		// Runs every frame from onBeforeRender, including during loading/hopping when there is no player.
		if(client.getLocalPlayer() == null) {
			return;
		}

		//Is in bank or altar, remove overlay entirely
		if( client.getLocalPlayer().getWorldLocation().isInArea2D(new WorldArea(3601, 3365, 8, 5,0))
				|| client.getLocalPlayer().getWorldLocation().isInArea2D(new WorldArea(3601, 3353, 8, 6,0))
		|| isPrayerOff()
		|| tooAFK()) {
			spotlightBlackoutOverlay.quads.add(new BlackoutQuad(0,0,client.getViewportWidth(),client.getViewportHeight()));
			return;
		}

		int boost = client.getBoostedSkillLevel(Skill.STRENGTH) - client.getRealSkillLevel(Skill.STRENGTH);
		if(boost < config.requireBoostedStrengthToBlackout()) {
			spotlightBlackoutOverlay.quads.add(new BlackoutQuad(0,0,client.getViewportWidth(),client.getViewportHeight()));
		}

		//Otherwise, let's selectively remove the overlay on certain things:
		for(GroundItem item : nearbyItems) {
			if(item.quantity * itemManager.getItemPrice(item.id) < config.nearbyThreshold() || ignoreItem(item.id)) {
				continue;
			}
			LocalPoint location = LocalPoint.fromWorld(client, item.worldPoint);
			if(location != null) {
				Polygon gon = Perspective.getCanvasTilePoly(client, location);
				spotlightBlackoutOverlay.addQuad(gon);
			}
		}
		//Altar
		if(client.getBoostedSkillLevel(Skill.PRAYER) <= config.altarThreshold()
				|| (config.blackoutGlobalDisplayAltar() && isAnyAccountLowPrayer())) {
			// fromWorld is null when the tile is outside the loaded scene (edge of Darkmeyer)
			LocalPoint altarTile = LocalPoint.fromWorld(client,3605,3354);
			if(altarTile != null) {
				Polygon altar = Perspective.getCanvasTileAreaPoly(client, altarTile,3,3,client.getPlane(),client.getLocalPlayer().getWorldArea().getHeight());
				spotlightBlackoutOverlay.addQuad(altar);
			}
		}
		//Altar door
		LocalPoint altarPoint = LocalPoint.fromWorld(client,3605,3358);
		if(altarPoint != null) {
			Polygon altarDoor = Perspective.getCanvasTilePoly(client, altarPoint);
			if ((client.getBoostedSkillLevel(Skill.PRAYER) <= config.altarThreshold() || client.getLocalPlayer().getWorldLocation().isInArea2D(new WorldArea(3601, 3353, 8, 6, 0)) || (config.blackoutGlobalDisplayAltar() && isAnyAccountLowPrayer()))
					&& isDoorClosed(3605, 3358)) {
				spotlightBlackoutOverlay.addQuad(altarDoor);
			}
		}
		//Bank
		LocalPoint bankTile = LocalPoint.fromWorld(client,3607,3368);
		if(getSlotsLeft() <= 2 && bankTile != null) {
			Polygon bank = Perspective.getCanvasTilePoly(client,bankTile);
			spotlightBlackoutOverlay.addQuad(bank);
		}
		//Bank door
		LocalPoint bankPoint = LocalPoint.fromWorld(client,3605,3365);
		if(bankPoint != null) {
			Polygon bankDoor = Perspective.getCanvasTilePoly(client, bankPoint);
			if ((getSlotsLeft() <= 2 || client.getLocalPlayer().getWorldLocation().isInArea2D(new WorldArea(3601, 3365, 8, 5, 0)))
					&& isDoorClosed(3605, 3365)) {
				spotlightBlackoutOverlay.addQuad(bankDoor);
			}
		}


	}

	public boolean isDoorClosed(int worldX, int worldY) {
		LocalPoint coords = LocalPoint.fromWorld(client,worldX,worldY);
		if(coords == null) {
			return false;
		}
		return client.getScene().getTiles()[0][coords.getSceneX()][coords.getSceneY()].getWallObject() != null;
	}

	// Used by the account tracker window's "Force Disable Blackout" button
	public Filepath getLockFile() {
		return lockFile;
	}

	public String getItemName(int id) {
		return client.getItemDefinition(id).getName();
	}
	public long getItemPrice(int id) {
		return itemManager.getItemPrice(id);
	}

	public boolean inventoryFull() {
		ItemContainer invent = client.getItemContainer(InventoryID.INVENTORY);
		if(invent == null) {
			return false;
		}
		if(invent.getItems().length < 28) {
			return false;
		}
		boolean full = true;
		for(Item i : invent.getItems()) {
			if(i == null) {
				full = false;
				break;
			}
			if(i.getQuantity() == 0) {
				full = false;
			}
		}
		return full;
	}
	public int getSlotsLeft() {
		ItemContainer invent = client.getItemContainer(InventoryID.INVENTORY);
		if(invent == null) {
			return 28;
		}
		int itemsCounted = 0;
		for(Item i : invent.getItems()) {
			if(i == null) {
				continue;
			}
			if(i.getQuantity() == 0) {
				continue;
			}
			itemsCounted++;
		}
		return 28-itemsCounted;
	}
	public boolean shardNear() {
		for(GroundItem i : nearbyItems) {
			if(i.id == ItemID.BLOOD_SHARD) {
				return true;
			}
		}
		return false;
	}
	public boolean tooAFK() {
		Player localPlayer = client.getLocalPlayer();
		if(localPlayer == null || localPlayer.getPlayerComposition() == null) {
			return false;
		}
		PlayerComposition comp = localPlayer.getPlayerComposition();
		if(comp.getEquipmentId(KitType.TORSO) == ItemID.VYRE_NOBLE_TOP || comp.getEquipmentId(KitType.LEGS) == ItemID.VYRE_NOBLE_LEGS || comp.getEquipmentId(KitType.BOOTS) == ItemID.VYRE_NOBLE_SHOES) {
			return true;
		}
		if(client.getVarpValue(172) == 1) {
			return true;
		}
		return false;
	}

	public boolean isPrayerOff() {
		for (Prayer pray : Prayer.values())	{
			if (client.isPrayerActive(pray)) {
				return false;
			}
		}
		return true;
	}

	public boolean isAnyAccountLowPrayer() {
		//this account
		if(client.getBoostedSkillLevel(Skill.PRAYER) <= config.altarThreshold()) {
			return true;
		}
		//any account
		ArrayList<SpotlightAccountInfo> info = spotlightAccountManager.getAllAccountInfo();
		for(SpotlightAccountInfo i : info) {
			if(i.prayer <= config.altarThreshold()) {
				return true;
			}
		}
		return false;
	}

	private boolean isPlayerInGoodRegionToEnablePlugin() {
		boolean result = false;
		Player localPlayer = client.getLocalPlayer();
		if(localPlayer != null) {
			if(config.onlyInDarkmeyer())
			{
				result = localPlayer.getWorldLocation().getRegionID() == 14388 || localPlayer.getWorldLocation().getRegionID() == 14387;
			}
			else
			{
				result = true;
			}
		}
		return result;
	}

	public boolean ignoreItem(int id) {
		String name = getItemName(id);
		return ignoreItem(name);
	}

	public boolean ignoreItem(String name) {
		for(String s : config.nearbyBlacklist().split(",")) {
			if(s.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	public String getRSN() {
		if(client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) {
			return "?";
		}
		return client.getLocalPlayer().getName();
	}

	private void removeAllPanels() {
		overlayManager.remove(spotlightAltarPanel);
		overlayManager.remove(spotlightRSNOverlay);
		overlayManager.remove(spotlightProfitPanel);
		overlayManager.remove(spotlightShoutPanel);
		overlayManager.remove(spotlightNearbyPanel);
		overlayManager.remove(spotlightSlotsLeftOverlay);
		overlayManager.remove(spotlightAlchsOverlay);
		overlayManager.remove(spotlightBlackoutOverlay);
		overlayManager.remove(spotlightPrayerTimeOverlay);
	}

	@Override
	protected void startUp() throws Exception
	{
		spotlightDirectory = getPluginDirectory();
		lockFile = spotlightDirectory.joinSegment(LOCK_FILE);
		Filepath[] soundFiles = new Filepath[SOUND_FILES.length];
		for(int i = 0; i < SOUND_FILES.length; i++) {
			soundFiles[i] = spotlightDirectory.joinSegment(SOUND_FILES[i]);
		}

		spotlightAccountManager = new SpotlightAccountManager(spotlightDirectory);
		sounds = new SpotlightSounds(audioPlayer, soundFiles);

		spotlightAltarPanel = new SpotlightAltarOverlay(this,0,0,config.altarThreshold(),config.altarBackground(),config.altarForeground(),config.altarForegroundLow(),config.altarForegroundOff(),config.altarFlashing());
		spotlightRSNOverlay = new SpotlightRSNOverlay(this);
		spotlightProfitPanel = new SpotlightProfitPanel(this);
		spotlightShoutPanel = new SpotlightShoutOverlay(this,client.getViewportWidth(),client.getViewportHeight());
		spotlightNearbyPanel = new SpotlightNearbyPanel(this);
		spotlightSlotsLeftOverlay = new SpotlightSlotsLeftOverlay(this);
		spotlightAlchsOverlay = new SpotlightAlchsOverlay(this);
		spotlightBlackoutOverlay = new SpotlightBlackoutOverlay(this,client.getViewportWidth(),client.getViewportHeight(),config.blackoutPadding(),config.blackoutColor());
		spotlightPrayerTimeOverlay = new SpotlightPrayerTimeOverlay(this);

		updateConfig();

	}

	@Override
	protected void shutDown()
	{
		sounds.stopAll();
		removeAllPanels();
	}

	private void playCustomSound(byte index, boolean justOnce)
	{
		sounds.play(index, justOnce ? 1 : Math.min(Math.max(1, config.loopBlasters()), 100));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked clicked) {
		if(clicked.getMenuOption().toLowerCase().contains("take")) {
			takingItem = 10;
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event) {
		if(event.getEntry().getMenuAction() == MenuAction.RUNELITE_OVERLAY &&
				event.getEntry().getTarget().equals("List") &&
				event.getEntry().getOption().equals("Clear")) {
			nearbyItems.clear();
		}
		if(event.getEntry().getMenuAction() == MenuAction.RUNELITE_OVERLAY &&
				event.getEntry().getTarget().equals("Profit") &&
				event.getEntry().getOption().equals("Clear")) {
			spotlightProfitPanel.profit = 0;
		}
	}

	private Item[] getInventoryList(ItemContainerChanged changed) {
		Item[] invent = {};
		ItemContainer c = client.getItemContainer(InventoryID.EQUIPMENT);
		if(c != null) {
			invent = client.getItemContainer(InventoryID.EQUIPMENT).getItems();
		}
		return ArrayUtils.addAll(changed.getItemContainer().getItems(),invent);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged containerChanged) {
		if(containerChanged.getContainerId() == InventoryID.INVENTORY.getId()) {
			if(takingItem == 0) {
				lastPlayerInventory = getInventoryList(containerChanged);
				return;
			}
			if(lastPlayerInventory == null) {
				lastPlayerInventory = getInventoryList(containerChanged);
				return;
			}
			mainLoop: for(int count = 0; count < containerChanged.getItemContainer().getItems().length; count++) {
				Item i = containerChanged.getItemContainer().getItems()[count];
				if(i == null) {
					continue;
				}
				//Make sure this item is unique and we aren't checking RUNE PLATEBODIES and things multiple times
				for(int count2 = 0; count2 < count; count2++) {
					if(containerChanged.getItemContainer().getItems()[count2].getId() == containerChanged.getItemContainer().getItems()[count].getId()) {
						continue mainLoop;
					}
				}

				int lastAmount = 0;
				int newAmount = 0;
				for(Item j : lastPlayerInventory) {
					if(j.getId() == i.getId()) {
						lastAmount += j.getQuantity();
					}
				}
				for(Item j : containerChanged.getItemContainer().getItems()) {
					if(j.getId() == i.getId()) {
						newAmount += j.getQuantity();
					}
				}
				if(newAmount - lastAmount > 0) {
					spotlightProfitPanel.profit += itemManager.getItemPrice(i.getId()) * (newAmount-lastAmount);
				}
			}
			lastPlayerInventory = getInventoryList(containerChanged);
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived npcLootReceived) {
		for(ItemStack i : npcLootReceived.getItems()) {
			if(i.getId() == ItemID.BLOOD_SHARD) {
				ownLootTimer = 10;
				break;
			}
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned) {
		TileItem item = itemSpawned.getItem();
		if (item.getId() == ItemID.BLOOD_SHARD && config.customBlushard()) {
			//For the yoink sounds, we'll play next tick to compare if it is the player's drop
			playShardSoundNextTick = true;
		} else if (item.getId() == ItemID.ONYX_BOLT_TIPS && config.customOnItsBoltTips()) {
			playCustomSound(ONYX,config.loopUntil());
		} else if (config.customRegularDrops() && getItemPrice(item.getId()) * item.getQuantity() >= config.nearbyThreshold()) {
			playCustomSound(REGULAR_DROP,false);
		}
		if(client.getLocalPlayer() == null) {
			return;
		}

		if(Math.abs(itemSpawned.getTile().getWorldLocation().getX() - client.getLocalPlayer().getWorldLocation().getX()) > config.nearbyRange() ||
				Math.abs(itemSpawned.getTile().getWorldLocation().getY() - client.getLocalPlayer().getWorldLocation().getY()) > config.nearbyRange()) {
			//item is outside of the range of the config
			return;
		}

		boolean existing = false;
		/*for(GroundItem i : nearbyItems) {
			if(i.id == item.getId()) {
				i.quantity += item.getQuantity();
				existing = true;
				break;
			}
		}*/
		// ^ For now, i'll disable the stacking of existing item stacks because I think the functionality
		// of this will behave better. For example, the blackout manager will now highlight separate blood rune
		// stacks instead just one that won't even disappear once picked up.
		//if(!existing) {
		nearbyItems.add(new GroundItem(item.getId(),item.getQuantity(),itemSpawned.getTile().getWorldLocation(), client.getGameCycle()));
		//}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned) {
		TileItem item = itemDespawned.getItem();

		for(GroundItem i : nearbyItems) {
			if(i.id == item.getId() && i.quantity == itemDespawned.getItem().getQuantity()) {
				nearbyItems.remove(i);
				break;
			}
		}

	}

	@Subscribe
	public void onBeforeRender(BeforeRender render) {
		// Starts queued repeats of the sound alerts once the previous play has finished
		sounds.update();
		if(config.blackoutFPS() && isPlayerInGoodRegionToEnablePlugin()) {
			updateBlackout();
		}
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged itemQuantityChanged) {
		TileItem item = itemQuantityChanged.getItem();

		for(GroundItem i : nearbyItems) {
			if(i.id == item.getId() && itemQuantityChanged.getOldQuantity() == i.quantity) {
				i.quantity = itemQuantityChanged.getNewQuantity();
				break;
			}
		}
	}

	public void updateConfig() {
		spotlightAltarPanel.backgroundColor = config.altarBackground();
		spotlightAltarPanel.foregroundColor = config.altarForeground();
		spotlightAltarPanel.flashingColor = config.altarFlashing();
		spotlightAltarPanel.foregroundLowColor = config.altarForegroundLow();
		spotlightAltarPanel.foregroundOffColor = config.altarForegroundOff();
		spotlightAltarPanel.threshold = config.altarThreshold();
		spotlightAltarPanel.barHeight = config.altarSize();
		spotlightAltarPanel.barOnBottom = config.altarBarOnBottom();
		spotlightAltarPanel.outlineColor = config.altarOutline();
		spotlightAltarPanel.displayPrayer = config.altarPrayer();
		spotlightAltarPanel.flashInterval = config.flashyInterval();

		spotlightRSNOverlay.fontSize = config.rsnFontSize();
		spotlightSlotsLeftOverlay.fontSize = config.slotsLeftFontSize();
		spotlightNearbyPanel.threshold = config.nearbyThreshold();

		spotlightShoutPanel.flashInterval = config.flashyInterval();

		spotlightBlackoutOverlay.color = config.blackoutColor();
		spotlightBlackoutOverlay.padding = config.blackoutPadding();

		if(currentManager != null) {
			currentManager.frame.setAlwaysOnTop(config.alwaysOnTopTracker());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		updateConfig();

		if(configChanged.getKey().equals("launchAccountTracker")) {
			if(currentManager != null) {
				currentManager.close();
			}
			currentManager = new SpotlightAccountManagerFrame(spotlightAccountManager,config.alwaysOnTopTracker(),this);
			currentManager.update(config.altarThreshold(),config.nearbyThreshold());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		int state = gameStateChanged.getGameState().getState();
		if(state == GameState.HOPPING.getState() || state == GameState.LOGGING_IN.getState() || state == GameState.STARTING.getState()) {
			nearbyItems.clear();
		}
	}

	int previousPrayer = 0;
	int previousHealth = 0;

	public void updateStatusNoise() {

		if(config.customPrayer() && previousPrayer > config.altarThreshold() && client.getBoostedSkillLevel(Skill.PRAYER) <= config.altarThreshold()) {
			playCustomSound(PRAYER,false);
		}
		if(config.customLowHP() && previousHealth > 50 && client.getBoostedSkillLevel(Skill.HITPOINTS) <= 49) {
			playCustomSound(HEALTH,false);
		}


		previousPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
		previousHealth = client.getBoostedSkillLevel(Skill.HITPOINTS);
	}


	@Subscribe
	public void onGameTick(GameTick gameTick) {
		if(client.getLocalPlayer() == null) {
			return;
		}
		if(config.useAccountTracker()) {
			ArrayList<LootItem> items = new ArrayList<>();
			for(GroundItem item : nearbyItems) {
				items.add(new LootItem(item,this));
			}
			spotlightAccountManager.saveAccountInfo(getRSN(),client.getBoostedSkillLevel(Skill.PRAYER),client.getBoostedSkillLevel(Skill.HITPOINTS),getSlotsLeft(),items,client.getLocalPlayer().getWorldLocation(),spotlightProfitPanel.profit,GPhr);
		}
		if(currentManager != null) {
			currentManager.update(config.altarThreshold(),config.nearbyThreshold());
		}
		if(!isPlayerInGoodRegionToEnablePlugin()) {
			removeAllPanels();
			return;
		}
		if(config.loopUntil()) {
			for(GroundItem item : nearbyItems) {
				if(item.id == ItemID.BLOOD_SHARD && !sounds.isPlaying(SHARD)) {
					playCustomSound(SHARD,true);
					break;
				}
				if(item.id == ItemID.ONYX_BOLT_TIPS && !sounds.isPlaying(ONYX)) {
					playCustomSound(ONYX,true);
					break;
				}
			}
		} else {
			if (playShardSoundNextTick) {
				if (ownLootTimer == 0 && config.customYoink()) {
					playCustomSound(YOINK,false);
				} else if (config.customBlushard()) {
					playCustomSound(SHARD,false);
				}
				playShardSoundNextTick = false;
			}
		}

		// NOTE: 5-minute expiration timer on nearby items.
		// removeIf rather than remove() inside a for-each, which threw ConcurrentModificationException
		// and skipped the rest of the tick's overlay updates whenever an item expired.
		final int gameCycle = client.getGameCycle();
		nearbyItems.removeIf(i -> {
			boolean overflow = ((gameCycle - i.addedAtGameCycle) < 0);
			return gameCycle - i.addedAtGameCycle >= GroundItem.GAME_CYCLES_BEFORE_REMOVAL || overflow;
		});

		updateStatusNoise();

		updateAltar();
		updateRSN();
		updateProfit();
		updateShout();
		updateNearby();
		updateSlotsLeft();
		updateBlackout();

		String alchCounter = config.countAlchs().trim().toLowerCase();
		String[] alchItems = {};
		if(alchCounter.length() > 0) {
			overlayManager.add(spotlightAlchsOverlay);
			alchItems = alchCounter.split(",");
			int count = 0;
			ItemContainer invent = client.getItemContainer(InventoryID.INVENTORY);
			if(invent != null) {
				for (Item i : invent.getItems()) {
					if (i != null && i.getQuantity() > 0) {
						boolean nameCounted = false;
						for(String test : alchItems) {
							String name = itemManager.getItemComposition(i.getId()).getMembersName();
							if(name.toLowerCase().contains(test.trim())) {
								count += i.getQuantity();
							}
						}
					}
				}
			}
			spotlightAlchsOverlay.count = count;
		} else {
			overlayManager.remove(spotlightAlchsOverlay);
		}


		if(config.prayerTime()) {
			overlayManager.add(spotlightPrayerTimeOverlay);
		} else {
			overlayManager.remove(spotlightPrayerTimeOverlay);
		}

		takingItem = Math.max(0,takingItem-1);
		ownLootTimer = Math.max(0,ownLootTimer-1);
	}

	@Provides
	SpotlightConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpotlightConfig.class);
	}
}
