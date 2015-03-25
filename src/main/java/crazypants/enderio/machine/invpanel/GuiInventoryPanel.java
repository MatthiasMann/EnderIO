package crazypants.enderio.machine.invpanel;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.gui.IconEIO;
import crazypants.enderio.machine.gui.GuiMachineBase;
import crazypants.gui.GhostSlot;
import crazypants.render.RenderUtil;
import java.awt.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class GuiInventoryPanel extends GuiMachineBase<TileInventoryPanel> {

  private static final Rectangle btnScrollUp   = new Rectangle(216,  27, 16, 8);
  private static final Rectangle btnScrollDown = new Rectangle(216, 109, 16, 8);
  private static final Rectangle thumbArea     = new Rectangle(216,  35, 16, 74);

  private static final int GHOST_COLUMNS = 6;
  private static final int GHOST_ROWS    = 5;

  private boolean scrollUpPressed;
  private boolean scrollDownPressed;
  private int scrollPos;
  private int scrollMax;
  private long scrollLastTime;

  public GuiInventoryPanel(TileInventoryPanel te, Container container) {
    super(te, container);
    redstoneButton.visible = false;
    configB.visible = false;
    scrollLastTime = Minecraft.getSystemTime();

    for(int y = 0; y < GHOST_ROWS; y++) {
      for(int x = 0; x < GHOST_COLUMNS; x++) {
        GhostSlot slot = new GhostSlot();
        slot.x = 109 + x*18;
        slot.y =  28 + y*18;
        ghostSlots.add(slot);
      }
    }
  }

  @Override
  protected void drawGuiContainerBackgroundLayer(float par1, int par2, int par3) {
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    RenderUtil.bindTexture("enderio:textures/gui/inventorypanel.png");
    int sx = guiLeft;
    int sy = guiTop;

    drawTexturedModalRect(sx, sy, 0, 0, xSize, ySize);

    long time = Minecraft.getSystemTime();
    if((time - scrollLastTime) >= 100) {
      scrollLastTime = time;
      doScroll();
    }

    Tessellator tes = Tessellator.instance;
    tes.startDrawingQuads();
    RenderUtil.bindTexture(IconEIO.TEXTURE);
    IconEIO icon;

    icon = scrollUpPressed ? IconEIO.UP_ARROW_ON : IconEIO.UP_ARROW_OFF;
    icon.renderIcon(sx+btnScrollUp.x, sy+btnScrollUp.y);

    icon = scrollDownPressed ? IconEIO.DOWN_ARROW_ON : IconEIO.DOWN_ARROW_OFF;
    icon.renderIcon(sx+btnScrollDown.x, sy+btnScrollDown.y);

    if(scrollMax > 0) {
      icon = IconEIO.VSCROLL_THUMB;
      icon.renderIcon(sx+thumbArea.x, sy+thumbArea.y + (thumbArea.height - icon.height) * scrollPos / scrollMax);
    }

    tes.draw();

    if(getDatabase().sortClientItems()) {
      updateGhostSlots();
    }

    super.drawGuiContainerBackgroundLayer(par1, par2, par3);
  }

  @Override
  protected void drawFakeItemStack(int x, int y, ItemStack stack) {
    FontRenderer font = stack.getItem().getFontRenderer(stack);
    if(font == null) {
      font = fontRendererObj;
    }
    String str = null;
    if(stack.stackSize > 999) {
      str = (stack.stackSize / 1000) + "k";
    }
    itemRender.renderItemAndEffectIntoGUI(font, mc.renderEngine, stack, x, y);
    itemRender.renderItemOverlayIntoGUI(font, mc.renderEngine, stack, x, y, str);
  }

  private InventoryDatabase getDatabase() {
    return getTileEntity().getDatabase();
  }

  private void doScroll() {
    scrollMax = Math.max(0, (getDatabase().getClientItemCount()+GHOST_COLUMNS-1) / GHOST_COLUMNS - GHOST_ROWS);
    if(scrollUpPressed && scrollPos > 0) {
      scrollPos--;
    }
    if(scrollDownPressed) {
      scrollPos++;
    }
    updateGhostSlots();
  }

  private void updateGhostSlots() {
    if(scrollPos > scrollMax) {
      scrollPos = scrollMax;
    }
    
    InventoryDatabase database = getDatabase();
    int index = scrollPos * GHOST_COLUMNS;
    int count = database.getClientItemCount();
    for(int i = 0; i < GHOST_ROWS*GHOST_COLUMNS; i++,index++) {
      GhostSlot slot = ghostSlots.get(i);
      if(index < count) {
        InventoryDatabase.ItemKey key = database.getClientItem(index);
        slot.stack = key.makeItemStack();
      } else {
        slot.stack = null;
      }
    }
  }

  @Override
  protected boolean showRecipeButton() {
    return false;
  }

  @Override
  public int getXSize() {
    return 238;
  }

  @Override
  public int getYSize() {
    return 212;
  }

  @Override
  protected void mouseClicked(int x, int y, int button) {
    super.mouseClicked(x, y, button);

    x -= guiLeft;
    y -= guiTop;

    scrollUpPressed   = btnScrollUp.contains(x, y);
    scrollDownPressed = btnScrollDown.contains(x, y);

    if(scrollUpPressed || scrollDownPressed) {
      scrollLastTime = Minecraft.getSystemTime();
      doScroll();
    }
  }

  @Override
  protected void mouseMovedOrUp(int x, int y, int button) {
    super.mouseMovedOrUp(x, y, button);
    scrollUpPressed   = false;
    scrollDownPressed = false;
  }

  @Override
  protected void mouseClickMove(int x, int y, int button, long time) {
    super.mouseClickMove(x, y, button, time);
  }

}