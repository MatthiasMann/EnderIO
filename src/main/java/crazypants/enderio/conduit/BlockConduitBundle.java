package crazypants.enderio.conduit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import powercrystals.minefactoryreloaded.api.rednet.IRedNetOmniNode;
import powercrystals.minefactoryreloaded.api.rednet.connectivity.RedNetConnectionType;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.BlockEio;
import crazypants.enderio.EnderIO;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.ModObject;
import crazypants.enderio.api.tool.ITool;
import crazypants.enderio.conduit.geom.CollidableComponent;
import crazypants.enderio.conduit.geom.ConduitConnectorType;
import crazypants.enderio.conduit.gui.ExternalConnectionContainer;
import crazypants.enderio.conduit.gui.GuiExternalConnection;
import crazypants.enderio.conduit.gui.GuiExternalConnectionSelector;
import crazypants.enderio.conduit.gui.PacketFluidFilter;
import crazypants.enderio.conduit.gui.PacketOpenConduitUI;
import crazypants.enderio.conduit.gui.item.PacketExistingItemFilterSnapshot;
import crazypants.enderio.conduit.gui.item.PacketModItemFilter;
import crazypants.enderio.conduit.liquid.PacketFluidLevel;
import crazypants.enderio.conduit.packet.PacketConnectionMode;
import crazypants.enderio.conduit.packet.PacketExtractMode;
import crazypants.enderio.conduit.packet.PacketItemConduitFilter;
import crazypants.enderio.conduit.packet.PacketRedstoneConduitOutputStrength;
import crazypants.enderio.conduit.packet.PacketRedstoneConduitSignalColor;
import crazypants.enderio.conduit.redstone.IRedstoneConduit;
import crazypants.enderio.item.ItemConduitProbe;
import crazypants.enderio.machine.painter.PainterUtil;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.tool.ToolUtil;
import crazypants.render.BoundingBox;
import crazypants.util.IFacade;
import crazypants.util.Util;

@Optional.Interface(iface = "powercrystals.minefactoryreloaded.api.rednet.IRedNetOmniNode", modid = "MineFactoryReloaded")
public class BlockConduitBundle extends BlockEio implements IGuiHandler, IFacade, IRedNetOmniNode {

  private static final String KEY_CONNECTOR_ICON = "enderIO:conduitConnector";
  private static final String KEY_CONNECTOR_ICON_EXTERNAL = "enderIO:conduitConnectorExternal";

  public static BlockConduitBundle create() {

    MinecraftForge.EVENT_BUS.register(ConduitNetworkTickHandler.instance);
    FMLCommonHandler.instance().bus().register(ConduitNetworkTickHandler.instance);

    PacketHandler.INSTANCE.registerMessage(PacketFluidLevel.class, PacketFluidLevel.class, PacketHandler.nextID(), Side.CLIENT);
    PacketHandler.INSTANCE.registerMessage(PacketExtractMode.class, PacketExtractMode.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketConnectionMode.class, PacketConnectionMode.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketItemConduitFilter.class, PacketItemConduitFilter.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketExistingItemFilterSnapshot.class, PacketExistingItemFilterSnapshot.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketModItemFilter.class, PacketModItemFilter.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketFluidFilter.class, PacketFluidFilter.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketRedstoneConduitSignalColor.class, PacketRedstoneConduitSignalColor.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketRedstoneConduitOutputStrength.class, PacketRedstoneConduitOutputStrength.class, PacketHandler.nextID(),
        Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketOpenConduitUI.class, PacketOpenConduitUI.class, PacketHandler.nextID(), Side.SERVER);

    BlockConduitBundle result = new BlockConduitBundle();
    result.init();
    MinecraftForge.EVENT_BUS.register(result);
    return result;
  }

  public static int rendererId = -1;

  private IIcon connectorIcon, connectorIconExternal;

  private IIcon lastRemovedComponetIcon = null;

  private Random rand = new Random();

