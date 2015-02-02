package crazypants.enderio.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

import crazypants.enderio.EnderIO;
import crazypants.enderio.machine.capbank.BlockItemCapBank;
import crazypants.enderio.machine.capbank.CapBankType;
import crazypants.enderio.material.Alloy;

public class UpgradeCapBankRecipeHandler extends TemplateRecipeHandler {

  public class CachedShapedRecipe extends CachedRecipe {
    private final ArrayList<PositionedStack> ingredients;
    private final PositionedStack result;

    public CachedShapedRecipe(ItemStack out, Object ... items) {
      result = new PositionedStack(out, 119, 24);
      ingredients = new ArrayList<PositionedStack>(9);

      for(int y=0 ; y<3 ; y++) {
        for(int x=0 ; x<3 ; x++) {
          if(items[y*3 + x] != null) {
            PositionedStack stack = new PositionedStack(items[y*3 + x], 25 + x*18, 6 + y*18, false);
            stack.setMaxSize(1);
            ingredients.add(stack);
          }
        }
      }
    }

    @Override
    public PositionedStack getResult() {
      return result;
    }

    @Override
    public List<PositionedStack> getIngredients() {
      return getCycledIngredients(cycleticks / 20, ingredients);
    }

    public void computeVisuals() {
      for(PositionedStack p : ingredients) {
        p.generatePermutations();
      }
    }
  }

  @Override
  public void loadUsageRecipes(ItemStack ingredient) {
    CachedShapedRecipe recipe = makeRecipe();
    if(recipe.contains(recipe.ingredients, ingredient)) {
      recipe.setIngredientPermutation(recipe.ingredients, ingredient);
      arecipes.add(recipe);
    }
  }

  @Override
  public void loadCraftingRecipes(String outputId, Object... results) {
    if("crafting".equals(outputId)) {
      addRecipe();
    } else {
      super.loadUsageRecipes(outputId, results);
    }
  }

  @Override
  public void loadCraftingRecipes(ItemStack result) {
    if(isActivatedCapBank(result)) {
      addRecipe();
    }
  }

  public static boolean isActivatedCapBank(ItemStack st) {
    return st != null && st.getItem() == Item.getItemFromBlock(EnderIO.blockCapBank) && CapBankType.getTypeFromMeta(st.getItemDamage()) == CapBankType.ACTIVATED;
  }

  private CachedShapedRecipe makeRecipe() {
    ItemStack enAlloy = new ItemStack(EnderIO.itemAlloy, 1, Alloy.ENERGETIC_ALLOY.ordinal());
    ItemStack capacitor2 = new ItemStack(EnderIO.itemBasicCapacitor, 1, 1);
    ItemStack capBank1 = BlockItemCapBank.createItemStackWithPower(CapBankType.getMetaFromType(CapBankType.SIMPLE), 0);
    ItemStack capBank2 = BlockItemCapBank.createItemStackWithPower(CapBankType.getMetaFromType(CapBankType.ACTIVATED), 0);
    return new CachedShapedRecipe(capBank2,
            enAlloy, enAlloy, enAlloy,
            capBank1, capacitor2, capBank1,
            enAlloy, enAlloy, enAlloy);
  }

  private void addRecipe() {
    CachedShapedRecipe recipe = makeRecipe();
    recipe.computeVisuals();
    arecipes.add(recipe);
  }

  @Override
  public String getGuiTexture() {
    return "textures/gui/container/crafting_table.png";
  }

  @Override
  public String getRecipeName() {
    return StatCollector.translateToLocal("enderio.nei.upgradeCapBank");
  }
}
