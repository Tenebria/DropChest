package com.narrowtux.DropChest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.ContainerBlock;
import org.bukkit.craftbukkit.entity.CraftStorageMinecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;

import com.narrowtux.DropChest.API.DropChestFillEvent;
import com.narrowtux.DropChest.API.DropChestSuckEvent;

public class DropChestItem {
	private ContainerBlock containerBlock;
	private Block block;
	
	private int radius;
	private String owner = "";
	private boolean protect = false;
	private String name = "";
	private int id;
	private HashMap<FilterType,List<Material > > filter = new HashMap<FilterType,List<Material> >();
	
	private boolean warnedNearlyFull;
	private DropChest plugin;
	private DropChestMinecartAction minecartAction = DropChestMinecartAction.IGNORE;
	private boolean loadedProperly = true;
	private static int currentId = 1;
	private Location loc = null;
	private long lastRedstoneDrop = 0;

	public DropChestItem(ContainerBlock containerBlock, int radius, Block block, DropChest plugin)
	{
		this.containerBlock = containerBlock;
		this.radius = radius;
		warnedNearlyFull = false;
		this.plugin = plugin;
		this.block = block;
		loc = new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
		id = currentId++;
		List<Material> f = new ArrayList<Material>();
		filter.put(FilterType.SUCK, f);
		f = new ArrayList<Material>();
		filter.put(FilterType.PUSH, f);
		f = new ArrayList<Material>();
		filter.put(FilterType.PULL, f);
	}

	public DropChestItem(String loadString, String fileVersion, DropChest plugin)
	{
		this.plugin = plugin;
		warnedNearlyFull = false;
		List<Material> f = new ArrayList<Material>();
		filter.put(FilterType.SUCK, f);
		f = new ArrayList<Material>();
		filter.put(FilterType.PUSH, f);
		f = new ArrayList<Material>();
		filter.put(FilterType.PULL, f);
		load(loadString, fileVersion);
		if(block != null){
			loc = new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
		}
	}

	public ContainerBlock getChest() {
		if(block.getState() instanceof ContainerBlock){
			containerBlock = (ContainerBlock)block.getState();
			return containerBlock;
		} else {
			return null;
		}
	}
	
	public Inventory getInventory(){
		//First, check if this is a double chest.
		BlockFace faces[] = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
		for(BlockFace face:faces){
			Block other = getBlock().getFace(face);
			if(other.getType().equals(Material.CHEST)){
				if(other.getState() instanceof ContainerBlock){
					Inventory inventories[] = {getChest().getInventory(), ((ContainerBlock)other.getState()).getInventory()};
					return new DropChestInventory(inventories);
				}
			}
		}
		return getChest().getInventory();
	}

	public void setChest(Chest chest) {
		this.containerBlock = chest;
	}

	public int getRadius() {
		return radius;
	}

	public void setRadius(int radius) {
		if(radius<0)
		{
			getOwnerPlayer().sendMessage(ChatColor.RED.toString()+"Chest radius was negative!, radius changed to 2");
			radius = 2;
		}
		else if(radius==0)
			getOwnerPlayer().sendMessage(ChatColor.RED.toString()+"Chest radius is 0, the chest will not suck");
		this.radius = radius;
	}
	
	public Player getOwnerPlayer(){
		return Bukkit.getServer().getPlayer(getOwner());
	}

	public double getPercentFull(){
		Inventory inv = getInventory();
		int stacks = inv.getSize();
		int maxStackSize = 0;
		int totalItems = 0;
		for(int j = 0; j<stacks; j++){
			ItemStack istack = inv.getItem(j);
			if(istack!=null){
				totalItems+=istack.getAmount();
				if(istack.getTypeId()==0){
					maxStackSize+=64;
				} else {
					maxStackSize+=istack.getMaxStackSize();
				}
			}
			
		}
		double percent = (double)totalItems/(double)maxStackSize;
		if(percent<plugin.config.getWarnFillStatus()/100)
			warnedNearlyFull = false;
		return percent;
	}

