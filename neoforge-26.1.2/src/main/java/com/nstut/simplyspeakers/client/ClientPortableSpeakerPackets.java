package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.network.OpenPortableSpeakerPacketS2C;
import com.nstut.simplyspeakers.network.PortableSpeakerPositionPacketS2C;
import com.nstut.simplyspeakers.portable.PortableSpeakerEndpoint;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-only packet application. Payload classes stay independently loadable
 * on dedicated servers, including during verifier/codec initialization.
 */
public final class ClientPortableSpeakerPackets {
    private ClientPortableSpeakerPackets() {}

    public static void position(PortableSpeakerPositionPacketS2C packet) {
        ClientPortableSpeakers.update(packet.getPos(), packet.getSnapshot());
    }

    public static void open(OpenPortableSpeakerPacketS2C packet) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !ClientPortableSpeakers.isPortableToken(packet.getPos())) return;
        var endpoint = new PortableSpeakerEndpoint(packet.getIdentity(), packet.getPos(), packet.getSpeakerId());
        endpoint.setLevel(client.level);
        // Ignore delayed opens after the client changed dimensions.
        if (!endpoint.getFullStateKey().equals(packet.getFullStateKey())) return;
        if (client.screen instanceof SpeakerScreen screen && screen.refreshPortableEndpoint(endpoint)) return;
        client.setScreen(new SpeakerScreen(endpoint));
    }
}
