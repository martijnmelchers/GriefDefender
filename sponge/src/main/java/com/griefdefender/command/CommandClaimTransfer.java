package com.griefdefender.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;

import com.griefdefender.GDPlayerData;
import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.api.claim.ClaimResult;
import com.griefdefender.api.claim.ClaimResultType;
import com.griefdefender.api.claim.TrustTypes;
import com.griefdefender.api.data.PlayerData;
import com.griefdefender.cache.MessageCache;
import com.griefdefender.claim.GDClaim;
import com.griefdefender.configuration.MessageStorage;
import com.griefdefender.event.GDCauseStackManager;
import com.griefdefender.permission.GDPermissions;
import net.kyori.text.TextComponent;
import net.kyori.text.adapter.spongeapi.TextAdapter;
import net.kyori.text.format.TextColor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;

import java.util.UUID;

@CommandAlias("%griefdefender")
@CommandPermission(GDPermissions.COMMAND_TRANSFER_CLAIM)
public class CommandClaimTransfer extends BaseCommand {

    @CommandCompletion("@gdplayers @gddummy")
    @CommandAlias("claimtransfer|transferclaim")
    @Description("Transfers a basic or admin claim to another player.")
    @Syntax("<player>")
    @Subcommand("claim transfer")
    public void execute(Player player, User targetPlayer) {
        final GDPlayerData playerData = GriefDefenderPlugin.getInstance().dataStore.getOrCreatePlayerData(player.getWorld(), player.getUniqueId());
        final GDClaim claim = GriefDefenderPlugin.getInstance().dataStore.getClaimAtPlayer(playerData, player.getLocation());

        if (claim == null || claim.isWilderness()) {
            GriefDefenderPlugin.sendMessage(player, MessageCache.getInstance().CLAIM_NOT_FOUND);
            return;
        }

        final UUID ownerId = claim.getOwnerUniqueId();
        final boolean isAdmin = playerData.canIgnoreClaim(claim);
        // check permission
        if (!isAdmin && claim.isAdminClaim() && !player.hasPermission(GDPermissions.COMMAND_ADMIN_CLAIMS)) {
            GriefDefenderPlugin.sendMessage(player, MessageCache.getInstance().PERMISSION_CLAIM_TRANSFER_ADMIN);
            return;
        } else if (!isAdmin && !player.getUniqueId().equals(ownerId) && claim.isUserTrusted(player, TrustTypes.MANAGER)) {
            if (claim.parent == null) {
                // Managers can only transfer child claims
                GriefDefenderPlugin.sendMessage(player, MessageCache.getInstance().CLAIM_NOT_YOURS);
                return;
            }
        } else if (!isAdmin && !claim.isAdminClaim() && !player.getUniqueId().equals(ownerId)) {
            // verify ownership
            GriefDefenderPlugin.sendMessage(player, MessageCache.getInstance().CLAIM_NOT_YOURS);
            return;
        }

        // change ownership
        GDCauseStackManager.getInstance().pushCause(player);
        final ClaimResult claimResult = claim.transferOwner(targetPlayer.getUniqueId());
        if (!claimResult.successful()) {
            PlayerData targetPlayerData = GriefDefenderPlugin.getInstance().dataStore.getOrCreatePlayerData(player.getWorld(), targetPlayer.getUniqueId());
            if (claimResult.getResultType() == ClaimResultType.INSUFFICIENT_CLAIM_BLOCKS) {
                TextAdapter.sendComponent(player, TextComponent.of("Could not transfer claim to player with UUID " + targetPlayer.getUniqueId() + "."
                    + " Player only has " + targetPlayerData.getRemainingClaimBlocks() + " claim blocks remaining." 
                    + " The claim requires a total of " + claim.getClaimBlocks() + " claim blocks to own.", TextColor.RED));
            } else if (claimResult.getResultType() == ClaimResultType.WRONG_CLAIM_TYPE) {
                TextAdapter.sendComponent(player, TextComponent.of("The wilderness claim cannot be transferred.", TextColor.RED));
            } else if (claimResult.getResultType() == ClaimResultType.CLAIM_EVENT_CANCELLED) {
                TextAdapter.sendComponent(player, TextComponent.of("Could not transfer the claim. A plugin has cancelled the TransferClaimEvent.", TextColor.RED));
            } else {
                TextAdapter.sendComponent(player, TextComponent.of("Could not transfer the claim. " + claimResult.getResultType().name(), TextColor.RED));
            }
        } else {
            GriefDefenderPlugin.sendMessage(player, GriefDefenderPlugin.getInstance().messageData.getMessage(MessageStorage.CLAIM_TRANSFER_SUCCESS));
        }
    }
}
