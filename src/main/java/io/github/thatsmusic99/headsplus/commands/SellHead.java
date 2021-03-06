package io.github.thatsmusic99.headsplus.commands;

import io.github.thatsmusic99.headsplus.HeadsPlus;
import io.github.thatsmusic99.headsplus.api.events.SellHeadEvent;
import io.github.thatsmusic99.headsplus.commands.maincommand.DebugPrint;
import io.github.thatsmusic99.headsplus.config.HeadsPlusMessagesManager;
import io.github.thatsmusic99.headsplus.inventories.InventoryManager;
import io.github.thatsmusic99.headsplus.listeners.DeathEvents;
import io.github.thatsmusic99.headsplus.nms.NMSManager;
import io.github.thatsmusic99.headsplus.reflection.NBTManager;
import io.github.thatsmusic99.headsplus.util.CachedValues;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@CommandInfo(
        commandname = "sellhead",
        permission = "headsplus.sellhead",
        subcommand = "sellead",
        maincommand = false,
        usage = "/sellhead [All|Head ID] [#]"
)
public class SellHead implements CommandExecutor, IHeadsPlusCommand {

	private final HeadsPlusMessagesManager hpc = HeadsPlus.getInstance().getMessagesConfig();
	private static final List<String> headIds = new ArrayList<>();
	private final int[] slots;
	private final HeadsPlus hp;

	public SellHead(HeadsPlus hp) {
	    headIds.addAll(DeathEvents.ableEntities);
	    headIds.add("PLAYER");
	    if (hp.getNMSVersion().getOrder() > 3) {
	        slots = new int[46];
	        slots[45] = 45; // off-hand slot
        } else {
	        slots = new int[45];
        }
	    for (int i = 0; i < 45; i++) {
            slots[i] = i;
        }
	    this.hp = hp;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		try {

		    if (hp.canSellHeads()) {
		        if (sender instanceof Player) {
		            Player player = (Player) sender;
		            if (args.length == 0) {
		                // Open the GUI
		                if (hp.getConfiguration().getMechanics().getBoolean("sellhead-gui") && player.hasPermission("headsplus.sellhead.gui")) {
                            HashMap<String, String> context = new HashMap<>();
                            context.put("section", "mobs");
                            InventoryManager.getManager(player).open(InventoryManager.InventoryType.SELLHEAD_CATEGORY, context);
                            return true;
                        } else {
		                    // Get the item in the player's hand
		                    ItemStack item = checkHand(player);
		                    // If the item exists and is sellable,
		                    if (item != null && NBTManager.isSellable(item)) {
		                        // Get the ID
		                        String id = NBTManager.getType(item);
		                        if (headIds.contains(id)) {
		                            double price = NBTManager.getPrice(item) * item.getAmount();
		                            SellData data = new SellData(player);
		                            data.addID(id, item.getAmount());
		                            data.addSlot(player.getInventory().getHeldItemSlot(), item.getAmount());
                                    pay(player, data, price);
                                }
                            }
                        }
                    } else {
		                if (args[0].equalsIgnoreCase("all")) {
		                    getValidHeads(player, null, -1);
                        } else if (headIds.contains(args[0])) {
                            String fixedId = args[0];
                            int limit = -1;
                            if (args.length > 1) {
                                if (CachedValues.MATCH_PAGE.matcher(args[1]).matches()) {
                                    limit = Integer.parseInt(args[1]);
                                } else {
                                    sender.sendMessage(hpc.getString("commands.errors.invalid-input-int", sender));
                                    return false;
                                }
                            }
                            getValidHeads(player, fixedId, limit);
                        } else if (CachedValues.MATCH_PAGE.matcher(args[0]).matches()) {
                            getValidHeads(player, null, Integer.parseInt(args[0]));
                        } else {
                            sender.sendMessage(HeadsPlus.getInstance().getMessagesConfig().getString("commands.errors.invalid-args", sender));
                        }
                    }
                } else {
                    sender.sendMessage(HeadsPlus.getInstance().getMessagesConfig().getString("commands.errors.not-a-player", sender));
                }
            } else {
                sender.sendMessage(hpc.getString("commands.errors.disabled", sender));
            }
        } catch (Exception e) {
		    DebugPrint.createReport(e, "Command (sellhead)", true, sender);
		}
        return false;
	}

