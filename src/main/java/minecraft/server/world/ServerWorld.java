package minecraft.server.world;

import java.util.ArrayList;
import java.util.List;

import minecraft.common.world.Blocks;
import minecraft.common.world.Direction;
import minecraft.common.world.IServerWorld;
import minecraft.common.world.World;
import minecraft.common.world.WorldChunk;
import minecraft.common.world.block.Block;
import minecraft.common.world.block.IBlockPosition;
import minecraft.common.world.block.MutableBlockPosition;
import minecraft.common.world.block.PlantBlock;
import minecraft.common.world.block.PlantType;
import minecraft.common.world.block.state.IBlockState;
import minecraft.common.world.gen.DiamondNoise;

public class ServerWorld extends World implements IServerWorld {
	
	public ServerWorld() {
	}
	
	@Override
	public void generateWorld() {
		DiamondNoise noise = new DiamondNoise(CHUNKS_X * WorldChunk.CHUNK_SIZE, random);
		
		int i = 0;
		for (int cz = 0; cz < CHUNKS_Z; cz++) {
			for (int cx = 0; cx < CHUNKS_X; cx++) {
				WorldChunk chunk = new WorldChunk(cx, cz);
				chunk.generateChunk(noise, random);
				chunks[i++] = chunk;
			}
		}
		
		for (i = 0; i < CHUNKS_Z * CHUNKS_X; i++)
			populateChunk(chunks[i]);
	}
	
	private void populateChunk(WorldChunk chunk) {
		MutableBlockPosition pos = new MutableBlockPosition();
		
		int x0 = chunk.getChunkX() * WorldChunk.CHUNK_SIZE;
		int z0 = chunk.getChunkZ() * WorldChunk.CHUNK_SIZE;
		int x1 = x0 + WorldChunk.CHUNK_SIZE;
		int z1 = z0 + WorldChunk.CHUNK_SIZE;
		
		for (pos.z = z0; pos.z < z1; pos.z++) {
			for (pos.x = x0; pos.x < x1; pos.x++) {
				pos.y = chunk.getHighestPoint(pos) + 1;
				
				if (random.nextInt(80) == 0) {
					growTree(pos);

					pos.y--;
					
					setBlock(pos, Blocks.DIRT_BLOCK, false);
				} else if (random.nextInt(20) == 0) {
					IBlockState plantState = Blocks.PLANT_BLOCK.getDefaultState();
					
					PlantType type = PlantType.TYPES[random.nextInt(PlantType.TYPES.length)];
					plantState = plantState.withProperty(PlantBlock.PLANT_TYPE, type);
					
					setBlockState(pos, plantState, false);
				}
			}
		}
	}
	
	@Override
	public void growTree(IBlockPosition pos) {
		int treeHeight = 5 + random.nextInt(3);
		int trunkHeight = Math.max(1, treeHeight - 5);
		
		MutableBlockPosition tmpPos = new MutableBlockPosition(pos);
		
		for (int i = 0; i < treeHeight; i++) {
			if (i > trunkHeight) {
				int yo = i - trunkHeight;
				
				for (int zo = -3; zo <= 3; zo++) {
					for (int xo = -3; xo <= 3; xo++) {
						tmpPos.x = pos.getX() + xo;
						tmpPos.z = pos.getZ() + zo;
						
						if (getBlock(tmpPos) == Blocks.AIR_BLOCK) {
							int distSqr = xo * xo + yo * yo + zo * zo;
							
							if (distSqr < 3 * 2 * 3)
								setBlock(tmpPos, Blocks.LEAVES_BLOCK, true);
						}
					}
				}
			}

			if (i < treeHeight - 1) {
				tmpPos.x = pos.getX();
				tmpPos.z = pos.getZ();
				
				setBlock(tmpPos, Blocks.WOOD_LOG_BLOCK, true);
			}

			tmpPos.y++;
		}
	}
	
