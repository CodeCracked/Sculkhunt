package ladysnake.sculkhunt.util;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class BlockPalette
{
    private List<BlockState> palette;

    public BlockPalette()
    {
        this.palette = Lists.newArrayList();
    }

    public int getBlockIndex(BlockState state)
    {
        int index = palette.indexOf(state);
        if (index < 0)
        {
            palette.add(state);
            return palette.size() - 1;
        }
        else return index;
    }
    public BlockState resolve(int index)
    {
        return palette.get(index);
    }

    public NbtList serialize()
    {
        NbtList list = new NbtList();
        for (BlockState state : palette) list.add(NbtHelper.fromBlockState(state));
        return list;
    }
    public static BlockPalette deserialize(NbtList list)
    {
        BlockPalette palette = new BlockPalette();
        for (int i = 0; i < list.size(); i++) palette.palette.add(NbtHelper.toBlockState(list.getCompound(i)));
        return palette;
    }
}
