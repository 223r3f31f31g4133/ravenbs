package keystrokesmod.command;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;

import java.util.ArrayList;
import java.util.List;

public abstract class Command implements IMinecraftInstance {
    protected String command;
    protected String[] alias;

    public Command(String command, String[] alias) {
        this.command = command;
        this.alias = alias;
    }

    public Command(String command) {
        this.command = command;
        this.alias = new String[]{command};
    }

    public abstract void onExecute(String[] args);

    public List<String> tabComplete(String[] args) {
        return new ArrayList<>();
    }

    protected void chatWithPrefix(String msg) {
        Utils.sendMessage("&7[&f" + this.command + "&7] &r" + (ModuleManager.lowercaseChatCommands() ? msg.toLowerCase() : msg));
    }

    protected void chat(String msg) {
        Utils.sendMessage(ModuleManager.lowercaseChatCommands() ? msg.toLowerCase() : msg);
    }

    protected void syntaxError() {
        Utils.sendMessage("§cSyntax error");
    }
}
