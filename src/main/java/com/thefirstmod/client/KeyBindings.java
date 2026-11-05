package com.thefirstmod.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

public final class KeyBindings
{
    /** 长按召唤流星群，默认 J */
    public static final KeyMapping METEOR = new KeyMapping(
            "key.thefirstmod.meteor",
            InputConstants.KEY_J,
            "key.categories.thefirstmod");

    /** 瞬闪斩，默认 K */
    public static final KeyMapping BLINK = new KeyMapping(
            "key.thefirstmod.blink",
            InputConstants.KEY_K,
            "key.categories.thefirstmod");

    private KeyBindings()
    {
    }
}
