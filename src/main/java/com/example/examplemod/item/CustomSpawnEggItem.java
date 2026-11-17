package com.example.examplemod.item;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;

import java.util.function.Supplier;

public class CustomSpawnEggItem extends ForgeSpawnEggItem {
    public CustomSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, Item.Properties props) {
        super(type, 0xFFFFFF, 0xFFFFFF, props);
    }

    @Override
    public int getColor(int tintIndex) {
        return 0xFFFFFF; // 0xFFFFFF ensures no color tinting so the custom texture displays faithfully
    }
}
