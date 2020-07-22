package melky.raidstracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RaidsTrackerPlugin.CONFIG_GROUP)
public interface RaidsTrackerConfig extends Config
{
	@ConfigItem(
		position = 0,
		keyName = "test",
		name = "test",
		description = "test"
	)
	default boolean test()
	{
		return true;
	}
}
