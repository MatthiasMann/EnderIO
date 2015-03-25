package crazypants.enderio.machine.invpanel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import crazypants.enderio.EnderIO;
import crazypants.enderio.network.MessageTileEntity;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

public class PacketItemInfo extends MessageTileEntity<TileInventoryPanel> implements IMessageHandler<PacketItemInfo, IMessage> {

  private byte[] compressed;

  public PacketItemInfo() {
  }

  public PacketItemInfo(TileInventoryPanel tile, List<InventoryDatabase.ItemKey> items) {
    super(tile);
    try {
      compressed = tile.getDatabase().compressItemInfo(items);
    } catch (IOException ex) {
      Logger.getLogger(PacketItemInfo.class.getName()).log(Level.SEVERE, "Exception while compressing items", ex);
      compressed = new byte[0];
    }
  }

  @Override
  public void fromBytes(ByteBuf buf) {
    super.fromBytes(buf);
    int size = buf.readInt();
    compressed = new byte[size];
    buf.readBytes(compressed);
  }

  @Override
  public void toBytes(ByteBuf buf) {
    super.toBytes(buf);
    buf.writeInt(compressed.length);
    buf.writeBytes(compressed);
  }

  @Override
  public IMessage onMessage(PacketItemInfo message, MessageContext ctx) {
    EntityPlayer player = EnderIO.proxy.getClientPlayer();
    TileEntity te = player.worldObj.getTileEntity(message.x, message.y, message.z);
    if(te instanceof TileInventoryPanel) {
      TileInventoryPanel teInvPanel = (TileInventoryPanel) te;
      InventoryDatabase db = teInvPanel.getDatabase();
      try {
        db.readCompressedItems(message.compressed);
      } catch (IOException ex) {
        Logger.getLogger(PacketItemInfo.class.getName()).log(Level.SEVERE, "Exception while reading item info", ex);
      }
    }
    return null;
  }
}