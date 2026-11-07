package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.ValorCreatureEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final RegistryObject<EntityType<ValorCreatureEntity>> VALOR_CREATURE =
            ENTITIES.register("valor_creature",
                    () -> EntityType.Builder.of(ValorCreatureEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 3.2F)
                            .build("valor_creature"));
}
