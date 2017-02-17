package me.nikl.minesweeper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import me.nikl.gamebox.game.IGameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.Wool;

public class GameManager implements IGameManager{

	private Main plugin;
	private Map<UUID, Game> games;
	private Language lang;
	private ItemStack covered, flagged, mine, number;
	private ItemStack[] items;

	private Map<String,GameRules> gameTypes;

	private float volume = 0.5f;

	public GameManager(Main plugin){
		this.games = new HashMap<>();
		this.plugin = plugin;
		this.lang = plugin.lang;

		if(!getMaterials()){
			Bukkit.getConsoleSender().sendMessage(plugin.chatColor(lang.PREFIX+" &4Failed to load materials from config"));
			Bukkit.getConsoleSender().sendMessage(plugin.chatColor(lang.PREFIX+" &4Using default materials"));
			this.flagged = new ItemStack(Material.SIGN);
			ItemMeta metaFlagged = flagged.getItemMeta();
			metaFlagged.setDisplayName(ChatColor.translateAlternateColorCodes('&',"&aFlag"));
			flagged.setItemMeta(metaFlagged);
			flagged.setAmount(1);
			this.covered = new ItemStack(Material.STAINED_GLASS_PANE);
			covered.setDurability((short) 8);
			ItemMeta metaCovered = covered.getItemMeta();
			metaCovered.setDisplayName(ChatColor.translateAlternateColorCodes('&',"&1Cover"));
			covered.setItemMeta(metaCovered);
			covered.setAmount(1);
			this.mine = new ItemStack(Material.TNT);
			ItemMeta metaMine = mine.getItemMeta();
			metaMine.setDisplayName(ChatColor.translateAlternateColorCodes('&',"&4Mine"));
			mine.setItemMeta(metaMine);
			this.number = new Wool(DyeColor.ORANGE).toItemStack();
			ItemMeta metaNumber = number.getItemMeta();
			metaNumber.setDisplayName(ChatColor.translateAlternateColorCodes('&',"&6Warning"));
			number.setItemMeta(metaNumber);
		}


		this.items = new ItemStack[]{covered,flagged,number,mine};
	}


	private Boolean getMaterials() {
		Boolean worked = true;

		Material mat = null;
		int data = 0;
		for(String key : Arrays.asList("cover", "warning", "mine", "flag")){
			if(!plugin.getConfig().isSet("materials." + key)) return false;
			String value = plugin.getConfig().getString("materials." + key);
			String[] obj = value.split(":");
			String name = "default";
			boolean named = false;
			if(plugin.getConfig().isSet("displaynames." + key) && plugin.getConfig().isString("displaynames." + key)){
				name = plugin.getConfig().getString("displaynames." + key);
				named = true;
			}


			if (obj.length == 2) {
				try {
					mat = Material.matchMaterial(obj[0]);
				} catch (Exception e) {
					worked = false; // material name doesn't exist
				}

				try {
					data = Integer.valueOf(obj[1]);
				} catch (NumberFormatException e) {
					worked = false; // data not a number
				}
			} else {
				try {
					mat = Material.matchMaterial(value);
				} catch (Exception e) {
					worked = false; // material name doesn't exist
				}
			}
			if(mat == null) return false;
			if(key.equals("cover")){
				this.covered = new ItemStack(mat, 1);
				if (obj.length == 2) covered.setDurability((short) data);
				ItemMeta metaCovered = covered.getItemMeta();
				metaCovered.setDisplayName("Cover");
				if(named)
					metaCovered.setDisplayName(plugin.chatColor(name));
				covered.setItemMeta(metaCovered);
				covered.setAmount(1);

			} else if(key.equals("warning")){
				this.number = new ItemStack(mat, 1);
				if (obj.length == 2) number.setDurability((short) data);
				ItemMeta metaNumber = number.getItemMeta();
				metaNumber.setDisplayName("Warning");
				if(named)
					metaNumber.setDisplayName(plugin.chatColor(name));
				number.setItemMeta(metaNumber);

			} else if(key.equals("mine")){
				this.mine = new ItemStack(mat, 1);
				if (obj.length == 2) mine.setDurability((short) data);
				ItemMeta metaMine = mine.getItemMeta();
				metaMine.setDisplayName("Boooom");
				if(named)
					metaMine.setDisplayName(plugin.chatColor(name));
				mine.setItemMeta(metaMine);

			} else if(key.equals("flag")){
				this.flagged = new ItemStack(mat, 1);
				if (obj.length == 2) flagged.setDurability((short) data);
				ItemMeta metaFlagged = flagged.getItemMeta();
				metaFlagged.setDisplayName("Flag");
				if(named)
					metaFlagged.setDisplayName(plugin.chatColor(name));
				flagged.setItemMeta(metaFlagged);
				flagged.setAmount(1);
			}
		}

		return worked;
	}


