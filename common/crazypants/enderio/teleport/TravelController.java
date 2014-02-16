package crazypants.enderio.teleport;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatMessageComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.PacketDispatcher;
import crazypants.enderio.EnderIO;
import crazypants.enderio.ModObject;
import crazypants.util.BlockCoord;
import crazypants.util.Lang;
import crazypants.util.Util;
import crazypants.vecmath.VecmathUtil;
import crazypants.vecmath.Vector3d;
import crazypants.vecmath.Vector4d;

public class TravelController implements ITickHandler {

  public static final TravelController instance = new TravelController();

  private Random rand = new Random();

  private boolean wasJumping = false;

  private boolean showTargets = false;

  BlockCoord onBlockCoord;

  BlockCoord selectedCoord;

  private final Set<BlockCoord> candidates = new HashSet<BlockCoord>();

  private boolean selectionEnabled = true;

  private TravelController() {
  }

  public boolean showTargets() {
    return showTargets && selectionEnabled;
  }

  public void setSelectionEnabled(boolean b) {
    selectionEnabled = b;
    if(!selectionEnabled) {
      candidates.clear();
    }
  }

  public boolean isBlockSelected(BlockCoord coord) {
    if(coord == null) {
      return false;
    }
    return coord.equals(selectedCoord);
  }

  public void addCandidate(BlockCoord coord) {
    candidates.add(coord);
  }

  public int getMaxTravelDistanceSq() {
    return TravelSource.getMaxDistanceSq();
  }

  public boolean isTargetEnderIO() {
    if(selectedCoord == null) {
      return false;
    }
    return EnderIO.instance.proxy.getClientPlayer().worldObj.getBlockId(selectedCoord.x, selectedCoord.y, selectedCoord.z) == ModObject.blockEnderIo.actualId;
  }

  @Override
  public void tickStart(EnumSet<TickType> type, Object... tickData) {
    if(type.contains(TickType.CLIENT)) {
      EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
      if(player == null) {
        return;
      }
      onBlockCoord = getActiveTravelBlock(player);
      boolean onBlock = onBlockCoord != null;
      showTargets = onBlock || ItemTravelStaff.isEquipped(player);
      if(showTargets) {
        updateSelectedTarget(player);
      } else {
        selectedCoord = null;
      }
      MovementInput input = player.movementInput;
      if(input.jump && !wasJumping && onBlock && selectedCoord != null) {
        if(travelToSelectedTarget(player, TravelSource.BLOCK, false)) {
          input.jump = false;
        }
      }
      wasJumping = input.jump;
      candidates.clear();
    }
  }

  public boolean hasTarget() {
    return selectedCoord != null;
  }

  public boolean travelToSelectedTarget(EntityPlayer player, TravelSource source, boolean conserveMotion) {
    return travelToLocation(player, source, selectedCoord, conserveMotion);
  }

  public boolean travelToLocation(EntityPlayer player, TravelSource source, BlockCoord coord, boolean conserveMotion) {
    int requiredPower = 0;
    if(source == TravelSource.STAFF) {
      requiredPower = getRequiredPower(player, source, coord);
      if(requiredPower < 0) {
        return false;
      }
    }
    if(!isInRangeTarget(player, coord, source.maxDistanceTravelledSq)) {
      player.sendChatToPlayer(ChatMessageComponent.func_111066_d(Lang.localize("blockTravelPlatform.outOfRange")));
      return false;
    }
    if(!isValidTarget(player, coord, source)) {
      player.sendChatToPlayer(ChatMessageComponent.func_111066_d(Lang.localize("blockTravelPlatform.invalidTarget")));
      return false;
    }
    sendTravelEvent(coord, source, requiredPower, conserveMotion);
    for (int i = 0; i < 6; ++i) {
      player.worldObj.spawnParticle("portal", player.posX + (rand.nextDouble() - 0.5D), player.posY + rand.nextDouble() * player.height - 0.25D,
          player.posZ + (rand.nextDouble() - 0.5D), (this.rand.nextDouble() - 0.5D) * 2.0D, -rand.nextDouble(),
          (rand.nextDouble() - 0.5D) * 2.0D);
    }
    return true;

  }

  public int getRequiredPower(EntityPlayer player, TravelSource source, BlockCoord coord) {
    int requiredPower;
    ItemStack staff = player.getCurrentEquippedItem();
    requiredPower = (int) (getDistance(player, coord) * source.powerCostPerBlockTraveledRF);
    int canUsePower = EnderIO.itemTravelStaff.getEnergyStored(staff);
    if(requiredPower > canUsePower) {
      player.sendChatToPlayer(ChatMessageComponent.func_111066_d(Lang.localize("itemTravelStaff.notEnoughPower")));
      return -1;
    }
    return requiredPower;
  }

