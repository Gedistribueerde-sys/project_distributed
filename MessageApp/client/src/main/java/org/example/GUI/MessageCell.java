// MessageCell.java
package org.example.GUI;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**De MessageCell klasse is verantwoordelijk voor de visuele presentatie (de weergave) van een bericht binnen de interface.
 *

Het zet de ruwe data uit de Message klasse om naar grafische elementen zoals tekstballonnetjes, kleuren, avatars en animaties.
Daarnaast bepaalt deze klasse de logica voor de lay-out, zodat inkomende berichten links en uitgaande berichten rechts op het scherm verschijnen.
 */
// A custom ListCell for displaying messages in a chat application.
public class MessageCell extends ListCell<Message> {

    private static final double AVATAR_SIZE = 32;
    private static final double MAX_BUBBLE_WIDTH = 420;

    public MessageCell() {
        // Make the cell transparent to show custom styling
        setStyle("-fx-background-color: transparent;");
    }

    // Updates the cell's content based on the Message item.
    protected void updateItem(Message item, boolean empty) { // Wordt automatisch opgeroepen om één “cell” (chatbericht) in de ListView te tekenen/updaten
        super.updateItem(item, empty); // Roept de standaard updateItem aan van de parent class (basisgedrag behouden)

        if (empty || item == null) { // Als de cell leeg is of er is geen bericht-object
            setText(null); // Verwijder eventuele tekst die nog in de cell stond
            setGraphic(null); // Verwijder eventuele UI (nodes) die nog in de cell stond
            return; // Stop hier, want er is niets om te tonen
        }

        boolean outgoing = item.isSent(); // Bepaalt of dit bericht “outgoing” is (door jezelf verstuurd) of “incoming”

        // Create the bubble content container
        VBox bubbleContent = new VBox(2); // Maakt een verticale container voor de inhoud van de bubble (met 2px spacing)
        bubbleContent.setMaxWidth(MAX_BUBBLE_WIDTH); // Beperkt de maximale breedte van de bubble-inhoud

        // Message text
        Label messageLabel = new Label(item.text()); // Maakt een label met de tekst van het bericht
        messageLabel.setWrapText(true); // Zorgt dat lange tekst automatisch naar de volgende lijn gaat
        messageLabel.setMaxWidth(MAX_BUBBLE_WIDTH - 24); // Max breedte van tekst (minus padding marge)
        messageLabel.getStyleClass().add("message-text"); // Voeg CSS class toe voor algemene message tekst styling
        messageLabel.getStyleClass().add(outgoing ? "message-text-outgoing" : "message-text-incoming"); // Voeg CSS class toe afhankelijk van outgoing/incoming

        // Bottom row: timestamp and status
        HBox bottomRow = new HBox(6); // Maakt een horizontale rij voor tijd + status met 6px spacing
        bottomRow.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); // Plaats tijd/status rechts bij outgoing, links bij incoming

        // Timestamp
        Label timeLabel = new Label(item.getFormattedTime()); // Label met de geformatteerde tijd van het bericht
        timeLabel.getStyleClass().add("message-time"); // CSS class voor tijdstijl
        timeLabel.getStyleClass().add(outgoing ? "message-time-outgoing" : "message-time-incoming"); // CSS class voor tijd afhankelijk van outgoing/incoming
        bottomRow.getChildren().add(timeLabel); // Voeg tijd-label toe aan de bottomRow

        // Status indicator for outgoing messages
        if (outgoing) { // Alleen bij berichten die jij verstuurt toon je een status (pending/delivered/failed/...)
            Label statusLabel = new Label(item.status().getIcon()); // Label met een icoon voor de status (bv. ✓, ⏳, ⚠)
            statusLabel.getStyleClass().add("message-status"); // CSS class voor statusstijl
            statusLabel.getStyleClass().add(getStatusStyleClass(item.status())); // Extra CSS class afhankelijk van de status (kleur/animatie)

            Tooltip statusTooltip = new Tooltip(item.status().getTooltip()); // Tooltiptekst die uitlegt wat de status betekent
            statusTooltip.setShowDelay(Duration.millis(200)); // Toon tooltip na 200ms hover (kleine vertraging)
            Tooltip.install(statusLabel, statusTooltip); // Koppel tooltip aan het statuslabel

            bottomRow.getChildren().add(statusLabel); // Voeg het statuslabel toe naast de tijd
        }

