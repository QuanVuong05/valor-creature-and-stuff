package com.example.examplemod.init;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ExampleMod.MODID);

    public static final RegistryObject<SimpleParticleType> LIGHTNING_BOLT_PARTICLE =
            PARTICLE_TYPES.register("lightning_bolt", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> THUNDER_ORB_EXPLOSION_PARTICLE =
            PARTICLE_TYPES.register("thunder_orb_explosion", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> THUNDER_STRIKE_PARTICLE =
            PARTICLE_TYPES.register("thunder_strike", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> EXPLOSION_RING_PARTICLE =
            PARTICLE_TYPES.register("explosion_ring", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> LIGHTNING_SPARK_PARTICLE =
            PARTICLE_TYPES.register("lightning_spark", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> DUST_PARTICLE =
            PARTICLE_TYPES.register("dust", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> DUST_MEDIUM_PARTICLE =
            PARTICLE_TYPES.register("dust_medium", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> DUST_SMALL_PARTICLE =
            PARTICLE_TYPES.register("dust_small", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> ELECTRIC_AURA_PARTICLE =
            PARTICLE_TYPES.register("electric_aura", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> THUNDER_LINE_CYAN_PARTICLE =
            PARTICLE_TYPES.register("thunder_line_cyan", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> THUNDER_LINE_BLUE_PARTICLE =
            PARTICLE_TYPES.register("thunder_line_blue", () -> new SimpleParticleType(true));
}