	public void warnNearlyFull(){
		if(!warnedNearlyFull&&plugin.config.getWarnFillStatus()!=-1){
			String warnString = plugin.config.getWarnString();
			warnString = warnString.replaceAll("\\$name", getName());
			warnString = warnString.replaceAll("\\$fill", String.valueOf((int)(getPercentFull()*100.0)));
			warnString = warnString.replaceAll("\\$owner", getOwner());
			Player player = plugin.getServer().getPlayer(getOwner());
			if(player!=null&&player.isOnline()){
				player.sendMessage(warnString);
			} else {
				plugin.getServer().broadcastMessage(warnString);
			}
			warnedNearlyFull = true;
		}
	}

	public List<Material> getFilter(FilterType filterType){
		return filter.get(filterType);
	}

	public HashMap<Integer, ItemStack> addItem(ItemStack item, FilterType filterType)
	{
		DropChestFillEvent fillEvent = new DropChestFillEvent(this);
		PluginManager pm = plugin.getServer().getPluginManager();
		getChest();
		if(filter.get(filterType).size()==0&&filterType==FilterType.SUCK){
			HashMap<Integer, ItemStack> ret = getInventory().addItem(item);
			pm.callEvent(fillEvent);
			return ret;
		}
		else
		{
			for(Material m : filter.get(filterType))
			{
				if(m.getId()==item.getTypeId()){
					pm.callEvent(fillEvent);
					return getInventory().addItem(item);
				}
			}
			HashMap<Integer, ItemStack> ret = new HashMap<Integer, ItemStack>();
			ret.put(0, item);
			return ret;
		}
	}

	/**
	 * @param minecartAction the minecartAction to set
	 */
	public void setMinecartAction(DropChestMinecartAction minecartAction) {
		this.minecartAction = minecartAction;
	}

	/**
	 * @return the minecartAction
	 */
	public DropChestMinecartAction getMinecartAction() {
		return minecartAction;
	}

	public void setRedstone(boolean value){
		Block below = block.getFace(BlockFace.DOWN);
		if(below.getTypeId() == Material.LEVER.getId()){
			byte data = below.getData();
			if(value){
				data=0x8|0x5;
			} else {
				data=0x5;
			}
			below.setData(data);
		}
	}

	public void triggerRedstone(){
		setRedstone(true);
		Timer timer = new Timer();
		timer.schedule(new RedstoneTrigger(this), 1000);
	}

