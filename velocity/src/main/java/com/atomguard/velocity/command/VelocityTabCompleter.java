package com.atomguard.velocity.command;

import com.atomguard.velocity.AtomGuardVelocity;
import com.velocitypowered.api.command.SimpleCommand;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /agv komutu için tab-complete.
 */
public class VelocityTabCompleter implements SimpleCommand {

    private static final List<String> SUBCOMMANDS = List.of(
        "durum", "yenile", "modul", "yasak", "af", "istatistik", "saldiri"
    );

    private final AtomGuardVelocity plugin;

    public VelocityTabCompleter(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        // Tab complete sınıfı yalnızca öneri sağlar
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0 || args.length == 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("modul")) {
            return plugin.getModuleManager().getAll().stream()
                .map(m -> m.getName())
                .filter(n -> n.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("atomguard.admin");
    }
}