	@Override
	public boolean onInventoryClick(InventoryClickEvent event) {
		if(!isInGame(event.getWhoClicked().getUniqueId()) || event.getInventory() == null){
			return false;
		}
		int slot = event.getSlot();
		Game game = games.get(event.getWhoClicked().getUniqueId());
		if(!game.isStarted()){
			game.start();
		}
		Player player = (Player) event.getWhoClicked();
		if(game.isCovered(slot)){
			if(event.getAction().equals(InventoryAction.PICKUP_HALF)){
				game.setFlagged(slot);
				if(Main.playSounds)player.playSound(player.getLocation(), Sounds.CLICK.bukkitSound(), volume, 1f);
			} else if (event.getAction().equals(InventoryAction.PICKUP_ALL)){
				game.uncover(slot);
				if(game.isWon()){
					game.cancelTimer();
					game.reveal();
					game.setState(lang.TITLE_END.replaceAll("%timer%", game.getDisplayTime()+""));
					if(Main.playSounds)player.playSound(player.getLocation(), Sounds.LEVEL_UP.bukkitSound(), volume, 1f);
					if(plugin.econEnabled && !event.getWhoClicked().hasPermission("gamebox.bypass") && !event.getWhoClicked().hasPermission("gamebox.bypass." + plugin.getGameID()) && gameTypes.get(game.getRule()).getReward() > 0.0){
						Main.econ.depositPlayer(player, gameTypes.get(game.getRule()).getReward());
						player.sendMessage(plugin.chatColor(lang.PREFIX + lang.GAME_WON_MONEY.replaceAll("%reward%", plugin.getReward()+"")));
					}
					if(plugin.wonCommandsEnabled && !event.getWhoClicked().hasPermission("gamebox.bypass") && !event.getWhoClicked().hasPermission("gamebox.bypass." + plugin.getGameID())){
						if(plugin.wonCommands != null && !plugin.wonCommands.isEmpty()) {
							for (String cmd : plugin.wonCommands) {
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
							}
						}
					}
				} else {
					if(Main.playSounds)player.playSound(player.getLocation(), Sounds.CLICK.bukkitSound(), volume, 1f);
				}
			}
		} else if(game.isFlagged(slot) && event.getAction().equals(InventoryAction.PICKUP_HALF)){
			game.deFlag(slot);
			if(Main.playSounds)player.playSound(player.getLocation(), Sounds.CLICK.bukkitSound(), volume, 1f);
		}
		return true;
	}

	@Override
	public boolean onInventoryClose(InventoryCloseEvent inventoryCloseEvent) {
		if(!isInGame(inventoryCloseEvent.getPlayer().getUniqueId()))
			return false;
		games.get(inventoryCloseEvent.getPlayer().getUniqueId()).cancelTimer();
		games.remove(inventoryCloseEvent.getPlayer().getUniqueId());
		return true;
	}

	@Override
	public boolean isInGame(UUID uuid) {
		return games.keySet().contains(uuid);
	}

	@Override
	public boolean startGame(Player[] players, String... strings) {
		// first and only argument atm is the number of bombs
		if(strings.length != 1){
			Bukkit.getLogger().log(Level.WARNING, " unknown number of arguments to start a game: " + Arrays.asList(strings));
			return false;
		}
		GameRules rule = gameTypes.get(strings[0]);
		if(rule == null){
			Bukkit.getLogger().log(Level.WARNING, " unknown argument to start a game: " + Arrays.asList(strings));
			return false;
		}
		games.put(players[0].getUniqueId(), new Game(plugin, players[0].getUniqueId(), rule.getNumberOfBombs(), items, strings[0]));
		return true;
	}

	@Override
	public void removeFromGame(UUID uuid) {
		games.get(uuid).cancelTimer();
		games.remove(uuid);
	}

	public void setGameTypes(Map<String,GameRules> gameTypes) {
		this.gameTypes = gameTypes;
	}
}
