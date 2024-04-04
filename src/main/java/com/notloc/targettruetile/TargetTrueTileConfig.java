package com.notloc.targettruetile;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("targettruetile")
public interface TargetTrueTileConfig extends Config
{
	String CONFIG_GROUP = "targettruetile";

	@ConfigSection(
			name = "Options",
			description = "Targeting options",
			position = 1
	)
	String options = "options";

	@ConfigSection(
			name = "Tile Style",
			description = "Visual options",
			position = 2
	)
	String style = "style";

	@ConfigSection(
			name = "Southwest Corner",
			description = "Southwest corner marking options",
			position = 3
	)
	String corner = "corner";


	@ConfigItem(
			keyName = "targettimeout",
			name = "Target Forget Time",
			description = "How many seconds to wait before forgetting a target.",
			position = 0,
			section = options
	)
	default int targetTimeout() { return 15; }

	@ConfigItem(
			keyName = "multipletargets",
			name = "Allow Multiple Targets",
			description = "Allow multiple targets to be tracked at once.",
			position = 1,
			section = options
	)
	default boolean allowMultipleTargets() { return false; }

	@ConfigItem(
			keyName = "hoverhighlight",
			name = "Show on Mouse Over",
			description = "Show the true tile when the mouse is over a target.",
			position = 2,
			section = options
	)
	default boolean highlightOnHover() { return false; }

	@ConfigItem(
			keyName = "targethighlight",
			name = "Show for Friendly Targets",
			description = "Show the true tile for friendly NPCs.",
			position = 3,
			section = options
	)
	default boolean highlightFriendlies() { return false; }

	@ConfigItem(
			keyName = "inherittagged",
			name = "Show for Tagged NPCs",
			description = "Show the true tile for tagged NPCs.",
			position = 4,
			section = options
	)
	default boolean inheritTaggedNpcs() { return true; }

	@Alpha
	@ConfigItem(
			keyName = "tilecolor",
			name = "Border Color",
			description = "The color of the target's tile.",
			position = 0,
			section = style
	)
	default Color tileColor() {
		return new Color(255, 192, 0, 96);
	}

	@Alpha
	@ConfigItem(
			keyName = "tilefillcolor",
			name = "Fill Color",
			description = "The inner color of the target's tile.",
			position = 1,
			section = style
	)
	default Color tileFillColor() {
		return new Color(0, 0, 0, 50);
	}

	@ConfigItem(
			keyName = "bordersize",
			name = "Border Size",
			description = "Thickness of the tile's border.",
			position = 2,
			section = style
	)
	default int borderSize() { return 2; }

	@ConfigItem(
			keyName = "showcorner",
			name = "Mark Southwest Corner",
			description = "Mark the southwest corner of a target's tile.",
			position = 1,
			section = corner
	)
	default boolean showCorner() {
		return true;
	}

	@ConfigItem(
			keyName = "largeonly",
			name = "Large Targets Only",
			description = "Only mark the southwest corner of targets larger than 1x1.",
			position = 2,
			section = corner
	)
	default boolean showCornerOnlyLarge() {
		return true;
	}

	@Alpha
	@ConfigItem(
			keyName = "cornercolor",
			name = "Corner Color",
			description = "The color of the southwest marking.",
			position = 4,
			section = corner
	)
	default Color tileCornerColor() {
		return new Color(233, 177, 0, 68);
	}

	@ConfigItem(
			keyName = "cornersize",
			name = "Corner Size",
			description = "Size of the southwest mark in units. (1 - 128)",
			position = 5,
			section = corner
	)
	default int tileCornerLength() { return 32; }
}
