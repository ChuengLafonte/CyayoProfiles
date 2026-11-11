package id.cyayo.profiles.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemSerializer {

    public static String itemStackArrayToBase64(ItemStack[] items) {
        if (items == null || items.length == 0) return "";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) {
        if (data == null || data.trim().isEmpty()) return new ItemStack[0];
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];

            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
    }

    public static String itemStackToBase64(ItemStack item) {
        if (item == null) return "";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static ItemStack itemStackFromBase64(String data) {
        if (data == null || data.trim().isEmpty()) return null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String potionEffectsToString(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PotionEffect effect : effects) {
            if (sb.length() > 0) sb.append(";");
            sb.append(effect.getType().getName()).append(",")
              .append(effect.getDuration()).append(",")
              .append(effect.getAmplifier()).append(",")
              .append(effect.isAmbient()).append(",")
              .append(effect.hasParticles()).append(",")
              .append(effect.hasIcon());
        }
        return sb.toString();
    }

    public static Collection<PotionEffect> potionEffectsFromString(String data) {
        List<PotionEffect> effects = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) return effects;

        String[] parts = data.split(";");
        for (String part : parts) {
            try {
                String[] values = part.split(",");
                if (values.length >= 3) {
                    PotionEffectType type = PotionEffectType.getByName(values[0]);
                    if (type != null) {
                        int duration = Integer.parseInt(values[1]);
                        int amplifier = Integer.parseInt(values[2]);
                        boolean ambient = values.length > 3 && Boolean.parseBoolean(values[3]);
                        boolean particles = values.length > 4 && Boolean.parseBoolean(values[4]);
                        boolean icon = values.length <= 5 || Boolean.parseBoolean(values[5]);
                        effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return effects;
    }

    public static String locationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + "," +
                loc.getX() + "," +
                loc.getY() + "," +
                loc.getZ() + "," +
                loc.getYaw() + "," +
                loc.getPitch();
    }

    public static Location locationFromString(String data) {
        if (data == null || data.trim().isEmpty()) return null;
        try {
            String[] parts = data.split(",");
            if (parts.length >= 4) {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) return null;
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
                float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
                return new Location(world, x, y, z, yaw, pitch);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