        bubbleContent.getChildren().addAll(messageLabel, bottomRow); // Voeg de tekst en de bottomRow toe in de bubble content

        // Create the bubble wrapper with styling
        VBox bubble = new VBox(bubbleContent); // Maakt de eigenlijke “bubble” container rond de inhoud
        bubble.getStyleClass().add("message-bubble"); // Algemene CSS class voor chat bubble styling
        bubble.getStyleClass().add(outgoing ? "outgoing" : "incoming"); // CSS class om bubble stijl/positie te bepalen (outgoing vs incoming)
        bubble.setPadding(new Insets(8, 12, 6, 12)); // Padding binnen de bubble (top, right, bottom, left)
        bubble.setMaxWidth(MAX_BUBBLE_WIDTH); // Max breedte van de bubble

        // Add pending animation for unsent messages
        if (outgoing && item.status() == Message.MessageStatus.PENDING) { // Als bericht outgoing is en nog “pending” (nog niet bevestigd)
            bubble.getStyleClass().add("pending"); // CSS class die pending styling/animatie kan activeren
            addPulseEffect(bubble); // Start een pulse-effect (bv. licht pulseren) om pending zichtbaar te maken
        }

        // Main container with avatar
        HBox container = new HBox(8); // Hoofdcontainer voor de hele chatregel (avatar + bubble) met 8px spacing
        container.getStyleClass().add("message-cell-hbox"); // CSS class voor de volledige message cell
        container.setPadding(new Insets(3, 12, 3, 12)); // Padding rond de hele rij (boven/onder klein, links/rechts groter)

        if (outgoing) { // Als jij het bericht hebt verstuurd
            // Outgoing: bubble on right, no avatar
            container.setAlignment(Pos.CENTER_RIGHT); // Zet alles rechts uitgelijnd
            container.getChildren().add(bubble); // Voeg enkel de bubble toe (geen avatar)
        } else { // Als het bericht inkomend is
            // Incoming: avatar on left, bubble on right
            container.setAlignment(Pos.CENTER_LEFT); // Zet alles links uitgelijnd
            StackPane avatar = createAvatar(item.sender()); // Maak een avatar node (bv. initialen/icoontje) voor de afzender
            container.getChildren().addAll(avatar, bubble); // Voeg avatar links en bubble daarnaast toe
        }

        // Apply fade-in animation for new messages
        if (getIndex() == getListView().getItems().size() - 1) { // Check of dit de laatste (nieuwste) message in de lijst is
            applyFadeIn(container); // Voeg een fade-in animatie toe zodat het nieuwe bericht mooi verschijnt
        }

        setText(null); // Zorg dat de cell geen gewone tekst-rendering gebruikt (we gebruiken graphics/nodes)
        setGraphic(container); // Zet de volledige container (avatar + bubble) als UI van deze cell
    } // Einde van updateItem

    // Creates an avatar with the sender's initial and a consistent background color.
    private StackPane createAvatar(String sender) {
        Circle circle = new Circle(AVATAR_SIZE / 2);
        circle.getStyleClass().add("avatar-circle");

        // Generate a consistent color based on the sender's name
        Color avatarColor = generateAvatarColor(sender);
        circle.setFill(avatarColor);

        // First letter of sender
        String initial = sender != null && !sender.isEmpty()
                ? sender.substring(0, 1).toUpperCase()
                : "?";

        Text initialText = new Text(initial);
        initialText.getStyleClass().add("avatar-text");
        initialText.setFill(Color.WHITE);

        StackPane avatar = new StackPane(circle, initialText);
        avatar.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        avatar.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);

        return avatar;
    }

    // Generates a color based on the hash of the sender's name.
    private Color generateAvatarColor(String name) {
        if (name == null || name.isEmpty()) {
            return Color.GRAY;
        }

        // Use hash to generate consistent color
        int hash = name.hashCode();
        double hue = Math.abs(hash % 360);
        return Color.hsb(hue, 0.6, 0.7);
    }

    // Maps message status to corresponding CSS style class.
    private String getStatusStyleClass(Message.MessageStatus status) {
        return switch (status) {
            case PENDING -> "status-pending";
            case SENT -> "status-sent";
            case DELIVERED -> "status-delivered";
        };
    }

    // Applies a fade-in animation to the message container.
    private void applyFadeIn(HBox container) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), container);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    // Adds a subtle pulse effect to indicate a pending message.
    private void addPulseEffect(VBox bubble) {
        bubble.setOpacity(0.85);
    }
}
