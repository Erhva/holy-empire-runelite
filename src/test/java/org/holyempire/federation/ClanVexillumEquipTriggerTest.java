package org.holyempire.federation;

import java.lang.reflect.Proxy;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanVexillumEquipTriggerTest {
 private static ItemContainer equipped(int itemId){
  Item[] slots=new Item[EquipmentInventorySlot.AMMO.getSlotIdx()+1];
  slots[EquipmentInventorySlot.WEAPON.getSlotIdx()]=new Item(itemId,1);
  return (ItemContainer)Proxy.newProxyInstance(
    ItemContainer.class.getClassLoader(),new Class[]{ItemContainer.class},
    (proxy,method,args)->{
     if(method.getName().equals("getItems"))return slots;
     if(method.getName().equals("getId"))return InventoryID.EQUIPMENT.getId();
     if(method.getReturnType()==boolean.class)return false;
     if(method.getReturnType()==int.class)return 0;
     return null;
    });
 }
 @Test public void detectsEveryVexillumColorInWeaponSlot(){
  int[] colors={
    ItemID.CLAN_VEXILLUM_BLACK,ItemID.CLAN_VEXILLUM_ORANGE,
    ItemID.CLAN_VEXILLUM_BLUE,ItemID.CLAN_VEXILLUM_RED,
    ItemID.CLAN_VEXILLUM_GREEN,ItemID.CLAN_VEXILLUM_YELLOW,
    ItemID.CLAN_VEXILLUM_LIGHTBLUE,ItemID.CLAN_VEXILLUM_PINK
  };
  for(int color:colors){
   ClanVexillumEquipTrigger trigger=new ClanVexillumEquipTrigger();
   assertTrue("Vexillum equip should refresh: "+color,
     trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),equipped(color),20_000));
   assertFalse("Repeated equipment notifications do not refetch",
     trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),equipped(color),21_000));
  }
 }
 @Test public void triggersAgainOnlyAfterUnequipAndRespectsCooldown(){
  ClanVexillumEquipTrigger trigger=new ClanVexillumEquipTrigger();
  assertFalse(trigger.onContainerChanged(InventoryID.INVENTORY.getId(),
    equipped(ItemID.CLAN_VEXILLUM_BLUE),20_000));
  assertTrue(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
    equipped(ItemID.CLAN_VEXILLUM_BLUE),20_001));
  assertFalse(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
    equipped(ItemID.CLAN_VEXILLUM_GREEN),21_000));
  assertFalse(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
    equipped(-1),21_001));
  assertFalse("Rapid re-equips are coalesced to protect network resources",
    trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
      equipped(ItemID.CLAN_VEXILLUM_GREEN),21_002));
  assertFalse(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
    equipped(-1),36_001));
  assertTrue(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
    equipped(ItemID.CLAN_VEXILLUM_PINK),36_002));
  trigger.reset();
  assertTrue("A new login may refresh even if already equipped",
    trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
      equipped(ItemID.CLAN_VEXILLUM_BLUE),37_000));
 }
 @Test public void ignoresUnrelatedEquipmentEvents(){
  ClanVexillumEquipTrigger trigger=new ClanVexillumEquipTrigger();
  assertFalse(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),null,100));
  assertFalse(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
    equipped(4151),101));
  assertFalse(trigger.onContainerChanged(InventoryID.INVENTORY.getId(),
    equipped(ItemID.CLAN_VEXILLUM_BLUE),102));
  assertTrue(trigger.onContainerChanged(InventoryID.EQUIPMENT.getId(),
    equipped(ItemID.CLAN_VEXILLUM_BLUE),103));
 }
}
