package io.github.thatsmusic99.headsplus.api.challenges;

import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import io.github.thatsmusic99.headsplus.api.Challenge;
import org.bukkit.inventory.ItemStack;

public class SellheadChallenge extends Challenge {
    public SellheadChallenge(String key, ConfigSection section, ItemStack icon, ItemStack completeIcon) {
        super(key, section, icon, completeIcon);
    }

    @Override
    public String getDatabaseType() {
        return "headsplussh";
    }
}
