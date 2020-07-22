package melky.raidstracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.inject.Provides;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.RuneLite;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@PluginDescriptor(
	name = "Raids tracker"
)
public class RaidsTrackerPlugin extends Plugin
{
	public static final String CONFIG_GROUP = "raidstracker";
	public final File RAID_DIR = new File(RuneLite.RUNELITE_DIR, "raids-tracker");

	private static final String RAID_START_MESSAGE = "The raid has begun!";
	private static final String SPECIAL_LOOT_MESSAGE = "Special loot:";
	private static final Pattern SPECIAL_DROP_MESSAGE = Pattern.compile("(.+) - (.+)");
	private static final Pattern LEVEL_COMPLETE_REGEX = Pattern.compile("(.+) level complete! Duration: ([0-9:]+)");
	private static final Pattern RAID_COMPLETE_REGEX = Pattern.compile("Congratulations - your raid is complete! Duration: ([0-9:]+)");
	private static final Pattern RAID_KILLCOUNT_REGEX = Pattern.compile("Your completed Chambers of Xeric count is: (\\d+)");

	private boolean chestOpened;
	private boolean raidStarted;
	private boolean waitForSpecialLoot;
	private boolean ready;
	private List<Integer> raidLevelTimes = new ArrayList<>();
	private List<String> raidPartyMembers = new ArrayList<>();
	@Getter
	private List<Raid> raidList = new ArrayList<>();
	private Raid unsavedRaidData;

	private static final Gson gson = new GsonBuilder()
		.registerTypeAdapter(ZonedDateTime.class, new TypeAdapter<ZonedDateTime>()
		{
			@Override
			public void write(JsonWriter out, ZonedDateTime value) throws IOException
			{
				out.value(value.toString());
			}

			@Override
			public ZonedDateTime read(JsonReader in) throws IOException
			{
				return ZonedDateTime.parse(in.nextString());
			}
		})
		.enableComplexMapKeySerialization()
		.create();
	;

	@Getter
	private boolean inRaidChambers;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;
	private NavigationButton navButton;
	private RaidsTrackerPanel panel;

	@Inject
	private ConfigManager configManager;
	@Inject
	private SessionManager sessionManager;
	@Inject
	private RaidsTrackerConfig config;

	@Inject
	private ItemManager itemManager;

	@Provides
	RaidsTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidsTrackerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		clientThread.invokeLater(() -> checkRaidPresence(true));

		panel = new RaidsTrackerPanel(this, itemManager, clientThread);

