package net.uniodex.USB3;

import com.gmail.filoghost.holographicdisplays.api.placeholder.PlaceholderReplacer;
import com.koletar.jj.mineresetlite.Mine;
import com.koletar.jj.mineresetlite.MineResetLite;

public class USBPlaceholderReplacer implements PlaceholderReplacer {

    @Override
    public String update() {
        Mine mine = MineResetLite.instance.mines.get(0);
        return mine.getTimeUntilReset() + " dakika";
    }

}
