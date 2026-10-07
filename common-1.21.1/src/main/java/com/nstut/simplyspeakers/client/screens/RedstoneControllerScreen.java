package com.nstut.simplyspeakers.client.screens;
import com.nstut.openui.api.*;
import com.nstut.openui.controls.Card;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.state.*;
import com.nstut.openui.layout.Justification;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity;
import com.nstut.simplyspeakers.client.ui.SimplySpeakersUiScreen;
import com.nstut.simplyspeakers.client.ui.SpeakerButtonWidget;
import com.nstut.simplyspeakers.control.ControllerAction;
import com.nstut.simplyspeakers.network.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Supplier;

/** Small action-specific form; no face assignments or user-defined trigger rules. */
public class RedstoneControllerScreen extends SimplySpeakersUiScreen {
    private final BlockPos pos;
    private final Signal<String> network=Signals.of(""), proxyCoordinates=Signals.of(""), clip=Signals.of("");
    private final Signal<Boolean> proxy=Signals.of(false), restart=Signals.of(false);
    private final Signal<ControllerAction> action=Signals.of(ControllerAction.TOGGLE);
    private final Signal<Double> ceiling=Signals.of(1.0);
    private final Signal<List<AudioFileMetadata>> clips=Signals.of(List.of());
    private final Signal<String> search=Signals.of("");
    private final Computed<List<AudioFileMetadata>> filteredClips=Signals.computed(() -> clips.get().stream()
        .filter(a -> search.get().isBlank() || a.hasTag(search.get().trim().toLowerCase(java.util.Locale.ROOT))).toList());
    private boolean fetched;
    private Subscription ceilingUpdates;
    private final Signal<Boolean> invalidCoordinates=Signals.of(false);
    public RedstoneControllerScreen(BlockPos pos) {
        super(text("title")); this.pos=pos;
        ceilingUpdates=ceiling.subscribe(value -> { if (fetched) saveConfiguration(); });
    }
    @Override public void removed() {
        if (ceilingUpdates != null) ceilingUpdates.close();
        ceilingUpdates=null;
        super.removed();
    }
    private static Component text(String key) { return Component.translatable("gui.simplyspeakers.controller."+key); }
    @Override protected void init() {
        if (!fetched && Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(pos) instanceof RedstoneControllerBlockEntity controller) {
            network.set(controller.getNetworkId()); proxy.set(controller.isProxyTarget());
            BlockPos p=controller.getProxyPos(); proxyCoordinates.set(p.getX()+" "+p.getY()+" "+p.getZ());
            action.set(controller.getAction()); clip.set(controller.getAudioId()); restart.set(controller.isRestartAnnouncement());
            ceiling.set((double)controller.getVolumeCeiling()); fetched=true;
        }
        super.init();
        if (ceilingUpdates == null) ceilingUpdates=ceiling.subscribe(value -> { if (fetched) saveConfiguration(); });
        if (Minecraft.getInstance().getConnection() != null) NetworkManager.sendToServer(new RequestAudioListPacketC2S(pos));
    }
    @Override protected UIComponent buildUI() {
        var form=Ui.column(
            Ui.text(text("target")),
            Ui.textField(network).placeholder(text("target_placeholder").getString()).fillWidth().tooltip(text("network_help")),
            SpeakerButtonWidget.button((Supplier<Component>)() -> text(proxy.get()?"proxy":"network"), () -> {
                proxy.set(!proxy.get());
                if (proxy.get() && !action.get().supportsProxy()) action.set(ControllerAction.ENABLED);
            }),
            Ui.switcher(proxy).when(false, () -> Ui.spacer().height(0))
                .when(true, () -> Ui.column(Ui.text(text("proxy_position")),
                    Ui.textField(proxyCoordinates).placeholder("X Y Z").fillWidth().tooltip(text("proxy_help"))).gap(4)),
            Ui.divider(),
            Ui.switcher(action)
                .when(ControllerAction.TOGGLE, () -> paragraph(text("help.toggle")))
                .when(ControllerAction.TRACK, () -> paragraph(text("help.track")))
                .when(ControllerAction.NEXT, () -> paragraph(text("help.next")))
                .when(ControllerAction.PREVIOUS, () -> paragraph(text("help.previous")))
                .when(ControllerAction.ANNOUNCEMENT, () -> paragraph(text("help.announcement")))
                .when(ControllerAction.ENABLED, () -> paragraph(text("help.enabled")))
                .when(ControllerAction.VOLUME, () -> paragraph(text("help.volume")))
                .when(ControllerAction.STOP, () -> paragraph(text("help.stop")))
                .when(ControllerAction.RESTART, () -> paragraph(text("help.restart"))),
            Ui.switcher(action)
                .when(ControllerAction.ANNOUNCEMENT,this::announcementOptions)
                .when(ControllerAction.VOLUME,() -> Ui.column(
                    Ui.text((Supplier<Component>)() -> Component.translatable("gui.simplyspeakers.controller.ceiling", Math.round(ceiling.get()*100))),
                    Ui.slider(ceiling,0,1).fillWidth().tooltip(text("help.volume"))).gap(4))
                .when(ControllerAction.TOGGLE,() -> Ui.text(Component.empty()))
                .when(ControllerAction.TRACK,() -> Ui.text(Component.empty()))
                .when(ControllerAction.NEXT,() -> Ui.text(Component.empty()))
                .when(ControllerAction.PREVIOUS,() -> Ui.text(Component.empty()))
                .when(ControllerAction.ENABLED,() -> Ui.text(Component.empty()))
                .when(ControllerAction.STOP,() -> Ui.text(Component.empty()))
                .when(ControllerAction.RESTART,() -> Ui.text(Component.empty()))
        ).gap(6);
        return buildWindow(Ui.column(
            Ui.row(Ui.heading(text("title")),buildThemeToggle()).align(com.nstut.openui.layout.Alignment.CENTER).justify(Justification.SPACE_BETWEEN),
            Ui.row(Ui.text(text("action")).flex(),
                SpeakerButtonWidget.button((Supplier<Component>)() -> text("action."+action.get().id()),this::chooseAction)
                    .width(170).height(24).key("controller.mode")).align(com.nstut.openui.layout.Alignment.CENTER).gap(8),
            Ui.scroll(form).flex(),
            Ui.text((Supplier<Component>)() -> invalidCoordinates.get() ? text("invalid_coordinates") : liveStatus()).wrap(),
            Ui.row(SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.save"),this::save).primary(),
                SpeakerButtonWidget.button(text("close"),this::onClose).ghost()).justify(Justification.CENTER).gap(6)
        ).gap(8),320);
    }
    private void chooseAction() {
        OverlayHandle[] handle={null};
        var options=new java.util.ArrayList<UIComponent>();
        for (ControllerAction job : ControllerAction.values()) {
            if (proxy.get() && !job.supportsProxy()) continue;
            var option=SpeakerButtonWidget.button(text("action."+job.id()),() -> {
                action.set(job); handle[0].close();
            }).width((Math.min(280,width-40)-28)/2).height(24).tooltip(text("help."+job.id())).key("controller.mode."+job.id());
            ((com.nstut.openui.api.ButtonWidget)option).setActive(job==action.get());options.add(option);
        }
        var rows=new java.util.ArrayList<UIComponent>();
        for(int i=0;i<options.size();i+=2) {
            var left=options.get(i);
            rows.add(i+1<options.size()?Ui.row(left,options.get(i+1)).gap(4):Ui.row(left));
        }
        var choices=Ui.column(rows.toArray(UIComponent[]::new)).gap(4);
        Card dialog=Ui.card(Ui.column(Ui.heading(text("action")),Ui.scroll(choices).key("controller.choices").height(((options.size()+1)/2)*28-4),
            SpeakerButtonWidget.button(text("close"),() -> handle[0].close()).ghost()).gap(8)).padding(12);
        dialog.width(Math.min(280,width-40));handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
    }
    private UIComponent paragraph(Component text) {
        int available=Math.max(64,Math.min(320,width-40));
        return new TextWidget(text) {
            @Override public int preferredHeight(net.minecraft.client.gui.Font font) {
                return font.split(getText(),available).size()*font.lineHeight+2;
            }
        }.wrap().key("controller.paragraph");
    }
    private Component liveStatus() {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof RedstoneControllerBlockEntity c)
            return Component.translatable("gui.simplyspeakers.controller.live",c.getLastSignal(),text("status."+c.getStatus()));
        return text("status.missing_target");
    }
    private UIComponent announcementOptions() {
        return Ui.column(
            Ui.text(text("clip")),
            Ui.text((Supplier<Component>)() -> {
                for (var a : clips.get()) if (a.getUuid().equals(clip.get())) return Component.literal(a.effectiveDisplayName());
                return text("choose_clip");
            }).wrap(),
            Ui.textField(search).placeholder(Component.translatable("gui.simplyspeakers.search.placeholder").getString()).fillWidth(),
            Ui.list(filteredClips,this::clipRow)
                .key(AudioFileMetadata::getUuid).itemHeight(24).height(80),
            SpeakerButtonWidget.button((Supplier<Component>)() -> text(restart.get()?"retrigger_restart":"retrigger_ignore"),() -> restart.set(!restart.get()))
        ).gap(5);
    }
    private UIComponent clipRow(AudioFileMetadata audio) {
        return new SpeakerButtonWidget(Component.literal(audio.effectiveDisplayName())) {
            @Override public void render(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font,int mx,int my,float pt) {
                int available=Math.max(0,getWidth()-12);String name=audio.effectiveDisplayName();
                setLabel(Component.literal(font.width(name)>available ? font.plainSubstrByWidth(name,Math.max(0,available-font.width("…")))+"…" : name));
                setActive(audio.getUuid().equals(clip.get()));super.render(g,font,mx,my,pt);
            }
        }.onPress(() -> clip.set(audio.getUuid())).ghost().small().tooltip(Component.literal(audio.effectiveDisplayName())).key("clip.title");
    }
    public void updateAudioList(List<AudioFileMetadata> list) {
        clips.set(list.stream().filter(a -> a.getDurationSeconds()>0).toList());
    }
    @Override public void onClose() {
        if (fetched && !saveConfiguration()) return;
        super.onClose();
    }
    private void save() { saveConfiguration(); }
    private boolean saveConfiguration() {
        invalidCoordinates.set(false);
        BlockPos target=BlockPos.ZERO;
        if (proxy.get()) {
            try {
                String[] coordinates=proxyCoordinates.get().trim().split("\\s+");
                if (coordinates.length != 3) { invalidCoordinates.set(true); return false; }
                target=new BlockPos(Integer.parseInt(coordinates[0]),Integer.parseInt(coordinates[1]),Integer.parseInt(coordinates[2]));
            } catch (NumberFormatException e) { invalidCoordinates.set(true); return false; }
        }
        NetworkManager.sendToServer(new ConfigureControllerPacketC2S(pos,network.get().trim(),proxy.get(),target,action.get().id(),clip.get(),restart.get(),ceiling.get().floatValue()));
        return true;
    }
}
