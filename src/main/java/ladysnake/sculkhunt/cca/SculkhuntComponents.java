package ladysnake.sculkhunt.cca;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import ladysnake.sculkhunt.common.Sculkhunt;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public class SculkhuntComponents implements EntityComponentInitializer {
    public static final ComponentKey<SculkComponent> SCULK = ComponentRegistry.getOrCreate(new Identifier(Sculkhunt.MODID, "sculk"), SculkComponent.class);
    public static final ComponentKey<BurrowingComponent> BURROWING = ComponentRegistry.getOrCreate(new Identifier(Sculkhunt.MODID, "burrowing"), BurrowingComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(LivingEntity.class, SCULK, SculkComponent::new);
        registry.registerFor(PlayerEntity.class, BURROWING, BurrowingComponent::new);
    }
}