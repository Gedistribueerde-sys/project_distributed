package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BulletinBoardImpl implements BulletinBoard {
    private static final Logger logger = LoggerFactory.getLogger(BulletinBoardImpl.class);

    private static final double LOAD_FACTOR_THRESHOLD = 0.01;
    private final transient ServerDatabaseManager dbManager;

    private volatile BoardGeneration activeBoard;
    /**
     * CopyOnWriteArrayList is een thread-safe lijst waarbij lezen zonder locks gebeurt en waarbij bij elke wijziging
     * (toevoegen of verwijderen) een nieuwe kopie van de lijst wordt gemaakt, zodat iteraties veilig zijn en nooit een ConcurrentModificationException veroorzaken.
     * In deze code is dit nodig omdat drainingBoards vaak wordt gelezen, soms wordt aangepast en tegelijkertijd door meerdere threads wordt gebruikt, waardoor veilige iteratie zonder expliciete synchronisatie essentieel is.
     * thread safety zonder complexe locking-mechanismen.
     */
    private final CopyOnWriteArrayList<BoardGeneration> drainingBoards = new CopyOnWriteArrayList<>();

    // --- Two-Phase Commit for Get ---
    private static class CheckedOutMessage {
        final BoardGeneration board;
        final long idx;
        final String tag;
        final byte[] value;
        final long timestamp = System.currentTimeMillis();

        CheckedOutMessage(BoardGeneration board, long idx, String tag, byte[] value) {
            this.board = board;
            this.idx = idx;
            this.tag = tag;
            this.value = value;
        }
    }

    /**
     * checkedOutMessages wordt gebruikt om berichten die door een get() zijn opgehaald tijdelijk bij te houden totdat ze definitief bevestigd (confirm) of teruggezet worden bij een timeout.
     * Dit ondersteunt een two-phase commit-achtig mechanisme dat voorkomt dat hetzelfde bericht meerdere keren tegelijk wordt verwerkt.
     * Het gebruik van een ConcurrentHashMap is nodig omdat meerdere threads gelijktijdig berichten kunnen checken, bevestigen of opruimen, en deze datastructuur dit toelaat op een thread-safe manier zonder globale locks,
     * met goede prestaties bij hoge gelijktijdigheid.
     */
    private final Map<String, CheckedOutMessage> checkedOutMessages = new ConcurrentHashMap<>();
    private static final long CHECKOUT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);
    // --------------------------------

    public BulletinBoardImpl(ServerDatabaseManager dbManager) {
        this.dbManager = dbManager;

        // --- Database Recovery ---
        List<ServerDatabaseManager.PersistedMessage> allMessages = dbManager.loadAllMessagesWithCapacity(); // alle berichten die in de db zitten
        Map<Integer, List<ServerDatabaseManager.PersistedMessage>> messagesByCapacity = allMessages.stream() // Maakt een stream van alle uit de database geladen berichten
                .collect(Collectors.groupingBy(ServerDatabaseManager.PersistedMessage::boardCapacity)); // Groepeert de berichten per board-capaciteit zodat berichten van verschillende boardgeneraties gescheiden worden

        if (messagesByCapacity.isEmpty()) { // Controleert of er geen berichten in de database staan (lege of nieuwe server)
            int initialSize = 1024; // Stelt een initiële capaciteit voor het eerste board in
            this.activeBoard = new BoardGeneration(initialSize); // Maakt een nieuw actief board aan met de startgrootte
            logger.info("No data in DB. Initialized new board size: {}", initialSize); // Logt dat een nieuw board is geïnitialiseerd
        } else {
            int activeCapacity = messagesByCapacity.keySet().stream().max(Integer::compare).get(); // Bepaalt het grootste board (laatste generatie) dat actief moet worden

            Map<Integer, BoardGeneration> boardsByCapacity = new HashMap<>(); // Map om boardgeneraties per capaciteit bij te houden
            for (Map.Entry<Integer, List<ServerDatabaseManager.PersistedMessage>> entry : messagesByCapacity.entrySet()) { // Doorloopt alle capaciteiten uit de database
                int capacity = entry.getKey(); // Haalt de board-capaciteit op
                List<ServerDatabaseManager.PersistedMessage> messages = entry.getValue(); // Haalt alle berichten voor die capaciteit op
                BoardGeneration board = new BoardGeneration(capacity); // Maakt een boardgeneratie aan met dezelfde capaciteit
                messages.forEach(board::loadMessage); // Laadt alle berichten opnieuw in het board
                boardsByCapacity.put(capacity, board); // Slaat het board op per capaciteit
            }

            this.activeBoard = boardsByCapacity.remove(activeCapacity); // Stelt het grootste board in als actief board
            this.drainingBoards.addAll(boardsByCapacity.values()); // Verplaatst oudere boards naar de draining-lijst

            String drainingSizes = drainingBoards.stream().map(b -> String.valueOf(b.capacity)).collect(Collectors.joining(", ")); // Maakt een string met de capaciteiten van de draining boards voor logging

            logger.info("Server loaded. Active size: {}. Draining sizes: [{}]", this.activeBoard.capacity, drainingSizes.isEmpty() ? "None" : drainingSizes);
        }
    }

    @Override
    public boolean add(long idx, byte[] value, String tag, long nonce) throws RemoteException {
        // Verify proof-of-work before accepting the message
        if (!ProofOfWork.verifyProof(tag, idx, nonce)) {
            logger.warn("REJECTED: Invalid proof-of-work for tag {} at idx {}", tag, idx);
            return false;
        }

        if (activeBoard.isOverloaded()) {
            checkAndResize();
        }
        return activeBoard.add(idx, value, tag, dbManager);
    }

    @Override
    public Pair get(long idx, String preimage) throws RemoteException {
        String tag = Encryption.preimageToTag(preimage);

        // Prevent processing a message that is already checked out
        if (checkedOutMessages.containsKey(tag)) {
            return null;
        }

        // Search draining boards first
        for (BoardGeneration board : drainingBoards) {
            Pair result = board.findAndRemoveFromBucket(idx, tag);
            if (result != null) {
                checkedOutMessages.put(tag, new CheckedOutMessage(board, idx, tag, result.value()));
                if (board.getTotalCount() == 0) {
                    drainingBoards.remove(board);
                }
                return result;
            }
        }

        // Search active board
        Pair result = activeBoard.findAndRemoveFromBucket(idx, tag);
        if (result != null) {
            checkedOutMessages.put(tag, new CheckedOutMessage(activeBoard, idx, tag, result.value()));
        }
        return result;
    }
    //De confirm-methode bevestigt definitief dat een opgehaald bericht correct is verwerkt en zorgt ervoor dat het permanent wordt verwijderd, zowel uit het geheugen als uit de database.
    @Override
    public boolean confirm(long idx, String tag) throws RemoteException {
        logger.debug("CONFIRM received for tag: {}", tag);

        // Best-effort cleanup of in-memory state first.
        checkedOutMessages.remove(tag);

        // Also clean up from the main buckets in case this is a retry after a server crash.
        // This makes the confirm idempotent for the in-memory state.
        activeBoard.findAndRemoveFromBucket(idx, tag);
        drainingBoards.forEach(b -> b.findAndRemoveFromBucket(idx, tag));

        try {
            // The authoritative step: delete from durable storage.
            dbManager.deleteMessage(tag);
            logger.info("CONFIRMED and deleted message with tag: {}", tag);
            return true;
        } catch (Exception e) {
            // This can happen if confirm is called multiple times for the same tag.
            // We can consider it a success if the message is already gone.
            logger.warn("DB Delete failed on confirm for tag: {}. Assuming already deleted.", tag, e);
            return true;
        }
    }
    //Elk opgehaald bericht volgt een verplicht confirm-pad; zonder confirm wordt het bericht na een timeout automatisch hersteld om verlies te voorkomen.
    public void cleanUpOrphanedMessages() { // Ruimt berichten op die zijn opgehaald maar nooit bevestigd (timeout)
        long now = System.currentTimeMillis(); // Huidige tijd om te bepalen hoe lang berichten al uitgecheckt zijn
        List<String> orphanedTags = new ArrayList<>(); // Lijst om tags van verlopen checkout-berichten bij te houden

        for (CheckedOutMessage checkedOut : checkedOutMessages.values()) { // Doorloopt alle tijdelijk uitgecheckte berichten
            if (now - checkedOut.timestamp > CHECKOUT_TIMEOUT_MS) { // Controleert of het bericht langer dan toegestaan in checkout staat
                // Return the message to its original board
                checkedOut.board.putBack(checkedOut.idx, checkedOut.tag, checkedOut.value); // Zet het bericht terug in het oorspronkelijke board
                orphanedTags.add(checkedOut.tag); // Markeert dit bericht om uit checkedOutMessages te verwijderen
                logger.warn("TIMED OUT message with tag: {}. Returned to board.", checkedOut.tag); // Logt dat het bericht is teruggezet na timeout
            }
        }
        // Remove all orphaned messages from the checkout list
        orphanedTags.forEach(checkedOutMessages::remove); // Verwijdert alle verlopen berichten uit de checkout-registratie
    }



    private synchronized void checkAndResize() {
        if (activeBoard.isOverloaded()) {
            logger.info("RESIZING: Board full ({} items). expanding...", activeBoard.getTotalCount());
            drainingBoards.add(this.activeBoard);
            int newSize = this.activeBoard.capacity * 2;
            this.activeBoard = new BoardGeneration(newSize);
            logger.info("RESIZING: New Active Board created with size {}", newSize);
        }
    }

    private static class BoardGeneration {
        private final int capacity;
        private final List<Map<String, byte[]>> buckets;
        private final AtomicInteger totalItems = new AtomicInteger(0);

        public BoardGeneration(int size) {
            this.capacity = size;
            this.buckets = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                buckets.add(new ConcurrentHashMap<>());
            }
        }
        // onveranderelijke records inladen vanuit de database bij serverstart
        public void loadMessage(ServerDatabaseManager.PersistedMessage msg) {
            int index = msg.cellIndex();
            if (index >= 0 && index < this.capacity) {
                this.buckets.get(index).put(msg.messageTag(), msg.messageValue());
                this.totalItems.incrementAndGet();
            } else {
                logger.error("LOAD ERROR: Message tag {} stored with index {} does not fit in board size {}. Data integrity compromised.", msg.messageTag(), index, this.capacity);
            }
        }
        // een bericht terugplaatsen in het board (bij timeout)
        public void putBack(long idx, String tag, byte[] value) {
            int index = computeIndex(idx);
            Map<String, byte[]> cell = buckets.get(index);
            synchronized (cell) {
                // Put back and increment count
                cell.put(tag, value);
                totalItems.incrementAndGet();
            }
        }

        public boolean add(long idx, byte[] value, String tag, ServerDatabaseManager db) {
            int index = computeIndex(idx);
            Map<String, byte[]> cell = buckets.get(index);
            synchronized (cell) {
                if (cell.containsKey(tag)) return false;
                try {
                    db.saveMessage(index, this.capacity, tag, value);
                    cell.put(tag, value);
                    totalItems.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    logger.error("DB Save failed", e);
                    return false;
                }
            }
        }

        public Pair findAndRemoveFromBucket(long idx, String tag) {
            int index = computeIndex(idx);
            Map<String, byte[]> cell = buckets.get(index);
            byte[] value;
            synchronized (cell) {
                value = cell.remove(tag);
            }
            if (value != null) {
                // Decrement count only if found and removed
                totalItems.decrementAndGet();
                return new Pair(value, tag);
            }
            return null;
        }

        public boolean isOverloaded() {
            return totalItems.get() > (capacity * LOAD_FACTOR_THRESHOLD);
        }

        public int getTotalCount() {
            return totalItems.get();
        }

        private int computeIndex(long idx) {
            // Handle negative indices correctly
            return (int) ((idx % capacity) + capacity) % capacity;
        }
    }
}