	public void dropAll(){
		if(isProtect()){
			return;
		}
		World world = block.getWorld();
		Location loc = block.getLocation();

                ItemStack[] contents = containerBlock.getInventory().getContents()
                for(ItemStack item : contents)
			if(item.getAmount()!=0){
				world.dropItem(loc, item);
				containerBlock.getInventory().remove(item);
			}
		}
		Date date = new Date();
		lastRedstoneDrop = date.getTime();
	}

	private void load(String loadString, String fileVersion)
	{
		if(fileVersion.equalsIgnoreCase("0.0")){
			String locSplit[] = loadString.split(",");
			if(locSplit.length>=3){
				Double x = Double.valueOf(locSplit[0]);
				Double y = Double.valueOf(locSplit[1]);
				Double z = Double.valueOf(locSplit[2]);

				int radius = 2;
				long worldid = 0;
				if(locSplit.length>=4){
					radius = (int)Integer.valueOf(locSplit[3]);
					if(locSplit.length>=5){
						worldid = (long)Long.valueOf(locSplit[4]);
					}
				}
				World wo = plugin.getWorldWithId(worldid);
				if(wo!=null)
				{
					Block b = wo.getBlockAt((int)(double)x,(int)(double)y,(int)(double)z);
					if(b.getTypeId() == Material.CHEST.getId()){
						ContainerBlock chest = (ContainerBlock)b.getState();
						this.containerBlock = chest;
						this.radius = radius;
						this.block = b;
						if(locSplit.length>=6){
							List<Material> filter = getFilter(FilterType.SUCK);
							for(int i = 5;i<locSplit.length;i++){
								filter.add(Material.getMaterial((int)Integer.valueOf(locSplit[i])));
							}
						}
					} else {
						loadedProperly = false;
					}
				} else {
					loadedProperly = false;
				}

			} else {
				loadedProperly = false;
			}
		} else if(fileVersion.equals("0.1")||fileVersion.equals("0.2")||fileVersion.equals("0.3")||fileVersion.equals("0.4")||fileVersion.equals("0.5")||fileVersion.equals("0.6")||fileVersion.equals("0.7")){
			String splt[] = loadString.split(";");
			if(splt.length>=1){
				String data[] = splt[0].split(",");
				String filters[];
				if(splt.length==2||splt.length==4){
					for(int ft=1;ft<splt.length;ft++){
						filters = splt[ft].split(",");
						FilterType type = null ;
						switch(ft){
						case 1:
							type = FilterType.SUCK;
							break;
						case 2:
							type = FilterType.PULL;
							break;
						case 3:
							type = FilterType.PUSH;
							break;
						default:
							type = FilterType.SUCK;
							break;
						}
						for(int i = 0; i<filters.length; i++)
						{
							List<Material> fi = getFilter(type);
							if(!filters[i].isEmpty()){
								Material m = null;
								try{
									m = Material.valueOf(filters[i]);
								} catch (IllegalArgumentException e){
									m = null;
								}
								if(m!=null)
								{
									fi.add(m);
								}
							}
						}
					}
				}
				if(data.length>=6){
					Double x,y,z;
					long worldid;
					World world;
					x = Double.valueOf(data[0]);
					y = Double.valueOf(data[1]);
					z = Double.valueOf(data[2]);
					radius = Integer.valueOf(data[3]);
					try{
						setMinecartAction(DropChestMinecartAction.valueOf(data[5]));
					} catch(java.lang.IllegalArgumentException e){
						setMinecartAction(DropChestMinecartAction.IGNORE);
					}
					org.bukkit.World.Environment env = org.bukkit.World.Environment.NORMAL;
					if(fileVersion.equals("0.3")){
						env = org.bukkit.World.Environment.valueOf(data[6]);
					}
					if(fileVersion.equalsIgnoreCase("0.1")){
						worldid = Long.valueOf(data[4]);
						world = plugin.getWorldWithId(worldid);
					} else {
						world = plugin.getServer().getWorld(data[4]);
						if(world==null)
						{
							world = plugin.getServer().createWorld(data[4], env);
						}
					}
					if(fileVersion.equals("0.4")){
						id = Integer.valueOf(data[7]);
						currentId = Math.max(currentId, id+1);
					} else {
						id = currentId++;
					}
					if((fileVersion.equals("0.6")||fileVersion.equals("0.7"))&&data.length>=9){
						setName(data[8]);
					}
					if(fileVersion.equals("0.7")&&data.length>=11){
						setOwner(data[9]);
						setProtect(Boolean.valueOf(data[10]));
					}
					if(world!=null){
						Block b = world.getBlockAt((int)(double)x,(int)(double)y,(int)(double)z);
						if(acceptsBlockType(b.getType())){
							this.containerBlock = (ContainerBlock)b.getState();
							this.block = b;
							if(this.containerBlock==null){
								loadedProperly = false;
								System.out.println("Chest is null...");
							}
						} else {
							System.out.println("Block is not accepted!");
							loadedProperly = false;
						}
					} else {
						System.out.println("World not found!");
						loadedProperly = false;
					}
				} else {
					System.out.println("Number of columns not accepted!");
					loadedProperly = false;
					return;
				}
			} else {
				System.out.println("Number of columns not accepted!");
				loadedProperly=false;
			}
		} else {
			System.out.println("File has invalid version: "+fileVersion);
			loadedProperly=false;
		}
	}

	public String save()
	{
		// VERSION!!!! 0.7
		String line = "";
		Location loc = block.getLocation();
		line = String.valueOf(loc.getX()) + "," + String.valueOf(loc.getY()) + "," + String.valueOf(loc.getZ()) + "," + String.valueOf(getRadius()) + "," + String.valueOf(loc.getWorld().getName());
		line += ",";
		line += "," + String.valueOf(loc.getWorld().getEnvironment());
		line += "," + String.valueOf(id);
		line += "," + name;
		line += "," + getOwner();
		line += "," + String.valueOf(isProtect());
		//Filter saving
		for(FilterType type : FilterType.values()){
			line += ";";
			int i = 0;
			for(Material m:getFilter(type))
			{
				if(i>0){
					line+=",";
				}
				line+=m.name();
				i++;
			}
			if(getFilter(type).size()==0){
				line+=type.toString();
			}
		}
		line+="\n";
		return line;
	}

	public boolean isLoadedProperly() {
		return loadedProperly;
	}

	public Block getBlock() {
		return block;
	}

	public void setBlock(Block block) {
		this.block = block;
	}

	static public boolean acceptsBlockType(Material m){
		return m.getId()==Material.CHEST.getId()
		||m.getId()==Material.DISPENSER.getId()
		||m.getId()==Material.FURNACE.getId()
		||m.getId()==Material.BURNING_FURNACE.getId();
	}

	public int getId() {
		return id;
	}

	public String listString(){
		String ret = "";
		ret+=getName();
		double p = getPercentFull();
		ret+=" | "+(int)(p*100)+"%";
		ret+=" | "+String.valueOf(getFilter(FilterType.SUCK).size()+getFilter(FilterType.PUSH).size()+getFilter(FilterType.PULL).size())+" ";
		ret+=" | "+String.valueOf(getRadius());
		return ret;
	}

	@SuppressWarnings("unused")
	public void minecartAction(StorageMinecart storage){
		ContainerBlock chest = getChest();
		Inventory chinv = chest.getInventory();
		Inventory miinv = storage.getInventory();
		if(false){
			//item stacks of furnaces:
			// stack 1: fuel, can be either wood or coal
			// stack 0: meltables, can be ores, sand, cobblestone, log and cactus
			// stack 2: products 
			ItemStack items[] = miinv.getContents();
			ItemStack furn[] = chinv.getContents();
			for(int i = 0; i<items.length;i++)
			{
				ItemStack is = items[i];
				if(is.getType().equals(Material.COAL)
						||is.getType().equals(Material.WOOD)
						||is.getType().equals(Material.LAVA_BUCKET)){
					//this is fuel
					ItemStack fs = furn[1];
					if(fs.getType().equals(is.getType())||fs.getAmount()==0){

						int remove = 0;
						if(fs.getAmount()==0){
							remove = 1;
						}

						fs.setType(is.getType());
						fs.setData(is.getData());

						if(fs.getAmount()+is.getAmount()-remove<=64){
							fs.setAmount(fs.getAmount()+is.getAmount()-remove);
							miinv.remove(is);
						} else {
							int remaining = fs.getAmount()+is.getAmount()-64;
							fs.setAmount(64);
							is.setAmount(remaining);
							miinv.setItem(i, is);
						}


						chinv.setItem(0, fs);
					}
				}
				if(is.getType().toString().contains("ORE")||
						is.getType().equals(Material.LOG)||
						is.getType().equals(Material.CACTUS)||
						is.getType().equals(Material.SAND)||
						is.getType().equals(Material.COBBLESTONE)){
					//this is burnable
					ItemStack fs = furn[0];
					if(fs.getType().equals(is.getType())||fs.getAmount()==0){

						int remove = 0;
						if(fs.getAmount()==0){
							remove = 1;
						}

						fs.setType(is.getType());
						fs.setData(is.getData());

						if(fs.getAmount()+is.getAmount()-remove<=64){
							fs.setAmount(fs.getAmount()+is.getAmount()-remove);
							miinv.remove(is);
						} else {
							int remaining = fs.getAmount()+is.getAmount()-64;
							fs.setAmount(64);
							is.setAmount(remaining);
							miinv.setItem(i, is);
						}


						chinv.setItem(0, fs);
					}
				}
			}
			if(furn[2].getAmount()!=0){
				ItemStack items1 = furn[2];
				HashMap<Integer,ItemStack> hash = miinv.addItem(items1);
				if(hash.size()!=0){
					items1.setAmount(items1.getAmount()-hash.get(0).getAmount());
				}
				chinv.remove(items1);
			}
		} else {
			for(int i = 0; i<chinv.getSize();i++){
				ItemStack items = chinv.getItem(i);
				if(items.getAmount()!=0){
					if(getFilter(FilterType.PUSH).contains(items.getType())){
						HashMap<Integer,ItemStack> hash = miinv.addItem(items);
						if(hash.size()!=0){
							ItemStack ret=hash.get(0);
							items.setAmount(ret.getAmount());
							chinv.setItem(i, items);
						}else {
							chinv.clear(i);
						}
					}
				}
			}
			for(int i = 0; i<miinv.getSize();i++){
				ItemStack items = miinv.getItem(i).clone();
				if(items.getAmount()!=0){
					if(getFilter(FilterType.PULL).contains(items.getType())){
						HashMap<Integer,ItemStack> hash = addItem(items, FilterType.PULL);
						if(hash.size()!=0){
							ItemStack ret=hash.get(0);
							items.setAmount(ret.getAmount());
							miinv.setItem(i, items);
						} else {
							miinv.clear(i);
						}
					}
				}
			}
		}
	}
	
	public String info(){
		String ret = ChatColor.WHITE.toString();
		String filterString = "";
		ret+="ID: "+ChatColor.YELLOW+getId()+ChatColor.WHITE+
		" Name: "+ChatColor.YELLOW+getName()+
		ChatColor.WHITE+" Radius: "+ChatColor.YELLOW+getRadius()+
		ChatColor.WHITE+" Owner: "+ChatColor.YELLOW+getOwner()+"\n";
		for(FilterType type:FilterType.values()){
			List<Material> filter = getFilter(type);
			if(filter.size()!=0)
			{
				filterString+=ChatColor.AQUA+type.toString()+":\n";
				boolean useId = false;
				if(filter.size()<5){
					useId = false;
				} else {
					useId = true;
				}
				for(int i = 0; i<filter.size();i++){
					Material m = filter.get(i);
					filterString+=ChatColor.YELLOW.toString();
					if(useId){
						filterString+=m.getId();
					} else {
						filterString+=m.toString();
					}
					if(i+1!=filter.size()){
						filterString+=ChatColor.WHITE+", ";
					} else {
						filterString+=ChatColor.WHITE+"\n";
					}
				}
			}
		}
		if(!filterString.equals("")){
			ret+=ChatColor.AQUA+"Filters:\n";
			ret+=filterString;
		}
		return ret;
	}

	public boolean isChest(){
		return block.getType().equals(Material.CHEST);
	}

	public boolean isFurnace(){
		return block.getType().equals(Material.FURNACE) || block.getType().equals(Material.BURNING_FURNACE);
	}

	public boolean isDispenser(){
		return block.getType().equals(Material.DISPENSER);
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		if(name.isEmpty()){
			return "#"+String.valueOf(id);
		}
		return name;
	}

	/**
	 * @return the location
	 */
	public Location getLocation() {
		return loc;
	}

	/**
	 * @param owner the owner to set
	 */
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/**
	 * @return the owner
	 */
	public String getOwner() {
		return owner;
	}

	/**
	 * @param protect the protect to set
	 */
	public void setProtect(boolean protect) {
		this.protect = protect;
	}

	/**
	 * @return the protect
	 */
	public boolean isProtect() {
		if(!plugin.config.isLetUsersProtectChests()){
			return false;
		}
		return protect;
	}
	
	
	/**
	 * @return the lastRedstoneDrop
	 */
	public long getLastRedstoneDrop() {
		return lastRedstoneDrop/1000;
	}


	
}
