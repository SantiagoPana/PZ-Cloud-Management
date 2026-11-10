package pzcloud;

import java.io.IOException;

/** Resource pause check; transfer sessions remain resumable when the game starts. */
@FunctionalInterface
public interface TransferGate {
    void check() throws IOException;
    static TransferGate unrestricted() { return () -> {}; }
    static TransferGate whileGameClosed(GameGuard guard) {
        return new TransferGate() {
            private long next;
            public void check() throws IOException {
                long now=System.nanoTime();
                if(now<next) return;
                if(guard.running()) throw new IOException("Pausado mientras jugás. Se continuará cuando cierres Zomboid.");
                next=now+2_000_000_000L;
            }
        };
    }
}
