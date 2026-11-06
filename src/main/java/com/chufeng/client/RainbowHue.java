package com.chufeng.client;

/** 色相 → 0xRRGGBB，程序化特效共用。hue 会自己滚回 [0, 1) */
final class RainbowHue
{
    /** 彩虹的饱和度，压低一点让加法混合后不至于一片死白 */
    private static final float SATURATION = 0.45F;

    static int rgb(float hue)
    {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) h;
        float f = h - sector;
        float p = 1.0F - SATURATION;
        float q = 1.0F - SATURATION * f;
        float t = 1.0F - SATURATION * (1.0F - f);

        float red;
        float green;
        float blue;

        switch (sector % 6)
        {
            case 0:
                red = 1.0F;
                green = t;
                blue = p;
                break;
            case 1:
                red = q;
                green = 1.0F;
                blue = p;
                break;
            case 2:
                red = p;
                green = 1.0F;
                blue = t;
                break;
            case 3:
                red = p;
                green = q;
                blue = 1.0F;
                break;
            case 4:
                red = t;
                green = p;
                blue = 1.0F;
                break;
            default:
                red = 1.0F;
                green = p;
                blue = q;
                break;
        }

        return ((int) (red * 255.0F) << 16) | ((int) (green * 255.0F) << 8) | (int) (blue * 255.0F);
    }

    private RainbowHue()
    {
    }
}
