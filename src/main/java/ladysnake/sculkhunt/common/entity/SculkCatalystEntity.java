package ladysnake.sculkhunt.common.entity;

import com.google.common.collect.Lists;
import ladysnake.sculkhunt.cca.SculkhuntComponents;
import ladysnake.sculkhunt.common.Sculkhunt;
import ladysnake.sculkhunt.common.block.SculkVeinBlock;
import ladysnake.sculkhunt.common.init.SculkhuntBlocks;
import ladysnake.sculkhunt.common.init.SculkhuntGamerules;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ladysnake.sculkhunt.common.Sculkhunt.SPAWN_RADIUS;

public class SculkCatalystEntity extends Entity {
    private static final TrackedData<Integer> BLOOMING_PHASE = DataTracker.registerData(SculkCatalystEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private final List<Sculk> sculks = Lists.newArrayList();
    private final List<Sculk> veins = Lists.newArrayList();
    private int bloomCounter = 0;
    private int incapacitatedTimer = 0;

    public SculkCatalystEntity(EntityType<SculkCatalystEntity> type, World world) {
        super(type, world);
    }

    public boolean canPlaceSculkAt(BlockPos blockPos) {
        return world.getBlockState(blockPos).isSolidBlock(world, blockPos)
                && blockPos != this.getBlockPos()
                && world.getBlockState(blockPos).getMaterial() != Material.SCULK;
    }

    public boolean canPlaceVeinAt(BlockPos blockPos)
    {
        return (world.getBlockState(blockPos).isAir() || world.getBlockState(blockPos).getMaterial() == Material.BAMBOO || world.getBlockState(blockPos).getMaterial() == Material.BAMBOO_SAPLING || world.getBlockState(blockPos).getMaterial() == Material.COBWEB || world.getBlockState(blockPos).getMaterial() == Material.FIRE || world.getBlockState(blockPos).getMaterial() == Material.CARPET || world.getBlockState(blockPos).getMaterial() == Material.CACTUS || world.getBlockState(blockPos).getMaterial() == Material.PLANT || world.getBlockState(blockPos).getMaterial() == Material.REPLACEABLE_PLANT || world.getBlockState(blockPos).getMaterial() == Material.REPLACEABLE_UNDERWATER_PLANT || world.getBlockState(blockPos).getMaterial() == Material.SNOW_LAYER || world.getBlockState(blockPos).getBlock() == Blocks.WATER)
                && hasConnectableNeighbor(blockPos)
                && blockPos != this.getBlockPos();
    }
    private boolean hasConnectableNeighbor(BlockPos blockPos)
    {
        for (Direction direction : Direction.values())
        {
            BlockPos neighborPos = blockPos.add(direction.getVector());
            BlockState neighborBlock = world.getBlockState(neighborPos);
            boolean canConnect = neighborBlock.isSideSolidFullSquare(world, neighborPos, direction.getOpposite()) && !neighborBlock.getMaterial().equals(Material.SCULK);
            if (canConnect) return true;
        }
        return false;
    }

    public BlockState getBlockStateForVein(BlockPos blockPos)
    {
        BlockState sculkVein = SculkhuntBlocks.SCULK_VEIN.getDefaultState().with(SculkVeinBlock.WATERLOGGED, world.getBlockState(blockPos).getFluidState().getFluid().equals(Fluids.WATER));

        for (Direction direction : Direction.values())
        {
            BlockPos neighborPos = blockPos.add(direction.getVector());
            BlockState neighborBlock = world.getBlockState(neighborPos);
            boolean canConnect = neighborBlock.isSideSolidFullSquare(world, neighborPos, direction.getOpposite()) && !neighborBlock.getMaterial().equals(Material.SCULK);
            sculkVein = sculkVein.with(ConnectingBlock.FACING_PROPERTIES.get(direction), canConnect);
        }

        return sculkVein;
    }

    public void spreadVein(BlockPos blockPos, BlockState vein)
    {
        for (Direction direction : Direction.values())
        {
            if (vein.get(ConnectingBlock.FACING_PROPERTIES.get(direction)))
            {
                // Place sculk block
                BlockPos neighborPos = blockPos.add(direction.getVector());
                this.sculks.add(new Sculk(neighborPos, world.getBlockState(neighborPos)));
                world.setBlockState(neighborPos, SculkhuntBlocks.SCULK.getDefaultState());

                // Remove veins attached to sculk block
                for (Direction offset : Direction.values())
                {
                    BlockPos veinPos = neighborPos.offset(offset);
                    BlockState state = world.getBlockState(veinPos);
                    if (state.getBlock().equals(SculkhuntBlocks.SCULK_VEIN))
                    {
                        // Remove connection to this sculk block
                        state = state.with(ConnectingBlock.FACING_PROPERTIES.get(offset.getOpposite()), false);
                        if (!(state.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.UP)) || state.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.DOWN)) || state.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.NORTH)) || state.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.SOUTH)) || state.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.EAST)) || state.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.WEST))))
                        {
                            Sculk remove = new Sculk(veinPos, null);
                            this.sculks.remove(remove);
                            this.veins.remove(remove);
                            world.setBlockState(veinPos, Blocks.AIR.getDefaultState());
                        }
                        else world.setBlockState(veinPos, state);
                    }
                }
            }
        }

        // Place veins wherever possible within a 3x3 lattice centered around where the vein used to be
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -1; y <= 1; y++)
            {
                for (int z = -1; z <= 1; z++)
                {
                    BlockPos placePos = blockPos.add(x, y, z);

                    if ((x != 0 ^ y != 0 ^ z != 0) && canPlaceVeinAt(placePos))
                    {
                        Sculk newVein = new Sculk(placePos, world.getBlockState(placePos));
                        this.sculks.add(newVein);
                        this.veins.add(newVein);
                        world.setBlockState(placePos, getBlockStateForVein(placePos));
                    }
                }
            }
        }
    }

    @Override
    public boolean collidesWith(Entity other) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        boolean hasSpread = false;
        if (!this.world.isClient)
        {
            if (this.isIncapacitated())
            {
                this.incapacitatedTimer--;

                if (this.age % 5 == 0) {
                    ((ServerWorld) this.world).playSound(this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_SCULK_SENSOR_BREAK, SoundCategory.BLOCKS, 1.0f, 1.5f, false);
                    ((ServerWorld) this.world).spawnParticles(Sculkhunt.SOUND, this.getX(), this.getY() + .5f, this.getZ(), 1, 0, 0, 0, 0);
                }
            }
            // If the sculk should spread
            else if (this.sculks.isEmpty() || (world.random.nextInt(Math.max(1, world.getGameRules().get(SculkhuntGamerules.SCULK_CATALYST_BLOOM_DELAY).get())) == 0 && getBloomingPhase() == -1))
            {
                // Each iteration spreads sculk
                for (int i = 0; i < world.getGameRules().get(SculkhuntGamerules.SCULK_CATALYST_BLOOM_RADIUS).get(); i++)
                {
                    // If there is no sculk yet
                    if (this.sculks.isEmpty())
                    {
                        // Set block under catalyst to sculk
                        BlockPos blockPos = this.getBlockPos().add(0, -1, 0);
                        this.sculks.add(new Sculk(blockPos, world.getBlockState(blockPos)));
                        hasSpread = true;
                        world.setBlockState(blockPos, SculkhuntBlocks.SCULK.getDefaultState());

                        // Place veins wherever possible within a 3x3 cube centered around the block beneath the catalyst
                        for (int x = -1; x <= 1; x++)
                        {
                            for (int y = -1; y <= 1; y++)
                            {
                                for (int z = -1; z <= 1; z++)
                                {
                                    BlockPos placePos = blockPos.add(x, y, z);

                                    if ((x != 0 ^ y != 0 ^ z != 0) && canPlaceVeinAt(placePos))
                                    {
                                        Sculk newVein = new Sculk(placePos, world.getBlockState(placePos));
                                        this.sculks.add(newVein);
                                        this.veins.add(newVein);
                                        world.setBlockState(placePos, getBlockStateForVein(placePos));
                                    }
                                }
                            }
                        }
                    }

                    // If there is sculk
                    else
                    {
                        // Try to spread a vein, if possible
                        if (!this.veins.isEmpty())
                        {
                            Sculk vein = this.veins.get(random.nextInt(this.veins.size()));
                            BlockPos blockPos = NbtHelper.toBlockPos(vein.blockPos);
                            BlockState veinState = world.getBlockState(blockPos);

                            // If this is still a sculk vein
                            if (veinState.getBlock() == SculkhuntBlocks.SCULK_VEIN)
                            {
                                // Add sculk around where the vein used to be, clearing any veins attached to those blocks
                                spreadVein(blockPos, veinState);
                                hasSpread = true;

                                // 2% chance of placing a sculk sensor where the vein used to be
                                if (random.nextInt(50) == 0)
                                {
                                    BlockPos checkPos = blockPos.offset(Direction.DOWN);
                                    if (world.getBlockState(checkPos).isSideSolidFullSquare(world, checkPos, Direction.UP))
                                    {
                                        this.sculks.add(new Sculk(blockPos, world.getBlockState(blockPos)));
                                        world.setBlockState(blockPos, Blocks.SCULK_SENSOR.getDefaultState());
                                    }
                                }
                            }
                        }

                        // Try to spread a random sculk
                        Sculk sculk = this.sculks.get(random.nextInt(this.sculks.size()));
                        BlockPos blockPos = NbtHelper.toBlockPos(sculk.blockPos);
                        BlockState sculkState = world.getBlockState(blockPos);

                        // If this sculk is a sculk vein
                        if (sculkState.getBlock() == SculkhuntBlocks.SCULK_VEIN)
                        {
                            // Add sculk around where the vein used to be, clearing any veins attached to those blocks
                            spreadVein(blockPos, sculkState);
                            hasSpread = true;

                            // 1% chance of placing a sculk sensor where the vein used to be
                            if (random.nextInt(50) == 0)
                            {
                                BlockPos checkPos = blockPos.offset(Direction.DOWN);
                                if (world.getBlockState(checkPos).isSideSolidFullSquare(world, checkPos, Direction.UP))
                                {
                                    this.sculks.add(new Sculk(blockPos, world.getBlockState(blockPos)));
                                    world.setBlockState(blockPos, Blocks.SCULK_SENSOR.getDefaultState());
                                }
                            }
                        }

                        // If this sculk is a sculk block
                        else if (world.getBlockState(blockPos).getBlock() == SculkhuntBlocks.SCULK)
                        {
                            // Spread downwards
                            Direction bloomDirection = Direction.random(this.random);
                            BlockPos depthPos = blockPos.offset(bloomDirection);
                            if (canPlaceSculkAt(depthPos))
                            {
                                this.sculks.add(new Sculk(depthPos, world.getBlockState(depthPos)));
                                world.setBlockState(depthPos, SculkhuntBlocks.SCULK.getDefaultState());
                            }

                            // Create veins
                            BlockPos veinCenter = blockPos.offset(bloomDirection.getOpposite());
                            for (int x = -1; x <= 1; x++)
                            {
                                for (int y = -1; y <= 1; y++)
                                {
                                    for (int z = -1; z <= 1; z++)
                                    {
                                        BlockPos placePos = veinCenter.add(x, y, z);

                                        if ((x != 0 ^ y != 0 ^ z != 0) && canPlaceVeinAt(placePos))
                                        {
                                            Sculk newVein = new Sculk(placePos, world.getBlockState(placePos));
                                            this.sculks.add(newVein);
                                            this.veins.add(newVein);
                                            world.setBlockState(placePos, getBlockStateForVein(placePos));
                                        }
                                    }
                                }
                            }

                            // Chance of spawning a sculk mob
                            if (this.random.nextInt(world.getGameRules().get(SculkhuntGamerules.SCULK_CATALYST_MOB_SPAWN_FREQUENCY).get()) == 0)
                            {
                                Entity entity;
                                switch (random.nextInt(4)) {
                                    case 0:
                                        entity = EntityType.CREEPER.create(world);
                                        break;
                                    case 1:
                                        entity = EntityType.SPIDER.create(world);
                                        break;
                                    case 2:
                                        entity = EntityType.SKELETON.create(world);
                                        break;
                                    default:
                                        entity = EntityType.ZOMBIE.create(world);
                                }

                                entity.setPos(blockPos.getX(), blockPos.getY() - entity.getHeight(), blockPos.getZ());
                                entity.updateTrackedPosition(blockPos.getX(), blockPos.getY() - entity.getHeight(), blockPos.getZ());
                                SculkhuntComponents.SCULK.get(entity).setSculk(true);
                                this.world.spawnEntity(entity);
                            }
                        }
                    }
                }
            }
        }

        if (hasSpread) {
            this.setBloomingPhase(0);
            this.playSound(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 1.0f);
        }

        if (this.getBloomingPhase() != -1) {
            if (this.getBloomingPhase() >= 11) {
                this.setBloomingPhase(-1);
            } else {
                this.setBloomingPhase(this.getBloomingPhase() + 1);
            }
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (SculkhuntComponents.SCULK.get(player).isSculk() && !this.isIncapacitated()) {
            for (int i = 0; i < (player.getWidth() * player.getHeight()) * 100; i++) {
                world.addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(SculkhuntBlocks.SCULK_CATALYST)), player.getX() + player.getRandom().nextGaussian() * player.getWidth() / 2f, (player.getY() + player.getHeight() / 2f) + player.getRandom().nextGaussian() * player.getHeight() / 2f, player.getZ() + player.getRandom().nextGaussian() * player.getWidth() / 2f, player.getRandom().nextGaussian() / 10f, player.getRandom().nextFloat() / 10f, player.getRandom().nextGaussian() / 10f);
            }
            player.playSound(SoundEvents.BLOCK_SCULK_SENSOR_BREAK, 1.0f, 0.9f);

            if (!this.world.isClient) {
                // respawn in sculk
                ServerWorld world = (ServerWorld) player.world;

                List<ServerPlayerEntity> players = world.getPlayers(serverPlayerEntity -> !serverPlayerEntity.isCreative() && !serverPlayerEntity.isSpectator() && !SculkhuntComponents.SCULK.get(serverPlayerEntity).isSculk());
                List<SculkCatalystEntity> catalysts;

                if (!players.isEmpty()) {
                    ServerPlayerEntity prey = players.get(world.random.nextInt(players.size()));
                    catalysts = world.getEntitiesByClass(SculkCatalystEntity.class, new Box(prey.getX() - SPAWN_RADIUS, prey.getY() - SPAWN_RADIUS / 2f, prey.getZ() - SPAWN_RADIUS, prey.getX() + SPAWN_RADIUS, prey.getY() + SPAWN_RADIUS / 2f, prey.getZ() + SPAWN_RADIUS), sculkCatalystEntity -> !sculkCatalystEntity.isIncapacitated());

                    if (!catalysts.isEmpty()) {
                        // filter catalysts that aren't in a 30 block radius of the player
                        catalysts = catalysts.stream().filter(sculkCatalystEntity -> sculkCatalystEntity.getBlockPos().getSquaredDistance(prey.getBlockPos()) >= 30).collect(Collectors.toList());
                        if (!catalysts.isEmpty()) {
                            catalysts.sort((o1, o2) -> (int) (prey.getPos().distanceTo(o1.getPos()) - prey.getPos().distanceTo(o2.getPos())));
                            BlockPos newPos = new BlockPos(catalysts.get(0).getPos().add(world.random.nextGaussian() * 2, 0, world.random.nextGaussian() * 2));

                            int tries = 25;
                            while (tries > 0 && !world.getBlockState(newPos).isAir() && !world.getBlockState(newPos.add(0, 1, 0)).isAir()) {
                                tries--;
                                newPos = new BlockPos(catalysts.get(0).getPos().add(world.random.nextGaussian() * 2, 0, world.random.nextGaussian() * 2));
                            }

                            if (world.getBlockState(newPos).isAir() && world.getBlockState(newPos.add(0, 1, 0)).isAir()) {
                                ((ServerPlayerEntity) player).networkHandler.requestTeleport(newPos.getX(), newPos.getY() - player.getHeight() * 2, newPos.getZ(), player.getYaw(), player.getPitch());
                            } else {
                                Sculkhunt.respawnAtRandomCatalyst(world, ((ServerPlayerEntity) player), ((ServerPlayerEntity) player));
                            }
                        } else {
                            Sculkhunt.respawnAtRandomCatalyst(world, ((ServerPlayerEntity) player), ((ServerPlayerEntity) player));
                        }
                    } else {
                        Sculkhunt.respawnAtRandomCatalyst(world, ((ServerPlayerEntity) player), ((ServerPlayerEntity) player));
                    }
                } else {
                    Sculkhunt.respawnAtRandomCatalyst(world, ((ServerPlayerEntity) player), ((ServerPlayerEntity) player));
                }
            }
        }

        return super.interact(player, hand);
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(SculkhuntBlocks.SCULK_CATALYST);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(BLOOMING_PHASE, -1);
    }

    public int getBloomingPhase() {
        return this.dataTracker.get(BLOOMING_PHASE);
    }

    public void setBloomingPhase(int bloomingPhase) {
        this.dataTracker.set(BLOOMING_PHASE, bloomingPhase);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {

    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {

    }

    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        this.bloomCounter = nbt.getInt("BloomCounter");

        this.sculks.clear();
        NbtList nbtList = nbt.getList("Sculks", 10);
        for (int i = 0; i < nbtList.size(); ++i) {
            NbtCompound nbtCompound = nbtList.getCompound(i);
            Sculk sculk = new Sculk(NbtHelper.toBlockPos(nbtCompound.getCompound("BlockPos")), NbtHelper.toBlockState(nbtCompound.getCompound("BlockState")));
            this.sculks.add(sculk);
        }

        this.veins.clear();
        nbtList = nbt.getList("Veins", 10);
        for (int i = 0; i < nbtList.size(); ++i) {
            NbtCompound nbtCompound = nbtList.getCompound(i);
            Sculk sculk = new Sculk(NbtHelper.toBlockPos(nbtCompound.getCompound("BlockPos")), NbtHelper.toBlockState(nbtCompound.getCompound("BlockState")));
            this.veins.add(sculk);
        }
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        nbt.putInt("BloomCounter", this.bloomCounter);
        nbt.put("Sculks", this.getSculks());
        nbt.put("Veins", this.getVeins());

        return nbt;
    }

    @Override
    public Packet<?> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    protected Entity.MoveEffect getMoveEffect() {
        return Entity.MoveEffect.NONE;
    }

    public boolean collides() {
        return true;
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public void animateDamage() {
        ((ServerWorld) world).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(SculkhuntBlocks.SCULK_CATALYST)), this.getBlockPos().getX() + 0.5, this.getBlockPos().getY() + 0.5, this.getBlockPos().getZ() + 0.5, 100, random.nextGaussian() / 5f, random.nextGaussian() / 5f, random.nextGaussian() / 5f, 0.15);
        ((ServerWorld) world).playSoundFromEntity(null, this, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    public boolean isIncapacitated() {
        return this.incapacitatedTimer > 0;
    }

    public boolean damage(DamageSource source, float amount)
    {
        if (!this.isRemoved() && !this.world.isClient)
        {
            // Incapacitate 10 Seconds
            this.incapacitatedTimer = 200;

            // Remove an amount of sculk equal to the damage dealt squared
            int amountOfSculkToRemove = Math.round(amount * amount);
            List<Sculk> newVeins = new ArrayList<>();

            // If there will be no more sculk, kill the catalyst
            if ((amountOfSculkToRemove >= this.sculks.size() || this.sculks.isEmpty()))
            {
                this.kill();
            }
            else
            {
                // Each iteration removes a sculk
                for (int i = 0; i < amountOfSculkToRemove; i++)
                {
                    // Get the most recent sculk added by this catalyst that still exists
                    Sculk sculk = this.sculks.get(this.sculks.size() - 1);
                    BlockState blockState = NbtHelper.toBlockState(this.sculks.get(this.sculks.size() - 1).blockstate);
                    BlockPos blockPos = NbtHelper.toBlockPos(this.sculks.get(this.sculks.size() - 1).blockPos);

                    // If the sculk is a sculk block
                    if (world.getBlockState(blockPos).getBlock() == SculkhuntBlocks.SCULK)
                    {
                        // Remove the sculk block
                        this.sculks.remove(this.sculks.size() - 1);
                        world.breakBlock(blockPos, false);
                        world.setBlockState(blockPos, blockState);

                        // Add a sculk veins around where the sculk block was
                        for (Direction direction : Direction.values())
                        {
                            if (blockState.isSideSolidFullSquare(world, blockPos, direction))
                            {
                                BlockPos neighborPos = blockPos.add(direction.getVector());
                                if (canPlaceVeinAt(neighborPos))
                                {
                                    newVeins.add(new Sculk(neighborPos, world.getBlockState(neighborPos)));
                                    world.breakBlock(neighborPos, false);
                                    world.setBlockState(neighborPos, getBlockStateForVein(neighborPos));
                                }
                            }
                        }
                    }
                    // If the sculk is a vein
                    else if (world.getBlockState(blockPos).getBlock() == SculkhuntBlocks.SCULK_VEIN)
                    {
                        // Remove the vein
                        world.breakBlock(blockPos, false);
                        world.setBlockState(blockPos, blockState);
                        Sculk remove = this.sculks.get(this.sculks.size() - 1);
                        this.sculks.remove(this.sculks.size() - 1);
                        this.veins.remove(remove);
                    }
                    else if (world.getBlockState(blockPos).getBlock() == Blocks.SCULK_SENSOR)
                    {
                        // Remove the sensor
                        world.breakBlock(blockPos, false);
                        world.setBlockState(blockPos, blockState);
                        this.sculks.remove(this.sculks.size() - 1);
                    }
                    else
                    {
                        this.sculks.remove(this.sculks.size() - 1);
                    }
                }
            }

            sculks.addAll(newVeins);
            veins.addAll(newVeins);

            return true;
        } else {
            return false;
        }
    }

    public void kill() {
        if (!this.isRemoved() && !this.world.isClient)
        {
            for (Sculk sculk : sculks)
            {
                BlockState blockState = NbtHelper.toBlockState(sculk.blockstate);
                BlockPos blockPos = NbtHelper.toBlockPos(sculk.blockPos);
                if (world.getBlockState(blockPos).getBlock() == SculkhuntBlocks.SCULK || world.getBlockState(blockPos).getBlock() == SculkhuntBlocks.SCULK_VEIN || world.getBlockState(blockPos).getBlock() == Blocks.SCULK_SENSOR) {
                    world.breakBlock(blockPos, false);
                    world.setBlockState(blockPos, blockState);
                }
            }

            ((ServerWorld) world).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(SculkhuntBlocks.SCULK_CATALYST)), this.getBlockPos().getX() + 0.5, this.getBlockPos().getY() + 0.5, this.getBlockPos().getZ() + 0.5, 100, random.nextGaussian() / 5f, random.nextGaussian() / 5f, random.nextGaussian() / 5f, 0.15);
            ((ServerWorld) world).playSoundFromEntity(null, this, SoundEvents.BLOCK_SCULK_SENSOR_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);

            super.kill();
        }
    }


    public NbtList getSculks() {
        NbtList nbtList = new NbtList();
        for (Sculk sculk : sculks) {
            NbtCompound nbtCompound = new NbtCompound();
            nbtCompound.put("BlockPos", sculk.blockPos);
            nbtCompound.put("BlockState", sculk.blockstate);
            nbtList.add(nbtCompound);
        }

        return nbtList;
    }
    public NbtList getVeins() {
        NbtList nbtList = new NbtList();
        for (Sculk vein : veins) {
            NbtCompound nbtCompound = new NbtCompound();
            nbtCompound.put("BlockPos", vein.blockPos);
            nbtCompound.put("BlockState", vein.blockstate);
            nbtList.add(nbtCompound);
        }

        return nbtList;
    }

    private static class Sculk {
        NbtCompound blockPos;
        NbtCompound blockstate;

        Sculk(BlockPos blockPos, BlockState blockState) {
            this.blockPos = NbtHelper.fromBlockPos(blockPos);
            if (blockState != null) this.blockstate = NbtHelper.fromBlockState(blockState);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Sculk sculk = (Sculk) o;
            return blockPos.equals(sculk.blockPos);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(blockPos);
        }
    }
}