  private boolean isInRangeTarget(EntityPlayer player, BlockCoord bc, float maxSq) {
    return getDistanceSquared(player, bc) <= maxSq;
  }

  private double getDistanceSquared(EntityPlayer player, BlockCoord bc) {
    if(player == null || bc == null) {
      return 0;
    }
    Vector3d eye = Util.getEyePositionEio(player);
    Vector3d target = new Vector3d(bc.x + 0.5, bc.y + 0.5, bc.z + 0.5);
    return eye.distanceSquared(target);
  }

  private double getDistance(EntityPlayer player, BlockCoord coord) {
    return Math.sqrt(getDistanceSquared(player, coord));
  }

  private boolean isValidTarget(EntityPlayer player, BlockCoord bc, TravelSource source) {
    if(bc == null) {
      return false;
    }
    World w = player.worldObj;
    return canTeleportTo(bc.getLocation(ForgeDirection.UP), w) && canTeleportTo(bc.getLocation(ForgeDirection.UP).getLocation(ForgeDirection.UP), w);
  }

  private boolean canTeleportTo(BlockCoord bc, World w) {
    int blockId = w.getBlockId(bc.x, bc.y, bc.z);
    Block block = Util.getBlock(blockId);
    if(block == null || block.isAirBlock(w, bc.x, bc.y, bc.z)) {
      return true;
    }
    final AxisAlignedBB aabb = block.getCollisionBoundingBoxFromPool(w, bc.x, bc.y, bc.z);
    return aabb == null || aabb.getAverageEdgeLength() < 0.7;
  }

  private void updateSelectedTarget(EntityClientPlayerMP player) {
    selectedCoord = null;
    if(candidates.isEmpty()) {
      return;
    }

    Vector3d eye = Util.getEyePositionEio(player);
    Vec3 look = player.getLookVec();

    Vector3d b = new Vector3d(eye);
    b.add(look.xCoord, look.yCoord, look.zCoord);

    Vector3d c = new Vector3d(eye);
    c.add(0, 1, 0);

    Vector4d leftPlane = new Vector4d();
    VecmathUtil.computePlaneEquation(eye, b, c, leftPlane);

    c.set(eye);
    c.add(leftPlane.x, leftPlane.y, leftPlane.z);

    Vector4d upPlane = new Vector4d();
    VecmathUtil.computePlaneEquation(eye, b, c, upPlane);

    double closestDistance = Double.MAX_VALUE;
    Vector3d point = new Vector3d();
    for (BlockCoord bc : candidates) {
      if(!bc.equals(onBlockCoord)) {
        point.set(bc.x + 0.5, bc.y + 0.5, bc.z + 0.5);
        double dl = VecmathUtil.distanceFromPointToPlane(leftPlane, point);
        double du = VecmathUtil.distanceFromPointToPlane(upPlane, point);
        double dSq = (dl * dl) + (du * du);
        if(dSq < closestDistance) {
          selectedCoord = bc;
          closestDistance = dSq;
        }
      }
    }

    if(selectedCoord != null) {
      if(selectedCoord.distanceSquared(new BlockCoord((int) eye.x, (int) eye.y, (int) eye.z)) > getMaxTravelDistanceSqForPlayer(player)) {
        selectedCoord = null;
      }
    }
  }

  private int getMaxTravelDistanceSqForPlayer(EntityClientPlayerMP player) {
    if(ItemTravelStaff.isEquipped(player)) {
      return TravelSource.STAFF.maxDistanceTravelledSq;
    }
    return TravelSource.BLOCK.maxDistanceTravelledSq;
  }

  private void sendTravelEvent(BlockCoord coord, TravelSource source, int powerUse, boolean conserveMotion) {
    Packet p = TravelPacketHandler.createMovePacket(coord.x, coord.y, coord.z, powerUse, conserveMotion);
    PacketDispatcher.sendPacketToServer(p);
  }

  private BlockCoord getActiveTravelBlock(EntityClientPlayerMP player) {
    World world = Minecraft.getMinecraft().theWorld;
    if(world != null && player != null) {
      int x = MathHelper.floor_double(player.posX);
      int y = MathHelper.floor_double(player.boundingBox.minY) - 1;
      int z = MathHelper.floor_double(player.posZ);
      if(world.getBlockId(x, y, z) == ModObject.blockTravelPlatform.actualId) {
        return new BlockCoord(x, y, z);
      }
    }
    return null;
  }

  @Override
  public void tickEnd(EnumSet<TickType> type, Object... tickData) {
  }

  @Override
  public EnumSet<TickType> ticks() {
    return EnumSet.of(TickType.CLIENT);
  }

  @Override
  public String getLabel() {
    return "TravelClientTickHandler";
  }

}