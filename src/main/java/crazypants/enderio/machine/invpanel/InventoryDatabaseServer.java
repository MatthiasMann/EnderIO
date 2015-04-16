package crazypants.enderio.machine.invpanel;

import crazypants.enderio.conduit.item.NetworkedInventory;
import crazypants.enderio.network.CompressedDataInput;
import crazypants.enderio.network.CompressedDataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import powercrystals.minefactoryreloaded.api.IDeepStorageUnit;

public class InventoryDatabaseServer extends InventoryDatabase<InventoryDatabaseServer.ItemEntry> {

  private AbstractInventoryKey[] inventories;
  private int currentInventory;
  private ChangeLog changeLog;

  public InventoryDatabaseServer() {
  }

  public void addChangeLog(ChangeLog cl) {
    if(changeLog == null) {
      changeLog = cl;
    } else if(changeLog instanceof ChangeLogList) {
      ((ChangeLogList)changeLog).add(cl);
    } else if(changeLog != cl) {
      changeLog = new ChangeLogList(changeLog, cl);
    }
  }

  public void removeChangeLog(ChangeLog cl) {
    if(changeLog == cl) {
      changeLog = null;
    } else if(changeLog instanceof ChangeLogList) {
      changeLog = ((ChangeLogList)changeLog).remove(cl);
    }
  }

  public List<ItemEntry> decompressMissingItems(byte[] compressed) throws IOException {
    CompressedDataInput cdi = new CompressedDataInput(compressed);
    try {
      int numIDs = cdi.readVariable();
      ArrayList<InventoryDatabaseServer.ItemEntry> items = new ArrayList<InventoryDatabaseServer.ItemEntry>(numIDs);
      for(int i = 0; i < numIDs; i++) {
        int dbIndex = cdi.readVariable();
        if(dbIndex < complexItems.size()) {
          InventoryDatabaseServer.ItemEntry entry = complexItems.get(dbIndex);
          items.add(entry);
        }
      }
      return items;
    } finally {
      cdi.close();
    }
  }

  public byte[] compressItemInfo(List<ItemEntry> items) throws IOException{
    CompressedDataOutput cdo = new CompressedDataOutput();
    try {
      int count = items.size();
      cdo.writeVariable(generation);
      cdo.writeVariable(count);
      for(ItemEntry entry : items) {
        assert entry.dbID >= COMPLEX_DBINDEX_START;
        int code = (entry.dbID - COMPLEX_DBINDEX_START) << 1;
        if(entry.nbt != null) {
          code |= 1;
        }
        cdo.writeVariable(code);
        cdo.writeVariable(entry.itemID);
        cdo.writeVariable(entry.meta);
        if(entry.nbt != null) {
          CompressedStreamTools.write(entry.nbt, cdo);
        }
        cdo.writeVariable(entry.countItems(this));
      }
      return cdo.getCompressed();
    } finally {
      cdo.close();
    }
  }

  public byte[] compressItemList() throws IOException {
    CompressedDataOutput cdo = new CompressedDataOutput();
    try {
      cdo.writeVariable(generation);
      cdo.writeByte(0);
      for(Map.Entry<Integer, ItemEntry> entry : simpleRegsitry.entrySet()) {
        int count = entry.getValue().countItems(this);
        if(count > 0) {
          cdo.writeVariable(count);
          cdo.writeShort(entry.getKey());
        }
      }
      cdo.writeByte(0);
      int prevID = COMPLEX_DBINDEX_START;
      for(ItemEntry entry : complexItems) {
        if(entry != null) {
          int count = entry.countItems(this);
          if(count > 0) {
            cdo.writeVariable(count);
            cdo.writeVariable(entry.dbID - prevID);
            prevID = entry.dbID;
          }
        }
      }
      cdo.writeByte(0);
      return cdo.getCompressed();
    } finally {
      cdo.close();
    }
  }

  public byte[] compressChangedItems(Collection<ItemEntry> items) throws IOException {
    CompressedDataOutput cdo = new CompressedDataOutput();
    try {
      cdo.writeVariable(generation);
      cdo.writeVariable(items.size());
      for(ItemEntry entry : items) {
        cdo.writeVariable(entry.dbID);
        cdo.writeVariable(entry.countItems(this));
      }
      return cdo.getCompressed();
    } finally {
      cdo.close();
    }
  }

