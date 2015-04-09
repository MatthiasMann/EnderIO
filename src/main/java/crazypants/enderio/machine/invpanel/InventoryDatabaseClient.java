package crazypants.enderio.machine.invpanel;

import crazypants.enderio.network.CompressedDataInput;
import crazypants.enderio.network.PacketHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

public class InventoryDatabaseClient extends InventoryDatabase<InventoryDatabaseClient.ItemEntry> {

  private final TileInventoryPanel te;
  private final ArrayList<ItemEntry> clientItems;
  private final HashSet<Integer> requestedItems;
  
  private boolean clientListNeedsSorting;

  public InventoryDatabaseClient(TileInventoryPanel te) {
    this.te = te;
    clientItems = new ArrayList<ItemEntry>();
    requestedItems = new HashSet<Integer>();
  }

  public void readCompressedItems(byte[] compressed) throws IOException {
    CompressedDataInput cdi = new CompressedDataInput(compressed);
    try {
      int pktGeneration = cdi.readVariable();
      if(pktGeneration != generation) {
        return;
      }
      int numEntries = cdi.readVariable();
      for(int i=0 ; i<numEntries ; i++) {
        int code = cdi.readVariable();
        int itemID = cdi.readVariable();
        int meta = cdi.readVariable();
        NBTTagCompound nbt = null;

        int dbIndex = code >> 1;
        if((code & 1) == 1) {
          nbt = CompressedStreamTools.read(cdi);
        }

        // item order can vary, ensure that the slot exists
        complexItems.ensureCapacity(dbIndex + 1);
        while(complexItems.size() <= dbIndex) {
          complexItems.add(null);
        }

        ItemEntry entry = complexItems.get(dbIndex);
        if(entry == null) {
          entry = createItemEntry(dbIndex + COMPLEX_DBINDEX_START, itemID, meta, nbt);
          complexItems.set(dbIndex, entry);
          complexRegistry.put(entry, entry);
        }

        int count = cdi.readVariable();
        setItemCount(entry, count);
      }
      clientListNeedsSorting = true;
    } finally {
      cdi.close();
    }
  }

  public void readCompressedItemList(byte[] compressed) throws IOException {
    CompressedDataInput cdi = new CompressedDataInput(compressed);
    try {
      int pktGeneration = cdi.readVariable();
      int changed = cdi.readVariable();

      List<Integer> missingItems = null;

      if(changed > 0) {
        if(pktGeneration != generation) {
          return;
        }

        for(int i = 0; i < changed; i++) {
          int dbID = cdi.readVariable();
          int count = cdi.readVariable();
          ItemEntry entry = getItem(dbID);
          if(entry != null) {
            setItemCount(entry, count);
          } else {
            missingItems = addMissingItems(missingItems, dbID);
          }
        }
      } else {
        for(ItemEntry entry : clientItems) {
          entry.count = 0;
        }
        clientItems.clear();
        generation = pktGeneration;

        int count = cdi.readVariable();
        while(count > 0) {
          int dbID = cdi.readUnsignedShort();
          ItemEntry entry = getSimpleItem(dbID);
          entry.count = count;
          clientItems.add(entry);
          count = cdi.readVariable();
        }

        count = cdi.readVariable();
        int dbID = COMPLEX_DBINDEX_START;
        while(count > 0) {
          dbID += cdi.readVariable();
          ItemEntry entry = getItem(dbID);
          if(entry != null) {
            entry.count = count;
            clientItems.add(entry);
          } else {
            missingItems = addMissingItems(missingItems, dbID);
          }
          count = cdi.readVariable();
        }
      }

      if(missingItems != null) {
        PacketHandler.INSTANCE.sendToServer(new PacketRequestMissingItems(te, missingItems));
      }
      clientListNeedsSorting = true;
    } finally {
      cdi.close();
    }
  }

  private void setItemCount(ItemEntry entry, int count) {
    if(entry.count == 0 && count > 0) {
      clientItems.add(entry);
    } else if(entry.count > 0 && count == 0) {
      clientItems.remove(entry);
    }
    entry.count = count;
  }

  private List<Integer> addMissingItems(List<Integer> list, Integer dbId) {
    if(!requestedItems.contains(dbId)) {
      if(list == null) {
        list = new ArrayList<Integer>();
      }
      list.add(dbId);
      requestedItems.add(dbId);
    }
    return list;
  }

  public boolean sortClientItems() {
    boolean res = clientListNeedsSorting;
    clientListNeedsSorting = false;
    return res;
  }

  public int getNumEntries() {
    return clientItems.size();
  }

  public ItemEntry getItemEntry(int index) {
    return clientItems.get(index);
  }

  public ItemStack getItemStack(int index) {
    return getItemEntry(index).makeItemStack();
  }

  @Override
  protected ItemEntry createItemEntry(int dbId, int hash, int itemID, int meta, NBTTagCompound nbt) {
    return new ItemEntry(dbId, hash, itemID, meta, nbt);
  }

  public static class ItemEntry extends ItemEntryBase {
    int count;

    public ItemEntry(int dbID, int hash, int itemID, int meta, NBTTagCompound nbt) {
      super(dbID, hash, itemID, meta, nbt);
    }

    public int getCount() {
      return count;
    }

    public ItemStack makeItemStack() {
      ItemStack stack = new ItemStack(Item.getItemById(itemID), count, meta);
      stack.stackTagCompound = nbt;
      return stack;
    }
  }
}
