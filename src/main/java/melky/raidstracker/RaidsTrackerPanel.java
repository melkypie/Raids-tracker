package melky.raidstracker;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

public class RaidsTrackerPanel extends PluginPanel
{
	private static final ImageIcon COLLAPSE_ICON;
	private static final ImageIcon EXPAND_ICON;

	@Inject
	private ClientThread clientThread;
	private final RaidsTrackerPlugin plugin;
	private final ItemManager itemManager;

	private final JPanel logsContainer = new JPanel();

	private final JPanel actionsContainer = new JPanel();
	private final JButton collapseBtn = new JButton();

	private final List<RaidBox> boxes = new ArrayList<>();

	static
	{
		final BufferedImage collapseImg = ImageUtil.getResourceStreamFromClass(RaidsTrackerPlugin.class, "/collapsed.png");
		final BufferedImage expandedImg = ImageUtil.getResourceStreamFromClass(RaidsTrackerPlugin.class, "/expanded.png");

		COLLAPSE_ICON = new ImageIcon(collapseImg);
		EXPAND_ICON = new ImageIcon(expandedImg);
	}

	@Inject
	public RaidsTrackerPanel(final RaidsTrackerPlugin plugin, final ItemManager itemManager, ClientThread clientThread)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.clientThread = clientThread;

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		actionsContainer.setLayout(new BorderLayout());
		actionsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actionsContainer.setPreferredSize(new Dimension(0, 30));
		actionsContainer.setBorder(new EmptyBorder(5, 5, 5, 10));
		actionsContainer.setVisible(false);

		final JPanel viewControls = new JPanel(new BorderLayout());
		viewControls.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JComboBox<LayoutChoice> layoutChoiceComboBox = new JComboBox<>(LayoutChoice.values());
		layoutChoiceComboBox.setSelectedItem(LayoutChoice.LOOT);
		layoutChoiceComboBox.setToolTipText("Filter the displayed raids by stats or loot");
		layoutChoiceComboBox.setMaximumSize(new Dimension(20, 0));
		layoutChoiceComboBox.setMaximumRowCount(3);
		layoutChoiceComboBox.addItemListener(e -> switchBoxLayout((LayoutChoice) e.getItem()));

		SwingUtil.removeButtonDecorations(collapseBtn);
		collapseBtn.setIcon(EXPAND_ICON);
		collapseBtn.setSelectedIcon(COLLAPSE_ICON);
		SwingUtil.addModalTooltip(collapseBtn, "Collapse All", "Un-Collapse All");
		collapseBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		collapseBtn.setUI(new BasicButtonUI()); // substance breaks the layout
		collapseBtn.addActionListener(ev -> changeCollapse());
		viewControls.add(collapseBtn, BorderLayout.EAST);


		actionsContainer.add(viewControls, BorderLayout.EAST);
		actionsContainer.add(layoutChoiceComboBox, BorderLayout.WEST);

		logsContainer.setLayout(new BoxLayout(logsContainer, BoxLayout.Y_AXIS));
		layoutPanel.add(actionsContainer);
		//layoutPanel.add(overallPanel);
		layoutPanel.add(logsContainer);
	}

	public void rebuildPanel(List<Raid> raidList)
	{
		SwingUtil.fastRemoveAll(logsContainer);
		boxes.clear();

		raidList.forEach(this::buildBox);
		//boxes.forEach(RaidBox::rebuild);
		logsContainer.revalidate();
		logsContainer.repaint();
	}


	private RaidBox buildBox(Raid raid)
	{
		actionsContainer.setVisible(true);

		// Create box
		final RaidBox box = new RaidBox(itemManager, raid);
		box.buildLoots();
		box.buildStats();

		// Add box to panel
		boxes.add(box);
		logsContainer.add(box, 0);

		return box;
	}

	void updateCollapseText()
	{
		collapseBtn.setSelected(isAllCollapsed());
	}

	/**
	 * Changes the collapse status of raid entries
	 */
	private void changeCollapse()
	{
		boolean isAllCollapsed = isAllCollapsed();

		for (RaidBox box : boxes)
		{
			if (isAllCollapsed)
			{
				box.expand();
			}
			else if (!box.isCollapsed())
			{
				box.collapse();
			}
		}

		updateCollapseText();
	}

	private boolean isAllCollapsed()
	{
		return boxes.stream()
			.filter(RaidBox::isCollapsed)
			.count() == boxes.size();
	}

	private void switchBoxLayout(LayoutChoice layoutChoice)
	{
		boxes.forEach(raidBox -> raidBox.switchLayout(layoutChoice));
	}
}
