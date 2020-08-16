package melky.raidstracker;

import com.sun.prism.paint.Paint;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RaidBox extends JPanel
{
	private static final int ITEMS_PER_ROW = 5;
	private static final int TITLE_PADDING = 5;
	private static final int PARTY_MEMBERS_DISPLAY_LIMIT = 15;
	private LayoutChoice currentLayout = LayoutChoice.LOOT;

	private final JPanel logTitle = new JPanel();

	private final JPanel allStatsContainer = new JPanel();
	private final JPanel allLootContainer = new JPanel();

	private final ItemManager itemManager;

	@Getter(AccessLevel.PACKAGE)
	private final Raid raid;

	RaidBox(final ItemManager itemManager,
			final Raid raid)
	{
		this.itemManager = itemManager;
		this.raid = raid;

		setLayout(new BorderLayout(0, 1));
		setBorder(new EmptyBorder(5, 0, 0, 0));

		logTitle.setLayout(new BoxLayout(logTitle, BoxLayout.X_AXIS));
		logTitle.setBorder(new EmptyBorder(7, 7, 7, 7));
		logTitle.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

		JLabel titleLabel = new JLabel();
		titleLabel.setText("KC #" + raid.getKillCount());
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(Color.WHITE);
		// Set a size to make BoxLayout truncate the name
		titleLabel.setMinimumSize(new Dimension(0, titleLabel.getPreferredSize().height));
		logTitle.add(titleLabel);
		JLabel subtitleLabel = new JLabel();
		subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
		subtitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		if (raid.getPartyMembers() != null)
		{
			subtitleLabel.setText(" (" + raid.getPartyMembers().size() + ")");
			subtitleLabel.setToolTipText(buildMembersTooltip(raid.getPartyMembers()));
		}


		logTitle.add(Box.createRigidArea(new Dimension(TITLE_PADDING, 0)));
		logTitle.add(subtitleLabel);
		logTitle.add(Box.createHorizontalGlue());
		logTitle.add(Box.createRigidArea(new Dimension(TITLE_PADDING, 0)));

		JLabel dateLabel = new JLabel();
		dateLabel.setFont(FontManager.getRunescapeSmallFont());
		dateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		ZonedDateTime currentDateTime = raid.getCompleteDateTime().withZoneSameInstant(ZoneId.systemDefault());
		dateLabel.setText(currentDateTime.format(
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)));
		logTitle.add(dateLabel);

		allStatsContainer.setLayout(new BoxLayout(allStatsContainer, BoxLayout.Y_AXIS));
		allLootContainer.setLayout(new BoxLayout(allLootContainer, BoxLayout.Y_AXIS));
		add(logTitle, BorderLayout.NORTH);
		add(allLootContainer);
		add(allStatsContainer);

	}

	void collapse()
	{
		if (!isCollapsed())
		{
			switch (currentLayout)
			{
				case LOOT:
					allLootContainer.setVisible(false);
					break;
				case STATS:
					allStatsContainer.setVisible(false);
					break;
			}
			applyDimmer(false, logTitle);
		}
	}

	void expand()
	{
		if (isCollapsed())
		{
			switch (currentLayout)
			{
				case LOOT:
					allLootContainer.setVisible(true);
					break;
				case STATS:
					allStatsContainer.setVisible(true);
					break;
			}
			applyDimmer(true, logTitle);
		}
	}

	void switchLayout(LayoutChoice layoutChoice)
	{
		switch (layoutChoice)
		{
			case LOOT:
				allLootContainer.setVisible(!isCollapsed());
				allStatsContainer.setVisible(false);
				break;
			case STATS:
				allStatsContainer.setVisible(!isCollapsed());
				allLootContainer.setVisible(false);
				break;
		}
		currentLayout = layoutChoice;
	}

	boolean isCollapsed()
	{
		switch (currentLayout)
		{
			case LOOT:
				return !allLootContainer.isVisible();
			case STATS:
				return !allStatsContainer.isVisible();
		}
		return false;
	}

	private void applyDimmer(boolean brighten, JPanel panel)
	{
		for (Component component : panel.getComponents())
		{
			Color color = component.getForeground();

			component.setForeground(brighten ? color.brighter() : color.darker());
		}
	}

	void rebuild()
	{
		validate();
		repaint();
	}

	void buildStats()
	{
		JPanel statsContainer = new JPanel();
		JPanel leftStatsContainer = new JPanel();
		JPanel rightStatsContainer = new JPanel();

		statsContainer.setLayout(new BorderLayout(0, 1));
		statsContainer.setBorder(new EmptyBorder(2, 2, 0, 2));
		statsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel pointsTitleLabel = new JLabel("Points: ");
		JLabel priceTitleLabel = new JLabel("Loot value: ");
		JLabel timeTitleLabel = new JLabel("Completion time: ");

		// TODO: Add tooltips

		pointsTitleLabel.setHorizontalAlignment(JLabel.RIGHT);
		priceTitleLabel.setHorizontalAlignment(JLabel.RIGHT);
		timeTitleLabel.setHorizontalAlignment(JLabel.RIGHT);

		leftStatsContainer.add(pointsTitleLabel);
		leftStatsContainer.add(priceTitleLabel);
		leftStatsContainer.add(timeTitleLabel);

		JLabel pointsValueLabel = new JLabel(String.valueOf(raid.getPersonalPoints()));
		JLabel timeValueLabel = new JLabel(raid.getFormattedCompleteTime());

		long price = raid.getChestLoot() != null ? raid.getChestLootGEValue() : raid.getSpecialLootGEValue();
		String formattedPrice = price > Integer.MAX_VALUE ? QuantityFormatter.quantityToStackSize(price) :
			QuantityFormatter.quantityToRSDecimalStack(Math.toIntExact(price), true);
		JLabel priceValueLabel = new JLabel(formattedPrice);

		// TODO: Add config options

		rightStatsContainer.add(pointsValueLabel);
		rightStatsContainer.add(priceValueLabel);
		rightStatsContainer.add(timeValueLabel);

		statsContainer.add(leftStatsContainer, BorderLayout.WEST);
		statsContainer.add(rightStatsContainer, BorderLayout.EAST);

		allStatsContainer.add(statsContainer, BorderLayout.NORTH);
	}

	public void buildLoots()
	{
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		log.debug("Called by: " + stackTrace[2].getMethodName());

		allLootContainer.removeAll();

		List<RaidItem> chestItems = this.raid.getChestLoot();
		if (!chestItems.isEmpty())
		{
			allLootContainer.add(buildLoot(chestItems), 0);
		}

		Map<RaidItem, String> specialItemsMap = this.raid.getSpecialLoot();
		if (!specialItemsMap.isEmpty())
		{
			//Build special loot items
			List<RaidItem> specialItems = new ArrayList<>(specialItemsMap.keySet());
			allLootContainer.add(buildLoot(specialItems, specialItemsMap), 0);
		}

		allLootContainer.repaint();
	}

	private JPanel buildLoot(List<RaidItem> items)
	{
		return buildLoot(items, null);
	}

	private JPanel buildLoot(List<RaidItem> items, Map<RaidItem, String> specialItems)
	{
		boolean special = specialItems != null;

		JPanel lootContainer = new JPanel();
		JPanel lootItemContainer = new JPanel();
		JPanel lootTitle = new JPanel();

		lootTitle.setLayout(new BoxLayout(lootTitle, BoxLayout.X_AXIS));
		lootTitle.setBorder(new EmptyBorder(7, 7, 7, 7));
		lootTitle.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		lootContainer.setLayout(new BorderLayout(0, 1));
		lootContainer.setBorder(new EmptyBorder(2, 2, 0, 2));

		JLabel lootNameLabel = new JLabel();

		String labelText = special ? "Special loot" : "Chest loot";

		lootNameLabel.setText(labelText);
		lootNameLabel.setFont(FontManager.getRunescapeSmallFont());
		lootNameLabel.setForeground(special ? Color.MAGENTA : Color.WHITE);
		lootNameLabel.setMinimumSize(new Dimension(0, lootNameLabel.getPreferredSize().height));

		JLabel lootPriceLabel = new JLabel();
		ToLongFunction<RaidItem> getPrice = RaidItem::getTotalGePrice;
		long price = items.stream().mapToLong(getPrice).sum();
		String priceText = price > Integer.MAX_VALUE ? QuantityFormatter.quantityToStackSize(price) :
			QuantityFormatter.quantityToRSDecimalStack(Math.toIntExact(price), true);

		lootPriceLabel.setText(priceText);
		lootPriceLabel.setFont(FontManager.getRunescapeSmallFont());
		lootPriceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lootPriceLabel.setMinimumSize(new Dimension(0, lootPriceLabel.getPreferredSize().height));

		lootTitle.add(lootNameLabel);
		lootTitle.add(Box.createRigidArea(new Dimension(0, 0)));
		lootTitle.add(Box.createHorizontalGlue());
		lootTitle.add(Box.createRigidArea(new Dimension(0, 0)));
		lootTitle.add(lootPriceLabel);

		lootContainer.add(lootTitle, BorderLayout.NORTH);

		final int rowSize = ((items.size() % ITEMS_PER_ROW == 0) ? 0 : 1) + items.size() / ITEMS_PER_ROW;
		lootItemContainer.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));


		items.sort(Comparator.comparingLong(getPrice).reversed());
		for (int i = 0; i < rowSize * ITEMS_PER_ROW; i++)
		{

			final JPanel slotContainer = new JPanel();
			slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			if (i < items.size())
			{
				final RaidItem item = items.get(i);
				String playerName = special ? specialItems.get(item) : "";
				buildItem(item, playerName, slotContainer);
			}

			lootItemContainer.add(slotContainer);
		}
		lootContainer.add(lootItemContainer);
		return lootContainer;
	}

	private void buildItem(RaidItem item, String playerName, JPanel slotContainer)
	{
		log.debug("Building " + item.getName());
		final JLabel imageLabel = new JLabel();
		imageLabel.setToolTipText(buildToolTip(item, playerName));
		imageLabel.setVerticalAlignment(SwingConstants.CENTER);
		imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

		AsyncBufferedImage itemImage = itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1);

		itemImage.addTo(imageLabel);

		slotContainer.add(imageLabel);
	}

	private static String buildToolTip(RaidItem item, String playerName)
	{
		final String name = item.getName();
		final int quantity = item.getQuantity();
		final long gePrice = item.getTotalGePrice();
		final long haPrice = item.getTotalHaPrice();
		if (playerName.equals(""))
		{
			return "<html>" + name + " x " + quantity
				+ "<br>GE: " + QuantityFormatter.quantityToStackSize(gePrice)
				+ "<br>HA: " + QuantityFormatter.quantityToStackSize(haPrice) + "</html>";
		}
		return "<html><u>" + playerName + "</u> received: "
			+ "<br>" + name
			+ "<br>GE: " + QuantityFormatter.quantityToStackSize(gePrice)
			+ "<br>HA: " + QuantityFormatter.quantityToStackSize(haPrice) + "</html>";

	}

	private String buildMembersTooltip(List<String> partyMembers)
	{
		StringBuilder members = new StringBuilder();
		for (int i = 0; i < Math.min(partyMembers.size(), PARTY_MEMBERS_DISPLAY_LIMIT); i++)
		{
			members.append(partyMembers.get(i)).append("<br>");
		}
		return "<html>" + members.toString() + "</html>";
	}
}
