package io.rcw.vibemine.ai.tools.message;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

@Named("send_message")
public final class SendMessageTool implements Tool<SendMessageTool.SendMessageInput, SendMessageTool.SendMessageOutput> {

    public record SendMessageInput(String componentJson) { }

    public record SendMessageOutput(boolean sent, String error) {
        public static SendMessageOutput error(String message) {
            return new SendMessageOutput(false, message);
        }
    }

    @Override
    public Class<SendMessageInput> inputClass() {
        return SendMessageInput.class;
    }

    @Override
    public Class<SendMessageOutput> outputClass() {
        return SendMessageOutput.class;
    }

    @Override
    public SendMessageOutput execute(Player player, SendMessageInput input) {
        if (input == null || input.componentJson() == null || input.componentJson().isBlank()) {
            return SendMessageOutput.error("Missing componentJson");
        }

        try {
            player.sendMessage(GsonComponentSerializer.gson().deserialize(input.componentJson()));
            return new SendMessageOutput(true, null);
        } catch (Exception exception) {
            return SendMessageOutput.error(exception.getMessage());
        }
    }

    @Override
    public String usage() {
        return """
                Send an Adventure JSON chat component to the player.
                Input: {"componentJson":"serialized Adventure component JSON"}
                You can pass componentJson returned by syntax_highlight.
                """;
    }
}
