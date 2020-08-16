package melky.raidstracker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Item;

@AllArgsConstructor
@Getter
public class RaidItem
{
	private final int id;
	private final String name;
	private int quantity;
	private final int gePrice;
	private final int haPrice;

	long getTotalGePrice()
	{
		return (long) gePrice * quantity;
	}

	long getTotalHaPrice()
	{
		return (long) haPrice * quantity;
	}
}
