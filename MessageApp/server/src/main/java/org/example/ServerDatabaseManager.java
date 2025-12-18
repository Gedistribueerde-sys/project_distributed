package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class ServerDatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(ServerDatabaseManager.class);
    private final String dbUrl;

    public ServerDatabaseManager(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public void initializeDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS bulletin_board (
                    cell_index INTEGER NOT NULL,
                    board_capacity INTEGER NOT NULL,
                    message_tag TEXT PRIMARY KEY,
                    message_value BLOB NOT NULL
                );
                """;

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // Start een try-with-resources blok dat automatisch de databaseverbinding en het statement zal sluiten
            stmt.execute(sql);
            // Voert het SQL-commando uit op de database (bv. CREATE, INSERT, UPDATE, DELETE)
            log.info("Database initialized successfully. Table 'bulletin_board' is ready.");
        } catch (SQLException e) {
            log.error("Error initializing the database", e);
            throw new RuntimeException("Failed to initialize the database", e);
        }
    }

    public void saveMessage(int cellIndex, int boardCapacity, String messageTag, byte[] messageValue) {
        String sql = "INSERT INTO bulletin_board(cell_index, board_capacity, message_tag, message_value) VALUES(?,?,?,?)";

        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cellIndex);
            // Zet de eerste parameter (?) op de waarde van cellIndex
            stmt.setInt(2, boardCapacity);
            // Zet de tweede parameter (?) op de waarde van boardCapacity
            stmt.setString(3, messageTag);
            // Zet de derde parameter (?) op de messageTag (String)
            stmt.setBytes(4, messageValue);
            // Zet de vierde parameter (?) op de messageValue als byte-array (bv. binaire data)
            stmt.executeUpdate();
            // Voert de INSERT-query uit en schrijft de gegevens naar de database
        } catch (SQLException e) {
            log.error("Error saving message with tag '{}'", messageTag, e);
            // Re-throw as a runtime exception to notify the caller of the failure
            throw new RuntimeException("Failed to save message", e);
        }
    }

    public void deleteMessage(String messageTag) {
        String sql = "DELETE FROM bulletin_board WHERE message_tag = ?";

        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Start een try-with-resources blok dat automatisch de databaseverbinding en het PreparedStatement sluit
            stmt.setString(1, messageTag);
            // Zet de eerste parameter (?) op de waarde van messageTag
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error deleting message with tag '{}'", messageTag, e);
            // Re-throw as a runtime exception to notify the caller of the failure
            throw new RuntimeException("Failed to delete message", e);
        }
    }

    private Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        // Set PRAGMA for durability to prevent data loss on crash
        //Een PRAGMA is een speciale SQLite-instructie om database-instellingen te lezen of te wijzigen.
        try (Statement stmt = conn.createStatement()) {
            //Het bepaalt hoe veilig SQLite schrijft naar disk.
            //SQLite wacht na elke schrijfoperatie tot:
            //data echt fysiek op schijf staat
            //de OS write buffers geflusht zijn
            stmt.execute("PRAGMA synchronous = FULL;");
        }
        return conn;
    }

    /**
     *
     PersistedMessage is een record (datacontainer) dat bedoeld is om berichten voor te stellen.
     getAllBoardCapacities() haalt alleen getallen (ints) op uit de database, geen berichten.
     Omdat je hier geen volledige rij als object nodig hebt, is het overkill om PersistedMessage te gebruiken.
     */
    public record PersistedMessage(int cellIndex, int boardCapacity, String messageTag, byte[] messageValue) {} //Dit is een Java record dat dient als een onveranderlijke data-container voor berichten die in de database worden opgeslagen.
    public List<Integer> getAllBoardCapacities() {String sql = "SELECT DISTINCT board_capacity FROM bulletin_board";
        // SQL-query die alle verschillende (unieke) board_capacity waarden selecteert
        List<Integer> capacities = new ArrayList<>();
        // Maak een lege lijst aan om de board_capacity waarden in op te slaan
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // Start een try-with-resources blok dat automatisch de verbinding, het statement en het resultset sluit
            while (rs.next()) {
                // Loop door alle rijen in het resultaat
                capacities.add(rs.getInt("board_capacity"));
                // Haal de waarde van board_capacity uit de huidige rij en voeg die toe aan de lijst
            }
        } catch (SQLException e) {
            log.error("Error retrieving board capacities", e);
            throw new RuntimeException("Failed to retrieve capacities", e);
        }
        return capacities;
    }
    public List<PersistedMessage> loadAllMessagesWithCapacity() {
        String sql = "SELECT cell_index, board_capacity, message_tag, message_value FROM bulletin_board";// SQL-query om alle berichten en hun bijhorende velden op te halen uit de tabel bulletin_board


        List<PersistedMessage> messages = new ArrayList<>();

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // Start een try-with-resources blok dat automatisch de databaseverbinding, het statement en het resultset sluit
            while (rs.next()) {
                // Loop door alle rijen in het resultaat
                messages.add(new PersistedMessage(
                        // Maak een nieuw PersistedMessage-object aan met de gegevens uit de huidige rij
                        rs.getInt("cell_index"),
                        // Haal de waarde van cell_index op
                        rs.getInt("board_capacity"),
                        // Haal de waarde van board_capacity op
                        rs.getString("message_tag"),
                        // Haal de waarde van message_tag op als String
                        rs.getBytes("message_value")
                        // Haal de waarde van message_value op als byte-array (binaire data)
                ));
            }
            log.info("Loaded {} raw messages from the database.", messages.size());
        } catch (SQLException e) {
            log.error("Error loading messages from the database", e);
            throw new RuntimeException("Failed to load messages from the database", e);
        }
        return messages;
    }
}