		navButton = NavigationButton.builder()
			.tooltip("Raids Tracker")
			.icon(ImageUtil.getResourceStreamFromClass(getClass(), "/olmlet.png"))
			.priority(9)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		inRaidChambers = false;
		chestOpened = false;
		raidStarted = false;
		waitForSpecialLoot = false;
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGING_IN:
				ready = true;
				break;
			case LOGGED_IN:
				if (ready)
				{
					clientThread.invokeLater(() ->
					{
						loadRaidData();

						executor.submit(() -> clientThread.invokeLater(() -> SwingUtilities.invokeLater(() ->
						{
							if (raidList != null)
							{
								panel.rebuildPanel(raidList);
							}
							ready = false;
						})));
						return true;
					});
				}
				break;
		}
	}

	@Subscribe
	public void onSessionOpen(SessionOpen event)
	{
		//Load new account config
		final AccountSession session = sessionManager.getAccountSession();
		if (session != null && session.getUsername() != null)
		{
			clientThread.invokeLater(() ->
			{
				loadRaidData();
				SwingUtilities.invokeLater(() -> panel.rebuildPanel(raidList));
				return true;
			});
		}
	}

	@Subscribe
	public void onSessionClose(SessionClose event)
	{
		clientThread.invokeLater(() ->
		{
			loadRaidData();
			SwingUtilities.invokeLater(() -> panel.rebuildPanel(raidList));
			return true;
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		clientThread.invokeLater(() -> checkRaidPresence(true));
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		checkRaidPresence(false);
	}


	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != WidgetID.CHAMBERS_OF_XERIC_REWARD_GROUP_ID ||
			chestOpened)
		{
			return;
		}

		chestOpened = true;
		waitForSpecialLoot = false;

		ItemContainer rewardItemContainer = client.getItemContainer(InventoryID.CHAMBERS_OF_XERIC_CHEST);


		if (rewardItemContainer == null || unsavedRaidData.getSpecialLoot().get(client.getLocalPlayer().getName()) != null)
		{
			return;
		}

		List<RaidItem> chestLoot = new ArrayList<>();
		for (Item item : rewardItemContainer.getItems())
		{
			final int itemId = item.getId();
			final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			final int realItemId = itemComposition.getNote() != -1 ? itemComposition.getLinkedNoteId() : itemId;
			final int gePrice = itemManager.getItemPrice(realItemId);
			final int haPrice = Math.round(itemComposition.getPrice() * Constants.HIGH_ALCHEMY_MULTIPLIER);
			chestLoot.add(new RaidItem(itemId, itemComposition.getName(), item.getQuantity(), gePrice, haPrice));
		}

		unsavedRaidData.setChestLoot(chestLoot);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (inRaidChambers)
		{
			if (event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION)
			{
				String message = Text.removeTags(event.getMessage());
				Matcher matcher;

				if (message.startsWith(RAID_START_MESSAGE))
				{
					raidStarted = true;
					raidLevelTimes.clear();
					unsavedRaidData = null;
					waitForSpecialLoot = false;
					log.debug("Raid started");
					raidPartyMembers.clear();

					client.getPlayers().forEach(p -> raidPartyMembers.add(p.getName()));
					log.debug("Got party members: " + StringUtils.join(raidPartyMembers));
				}
				else if (message.startsWith(SPECIAL_LOOT_MESSAGE))
				{
					waitForSpecialLoot = true;
					log.debug("Waiting for other special loot messages");
				}

				matcher = LEVEL_COMPLETE_REGEX.matcher(message);
				if (raidStarted && matcher.find())
				{
					String floor = matcher.group(1);
					int time = timeToSeconds(matcher.group(2));
					if (floor.equals("Upper"))
					{
						raidLevelTimes.add(time);
					}
					else if (floor.equals("Middle"))
					{
						raidLevelTimes.add(time);
					}
					else if (floor.equals("Lower"))
					{
						raidLevelTimes.add(time);
					}
					log.debug("Updated raid level times: " + StringUtils.join(raidLevelTimes));
				}

				matcher = RAID_COMPLETE_REGEX.matcher(message);
				if (matcher.find())
				{
					raidStarted = false;
					chestOpened = false;
					//Get points, special loot trigger, time done, sum up info wait for chestOpened
					//TODO: Get damage dealt during raid
					unsavedRaidData = new Raid(
						0, 0,
						0, ZonedDateTime.now(), client.getVar(Varbits.TOTAL_POINTS),
						client.getVar(Varbits.PERSONAL_POINTS), raidLevelTimes, timeToSeconds(matcher.group(1)),
						new HashMap<>(), new ArrayList<>(), raidPartyMembers);
					log.debug("Created: " + unsavedRaidData);
				}

				if (waitForSpecialLoot)
				{
					matcher = SPECIAL_DROP_MESSAGE.matcher(message);
					if (matcher.find())
					{
						final String lootReceiver = matcher.group(1);
						final String lootItem = matcher.group(2);
						log.debug("Player " + lootReceiver + " has received a unique (" + lootItem + ")");

						final int lootItemId = itemManager.search(lootItem).get(0).getId();
						final ItemComposition itemComposition = itemManager.getItemComposition(lootItemId);
						final int gePrice = itemManager.getItemPrice(lootItemId);
						final int haPrice = Math.round(itemComposition.getPrice() * Constants.HIGH_ALCHEMY_MULTIPLIER);
						if (unsavedRaidData.getSpecialLoot() == null) {
							unsavedRaidData.setSpecialLoot(new HashMap<>());
						}
						unsavedRaidData.getSpecialLoot().put(new RaidItem(lootItemId, itemComposition.getName(),
								1, gePrice, haPrice),
							lootReceiver);
					}
				}
			}
			else if (event.getType() == ChatMessageType.GAMEMESSAGE)
			{
				String message = Text.removeTags(event.getMessage());
				Matcher matcher;

				matcher = RAID_KILLCOUNT_REGEX.matcher(message);
				if (matcher.find())
				{
					log.debug("New KC found: " + matcher.group(1));
					unsavedRaidData.setKillCount(Integer.parseInt(matcher.group(1)));
				}
			}
		}
	}

	private void checkRaidPresence(boolean force)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		boolean setting = client.getVar(Varbits.IN_RAID) == 1;

		if (force || inRaidChambers != setting)
		{
			inRaidChambers = setting;

			if (inRaidChambers)
			{
				chestOpened = false;
			}
		}

		// If we left party, raid was started or we left raid
		if (client.getVar(VarPlayer.IN_RAID_PARTY) == -1 && !inRaidChambers)
		{
			raidStarted = false;
			if (unsavedRaidData != null)
			{
				raidList.add(unsavedRaidData);
				log.debug("New raid added: " + unsavedRaidData.toString());
				unsavedRaidData = null;
				updateRaidData();
				executor.submit(() -> clientThread.invokeLater(() -> SwingUtilities.invokeLater(() ->
				{
					if (raidList != null)
					{
						panel.rebuildPanel(raidList);
					}
					ready = false;
				})));
			}
		}
	}

	public void updateRaidData()
	{
		if (raidList == null)
		{
			return;
		}

		executor.submit(() ->
		{
			if (!RAID_DIR.exists())
			{
				RAID_DIR.mkdir();
			}
			final File playerFile = new File(RAID_DIR, client.getUsername() + ".json");
			try
			{
				FileWriter jsonWriter = new FileWriter(playerFile.toString());
				gson.toJson(raidList, jsonWriter);
				jsonWriter.close();
			}
			catch (IOException e)
			{
				log.debug("Could not save the raid log to file: " + e);
			}
		});
	}

	public void loadRaidData()
	{
		log.debug("Loading raid tracker config");

		if (!RAID_DIR.exists())
		{
			RAID_DIR.mkdir();
		}
		final File playerFile = new File(RAID_DIR, client.getUsername() + ".json");

		if (!playerFile.exists() && playerFile.length() == 0)
		{
			return;
		}
		try
		{
			Type type = new TypeToken<ArrayList<Raid>>()
			{
			}.getType();
			JsonReader jsonReader = new JsonReader(new FileReader(playerFile.toString()));
			raidList = gson.fromJson(jsonReader, type);
			jsonReader.close();
		}
		catch (FileNotFoundException e)
		{
			log.info("User file not found: " + e);
		}
		catch (IOException e)
		{
			log.info("Unable to close file: " + e);
		}
	}

	private int timeToSeconds(String s)
	{
		int seconds = -1;
		String[] split = s.split(":");
		if (split.length == 2)
		{
			seconds = Integer.parseInt(split[0]) * 60 + Integer.parseInt(split[1]);
		}
		if (split.length == 3)
		{
			seconds = Integer.parseInt(split[0]) * 3600 + Integer.parseInt(split[1]) * 60 + Integer.parseInt(split[2]);
		}
		return seconds;
	}
}
