package keystrokesmod.command.impl;

import keystrokesmod.utility.Utils;
import keystrokesmod.command.Command;
import keystrokesmod.utility.system.SystemUtils;

public class Name extends Command {
    public Name() {
        super("name", new String[] { "ign", "name" });
    }

    @Override
    public void onExecute(String[] args) {
        if (!Utils.nullCheck()) {
            return;
        }
        SystemUtils.addToClipboard(mc.thePlayer.getName());
        chatWithPrefix("&7Copied &b" + mc.thePlayer.getName() + " &7to clipboard");
    }
}
