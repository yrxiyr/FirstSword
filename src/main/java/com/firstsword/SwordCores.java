package com.firstsword;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 词条注册表。
 *
 * 附属模组在 mod 初始化阶段（构造期，别等到 common setup）调用 {@link #register}，
 * 之后剑上的词条 id 就能查到对应实现。
 */
public final class SwordCores
{
    private static final Map<ResourceLocation, SwordCore> REGISTRY = new LinkedHashMap<>();

    public static void register(SwordCore core)
    {
        REGISTRY.put(core.id(), core);
    }

    /** 这把剑上装着的词条，按安装顺序返回 */
    public static List<SwordCore> on(ItemStack stack)
    {
        List<ResourceLocation> ids = idsOf(stack);

        if (ids.isEmpty())
        {
            return List.of();
        }

        List<SwordCore> result = new ArrayList<>(ids.size());

        for (ResourceLocation id : ids)
        {
            SwordCore core = REGISTRY.get(id);

            if (core != null)
            {
                result.add(core);
            }
        }

        return result;
    }

    public static boolean has(ItemStack stack, ResourceLocation id)
    {
        return idsOf(stack).contains(id);
    }

    /** 给剑打上词条；已经装过就返回 false */
    public static boolean attach(ItemStack stack, ResourceLocation id)
    {
        List<ResourceLocation> ids = idsOf(stack);

        if (ids.contains(id))
        {
            return false;
        }

        List<ResourceLocation> next = new ArrayList<>(ids.size() + 1);
        next.addAll(ids);
        next.add(id);

        stack.set(ModComponents.CORES.get(), List.copyOf(next));

        SwordCore core = REGISTRY.get(id);

        if (core != null)
        {
            core.onAttach(stack);
        }

        return true;
    }

    private static List<ResourceLocation> idsOf(ItemStack stack)
    {
        return stack.getOrDefault(ModComponents.CORES.get(), List.of());
    }

    private SwordCores()
    {
    }
}