	public void getValidHeads(Player player, String fixedId, int limit) {
        double price = 0;
        SellData data = new SellData(player);
        for (int slot : slots) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && NBTManager.isSellable(item)) {
                String id = NBTManager.getType(item);
                if (fixedId != null) {
                    if (!fixedId.equals(id)) continue;
                } else if (!headIds.contains(id)){
                    continue;
                }
                double headPrice = NBTManager.getPrice(item);
                if (limit <= item.getAmount() && limit != -1) {
                    data.addSlot(slot, limit);
                    data.addID(id, limit);
                    price += headPrice * limit;
                    break;
                } else {
                    data.addSlot(slot, item.getAmount());
                    data.addID(id, item.getAmount());
                    price += headPrice * item.getAmount();
                    if (limit != -1) {
                        limit -= item.getAmount();
                    }
                }
            }
        }
        if (price > 0) {
            pay(player, data, price);
        } else {
            player.sendMessage(hpc.getString("commands.sellhead.no-heads", player));
        }
    }


	@SuppressWarnings("deprecation")
    private static ItemStack checkHand(Player p) {
		if (Bukkit.getVersion().contains("1.8")) {
			return p.getInventory().getItemInHand();
		} else {
			return p.getInventory().getItemInMainHand();
		}
	}

	private void pay(Player player, SellData data, double price) {
        double balance = hp.getEconomy().getBalance(player);
        SellHeadEvent event = new SellHeadEvent(price, player, balance, balance + price, data.ids);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            EconomyResponse response = hp.getEconomy().depositPlayer(player, price);
            if (response.transactionSuccess()) {
                if (price > 0) {
                    removeItems(player, data);
                    player.sendMessage(hpc.getString("commands.sellhead.sell-success", player).replaceAll("\\{price}", Double.toString(price))
                            .replaceAll("\\{balance}", hp.getConfiguration().fixBalanceStr(balance + price)));
                }
            } else {
                player.sendMessage(hpc.getString("commands.errors.cmd-fail", player));
            }
        }
    }

	public static class SellData {
	    private final HashMap<String, Integer> ids = new HashMap<>();
	    private final HashMap<Integer, Integer> slots = new HashMap<>();
	    private final UUID player;

	    public SellData(Player player) {
	        this.player = player.getUniqueId();
        }

        public HashMap<String, Integer> getIds() {
            return ids;
        }

        public UUID getPlayer() {
            return player;
        }

        public void addID(String id, int amount) {
	        if (ids.containsKey(id)) {
	            int currAmount = ids.get(id);
	            ids.put(id, currAmount + amount);
            } else {
	            ids.put(id, amount);
            }
        }

        public void addSlot(int slot, int amount) {
	        slots.put(slot, amount);
        }
    }


    private void removeItems(Player player, SellData data) {
	    for (int slot : data.slots.keySet()) {
	        ItemStack item = player.getInventory().getItem(slot);
	        int limit = data.slots.get(slot);
	        if (item != null) {
	            if (item.getAmount() > limit && limit != -1) {
	                item.setAmount(item.getAmount() - limit);
	                break;
                } else {
                    player.getInventory().setItem(slot, new ItemStack(Material.AIR));
                }
            }
        }
    }

    public static void registerHeadID(String name) {
	    if (!headIds.contains(name)) {
            headIds.add(name);
        }
    }

    public static List<String> getRegisteredIDs() {
	    return headIds;
    }

    @Override
    public String getCmdDescription(CommandSender sender) {
        return HeadsPlus.getInstance().getMessagesConfig().getString("descriptions.sellhead", sender);
    }

    @Override
    public boolean fire(String[] args, CommandSender sender) {
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return new ArrayList<>();
    }
}