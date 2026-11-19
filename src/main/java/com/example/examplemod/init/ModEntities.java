package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.BoltBaronEntity;
import com.example.examplemod.entity.LightningProjectileEntity;
import com.example.examplemod.entity.ThunderOrbEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final RegistryObject<EntityType<BoltBaronEntity>> BOLT_BARON =
            ENTITIES.register("bolt_baron",
                    () -> EntityType.Builder.of(BoltBaronEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 3.2F)
                            .build("bolt_baron"));

    public static final RegistryObject<EntityType<ThunderOrbEntity>> THUNDER_ORB =
            ENTITIES.register("thunder_orb",
                    () -> EntityType.Builder.<ThunderOrbEntity>of(ThunderOrbEntity::new, MobCategory.MISC)
                            .sized(0.8F, 0.8F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("thunder_orb"));

    public static final RegistryObject<EntityType<LightningProjectileEntity>> LIGHTNING_PROJECTILE =
            ENTITIES.register("lightning_projectile",
                    () -> EntityType.Builder.<LightningProjectileEntity>of(LightningProjectileEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .noSummon()
                            .build("lightning_projectile"));
}
