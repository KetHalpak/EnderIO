package crazypants.enderio.conduit.gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import crazypants.enderio.EnderIO;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.network.MessageTileEntity;

public class PacketOpenConduitUI extends MessageTileEntity<TileEntity> implements IMessageHandler<PacketOpenConduitUI, IMessage>{

  private ForgeDirection dir;

  public PacketOpenConduitUI() {
  }

  public PacketOpenConduitUI(TileEntity tile, ForgeDirection dir) {
    super(tile);
    this.dir = dir;
  }

  @Override
  public void toBytes(ByteBuf buf) {
    buf.writeShort(dir.ordinal());
  }

  @Override
  public void fromBytes(ByteBuf buf) {
    dir = ForgeDirection.values()[buf.readShort()];
  }

  public IMessage onMessage(PacketOpenConduitUI message, MessageContext ctx) {
      EntityPlayer player = ctx.getServerHandler().playerEntity;
      TileEntity tile = message.getWorld(ctx).getTileEntity(x, y, z);
      player.openGui(EnderIO.instance, GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE + message.dir.ordinal(), player.worldObj, tile.xCoord, tile.yCoord, tile.zCoord);
      return null;
  }

}
