package codechicken.nei.recipe;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.nei.FastTransferManager;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

@SuppressWarnings("rawtypes, unchecked")
public class DefaultOverlayHandler implements IOverlayHandler {
    public static Class gtItem;

    static {
        try {
            gtItem = Class.forName("gregtech.api.items.GT_MetaBase_Item");
        } catch (ClassNotFoundException ignored) {
            gtItem = null;
        }
    }

    public static class DistributedIngred {
        public DistributedIngred(ItemStack item) {
            stack = InventoryUtils.copyStack(item, 1);
        }

        public ItemStack stack;
        public int invAmount;
        public int distributed;
        public int numSlots;
        public int recipeAmount;
    }

    public static class IngredientDistribution {
        public IngredientDistribution(DistributedIngred distrib, ItemStack permutation) {
            this.distrib = distrib;
            this.permutation = permutation;
        }

        public DistributedIngred distrib;
        public ItemStack permutation;
        public Slot[] slots;
    }

    public DefaultOverlayHandler(int x, int y) {
        offsetx = x;
        offsety = y;
    }

    public DefaultOverlayHandler() {
        this(5, 11);
    }

    public int offsetx;
    public int offsety;

    @Override
    public void overlayRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, boolean shift) {
        List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);
        List<DistributedIngred> ingredStacks = getPermutationIngredients(ingredients);

        if (!clearIngredients(gui, ingredients)) return;

        findInventoryQuantities(gui, ingredStacks);

        List<IngredientDistribution> assignedIngredients = assignIngredients(ingredients, ingredStacks);
        if (assignedIngredients == null) return;

        assignIngredSlots(gui, ingredients, assignedIngredients);
        int quantity = calculateRecipeQuantity(assignedIngredients);

        if (quantity != 0) moveIngredients(gui, assignedIngredients, quantity);
    }

    @SuppressWarnings("unchecked")
    private boolean clearIngredients(GuiContainer gui, List<PositionedStack> ingreds) {
        for (PositionedStack pstack : ingreds)
            for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots)
                if (slot.xDisplayPosition == pstack.relx + offsetx && slot.yDisplayPosition == pstack.rely + offsety) {
                    if (!slot.getHasStack()) continue;

                    FastTransferManager.clickSlot(gui, slot.slotNumber, 0, 1);
                    if (slot.getHasStack()) return false;
                }

        return true;
    }

    @SuppressWarnings("unchecked")
    private void moveIngredients(GuiContainer gui, List<IngredientDistribution> assignedIngredients, int quantity) {
        for (IngredientDistribution distrib : assignedIngredients) {
            if (distrib.slots.length == 0) continue;

            ItemStack pstack = distrib.permutation;
            int transferCap = quantity * pstack.stackSize;
            int transferred = 0;

            int destSlotIndex = 0;
            Slot dest = distrib.slots[0];
            int slotTransferred = 0;
            int slotTransferCap = pstack.getMaxStackSize();

            for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
                if (!slot.getHasStack() || !canMoveFrom(slot, gui)) continue;

                ItemStack stack = slot.getStack();
                if (!canStack(stack, pstack)) continue;

                FastTransferManager.clickSlot(gui, slot.slotNumber);
                int amount = Math.min(transferCap - transferred, stack.stackSize);
                for (int c = 0; c < amount; c++) {
                    FastTransferManager.clickSlot(gui, dest.slotNumber, 1);
                    transferred++;
                    slotTransferred++;
                    if (slotTransferred >= slotTransferCap) {
                        destSlotIndex++;
                        if (destSlotIndex == distrib.slots.length) {
                            dest = null;
                            break;
                        }
                        dest = distrib.slots[destSlotIndex];
                        slotTransferred = 0;
                    }
                }
                FastTransferManager.clickSlot(gui, slot.slotNumber);
                if (transferred >= transferCap || dest == null) break;
            }
        }
    }

    private int calculateRecipeQuantity(List<IngredientDistribution> assignedIngredients) {
        int quantity = Integer.MAX_VALUE;
        for (IngredientDistribution distrib : assignedIngredients) {
            DistributedIngred istack = distrib.distrib;
            if (istack.numSlots == 0) return 0;

            int allSlots = istack.invAmount;
            if (allSlots / istack.numSlots > istack.stack.getMaxStackSize())
                allSlots = istack.numSlots * istack.stack.getMaxStackSize();

            quantity = Math.min(quantity, allSlots / istack.distributed);
        }

        return quantity;
    }

    private Slot[][] assignIngredSlots(
            GuiContainer gui, List<PositionedStack> ingredients, List<IngredientDistribution> assignedIngredients) {
        Slot[][] recipeSlots = mapIngredSlots(gui, ingredients); // setup the slot map

        HashMap<Slot, Integer> distribution = new HashMap<>();
        for (Slot[] recipeSlot : recipeSlots)
            for (Slot slot : recipeSlot) if (!distribution.containsKey(slot)) distribution.put(slot, -1);

        HashSet<Slot> avaliableSlots = new HashSet<>(distribution.keySet());
        HashSet<Integer> remainingIngreds = new HashSet<>();
        ArrayList<LinkedList<Slot>> assignedSlots = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            remainingIngreds.add(i);
            assignedSlots.add(new LinkedList<>());
        }

        while (avaliableSlots.size() > 0 && remainingIngreds.size() > 0) {
            for (Iterator<Integer> iterator = remainingIngreds.iterator(); iterator.hasNext(); ) {
                int i = iterator.next();
                boolean assigned = false;
                DistributedIngred istack = assignedIngredients.get(i).distrib;

                for (Slot slot : recipeSlots[i]) {
                    if (avaliableSlots.contains(slot)) {
                        avaliableSlots.remove(slot);
                        if (slot.getHasStack()) continue;

                        istack.numSlots++;
                        assignedSlots.get(i).add(slot);
                        assigned = true;
                        break;
                    }
                }

                if (!assigned || istack.numSlots * istack.stack.getMaxStackSize() >= istack.invAmount)
                    iterator.remove();
            }
        }

        for (int i = 0; i < ingredients.size(); i++)
            assignedIngredients.get(i).slots = assignedSlots.get(i).toArray(new Slot[0]);
        return recipeSlots;
    }

    private List<IngredientDistribution> assignIngredients(
            List<PositionedStack> ingredients, List<DistributedIngred> ingredStacks) {
        ArrayList<IngredientDistribution> assignedIngredients = new ArrayList<>();
        for (PositionedStack posstack : ingredients) // assign what we need and have
        {
            DistributedIngred biggestIngred = null;
            ItemStack permutation = null;
            int biggestSize = 0;
            for (ItemStack pstack : posstack.items) {
                for (DistributedIngred istack : ingredStacks) {
                    if (!canStack(pstack, istack.stack)
                            || istack.invAmount - istack.distributed < pstack.stackSize
                            || istack.recipeAmount == 0
                            || pstack.stackSize == 0) continue;

                    int relsize = (istack.invAmount - istack.invAmount / istack.recipeAmount * istack.distributed)
                            / pstack.stackSize;
                    if (relsize > biggestSize) {
                        biggestSize = relsize;
                        biggestIngred = istack;
                        permutation = pstack;
                        break;
                    }
                }
            }

            if (biggestIngred == null) // not enough ingreds
            return null;

            biggestIngred.distributed += permutation.stackSize;
            assignedIngredients.add(new IngredientDistribution(biggestIngred, permutation));
        }

        return assignedIngredients;
    }

    @SuppressWarnings("unchecked")
    private void findInventoryQuantities(GuiContainer gui, List<DistributedIngred> ingredStacks) {
        for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) /*work out how much we have to go round*/ {
            if (slot.getHasStack() && canMoveFrom(slot, gui)) {
                ItemStack pstack = slot.getStack();
                DistributedIngred istack = findIngred(ingredStacks, pstack);
                if (istack != null) istack.invAmount += pstack.stackSize;
            }
        }
    }

    private List<DistributedIngred> getPermutationIngredients(List<PositionedStack> ingredients) {
        ArrayList<DistributedIngred> ingredStacks = new ArrayList<>();
        for (PositionedStack posstack : ingredients) /*work out what we need*/ {
            for (ItemStack pstack : posstack.items) {
                DistributedIngred istack = findIngred(ingredStacks, pstack);
                if (istack == null) ingredStacks.add(istack = new DistributedIngred(pstack));
                istack.recipeAmount += pstack.stackSize;
            }
        }
        return ingredStacks;
    }

    public boolean canMoveFrom(Slot slot, GuiContainer gui) {
        return slot.inventory instanceof InventoryPlayer;
    }

    @SuppressWarnings("unchecked")
    public Slot[][] mapIngredSlots(GuiContainer gui, List<PositionedStack> ingredients) {
        Slot[][] recipeSlotList = new Slot[ingredients.size()][];
        for (int i = 0; i < ingredients.size(); i++) /*identify slots*/ {
            LinkedList<Slot> recipeSlots = new LinkedList<>();
            PositionedStack pstack = ingredients.get(i);
            for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
                if (slot.xDisplayPosition == pstack.relx + offsetx && slot.yDisplayPosition == pstack.rely + offsety) {
                    recipeSlots.add(slot);
                    break;
                }
            }
            recipeSlotList[i] = recipeSlots.toArray(new Slot[0]);
        }
        return recipeSlotList;
    }

    public DistributedIngred findIngred(List<DistributedIngred> ingredStacks, ItemStack pstack) {
        for (DistributedIngred istack : ingredStacks) if (canStack(pstack, istack.stack)) return istack;
        return null;
    }

    protected boolean canStack(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null || stack2 == null) return true;
        if (stack1.getItem() == stack2.getItem() && stack1.getItemDamage() == stack2.getItemDamage()) {
            if (ItemStack.areItemStackTagsEqual(stack2, stack1)) return true;

            // GT Items don't have any NBT set for the recipe, so if either of the stacks has a NULL nbt, and the other
            // doesn't, pretend they stack
            if (((gtItem != null && gtItem.isInstance(stack1.getItem()))
                            || (stack1.getMaxStackSize() == 1 && stack2.getMaxStackSize() == 1))
                    && (stack1.stackTagCompound == null ^ stack2.stackTagCompound == null)) return true;
        }
        return false;
    }
}