  public void setNetworkSources(List<NetworkedInventory> sources) {
    simpleRegsitry.clear();
    complexRegistry.clear();
    complexItems.clear();
    generation++;
    currentInventory = 0;

    if(sources == null || sources.isEmpty()) {
      this.inventories = null;
    } else {
      this.inventories = new AbstractInventoryKey[sources.size()];
      for(int i=0; i<sources.size(); i++) {
        NetworkedInventory ni = sources.get(i);
        if(ni.getInventory() instanceof IDeepStorageUnit) {
          inventories[i] = new DSUKey((IDeepStorageUnit) ni.getInventory());
        } else {
          inventories[i] = new InventoryKey(ni);
        }
      }
    }

    if(changeLog != null) {
      changeLog.databaseReset();
    }
  }

  public void scanNextInventory() {
    if(inventories == null) {
      return;
    }

    AbstractInventoryKey inv = inventories[currentInventory];
    inv.scanInventory(this, currentInventory);

    currentInventory = (currentInventory+1) % inventories.length;
  }

  void entryChanged(ItemEntry entry) {
    if(changeLog != null) {
      changeLog.entryChanged(entry);
    }
  }

  public void sendChangeLogs() {
    if(changeLog != null) {
      changeLog.sendChangeLog();
    }
  }

  @Override
  protected ItemEntry createItemEntry(int dbId, int hash, int itemID, int meta, NBTTagCompound nbt) {
    return new ItemEntry(dbId, hash, itemID, meta, nbt);
  }

  public static class ItemEntry extends ItemEntryBase {
    private final HashSet<Integer> slots = new HashSet<Integer>();

    public ItemEntry(int dbID, int hash, int itemID, int meta, NBTTagCompound nbt) {
      super(dbID, hash, itemID, meta, nbt);
    }

    static int encodeNISlot(int niIndex, int slot) {
      return (niIndex << 12) | slot;
    }

    void addSlot(int niIndex, int slot) {
      slots.add(encodeNISlot(niIndex, slot));
    }

    void removeSlot(int niIndex, int slot) {
      slots.remove(encodeNISlot(niIndex, slot));
    }

    int countItems(InventoryDatabaseServer db) {
      int count = 0;
      for(Integer idx : slots) {
        AbstractInventoryKey key = db.inventories[idx >> 12];
        count += key.itemCounts[idx & 4095];
      }
      return count;
    }

    int extractItems(InventoryDatabaseServer db, int count) {
      int extracted = 0;
      Integer[] copy = slots.toArray(new Integer[slots.size()]);
      for(Integer idx : copy) {
        int niIndex = idx >> 12;
        int slotIndex = idx & 4095;
        AbstractInventoryKey key = db.inventories[niIndex];
        int amount = key.extractItem(db, this, slotIndex, niIndex, count);
        count -= amount;
        extracted += amount;
      }
      return extracted;
    }
  }

  static abstract class AbstractInventoryKey {
    static final ItemEntry[] NO_SLOTS = new ItemEntry[0];

    ItemEntry[] slotItems = NO_SLOTS;
    int[] itemCounts;

    protected void setEmpty(InventoryDatabaseServer db, int niIndex) {
      if(slotItems.length != 0) {
        reset(db, 0, niIndex);
      }
    }

    protected void reset(InventoryDatabaseServer db, int count, int niIndex) {
      for(int slot=0; slot<slotItems.length; slot++) {
        ItemEntry key = slotItems[slot];
        if(key != null) {
          key.removeSlot(niIndex, slot);
          db.entryChanged(key);
        }
      }

      slotItems = new ItemEntry[count];
      itemCounts = new int[count];
    }

    protected void updateSlot(InventoryDatabaseServer db, int slot, int niIndex, ItemStack stack) {
      ItemEntry current = slotItems[slot];
      if(stack == null || stack.stackSize <= 0) {
        if(current != null) {
          slotItems[slot] = null;
          itemCounts[slot] = 0;
          current.removeSlot(niIndex, slot);
          db.entryChanged(current);
        }
      } else {
        ItemEntry key = db.lookupItem(stack, current, true);
        if(key != current) {
          slotItems[slot] = key;
          itemCounts[slot] = stack.stackSize;
          key.addSlot(niIndex, slot);
          db.entryChanged(key);
          if(current != null) {
            current.removeSlot(niIndex, slot);
            db.entryChanged(current);
          }
        } else if(itemCounts[slot] != stack.stackSize) {
          itemCounts[slot] = stack.stackSize;
          db.entryChanged(current);
        }
      }
    }

    protected void updateCount(InventoryDatabaseServer db, int slot, int niIndex, ItemEntry entry, int count) {
      if(itemCounts[slot] != count) {
        itemCounts[slot] = count;
        if(count == 0) {
          slotItems[slot] = null;
          entry.removeSlot(niIndex, slot);
        }
        db.entryChanged(entry);
      }
    }

