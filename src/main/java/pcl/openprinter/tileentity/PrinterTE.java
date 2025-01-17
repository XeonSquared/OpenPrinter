package pcl.openprinter.tileentity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemWritableBook;
import net.minecraft.item.ItemWrittenBook;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import pcl.openprinter.ContentRegistry;
import pcl.openprinter.OpenPrinter;
import pcl.openprinter.books.BiblioCraftBigBook;
import pcl.openprinter.books.VanillaBook;
import pcl.openprinter.items.PrintedPage;
import pcl.openprinter.items.PrinterInkBlack;
import pcl.openprinter.items.PrinterInkColor;
import pcl.openprinter.items.PrinterPaperRoll;
import pcl.openprinter.util.ItemUtils;
import pcl.openprinter.util.CharacterWidth;

import javax.annotation.Nonnull;

import static pcl.openprinter.tileentity.PrinterTE.InventoryMaterial.*;

public class PrinterTE extends TileEntity implements ITickable, Environment {
	private static final double PrinterFormatVersion = 2.0;

	private ComponentConnector node = Network.newNode(this, Visibility.Network).withComponent(getComponentName()).withConnector(32).create();
	private boolean addedToNetwork = false;

	private ItemStackHandler inventoryOutput = new InventoryOutput();
	private ItemStackHandler inventoryScanner = new InventoryScanner();
	private ItemStackHandler inventoryMaterials = new InventoryMaterial();

	private List<String> lines = new ArrayList<>();
	private List<String> align = new ArrayList<>();
	private List<Integer> colors = new ArrayList<>();
	private String pageTitle = "";

	private UUID uuid = UUID.randomUUID();

	public PrinterTE() {}

	class InventoryOutput extends ItemStackHandler {
		InventoryOutput(){
			super(9);
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack){
			return stack.getItem() instanceof PrintedPage;
		}

