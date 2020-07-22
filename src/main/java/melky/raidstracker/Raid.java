package melky.raidstracker;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@AllArgsConstructor
public class Raid
{
	@Setter
	private int killCount;
	private final int othersDamageDealt;
	private final int personalDamageDealt;
	private final ZonedDateTime completeDateTime;
	private final int totalPoints;
	private final int personalPoints;
	private final List<Integer> levelTimes;
	private final int completeTime;
	@Setter
	private Map<RaidItem, String> specialLoot;
	@Setter
	private List<RaidItem> chestLoot;
	@Nullable
	private List<String> partyMembers;

	public long getSpecialLootGEValue()
	{
		return specialLoot.keySet().stream()
			.filter(raidItem -> raidItem.getGePrice() > 0)
			.mapToLong(RaidItem::getTotalGePrice).sum();
	}

	public long getSpecialLootHAValue()
	{
		return specialLoot.keySet().stream()
			.filter(raidItem -> raidItem.getHaPrice() > 0)
			.mapToLong(RaidItem::getTotalHaPrice).sum();
	}

	public long getChestLootGEValue()
	{
		return chestLoot.stream().filter(raidItem -> raidItem.getGePrice() > 0)
			.mapToLong(RaidItem::getTotalGePrice).sum();
	}

	public long getChestLootHAValue()
	{
		return chestLoot.stream().filter(raidItem -> raidItem.getHaPrice() > 0)
			.mapToLong(RaidItem::getTotalHaPrice).sum();
	}

	public double getPersonalUniqueChance()
	{
		return (double) personalPoints / 86500;
	}

	public double getTotalUniqueChance()
	{
		return (double) totalPoints / 86500;
	}

	public double getContribution()
	{
		return (double) personalPoints / totalPoints;
	}

	public String getFormattedCompleteTime()
	{
		Duration duration = Duration.ofSeconds(this.completeTime);
		long h = duration.toHours();
		long m = duration.minusHours(h).toMinutes();
		long s = duration.minusHours(h).minusMinutes(m).getSeconds();

		return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
	}

	public String toString()
	{
		return "Raid (\n" +
			"kc: " + killCount + "\n" +
			"oDmgDealt: " + othersDamageDealt + "\n" +
			"pDmgDealt: " + personalDamageDealt + "\n" +
			"fDate: " + completeDateTime.toString() + "\n" +
			"tPoints: " + totalPoints + "\n" +
			"pPoints: " + personalPoints + "\n" +
			"lTimes: " + StringUtils.join(levelTimes) + "\n" +
			"cTime: " + completeTime + "\n" +
			"sLoot: " + StringUtils.join(specialLoot) + "\n" +
			"sLootValue: " + getSpecialLootGEValue() + "\n" +
			"cLoot: " + StringUtils.join(chestLoot) + "\n" +
			"cLootValue: " + getChestLootGEValue() + "\n" +
			"pMembers: " + StringUtils.join(partyMembers) + "\n)";
	}
}