    abstract void scanInventory(InventoryDatabaseServer db, int niIndex);
    abstract int extractItem(InventoryDatabaseServer db, ItemEntry entry, int slot, int niIndex, int count);
  }

  static class InventoryKey extends AbstractInventoryKey {

    final NetworkedInventory ni;

    InventoryKey(NetworkedInventory ni) {
      this.ni = ni;
    }

    @Override
    void scanInventory(InventoryDatabaseServer db, int niIndex) {
      ISidedInventory inv = ni.getInventoryRecheck();
      int side = ni.getInventorySide();
      int[] slotIndices = inv.getAccessibleSlotsFromSide(side);
      if(slotIndices == null || slotIndices.length == 0) {
        setEmpty(db, niIndex);
        return;
      }

      int count = slotIndices.length;
      if(count != slotItems.length) {
        reset(db, count, niIndex);
      }

      for(int slot=0; slot<count; slot++) {
        int invSlot = slotIndices[slot];
        ItemStack stack = inv.getStackInSlot(invSlot);
        if(stack != null && !inv.canExtractItem(invSlot, stack, side)) {
          stack = null;
        }
        updateSlot(db, slot, niIndex, stack);
      }
    }

    @Override
    public int extractItem(InventoryDatabaseServer db, ItemEntry entry, int slot, int niIndex, int count) {
      ISidedInventory inv = ni.getInventoryRecheck();
      int side = ni.getInventorySide();
      int[] slotIndices = inv.getAccessibleSlotsFromSide(side);
      if(slotIndices == null || slot >= slotIndices.length) {
        return 0;
      }

      int invSlot = slotIndices[slot];
      ItemStack stack = inv.getStackInSlot(invSlot);
      if(stack == null || !inv.canExtractItem(invSlot, stack, side)) {
        return 0;
      }

      if(db.lookupItem(stack, entry, false) != entry) {
        return 0;
      }

      int remaining = stack.stackSize;
      if(count > remaining) {
        count = remaining;
      }

      ni.itemExtracted(invSlot, count);
      remaining -= count;

      updateCount(db, slot, niIndex, entry, remaining);

      System.out.println("result " + count);
      return count;
    }
  }

  static class DSUKey extends AbstractInventoryKey {
    private final IDeepStorageUnit dsu;

    DSUKey(IDeepStorageUnit dsu) {
      this.dsu = dsu;
    }

    @Override
    public void scanInventory(InventoryDatabaseServer db, int niIndex) {
      if(slotItems.length != 1) {
        reset(db, 1, niIndex);
      }
      ItemStack stack = dsu.getStoredItemType();
      updateSlot(db, 0, niIndex, stack);
    }

    @Override
    public int extractItem(InventoryDatabaseServer db, ItemEntry entry, int slot, int niIndex, int count) {
      if(slotItems.length != 1) {
        return 0;
      }

      ItemStack stack = dsu.getStoredItemType();
      if(db.lookupItem(stack, entry, false) != entry) {
        return 0;
      }

      int remaining = stack.stackSize;
      if(count > remaining) {
        count = remaining;
      }

      remaining -= count;
      dsu.setStoredItemCount(remaining);

      updateCount(db, slot, niIndex, entry, remaining);
      return count;
    }
  }

  public interface ChangeLog {
    void entryChanged(ItemEntry entry);
    void databaseReset();
    void sendChangeLog();
  }

  static final class ChangeLogList implements ChangeLog {
    final ArrayList<ChangeLog> clList;

    public ChangeLogList(ChangeLog cl0, ChangeLog cl1) {
       clList = new ArrayList<InventoryDatabaseServer.ChangeLog>(2);
       clList.add(cl0);
       clList.add(cl1);
    }

    @Override
    public void entryChanged(ItemEntry entry) {
      for(ChangeLog cl : clList) {
        cl.entryChanged(entry);
      }
    }

    @Override
    public void databaseReset() {
      for(ChangeLog cl : clList) {
        cl.databaseReset();
      }
    }

    @Override
    public void sendChangeLog() {
      for(ChangeLog cl : clList) {
        cl.sendChangeLog();
      }
    }

    ChangeLog remove(ChangeLog cl) {
      clList.remove(cl);
      if(clList.size() == 1) {
        return clList.get(0);
      }
      return this;
    }

    void add(ChangeLog cl) {
      if(!clList.contains(cl)) {
        clList.add(cl);
      }
    }
  }
}