  protected BlockConduitBundle() {
    super(ModObject.blockConduitBundle.unlocalisedName, TileConduitBundle.class);
    setBlockBounds(0.334f, 0.334f, 0.334f, 0.667f, 0.667f, 0.667f);
    setHardness(1.5f);
    setResistance(10.0f);
    setCreativeTab(null);
  }

  @SideOnly(Side.CLIENT)
  @Override
  public boolean addHitEffects(World world, MovingObjectPosition target,
      EffectRenderer effectRenderer) {
    IIcon tex = null;

    TileConduitBundle cb = (TileConduitBundle)
        world.getTileEntity(target.blockX, target.blockY, target.blockZ);
    if(ConduitUtil.isSolidFacadeRendered(cb, Minecraft.getMinecraft().thePlayer)) {
      if(cb.getFacadeId() != null) {
        tex = cb.getFacadeId().getIcon(target.sideHit,
            cb.getFacadeMetadata());
      }
    } else if(target.hitInfo instanceof CollidableComponent) {
      CollidableComponent cc = (CollidableComponent) target.hitInfo;
      IConduit con = cb.getConduit(cc.conduitType);
      if(con != null) {
        tex = con.getTextureForState(cc);
      }
    }
    if(tex == null) {
      tex = blockIcon;
    }
    lastRemovedComponetIcon = tex;
    addBlockHitEffects(world, effectRenderer, target.blockX, target.blockY,
        target.blockZ, target.sideHit, tex);
    return true;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public boolean addDestroyEffects(World world, int x, int y, int z, int
      meta, EffectRenderer effectRenderer) {
    IIcon tex = lastRemovedComponetIcon;
    byte b0 = 4;
    for (int j1 = 0; j1 < b0; ++j1) {
      for (int k1 = 0; k1 < b0; ++k1) {
        for (int l1 = 0; l1 < b0; ++l1) {
          double d0 = x + (j1 + 0.5D) / b0;
          double d1 = y + (k1 + 0.5D) / b0;
          double d2 = z + (l1 + 0.5D) / b0;
          int i2 = rand.nextInt(6);
          EntityDiggingFX fx = new EntityDiggingFX(world, d0, d1, d2, d0 - x - 0.5D,
              d1 - y - 0.5D, d2 - z - 0.5D, this, i2, 0).applyColourMultiplier(x, y, z);
          fx.setParticleIcon(tex);
          effectRenderer.addEffect(fx);
        }
      }
    }
    return true;

  }

  @SideOnly(Side.CLIENT)
  private void addBlockHitEffects(World world, EffectRenderer effectRenderer,
      int x, int y, int z, int side, IIcon tex) {
    float f = 0.1F;
    double d0 = x + rand.nextDouble() * (getBlockBoundsMaxX() -
        getBlockBoundsMinX() - f * 2.0F) + f + getBlockBoundsMinX();
    double d1 = y + rand.nextDouble() * (getBlockBoundsMaxY() -
        getBlockBoundsMinY() - f * 2.0F) + f + getBlockBoundsMinY();
    double d2 = z + rand.nextDouble() * (getBlockBoundsMaxZ() -
        getBlockBoundsMinZ() - f * 2.0F) + f + getBlockBoundsMinZ();
    if(side == 0) {
      d1 = y + getBlockBoundsMinY() - f;
    } else if(side == 1) {
      d1 = y + getBlockBoundsMaxY() + f;
    } else if(side == 2) {
      d2 = z + getBlockBoundsMinZ() - f;
    } else if(side == 3) {
      d2 = z + getBlockBoundsMaxZ() + f;
    } else if(side == 4) {
      d0 = x + getBlockBoundsMinX() - f;
    } else if(side == 5) {
      d0 = x + getBlockBoundsMaxX() + f;
    }
    EntityDiggingFX digFX = new EntityDiggingFX(world, d0, d1, d2, 0.0D, 0.0D,
        0.0D, this, side, 0);
    digFX.applyColourMultiplier(x, y,
        z).multiplyVelocity(0.2F).multipleParticleScaleBy(0.6F);
    digFX.setParticleIcon(tex);
    effectRenderer.addEffect(digFX);
  }

  @Override
  protected void init() {
    super.init();
    for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
      EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE + dir.ordinal(), this);
    }
    EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_EXTERNAL_CONNECTION_SELECTOR, this);
  }

  @Override
  public ItemStack getPickBlock(MovingObjectPosition target, World world, int
      x, int y, int z) {
    if(target != null && target.hitInfo instanceof CollidableComponent) {
      CollidableComponent cc = (CollidableComponent) target.hitInfo;
      TileConduitBundle bundle = (TileConduitBundle) world.getTileEntity(x,
          y, z);
      IConduit conduit = bundle.getConduit(cc.conduitType);
      if(conduit != null) {
        return conduit.createItem();
      } else if(cc.conduitType == null && bundle.getFacadeId() != null) {
        // use the facde
        ItemStack fac = new ItemStack(EnderIO.itemConduitFacade, 1, 0);
        PainterUtil.setSourceBlock(fac, bundle.getFacadeId(),
            bundle.getFacadeMetadata());
        return fac;
      }
    }
    return null;
  }

  @Override
  public int getDamageValue(World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return 0;
    }
    IConduitBundle bun = (IConduitBundle) te;
    return bun.getFacadeId() != null ? bun.getFacadeMetadata() : 0;
  }

  @Override
  public int quantityDropped(Random r) {
    return 0;
  }

  public IIcon getConnectorIcon(Object data) {
    return data == ConduitConnectorType.EXTERNAL ? connectorIconExternal : connectorIcon;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void registerBlockIcons(IIconRegister IIconRegister) {
    connectorIcon = IIconRegister.registerIcon(KEY_CONNECTOR_ICON);
    connectorIconExternal = IIconRegister.registerIcon(KEY_CONNECTOR_ICON_EXTERNAL);
    blockIcon = connectorIcon;
  }

  @Override
  public boolean isSideSolid(IBlockAccess world, int x, int y, int z,
      ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return false;
    }
    IConduitBundle con = (IConduitBundle) te;
    if(con.getFacadeId() != null) {
      return true;
    }
    return false;
  }

  @Override
  public boolean canBeReplacedByLeaves(IBlockAccess world, int x, int y, int z) {
    return false;
  }

  @Override
  public boolean isOpaqueCube() {
    return false;
  }

  @Override
  public int getRenderType() {
    return rendererId;
  }

  @Override
  public boolean renderAsNormalBlock() {
    return false;
  }

  @Override
  public int getLightOpacity(IBlockAccess world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return super.getLightOpacity(world, x, y, z);
    }
    IConduitBundle con = (IConduitBundle) te;
    return con.getLightOpacity();
  }

  @Override
  public int getLightValue(IBlockAccess world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return super.getLightValue(world, x, y, z);
    }
    IConduitBundle con = (IConduitBundle) te;
    if(con.getFacadeId() != null) {
      return 0;
    }
    Collection<IConduit> conduits = con.getConduits();
    int result = 0;
    for (IConduit conduit : conduits) {
      result += conduit.getLightValue();
    }
    return result;
  }
  
  @SubscribeEvent
  public void onBreakSpeed(BreakSpeed event) {
    if (event.block == this && event.entityPlayer.getCurrentEquippedItem() == null) {
      event.newSpeed *= 3;
    }
  }
  
  @Override
  public int getRenderBlockPass() {
    return 1;
  }
  
  public static volatile int theRenderPass;
  
  @Override
  public boolean canRenderInPass(int pass) {
    theRenderPass = pass;
    return pass == 0 || pass == 1;
  }

  @Override
  public int isProvidingStrongPower(IBlockAccess world, int x, int y, int z,
      int par5) {
    IRedstoneConduit con = getRedstoneConduit(world, x, y, z);
    if(con == null) {
      return 0;
    }
    return con.isProvidingStrongPower(ForgeDirection.getOrientation(par5));
  }

  @Override
  public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z,
      int par5) {
    IRedstoneConduit con = getRedstoneConduit(world, x, y, z);
    if(con == null) {
      return 0;
    }

    return con.isProvidingWeakPower(ForgeDirection.getOrientation(par5));
  }

  @Override
  public boolean canProvidePower() {
    return true;
  }

  @Override
  public boolean removedByPlayer(World world, EntityPlayer player, int x,
      int y, int z) {
    IConduitBundle te = (IConduitBundle) world.getTileEntity(x, y, z);
    if(te == null) {
      return true;
    }

    boolean breakBlock = true;
    List<ItemStack> drop = new ArrayList<ItemStack>();
    if(ConduitUtil.isSolidFacadeRendered(te, player)) {
      breakBlock = false;
      ItemStack fac = new ItemStack(EnderIO.itemConduitFacade, 1, 0);
      PainterUtil.setSourceBlock(fac, te.getFacadeId(), te.getFacadeMetadata());
      drop.add(fac);
      te.setFacadeId(null);
      te.setFacadeMetadata(0);
    }

    if(breakBlock) {
      List<RaytraceResult> results = doRayTraceAll(world, x, y, z, player);
      RaytraceResult.sort(Util.getEyePosition(player), results);
      for (RaytraceResult rt : results) {
        if(breakConduit(te, drop, rt, player)) {
          break;
        }
      }
    }

    breakBlock = te.getConduits().isEmpty() && !te.hasFacade();

    if(!world.isRemote && !player.capabilities.isCreativeMode) {
      for (ItemStack st : drop) {
        Util.dropItems(world, st, x, y, z, false);
      }
    }

    if(!breakBlock) {
      world.markBlockForUpdate(x, y, z);
      return false;
    }
    world.setBlockToAir(x, y, z);
    return true;
  }

  private boolean breakConduit(IConduitBundle te, List<ItemStack> drop, RaytraceResult rt, EntityPlayer player) {
    if(rt == null || rt.component == null) {
      return false;
    }
    Class<? extends IConduit> type = rt.component.conduitType;
    if(!ConduitUtil.renderConduit(player, type)) {
      return false;
    }

    if(type == null) {
      // broke a conector so drop any conduits with no connections as there
      // is no other way to remove these
      List<IConduit> cons = new ArrayList<IConduit>(te.getConduits());
      boolean droppedUnconected = false;
      for (IConduit con : cons) {
        if(con.getConduitConnections().isEmpty() &&
            con.getExternalConnections().isEmpty() && ConduitUtil.renderConduit(player, con)) {
          te.removeConduit(con);
          drop.addAll(con.getDrops());
          droppedUnconected = true;
        }
      }
      // If there isn't, then drop em all
      if(!droppedUnconected) {
        for (IConduit con : cons) {
          if(ConduitUtil.renderConduit(player, con)) {
            te.removeConduit(con);
            drop.addAll(con.getDrops());
          }
        }
      }
    } else {
      IConduit con = te.getConduit(type);
      if(con != null) {
        te.removeConduit(con);
        drop.addAll(con.getDrops());
      }
    }

    return true;
  }

  @Override
  public void breakBlock(World world, int x, int y, int z, Block par5, int par6) {

    TileEntity tile = world.getTileEntity(x, y, z);
    if(!(tile instanceof IConduitBundle)) {
      return;
    }
    IConduitBundle te = (IConduitBundle) tile;
    if(te != null) {
      te.onBlockRemoved();
    }
    world.removeTileEntity(x, y, z);
  }

  @Override
  public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {
    ItemStack equipped = player.getCurrentEquippedItem();
    if(!player.isSneaking() || equipped == null || equipped.getItem() != EnderIO.itemYetaWench) {
      return;
    }
    ConduitUtil.openConduitGui(world, x, y, z, player);
  }

  @Override
  public boolean onBlockActivated(World world, int x, int y, int z,
      EntityPlayer player, int par6, float par7, float par8, float par9) {

    IConduitBundle bundle = (IConduitBundle) world.getTileEntity(x, y, z);
    if(bundle == null) {
      return false;
    }

    ItemStack stack = player.getCurrentEquippedItem();
    if(stack != null && stack.getItem() == EnderIO.itemConduitFacade && !bundle.hasFacade()) {
      //add facade
      return handleFacadeClick(world, x, y, z, player, bundle, stack);

    } else if(ConduitUtil.isConduitEquipped(player)) {
      // Add conduit
      if(player.isSneaking()) {
        return false;
      }
      if(handleConduitClick(world, x, y, z, player, bundle, stack)) {
        return true;
      }

    } else if(ConduitUtil.isProbeEquipped(player)) {
      //Handle copy / paste of settings
      if(handleConduitProbeClick(world, x, y, z, player, bundle, stack)) {
        return true;
      }
    } else if(ToolUtil.isToolEquipped(player) && player.isSneaking()) {
      // Break conduit with tool
      if(handleWrenchClick(world, x, y, z, player)) {
        return true;
      }
    }

    // Check conduit defined actions
    RaytraceResult closest = doRayTrace(world, x, y, z, player);
    List<RaytraceResult> all = null;
    if(closest != null) {
      all = doRayTraceAll(world, x, y, z, player);
    }

    if(closest != null && closest.component != null && closest.component.data instanceof
        ConduitConnectorType) {

      ConduitConnectorType conType = (ConduitConnectorType) closest.component.data;
      if(conType == ConduitConnectorType.INTERNAL) {
        boolean result = false;
        // if its a connector pass the event on to all conduits
        for (IConduit con : bundle.getConduits()) {
          if(ConduitUtil.renderConduit(player, con.getCollidableType())
              && con.onBlockActivated(player, getHitForConduitType(all, con.getCollidableType()), all)) {
            bundle.getEntity().markDirty();
            result = true;
          }

        }
        if(result) {
          return true;
        }
      } else {
        if(!world.isRemote) {
          player.openGui(EnderIO.instance, GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE + closest.component.dir.ordinal(), world, x, y, z);
        }
        return true;
      }
    }

    if(closest == null || closest.component == null || closest.component.conduitType ==
        null && all == null) {
      // Nothing of interest hit
      return false;
    }

    // Conduit specific actions
    if(all != null) {
      RaytraceResult.sort(Util.getEyePosition(player), all);
      for (RaytraceResult rr : all) {
        if(ConduitUtil.renderConduit(player, rr.component.conduitType) && !(rr.component.data instanceof
            ConduitConnectorType)) {

          IConduit con = bundle.getConduit(rr.component.conduitType);
          if(con != null && con.onBlockActivated(player,
              rr, all)) {
            bundle.getEntity().markDirty();
            return true;
          }
        }
      }
    } else {
      IConduit closestConduit = bundle.getConduit(closest.component.conduitType);
      if(closestConduit != null && ConduitUtil.renderConduit(player, closestConduit) && closestConduit.onBlockActivated(player,
          closest, all)) {
        bundle.getEntity().markDirty();
        return true;
      }
    }
    return false;

  }

  private boolean handleWrenchClick(World world, int x, int y, int z, EntityPlayer player) {
    ITool tool = ToolUtil.getEquippedTool(player);
    if(tool != null) {
      if(tool.canUse(player.getCurrentEquippedItem(), player, x, y, z)) {
        if(!world.isRemote) {
          removedByPlayer(world, player, x, y, z);
          tool.used(player.getCurrentEquippedItem(), player, x, y, z);
        }
        return true;
      }
    }
    return false;
  }

  private boolean handleConduitProbeClick(World world, int x, int y, int z, EntityPlayer player, IConduitBundle bundle, ItemStack stack) {
    if(stack.getItemDamage() != 1) {
      return false; //not in copy paste mode
    }
    RaytraceResult rr = doRayTrace(world, x, y, z, player);
    if(rr == null || rr.component == null) {
      return false;
    }
    return ItemConduitProbe.copyPasteSettings(player, stack, bundle, rr.component.dir);
  }

  private boolean handleConduitClick(World world, int x, int y, int z, EntityPlayer player, IConduitBundle bundle, ItemStack stack) {
    IConduitItem equipped = (IConduitItem) stack.getItem();
    if(!bundle.hasType(equipped.getBaseConduitType())) {
      bundle.addConduit(equipped.createConduit(stack, player));
      if(!player.capabilities.isCreativeMode) {
        world.playSoundEffect(x + 0.5F, y + 0.5F, z + 0.5F, stepSound.getStepResourcePath(),
            (stepSound.getVolume() + 1.0F) / 2.0F, stepSound.getPitch() * 0.8F);
        player.getCurrentEquippedItem().stackSize--;
      }
      return true;
    }
    return false;
  }

  private boolean handleFacadeClick(World world, int x, int y, int z, EntityPlayer player, IConduitBundle bundle, ItemStack stack) {
    // Add facade
    if(player.isSneaking()) {
      return false;
    }

    if(PainterUtil.getSourceBlock(player.getCurrentEquippedItem()) == null) {
      return false;
    }

    bundle.setFacadeId(PainterUtil.getSourceBlock(player.getCurrentEquippedItem()));
    bundle.setFacadeMetadata(PainterUtil.getSourceBlockMetadata(player.getCurrentEquippedItem()));
    if(!player.capabilities.isCreativeMode) {
      stack.stackSize--;
    }
    world.markBlockForUpdate(x, y, z);
    bundle.getEntity().markDirty();
    return true;
  }

  @Override
  public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
    if(id == GuiHandler.GUI_ID_EXTERNAL_CONNECTION_SELECTOR) {
      return null;
    }
    // The server needs the container as it manages the adding and removing of
    // items, which are then sent to the client for display
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof IConduitBundle) {
      return new ExternalConnectionContainer(player.inventory, (IConduitBundle) te, ForgeDirection.values()[id - GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE]);
    }
    return null;
  }

  @Override
  public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof IConduitBundle) {
      if(id == GuiHandler.GUI_ID_EXTERNAL_CONNECTION_SELECTOR) {
        return new GuiExternalConnectionSelector((IConduitBundle) te);
      }
      return new GuiExternalConnection(player.inventory, (IConduitBundle) te, ForgeDirection.values()[id - GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE]);
    }
    return null;
  }

  private RaytraceResult getHitForConduitType(List<RaytraceResult> all, Class<? extends IConduit> collidableType) {
    for (RaytraceResult rr : all) {
      if(rr.component != null && rr.component.conduitType == collidableType) {
        return rr;
      }
    }
    return null;
  }

  @Override
  public void onNeighborBlockChange(World world, int x, int y, int z, Block
      blockId) {
    TileEntity tile = world.getTileEntity(x, y, z);
    if((tile instanceof IConduitBundle)) {
      ((IConduitBundle) tile).onNeighborBlockChange(blockId);
    }
  }

  @Override
  public void onNeighborChange(IBlockAccess world, int x, int y, int z, int tileX, int tileY, int tileZ) {
    TileEntity conduit = world.getTileEntity(x, y, z);
    if(conduit instanceof IConduitBundle) {
      ((IConduitBundle) conduit).onNeighborChange(world, x, y, z, tileX, tileY, tileZ);
    }
  }

  @Override
  public void addCollisionBoxesToList(World world, int x, int y, int z,
      AxisAlignedBB axisalignedbb, @SuppressWarnings("rawtypes") List arraylist,
      Entity par7Entity) {

    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return;
    }
    IConduitBundle con = (IConduitBundle) te;
    if(con.getFacadeId() != null) {
      setBlockBounds(0, 0, 0, 1, 1, 1);
      super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, arraylist,
          par7Entity);
    } else {

      Collection<CollidableComponent> bounds = con.getCollidableComponents();
      for (CollidableComponent bnd : bounds) {
        setBlockBounds(bnd.bound.minX, bnd.bound.minY, bnd.bound.minZ,
            bnd.bound.maxX, bnd.bound.maxY, bnd.bound.maxZ);
        super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, arraylist,
            par7Entity);
      }

      if(con.getConduits().isEmpty()) { // just in case
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        super.addCollisionBoxesToList(world, x, y, z, axisalignedbb, arraylist,
            par7Entity);
      }
    }

    setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);

  }

  @Override
  public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int
      y, int z) {

    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return null;
    }
    IConduitBundle con = (IConduitBundle) te;

    BoundingBox minBB = new BoundingBox(1, 1, 1, 0, 0, 0);
    if(!ConduitUtil.isSolidFacadeRendered(con, EnderIO.proxy.getClientPlayer())) {

      Collection<CollidableComponent> bounds = con.getCollidableComponents();
      for (CollidableComponent bnd : bounds) {
        minBB = minBB.expandBy(bnd.bound);
      }

    } else {
      minBB = new BoundingBox(0, 0, 0, 1, 1, 1);
    }

    if(!minBB.isValid()) {
      minBB = new BoundingBox(0, 0, 0, 1, 1, 1);
    }

    return AxisAlignedBB.getBoundingBox(x + minBB.minX, y + minBB.minY,
        z + minBB.minZ, x + minBB.maxX, y + minBB.maxY, z +
            minBB.maxZ);
  }

  @Override
  public MovingObjectPosition collisionRayTrace(World world, int x, int y,
      int z, Vec3 origin, Vec3 direction) {
    RaytraceResult raytraceResult = doRayTrace(world, x, y, z, origin,
        direction, null);
    if(raytraceResult == null) {
      return null;
    }

    if(raytraceResult.movingObjectPosition != null) {
      raytraceResult.movingObjectPosition.hitInfo = raytraceResult.component;

    }
    return raytraceResult.movingObjectPosition;
  }

  public RaytraceResult doRayTrace(World world, int x, int y, int z,
      EntityPlayer entityPlayer) {
    List<RaytraceResult> allHits = doRayTraceAll(world, x, y, z, entityPlayer);
    if(allHits == null) {
      return null;
    }
    Vec3 origin = Util.getEyePosition(entityPlayer);
    return RaytraceResult.getClosestHit(origin, allHits);
  }

  public List<RaytraceResult> doRayTraceAll(World world, int x, int y, int z,
      EntityPlayer entityPlayer) {
    double pitch = Math.toRadians(entityPlayer.rotationPitch);
    double yaw = Math.toRadians(entityPlayer.rotationYaw);

    double dirX = -Math.sin(yaw) * Math.cos(pitch);
    double dirY = -Math.sin(pitch);
    double dirZ = Math.cos(yaw) * Math.cos(pitch);

    double reachDistance = EnderIO.proxy.getReachDistanceForPlayer(entityPlayer);

    Vec3 origin = Util.getEyePosition(entityPlayer);
    Vec3 direction = origin.addVector(dirX * reachDistance, dirY *
        reachDistance, dirZ * reachDistance);
    return doRayTraceAll(world, x, y, z, origin, direction,
        entityPlayer);
  }

  private RaytraceResult doRayTrace(World world, int x, int y, int z, Vec3 origin, Vec3 direction, EntityPlayer entityPlayer) {
    List<RaytraceResult> allHits = doRayTraceAll(world, x, y, z, origin, direction, entityPlayer);
    if(allHits == null) {
      return null;
    }
    return RaytraceResult.getClosestHit(origin, allHits);
  }

  protected List<RaytraceResult> doRayTraceAll(World world, int x, int y, int z, Vec3
      origin, Vec3 direction, EntityPlayer player) {

    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return null;
    }
    IConduitBundle bundle = (IConduitBundle) te;
    List<RaytraceResult> hits = new ArrayList<RaytraceResult>();
    
    if (player == null) {
      player = EnderIO.proxy.getClientPlayer();
    }

    if(ConduitUtil.isSolidFacadeRendered(bundle, player)) {
      setBlockBounds(0, 0, 0, 1, 1, 1);
      MovingObjectPosition hitPos = super.collisionRayTrace(world, x, y, z,
          origin, direction);
      if(hitPos != null) {
        hits.add(new RaytraceResult(new CollidableComponent(null,
            BoundingBox.UNIT_CUBE, ForgeDirection.UNKNOWN, null), hitPos));
      }
    } else {

      Collection<CollidableComponent> components =
          new ArrayList<CollidableComponent>(bundle.getCollidableComponents());
      for (CollidableComponent component : components) {
        setBlockBounds(component.bound.minX, component.bound.minY,
            component.bound.minZ, component.bound.maxX, component.bound.maxY,
            component.bound.maxZ);
        MovingObjectPosition hitPos = super.collisionRayTrace(world, x, y, z,
            origin, direction);
        if(hitPos != null) {
          hits.add(new RaytraceResult(component, hitPos));
        }
      }

      // safety to prevent unbreakable empty bundles in case of a bug
      if(bundle.getConduits().isEmpty() && !ConduitUtil.isFacadeHidden(bundle,
          player)) {
        setBlockBounds(0, 0, 0, 1, 1, 1);
        MovingObjectPosition hitPos = super.collisionRayTrace(world, x, y, z,
            origin, direction);
        if(hitPos != null) {
          hits.add(new RaytraceResult(null, hitPos));
        }
      }
    }

    setBlockBounds(0, 0, 0, 1, 1, 1);

    return hits;
  }

  @Override
  public int getFacadeMetadata(IBlockAccess world, int x, int y, int z, int side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return 0;
    }
    IConduitBundle cb = (IConduitBundle) te;
    return cb.getFacadeMetadata();
  }

  @Override
  public Block getFacade(IBlockAccess world, int x, int y, int z, int side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return this;
    }
    IConduitBundle cb = (IConduitBundle) te;
    Block res = cb.getFacadeId();
    if(res == null) {
      return this;
    }
    return res;
  }

  @Override
  public void onInputsChanged(World world, int x, int y, int z, ForgeDirection side, int[] inputValues) {
    IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
    if(conduit == null) {
      return;
    }

    conduit.onInputsChanged(world, x, y, z, side, inputValues);
  }

  @Override
  public void onInputChanged(World world, int x, int y, int z, ForgeDirection side, int inputValue) {
    // Unused because only called in "Single" mode.
  }

  @Override
  public int[] getOutputValues(World world, int x, int y, int z, ForgeDirection side) {
    IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
    if(conduit == null) {
      return null;
    }

    return conduit.getOutputValues(world, x, y, z, side);
  }

  @Override
  public int getOutputValue(World world, int x, int y, int z, ForgeDirection side, int subnet) {
    IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
    if(conduit == null) {
      return 0;
    }

    return conduit.getOutputValue(world, x, y, z, side, subnet);
  }

  @Override
  @Optional.Method(modid = "MineFactoryReloaded")
  public RedNetConnectionType getConnectionType(World world, int x, int y, int z, ForgeDirection side) {
    IRedstoneConduit conduit = getRedstoneConduit(world, x, y, z);
    if(conduit == null) {
      return RedNetConnectionType.None;
    }
    return conduit.canConnectToExternal(side, false) ? RedNetConnectionType.CableAll : RedNetConnectionType.None;
  }

  private static IRedstoneConduit getRedstoneConduit(IBlockAccess world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(!(te instanceof IConduitBundle)) {
      return null;
    }
    IConduitBundle bundle = (IConduitBundle) te;
    return bundle.getConduit(IRedstoneConduit.class);
  }
}
