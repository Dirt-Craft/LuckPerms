package me.lucko.luckperms.forge.messaging;

import net.minecraft.network.PacketBuffer;

import java.nio.charset.StandardCharsets;

public class ForgeMessagePacket {
    private final String message;
    public ForgeMessagePacket(String message){
        this.message = message;
    }

    public ForgeMessagePacket(PacketBuffer buf){
        this.message = new String(buf.readByteArray(), StandardCharsets.UTF_8);
    }

    public void toPacket(PacketBuffer buf){
        buf.writeByteArray(message.getBytes(StandardCharsets.UTF_8));
    }

    public String getMessage(){
        return message;
    }
}
