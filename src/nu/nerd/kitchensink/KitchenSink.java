package nu.nerd.kitchensink;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.bukkit.Art;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public class KitchenSink extends JavaPlugin {
	private static final int ONE_MINUTE = 60 * 20;
	private KitchenSinkListener listener = new KitchenSinkListener(this);
	private LagCheck lagCheck = new LagCheck();
	public final Configuration config = new Configuration(this);
	public final static Logger log = Logger.getLogger("Minecraft");
	public final List<Recipe> recipeList = new ArrayList<Recipe>();

	/**
	 * Key of Player metadata used to record most recently selected painting.
	 */
	public static final String PAINTING_META_KEY = "KitchenSink.painting";

	/**
	 * Key of Player metadata set to signify that the next right click on a
	 * horse is an attempted lock/unlock. Value is the new boolean lock state.
	 */
	public static final String HORSE_DO_LOCK_KEY = "KitchenSink.do_lock";

	/**
	 * Key of Horse metadata set to signify that the horse is unlocked for
	 * riding by players other than the owner. If absent, horse is locked.
	 */
	public static final String HORSE_UNLOCKED_KEY = "KitchenSink.unlocked";

	/**
	 * Key of Player metadata set to signify that the next right click on a
	 * Tameable mob owned by the player will un-tame the mob.
	 */
	public static final String UNTAME_KEY = "KitchenSink.untame";

	@Override
	public void onDisable() {
		getServer().getScheduler().cancelTasks(this);

		Iterator<Recipe> recipeIterator = getServer().recipeIterator();
		while (recipeIterator.hasNext()) {
			Recipe r = recipeIterator.next();
			if (recipeList.contains(r)) {
				recipeIterator.remove();
				recipeList.remove(r);
			}
		}
	}

	@Override
	public void onEnable() {
		File config_file = new File(getDataFolder(), "config.yml");
		if (!config_file.exists()) {
			getConfig().options().copyDefaults(true);
			saveConfig();
		}

		config.load();

		if (config.SAFE_BOATS) {
			getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				public void run() {
					for (World world : getServer().getWorlds()) {
						for (Boat boat : world.getEntitiesByClass(Boat.class)) {
							if (boat.isEmpty()) {
								boat.remove();
								if (config.SAFE_BOATS_DROP) {
									world.dropItem(boat.getLocation(), new ItemStack(Material.BOAT, 1));
								}
							}
						}
					}
				}
			}, config.SAFE_BOATS_DELAY, config.SAFE_BOATS_DELAY);
		}

		if (config.SAFE_MINECARTS) {
			getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				public void run() {
					for (World world : getServer().getWorlds()) {
						for (Minecart minecart : world.getEntitiesByClass(Minecart.class)) {
							if (minecart.isEmpty()) {
								if (config.SAFE_SPECIAL_CARTS) {
									if (minecart instanceof StorageMinecart || minecart instanceof PoweredMinecart) {
										continue;
									}
								}
								minecart.remove();
								if (config.SAFE_MINECARTS_DROP) {
									world.dropItem(minecart.getLocation(), new ItemStack(Material.MINECART, 1));
								}
							}
						}
					}
				}
			}, config.SAFE_MINECARTS_DELAY, config.SAFE_MINECARTS_DELAY);
		}
		if (config.ANIMAL_COUNT) {
			final BukkitScheduler sched = getServer().getScheduler();
			Runnable task = new Runnable() {
				@Override
				public void run() {
					Future<Collection<LivingEntity>> future = sched.callSyncMethod(KitchenSink.this, new Callable<Collection<LivingEntity>>() {
						@Override
						public Collection<LivingEntity> call() throws Exception {
							return getServer().getWorlds().get(0).getEntitiesByClass(LivingEntity.class);
						}
					});

					Collection<LivingEntity> livingEntities;
					try {
						livingEntities = future.get();
						System.out.println("-!- Starting Mob count");
						HashMap<String, Integer> a = new HashMap<String, Integer>();
						for (LivingEntity animal : livingEntities) {
							if (a.containsKey(animal.getType().name())) {
								a.put(animal.getType().name(), a.get(animal.getType().name()) + 1);
							} else {
								a.put(animal.getType().name(), 1);
							}
						}
						System.out.println("-!- " + a);
					} catch (Exception ex) {
					}
				}
			};
			sched.runTaskTimerAsynchronously(this, task, ONE_MINUTE, 10 * ONE_MINUTE);
		}

		if (config.LEATHERLESS_BOOKS) {
			ShapelessRecipe cheapBook = new ShapelessRecipe(new ItemStack(Material.BOOK));
			cheapBook.addIngredient(3, Material.PAPER);
			getServer().addRecipe(cheapBook);
			recipeList.add(cheapBook);
		}

		if (config.HORSE_RECIPES) {
			ShapedRecipe ironHorseArmor = new ShapedRecipe(new ItemStack(Material.IRON_BARDING))
			.shape("  b", "ilb", "iii")
			.setIngredient('l', Material.LEATHER)
			.setIngredient('i', Material.IRON_INGOT)
			.setIngredient('b', Material.IRON_BLOCK);
			getServer().addRecipe(ironHorseArmor);
			recipeList.add(ironHorseArmor);

			ShapedRecipe goldHorseArmor = new ShapedRecipe(new ItemStack(Material.GOLD_BARDING))
			.shape("  b", "ilb", "iii")
			.setIngredient('l', Material.LEATHER)
			.setIngredient('i', Material.GOLD_INGOT)
			.setIngredient('b', Material.GOLD_BLOCK);
			getServer().addRecipe(goldHorseArmor);
			recipeList.add(goldHorseArmor);

			ShapedRecipe diamondHorseArmor = new ShapedRecipe(new ItemStack(Material.DIAMOND_BARDING))
			.shape("  b", "ilb", "iii")
			.setIngredient('l', Material.LEATHER)
			.setIngredient('i', Material.DIAMOND)
			.setIngredient('b', Material.DIAMOND_BLOCK);
			getServer().addRecipe(diamondHorseArmor);
			recipeList.add(diamondHorseArmor);

			ShapedRecipe nameTag = new ShapedRecipe(new ItemStack(Material.NAME_TAG))
			.shape(" bb", " bb", " bb")
			.setIngredient('b', Material.IRON_BLOCK);
			getServer().addRecipe(nameTag);
			recipeList.add(nameTag);

			ShapedRecipe saddle = new ShapedRecipe(new ItemStack(Material.SADDLE))
			.shape("lll", "lll", "l l")
			.setIngredient('l', Material.LEATHER);
			getServer().addRecipe(saddle);
			recipeList.add(saddle);
		}

		getServer().getScheduler().scheduleSyncRepeatingTask(this, lagCheck, 20, 20);
		getServer().getPluginManager().registerEvents(listener, this);

		// For /nextrestart
		config.NEXT_RESTART = (System.currentTimeMillis() / 1000L) + config.RESTART_TIME;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String name, String[] args) {
		if (command.getName().equalsIgnoreCase("unenchant") && sender instanceof Player) {
			Player player = (Player) sender;
			try {
				if (player.getItemInHand().getType().equals(Material.ENCHANTED_BOOK)) {
					player.setItemInHand(new ItemStack(Material.BOOK));
				} else {
					for (Enchantment e : player.getItemInHand().getEnchantments().keySet()) {
						player.getItemInHand().removeEnchantment(e);
					}
				}
				player.sendMessage("Enchantments removed.");
			} catch (Exception e) {
				player.sendMessage("No enchantments removed.");
			}
			return true;
		}
		if (command.getName().equalsIgnoreCase("lag")) {
			if (sender.hasPermission("kitchensink.lag")) {
				sendLagStats(sender);
				return true;
			}
		}
		if (command.getName().equalsIgnoreCase("list")) {
			if (sender.hasPermission("kitchensink.list")) {
				sendList(sender);
				return true;
			}
		}
		if (command.getName().equalsIgnoreCase("ksinventory")) {
			if (args.length >= 1) {
				Player mutee = getServer().getPlayer(args[0]);
				if (args.length == 2) {
					if (args[1].equals("clear")) {
						mutee.getInventory().clear();
						mutee.getInventory().setArmorContents(new ItemStack[mutee.getInventory().getArmorContents().length]);
						mutee.saveData();
						sender.sendMessage("Inventory Cleared.");
						return true;
					}
				}
				if (mutee != null && sender instanceof Player) {
					((Player) sender).openInventory(mutee.getPlayer().getInventory());
				}
				return true;
			}
		}
		if (command.getName().equalsIgnoreCase("painting")) {
			// No arguments ==> list all painting types.
			if (args.length == 0) {
				StringBuilder message = new StringBuilder();
				message.append(ChatColor.GOLD);
				message.append("Available paintings: ");
				for (int i = 0; i < Art.values().length; ++i) {
					Art art = Art.values()[i];
					message.append(ChatColor.YELLOW);
					message.append(art.name().toLowerCase());
					message.append(ChatColor.GRAY);
					message.append(" (");
					message.append(art.getBlockWidth());
					message.append('x');
					message.append(art.getBlockHeight());
					message.append(")");
					if (i < Art.values().length - 1) {
						message.append(", ");
					}
				}
				sender.sendMessage(message.toString());
				return true;
			} else if (args.length == 1) {
				if (sender instanceof Player) {
					try {
						Player player = (Player) sender;
						Art art = Art.getByName(args[0]);
						player.setMetadata("KitchenSink.painting", new FixedMetadataValue(this, art));
						sender.sendMessage(ChatColor.GOLD + "The next painting you place will be: " +
											ChatColor.YELLOW + art.name().toLowerCase());
					} catch (Exception ex) {
						sender.sendMessage(ChatColor.RED + "Unknown painting: " + args[0]);
					}
				} else {
					sender.sendMessage("You need to be in-game to place paintings.");
				}
				return true;
			}
		}

		if (command.getName().equalsIgnoreCase("nextrestart")) {
			int time = (int) (config.NEXT_RESTART - (System.currentTimeMillis() / 1000L));
			if (time < 120) {
				sender.sendMessage("The server will restart in " + time + " second" + ((time == 1) ? "" : "s"));
			} else {
				sender.sendMessage("The server will restart in " + time / 60 + " minute" + ((time == 1) ? "" : "s"));
			}

			return true;
		}

		if (command.getName().equalsIgnoreCase("lock-horse")) {
			setHorseLockState(sender, true);
			return true;
		} else if (command.getName().equalsIgnoreCase("unlock-horse")) {
			setHorseLockState(sender, false);
			return true;
		} else if (command.getName().equalsIgnoreCase("untame")) {
			if (config.UNTAME_PETS) {
				if (sender instanceof Player) {
					Player player = (Player) sender;
					player.setMetadata(UNTAME_KEY, new FixedMetadataValue(this, null));
					sender.sendMessage(ChatColor.GOLD + "Right click on a pet that you own.");
				} else {
					sender.sendMessage("You need to be in-game to untame pets.");
				}
			} else {
				sender.sendMessage(ChatColor.RED + "That command is disabled.");
			}
			return true;
		}

		return false;
	}

	public void sendLagStats(CommandSender sender) {
		float tps = 0;
		for (Long l : lagCheck.history) {
			if (l != null)
				tps += 20 / (l / (float) 1000);
		}
		tps = tps / lagCheck.history.size();
		if (tps > 20) {
			tps = 20;
		}
		long memUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
		long memMax = Runtime.getRuntime().maxMemory() / 1048576;

		sender.sendMessage(String.format("TPS: %5.2f Mem: %dM/%dM", tps, memUsed, memMax));
	}

	public void sendList(CommandSender sender) {
		ArrayList<String> list = new ArrayList<String>();
		for (Player player : getServer().getOnlinePlayers()) {
			list.add(player.getName());
		}
		Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
		sender.sendMessage("Players Online: " + list.size());
		if (list.size() == 0) {
			return;
		}
		String onlinelist = "Players:";
		int index = 0;
		for (String p : list) {
			ChatColor color = ChatColor.GRAY;
			if (index++ % 2 == 0) {
				color = ChatColor.WHITE;
			}
			onlinelist += " " + color + p;
		}
		sender.sendMessage(onlinelist);
	}

	/**
	 * Handle the /lock-horse and /unlock-horse commands.
	 * 
	 * The player must subsequently right click on the horse.
	 * 
	 * @param sender the sender of the command.
	 * @param locked true if the request is to lock the horse; false for unlock.
	 */
	protected void setHorseLockState(CommandSender sender, boolean locked) {
		if (config.LOCK_HORSES) {
			if (sender instanceof Player) {
				Player player = (Player) sender;
				player.setMetadata(HORSE_DO_LOCK_KEY, new FixedMetadataValue(this, locked));
				sender.sendMessage(ChatColor.GOLD + "Right click on a horse that you own.");
			} else {
				sender.sendMessage("You need to be in-game to lock horses.");
			}
		} else {
			sender.sendMessage(ChatColor.RED + "That command is disabled.");
		}
	}
}