	@Override
	public boolean setBlockState(IBlockPosition pos, IBlockState state, boolean updateNeighbors) {
		WorldChunk chunk = getChunk(pos);
		
		if (chunk != null) {
			IBlockState oldState = chunk.getBlockState(pos);

			if (chunk.setBlockState(pos, state)) {
				if (updateNeighbors) {
					if (state.isAir())
						oldState.onRemoved(this, pos);
					if (oldState.isAir())
						state.onAdded(this, pos);
					if (oldState.isOf(state.getBlock()))
						state.onStateReplaced(this, pos);
				}
				
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public boolean setBlock(IBlockPosition pos, Block block, boolean updateNeighbors) {
		return setBlockState(pos, block.getDefaultState(), updateNeighbors);
	}
	
	@Override
	public void updateNeighbors(IBlockPosition pos, IBlockState state, int flags) {
		List<Integer> usedFlags = new ArrayList<>();
		if ((flags | BLOCK_UPDATE_FLAG) != 0) {
			usedFlags.add(BLOCK_UPDATE_FLAG);
		}
		if ((flags | STATE_UPDATE_FLAG) != 0) {
			usedFlags.add(STATE_UPDATE_FLAG);
		}
		if ((flags | INVENTORY_UPDATE_FLAG) != 0) {
			usedFlags.add(INVENTORY_UPDATE_FLAG);
		}
		
		for (int flag : usedFlags) {
			for (Direction dir : Direction.DIRECTIONS) {
				updateNeighbor(pos.offset(dir), dir.getOpposite(), state, flag);
			}
		}
	}
	
	@Override
	public void updateNeighbor(IBlockPosition pos, Direction fromDir, IBlockState neighborState, int flag) {
		IBlockState state = getBlockState(pos);
		
		switch (flag) {
		case BLOCK_UPDATE_FLAG:
			state.onBlockUpdate(this, pos, fromDir, neighborState);
			break;
		case STATE_UPDATE_FLAG:
			state.onStateUpdate(this, pos, fromDir, neighborState);
			break;
		case INVENTORY_UPDATE_FLAG:
			state.onInventoryUpdate(this, pos, fromDir, neighborState);
			break;
		default:
			break;
		}
	}
	
	@Override
	public int getPower(IBlockPosition pos, int powerFlags) {
		return getPowerExcept(pos, powerFlags, null);
	}

	@Override
	public int getPowerExcept(IBlockPosition pos, int powerFlags, Direction exceptDir) {
		int highestPower = 0;
		
		for (Direction dir : Direction.DIRECTIONS) {
			if (dir != exceptDir) {
				IBlockPosition neighborPos = pos.offset(dir);
				IBlockState state = getBlockState(neighborPos);
			
				if ((state.getOutputPowerFlags(dir) & powerFlags) != 0) {
					int power = state.getPower(this, neighborPos, dir, powerFlags);
					
					if (power > highestPower)
						highestPower = power;
				}
			}
		}
		
		return highestPower;
	}
	
	@Override
	public void update() {
		super.update();
		
		performRandomUpdates();
	}
	
	private void performRandomUpdates() {
		MutableBlockPosition pos = new MutableBlockPosition();

		for (int chunkX = 0; chunkX < CHUNKS_X; chunkX++) {
			for (int chunkZ = 0; chunkZ < CHUNKS_Z; chunkZ++) {
				WorldChunk chunk = getChunk(chunkX, chunkZ);
				if (chunk != null && chunk.hasRandomUpdates()) {
					for (int i = 0; i < RANDOM_TICK_SPEED; i++) {
						pos.x = random.nextInt(WorldChunk.CHUNK_SIZE) + chunkX * WorldChunk.CHUNK_SIZE;
						pos.y = random.nextInt(WORLD_HEIGHT);
						pos.z = random.nextInt(WorldChunk.CHUNK_SIZE) + chunkZ * WorldChunk.CHUNK_SIZE;

						IBlockState state = chunk.getBlockState(pos);
						
						if (state.getBlock().hasRandomUpdate())
							state.onRandomUpdate(this, pos, random);
					}
				}
			}
		}
	}
}
