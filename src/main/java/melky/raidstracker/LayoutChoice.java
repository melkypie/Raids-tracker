package melky.raidstracker;

public enum LayoutChoice
{
	STATS,
	LOOT;

	@Override
	public String toString() {
		return name().substring(0, 1).toUpperCase() + name().substring(1).toLowerCase();
	}
}