		@Override
		@Nonnull
		public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
			return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
		}
	}

	class InventoryScanner extends ItemStackHandler {
		InventoryScanner(){
			super(1);
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack){
			return stack.getItem() instanceof PrintedPage
					|| stack.getItem().equals(Items.WRITTEN_BOOK)
					|| stack.getItem().equals(Items.WRITABLE_BOOK)
					|| stack.getItem().getRegistryName().toString().equals("bibliocraft:bigbook");
		}

		@Override
		@Nonnull
		public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
			return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
		}
	}

	class InventoryMaterial extends ItemStackHandler {
		static final short BLACK_INK_SLOT = 0;
		static final short COLOR_INK_SLOT = 1;
		static final short PAPER_SLOT = 2;

		InventoryMaterial(){
			super(3);
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack){
			switch(slot){
				case PAPER_SLOT:
					return stack.getItem() instanceof PrinterPaperRoll
						|| stack.getItem().equals(Items.PAPER)
						|| stack.getItem().equals(Items.NAME_TAG);
				case COLOR_INK_SLOT:
					return stack.getItem() instanceof PrinterInkColor;
				case BLACK_INK_SLOT:
					return stack.getItem() instanceof PrinterInkBlack;
			}
			return false;
		}

		@Override
		@Nonnull
		public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
			return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
		}
	}

	public void removed(){
		ItemUtils.dropItems(inventoryScanner, world, getPos(), true, 10);
		ItemUtils.dropItems(inventoryMaterials, world, getPos(), true, 10);
		ItemUtils.dropItems(inventoryOutput, world, getPos(), true, 10);
	}

	private String getComponentName() {
		return "openprinter";
	}

	@Override
	public Node node() {
		return node;
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if (node != null)
			node.remove();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if (node != null)
			node.remove();
	}

	@Override
	public void onConnect(final Node node) {}

	@Override
	public void onDisconnect(final Node node) {}

	@Override
	public void update() {
		if(!addedToNetwork) {
			addToNetwork();
		}
	}

	protected void addToNetwork() {
		if(!addedToNetwork) {
			addedToNetwork = true;
			Network.joinOrCreateNetwork(this);
		}
	}


	@Deprecated
	public void readOldInventoryFromNBT(NBTTagCompound nbt) {
		if(!nbt.hasKey("Items"))
			return;

		NBTTagList var2 = nbt.getTagList("Items",nbt.getId());
		for (int var3 = 0; var3 < var2.tagCount(); ++var3)
		{
			NBTTagCompound var4 = (NBTTagCompound)var2.getCompoundTagAt(var3);
			byte var5 = var4.getByte("Slot");

			switch(var5){
				case 13:
					inventoryScanner.setStackInSlot(0, new ItemStack(var4));
					break;
				case 0:
					inventoryMaterials.setStackInSlot(0, new ItemStack(var4));
					break;
				case 1:
					inventoryMaterials.setStackInSlot(1, new ItemStack(var4));
					break;
				case 2:
					inventoryMaterials.setStackInSlot(2, new ItemStack(var4));
					break;
				default:
					var5-=3;
					if(var5 > 0 && var5 < inventoryOutput.getSlots())
						inventoryOutput.setStackInSlot(var5, new ItemStack(var4));
					break;
			}
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		if (node != null && node.host() == this) {
			node.load(nbt.getCompoundTag("oc:node"));
		}

		if(nbt.hasKey("inventoryOutput")) {
			inventoryOutput.deserializeNBT(nbt.getCompoundTag("inventoryOutput"));
			inventoryScanner.deserializeNBT(nbt.getCompoundTag("inventoryScanner"));
			inventoryMaterials.deserializeNBT(nbt.getCompoundTag("inventoryMaterials"));
		}
		else
			readOldInventoryFromNBT(nbt); //remove when porting upwards

		if(nbt.hasUniqueId("printerUUID"))
			setUniqueId(nbt.getUniqueId("printerUUID"));

		if(nbt.hasKey("buffers"))
			readBuffersFromNBT(nbt.getCompoundTag("buffers"));
	}

	private NBTTagCompound writeBuffersToNBT(NBTTagCompound nbt){
		for(int i=0; i < lines.size(); i++){
			NBTTagCompound lineNBT = new NBTTagCompound();
			lineNBT.setString("text", lines.get(i));
			lineNBT.setString("align", align.get(i));
			lineNBT.setInteger("color", colors.get(i));
			nbt.setTag("line"+i, lineNBT);
		}

		return nbt;
	}

	private void readBuffersFromNBT(NBTTagCompound nbt){
		lines.clear();
		align.clear();
		colors.clear();
		for(int i=0; nbt.hasKey("line"+i); i++){
			NBTTagCompound lineNBT = nbt.getCompoundTag("line"+i);
			lines.add(lineNBT.getString("text"));
			align.add(lineNBT.getString("align"));
			colors.add(lineNBT.getInteger("color"));
		}
	}

	@Override
	@Nonnull
	public NBTTagCompound writeToNBT(NBTTagCompound nbt)
	{
		if (node != null && node.host() == this) {
			final NBTTagCompound nodeNbt = new NBTTagCompound();
			node.save(nodeNbt);
			nbt.setTag("oc:node", nodeNbt);
		}

		nbt.setTag("inventoryScanner", inventoryScanner.serializeNBT());
		nbt.setTag("inventoryMaterials", inventoryMaterials.serializeNBT());
		nbt.setTag("inventoryOutput", inventoryOutput.serializeNBT());

		nbt.setTag("buffers", writeBuffersToNBT(new NBTTagCompound()));

		nbt.setUniqueId("printerUUID", getUniqueId());

		return super.writeToNBT(nbt);
	}

	@Override
	@Nonnull
	public NBTTagCompound getUpdateTag() {
		NBTTagCompound tag = new NBTTagCompound();
		this.writeToNBT(tag);
		return tag;
	}

	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
		readFromNBT(packet.getNbtCompound());
	}


	@Callback
	public Object[] greet(Context context, Arguments args) {
		return new Object[] { "Lasciate ogne speranza, voi ch'intrate" };
	}

	//Real Printer methods follow:
	@Callback(doc = "function(Integer:lineNumber):String; -- scans the given line of a printed page")
	public Object[] scanLine(Context context, Arguments args) {
		if(!args.isInteger(0))
			return new Object[] { false, "first argument has to be a line number" };

		ItemStack scannedPage = inventoryScanner.getStackInSlot(0);

		if (scannedPage.getItem() instanceof PrintedPage && scannedPage.hasTagCompound()) {
			return new Object[] { scannedPage.getTagCompound().getString("line" + args.checkInteger(0)) };
		} else {
			return new Object[] { false };
		}
	}

	@Callback(doc = "function():table; -- scans a printed page")
	public Object[] scan(Context context, Arguments args) {
		ItemStack scannerInput = inventoryScanner.getStackInSlot(0);

		if (scannerInput.getItem() instanceof PrintedPage) {
			return readPrintedPage(scannerInput);
		}

		return new Object[] { false };
	}

	@Callback(doc = "function():table; -- scans a book")
	public Object[] scanBook(Context context, Arguments args) {
		ItemStack scannerInput = inventoryScanner.getStackInSlot(0);

		if (scannerInput.getItem() instanceof ItemWritableBook || scannerInput.getItem() instanceof ItemWrittenBook) {
			return new VanillaBook().readFromStack(scannerInput);
		}
		else if (scannerInput.getItem().getRegistryName().toString().equals("bibliocraft:bigbook"))
			return new BiblioCraftBigBook().readFromStack(scannerInput);

		return new Object[] { false };
	}

	private static Object[] readPrintedPage(ItemStack printedPage){
		if(!printedPage.hasTagCompound())
			return new Object[] { false, "page doesnt have nbt data"};

		String outPageTitle = null;
		Map<Integer, String> output = new HashMap<Integer, String>();

		if (printedPage.getTagCompound().hasKey("pageTitle")) {
			outPageTitle = printedPage.getTagCompound().getString("pageTitle");
		}
		for (int x = 0; x <= 20; x++) {
			if(printedPage.getTagCompound().hasKey("line"+x)) {
				output.put(x, printedPage.getTagCompound().getString("line"+x));
			}
		}

		return new Object[] { outPageTitle, output, scanPageMeta(printedPage) };
	}

	private static Map<String, String> scanPageMeta(ItemStack scannerInput) {
		Map<String, String> output = new HashMap<>();
		if (scannerInput.getItem() instanceof PrintedPage) {
			if(scannerInput.getTagCompound().hasUniqueId("printerUUID"))
				output.put("printerUUID", scannerInput.getTagCompound().getUniqueId("printerUUID").toString());
			if(scannerInput.getTagCompound().hasUniqueId("pageUUID"))
				output.put("pageUUID", scannerInput.getTagCompound().getUniqueId("pageUUID").toString());
		}

		return output;
	}

	@Callback(doc = "function(String:title):boolean; -- prints a name tag")
	public Object[] printTag(Context context, Arguments args) throws Exception {
		if (!OpenPrinter.cfg.enableNameTag)
			throw new Exception("Name Tag printing is disabled.");

		if(!args.isString(0))
			throw new Exception("First argument has to be a valid String");

		if (inventoryMaterials.getStackInSlot(BLACK_INK_SLOT).isEmpty())
			throw new Exception("Please load Black Ink.");

		if (!inventoryMaterials.getStackInSlot(PAPER_SLOT).getItem().equals(Items.NAME_TAG))
			throw new Exception("Please load Name Tags.");


		int x = getEmptyOutputSlot();
		if(x == -1)
			throw new Exception("no empty output slot");

		ItemStack output = new ItemStack(Items.NAME_TAG);
		output.setTagCompound(new NBTTagCompound());
		NBTTagCompound nameTag = new NBTTagCompound();
		nameTag.setString("Name", args.checkString(0));
		output.getTagCompound().setTag("display", nameTag);

		damageMaterial(BLACK_INK_SLOT, 1);
		damageMaterial(PAPER_SLOT, 1);

		inventoryOutput.setStackInSlot(x, output);

		return new Object[]{ true };
	}

	private void damageMaterial(short materialSlot, int damage){
		ItemStack slotItem = inventoryMaterials.getStackInSlot(materialSlot);
		if(slotItem.getCount() > 1){
			inventoryMaterials.extractItem(materialSlot, 1, false);
			return;
		}

		slotItem.setItemDamage(slotItem.getItemDamage() + damage);
		if(slotItem.getItemDamage() >= slotItem.getMaxDamage()) {
			inventoryMaterials.setStackInSlot(materialSlot, ItemStack.EMPTY);
		}
		else
			inventoryMaterials.setStackInSlot(materialSlot, slotItem);
	}

	private int getEmptyOutputSlot(){
		for(int slot = 0; slot < inventoryOutput.getSlots(); slot++)
			if(inventoryOutput.getStackInSlot(slot).isEmpty())
				return slot;

		return -1;
	}


	@Callback(doc = "function([Integer:copyCount]):boolean; -- prints the current page")
	public Object[] print(Context context, Arguments args) throws Exception {
		int copies = args.optInteger(0, 1);
		int copiesDone = 0;

		for (int i = 0; i < copies; i++) {
			if((inventoryMaterials.getStackInSlot(BLACK_INK_SLOT).isEmpty() || inventoryMaterials.getStackInSlot(COLOR_INK_SLOT).isEmpty())) //No color ink
				throw new Exception("Please load Ink.");

			if (!inventoryMaterials.getStackInSlot(PAPER_SLOT).getItem().equals(Items.PAPER))
				throw new Exception("Please load Paper.");

			int x = getEmptyOutputSlot();

			if(x == -1)
				throw new Exception("no empty output slot");

			ItemStack output = new ItemStack(ContentRegistry.printedPage);
			output.setTagCompound(new NBTTagCompound());
			if(pageTitle.length() > 0) {
				output.getTagCompound().setString("pageTitle", pageTitle);
				output.setStackDisplayName(pageTitle);
			}
			output.getTagCompound().setDouble("version", PrinterFormatVersion);
			int iter = 0;
			for (String s : lines) {
				output.getTagCompound().setString("line"+iter, lines.get(iter) + "∞" + colors.get(iter) + "∞" + align.get(iter));

				if (colors.get(iter) != 0x000000) {
					damageMaterial(COLOR_INK_SLOT, 1);
				} else {
					damageMaterial(BLACK_INK_SLOT, 1);
				}
				if(lines.get(iter).matches(".*§[0-9a-f].*")) {
					Pattern regex = Pattern.compile("§[0-9a-f]*");
					Matcher matcher = regex.matcher(lines.get(iter));
					while (matcher.find())
						damageMaterial(COLOR_INK_SLOT, 1);
				}
				iter++;
			}

			damageMaterial(PAPER_SLOT, 1);

			output.getTagCompound().setUniqueId("printerUUID", getUniqueId());
			output.getTagCompound().setUniqueId("pageUUID", UUID.randomUUID());

			inventoryOutput.setStackInSlot(x, output);

			copiesDone++;

		}

		pageTitle = "";
		lines.clear();
		colors.clear();
		align.clear();

		return new Object[] { copiesDone == copies };
	}

	@Callback(doc = "function(String:text[, Integer:color, String:alignment]):boolean; -- prints a line to the printer buffer")
	public Object[] writeln(Context context, Arguments args) throws Exception{
		if(!args.isString(0))
			throw new Exception("First argument has to be a valid String");


		if(lines.size() >= 20) {
			throw new Exception("To many lines.");
		}
		int color = 0x000000;
		String alignment = "left";
		if (args.count() == 2){
			if (args.isInteger(1)) {
				color = args.checkInteger(1);
			} else if (args.isString(1)) {
				alignment = args.checkString(1);
			}
		} 

		if (args.count() == 3){
			if (args.isInteger(1)) {
				color = args.checkInteger(1);
			}
			if (args.isString(2)) {
				alignment = args.checkString(2);
			}
		}

		lines.add(CharacterWidth.limitWidth(args.checkString(0), CharacterWidth.maxWidth));
		colors.add(color);
		align.add(alignment);
		return new Object[] { true };
	}

	@Callback(doc = "function(String:text):boolean; -- sets the page title")
	public Object[] setTitle(Context context, Arguments args) {
		pageTitle = args.optString(0, "");
		return new Object[] { true };
	}

	@Callback(doc = "function():Integer; -- gets the current paper level")
	public Object[] getPaperLevel(Context context, Arguments args) { 
		if(inventoryMaterials.getStackInSlot(PAPER_SLOT).isEmpty())
			return new Object[] { 0 };

		if (inventoryMaterials.getStackInSlot(PAPER_SLOT).getItem() instanceof PrinterPaperRoll) {
			return new Object[] { 256 - inventoryMaterials.getStackInSlot(PAPER_SLOT).getItemDamage() };
		} else {
			return new Object[] { inventoryMaterials.getStackInSlot(PAPER_SLOT).getCount() };
		}
	}

	@Callback(doc = "function():Integer; -- gets the current black ink level")
	public Object[] getBlackInkLevel(Context context, Arguments args) { 
		if(inventoryMaterials.getStackInSlot(BLACK_INK_SLOT).isEmpty())
			return new Object[] { 0 };

		return new Object[] { OpenPrinter.cfg.printerInkUse - inventoryMaterials.getStackInSlot(BLACK_INK_SLOT).getItemDamage()};
	}

	@Callback(doc = "function():Integer; -- gets the current color ink level")
	public Object[] getColorInkLevel(Context context, Arguments args) { 
		if(inventoryMaterials.getStackInSlot(COLOR_INK_SLOT).isEmpty())
			return new Object[] { false };

		return new Object[] { OpenPrinter.cfg.printerInkUse - inventoryMaterials.getStackInSlot(COLOR_INK_SLOT).getItemDamage() };
	}

	@Callback(doc = "function(String:input):Integer; -- gets the character count for the given String", direct = true)
	public Object[] charCount(Context context, Arguments args) {
		return new Object[] { args.checkString(0).replaceAll("(?:§[0-9a-fk-or])+", "").length() };
	}

        @Callback(doc = "function(String:input):Integer; -- gets the pixel width of a given String", direct = true)
        public Object[] width(Context context, Arguments args) {
		return new Object[] { CharacterWidth.calculateWidth(args.checkString(0)) };
        }

        @Callback(doc = "function():Integer; -- returns the maximum allowed width of a line", direct = true)
        public Object[] maxWidth(Context context, Arguments args) {
		return new Object[] { CharacterWidth.maxWidth };
        }

	@Callback(doc = "function():boolean; -- clears the printer buffer")
	public Object[] clear(Context context, Arguments args) {
		lines.clear();
		colors.clear();
		align.clear();
		pageTitle = "";
		return new Object[] { true };
	}

	@Override
	public void onMessage(Message arg0) {
	}

	@Override
	public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
		return (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) ||  super.hasCapability(capability, facing);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			if(EnumFacing.UP.equals(facing))
				return (T) inventoryScanner;
			else if(EnumFacing.DOWN.equals(facing))
				return (T) inventoryOutput;
			else
				return (T) inventoryMaterials;
		}

		return super.getCapability(capability, facing);
	}

	public UUID getUniqueId(){
		return uuid;
	}

	public void setUniqueId(UUID uniqueId){
		this.uuid = uniqueId;
	}

}
