package melky.raidstracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Raids tracker"
)
public class RaidsTrackerPlugin extends Plugin
{
	private static final String RAID_START_MESSAGE = "The raid has begun!";
	private static final String LEVEL_COMPLETE_MESSAGE = "level complete!";
	private static final String RAID_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";//
	@Inject
	private Client client;

	@Inject
	private RaidsTrackerConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Raids tracker started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Raids tracker stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Raids tracker says " + config.greeting(), null);
		}
	}

	@Provides
	RaidsTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidsTrackerConfig.class);
	}
}
