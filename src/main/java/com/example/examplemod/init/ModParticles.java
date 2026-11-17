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
}
