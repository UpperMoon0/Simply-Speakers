package com.nstut.simplyspeakers.platform;
import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
/** Native dialogs may block a worker; all game/UI callbacks belong to the client executor. */
public final class ClientFileSelection {
    private ClientFileSelection() {}
    public static Thread open(Supplier<String> dialog,Consumer<Runnable> clientExecutor,Consumer<File> callback) {
        Objects.requireNonNull(dialog);Objects.requireNonNull(clientExecutor);Objects.requireNonNull(callback);
        Thread worker=new Thread(()->{
            String result=dialog.get();
            complete(result==null?null:new File(result),clientExecutor,callback);
        },"Simply Speakers file dialog");
        worker.setDaemon(true);worker.start();return worker;
    }
    public static void complete(File selected,Consumer<Runnable> clientExecutor,Consumer<File> callback) {
        if(selected!=null)clientExecutor.accept(()->callback.accept(selected));
    }
}
