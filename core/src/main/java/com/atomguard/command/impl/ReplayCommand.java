package com.atomguard.command.impl;

import com.atomguard.AtomGuard;
import com.atomguard.command.SubCommand;
import com.atomguard.forensics.AttackSnapshot;
import com.atomguard.forensics.ForensicsManager;
import com.atomguard.forensics.ForensicsReport;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /ag replay <list|latest|id|export> komutu.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class ReplayCommand implements SubCommand {

    private final AtomGuard plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ReplayCommand(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getName() { return "replay"; }
    @Override public @NotNull String getDescriptionKey() { return "adli-analiz.komut-aciklama"; }
    @Override public @NotNull String getPermission() { return "atomguard.admin"; }
    @Override public @NotNull String getUsageKey() { return "adli-analiz.kullanim"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        ForensicsManager fm = plugin.getForensicsManager();
        if (fm == null || !fm.isEnabled()) {
            sender.sendMessage(mm.deserialize("<red>Adli analiz sistemi devre dışı."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /ag replay <list|latest|<id>|export <id>>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> handleList(sender, fm);
            case "latest" -> handleLatest(sender, fm);
            case "export" -> handleExport(sender, args, fm);
            default -> handleById(sender, args[1], fm);
        }
    }

    private void handleList(CommandSender sender, ForensicsManager fm) {
        List<AttackSnapshot> snapshots = fm.getRecentSnapshots();
        sender.sendMessage(mm.deserialize("<aqua>━━━ Son Saldırılar ━━━"));

        if (snapshots.isEmpty()) {
            sender.sendMessage(mm.deserialize("<gray>Kayıtlı saldırı bulunamadı."));
            return;
        }

        for (int i = 0; i < snapshots.size(); i++) {
            sender.sendMessage(mm.deserialize(ForensicsReport.formatListLine(i + 1, snapshots.get(i))));
        }
        sender.sendMessage(mm.deserialize("<gray>ID ile detay için: /ag replay <id>"));
        sender.sendMessage(mm.deserialize("<aqua>━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleLatest(CommandSender sender, ForensicsManager fm) {
        AttackSnapshot latest = fm.getLatestSnapshot();
        if (latest == null) {
            sender.sendMessage(mm.deserialize("<gray>Henüz kayıtlı saldırı yok."));
            return;
        }
        sendReport(sender, latest);
    }

    private void handleById(CommandSender sender, String idPrefix, ForensicsManager fm) {
        AttackSnapshot snap = fm.getSnapshot(idPrefix);
        if (snap == null) {
            sender.sendMessage(mm.deserialize("<red>✗ Saldırı kaydı bulunamadı: " + idPrefix));
            return;
        }
        sendReport(sender, snap);
    }

    private void handleExport(CommandSender sender, String[] args, ForensicsManager fm) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /ag replay export <id>"));
            return;
        }
        String idPrefix = args[2];
        AttackSnapshot snap = fm.getSnapshot(idPrefix);
        if (snap == null) {
            sender.sendMessage(mm.deserialize("<red>✗ Saldırı kaydı bulunamadı: " + idPrefix));
            return;
        }
        String path = fm.getForensicsDir() + "/attack-" + snap.getSnapshotId() + ".json";
        sender.sendMessage(mm.deserialize("<green>✓ Rapor dosyası: <white>" + path));
    }

    private void sendReport(CommandSender sender, AttackSnapshot snap) {
        for (String line : ForensicsReport.format(snap)) {
            sender.sendMessage(mm.deserialize(line));
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return Arrays.asList("list", "latest", "export");
        return Collections.emptyList();
    }
}
