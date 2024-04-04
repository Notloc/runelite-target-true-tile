package com.notloc.targettruetile;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TargetTrueTilePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TargetTrueTilePlugin.class);
		RuneLite.main(args);
	}
}