package com.firstsword.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

public final class KeyBindings
{
    /** 长按召唤流星群，默认 J */
    public static final KeyMapping METEOR = new KeyMapping(
            "key.firstsword.meteor",
            InputConstants.KEY_J,
            "key.categories.firstsword");

    /** 瞬闪斩，默认 K */
    public static final KeyMapping BLINK = new KeyMapping(
            "key.firstsword.blink",
            InputConstants.KEY_K,
            "key.categories.firstsword");

    private KeyBindings()
    {
    }
}
