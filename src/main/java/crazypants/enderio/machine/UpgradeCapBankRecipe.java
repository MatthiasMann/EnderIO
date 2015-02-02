package crazypants.enderio.machine;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.oredict.RecipeSorter;

import crazypants.enderio.EnderIO;
import crazypants.enderio.machine.capbank.BlockItemCapBank;
import crazypants.enderio.machine.capbank.CapBankType;
import crazypants.enderio.material.Alloy;
import crazypants.enderio.power.Capacitors;
import crazypants.enderio.power.PowerHandlerUtil;

public class UpgradeCapBankRecipe implements IRecipe {

  static {
    RecipeSorter.register("EnderIO:upgradeCapBank", UpgradeCapBankRecipe.class, RecipeSorter.Category.SHAPED, "after:minecraft:shaped before:minecraft:shapeless");
  }

  @Override
  public boolean matches(InventoryCrafting ic, World world) {
    for(int i=0 ; i<3 ; i++) {
      if(!isEnergeticAlloy(ic.getStackInRowAndColumn(i, 0))) {
        return false;
      }
      if(!isEnergeticAlloy(ic.getStackInRowAndColumn(i, 2))) {
        return false;
      }
    }
    if(!isBasicCapBank(ic.getStackInRowAndColumn(0, 1))) {
      return false;
    }
    if(!isBasicCapBank(ic.getStackInRowAndColumn(2, 1))) {
      return false;
    }
    return isDoubleLayerCapacitor(ic.getStackInRowAndColumn(1, 1));
  }

  public static boolean isEnergeticAlloy(ItemStack st) {
    return st != null && st.getItem() == EnderIO.itemAlloy && st.getItemDamage() == Alloy.ENERGETIC_ALLOY.ordinal();
  }

  public static boolean isBasicCapBank(ItemStack st) {
    return st != null && st.getItem() == Item.getItemFromBlock(EnderIO.blockCapBank) && CapBankType.getTypeFromMeta(st.getItemDamage()) == CapBankType.SIMPLE;
  }

  public static boolean isDoubleLayerCapacitor(ItemStack st) {
    return st != null && st.getItem() == EnderIO.itemBasicCapacitor && st.getItemDamage() == Capacitors.ACTIVATED_CAPACITOR.ordinal();
  }

  @Override
  public ItemStack getCraftingResult(InventoryCrafting ic) {
    long power = 0;
    ItemStack st = ic.getStackInRowAndColumn(0, 1);
    if(st != null) {
      power += PowerHandlerUtil.getStoredEnergyForItem(st);
    }
    st = ic.getStackInRowAndColumn(2, 1);
    if(st != null) {
      power += PowerHandlerUtil.getStoredEnergyForItem(st);
    }
    return BlockItemCapBank.createItemStackWithPower(CapBankType.getMetaFromType(CapBankType.ACTIVATED),
            (int)Math.min(power, CapBankType.ACTIVATED.getMaxEnergyStored()));
  }

  @Override
  public int getRecipeSize() {
    return 9;
  }

  @Override
  public ItemStack getRecipeOutput() {
    return BlockItemCapBank.createItemStackWithPower(CapBankType.getMetaFromType(CapBankType.ACTIVATED), 0);
  }
}
