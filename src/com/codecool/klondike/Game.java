package com.codecool.klondike;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableArray;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;
import javafx.scene.control.Button;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Game extends Pane {

    private List<Card> deck = new ArrayList<>();

    private Pile stockPile;
    private Pile discardPile;
    private List<Pile> foundationPiles = FXCollections.observableArrayList();
    private List<Pile> tableauPiles = FXCollections.observableArrayList();

    private double dragStartX, dragStartY;
    private List<Card> draggedCards = FXCollections.observableArrayList();
    private List<Card> undoCards;
    private Pile undoPile;

    private Scene scene;

    private static double STOCK_GAP = 1;
    private static double FOUNDATION_GAP = 0;
    private static double TABLEAU_GAP = 30;

    private EventHandler<MouseEvent> undoBtnClickedHandler = e -> {
        if (undoCards == null)
            return;
        if (undoPile.getTopCard() != null
                && !undoCards.isEmpty()
                && undoPile.getPileType() != Pile.PileType.DISCARD)
            undoPile.getTopCard().flip();

        for (Card card: undoCards)
            card.moveToPile(undoPile);
        undoCards.clear();
    };

    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();
        if (card.getContainingPile().getPileType() == Pile.PileType.STOCK &&
                card.getContainingPile().getCards().indexOf(card) == card.getContainingPile().numOfCards() - 1) {
            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        }
    };

    private EventHandler<MouseEvent> restartButtonClickedHandler =  e -> {
        restart();
    };

    private EventHandler<MouseEvent> stockReverseCardsHandler = e -> {
        refillStockFromDiscard();
    };

    private EventHandler<MouseEvent> onMousePressedHandler = e -> {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
    };

    private EventHandler<MouseEvent> onMouseDraggedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile activePile = card.getContainingPile();
        if (activePile.getPileType().equals(Pile.PileType.STOCK) || card.isFaceDown())
            return;
        if(activePile.getPileType().equals(Pile.PileType.DISCARD) &&
                activePile.getCards().indexOf(card) < activePile.numOfCards() - 1)
            return;

        double offsetX = e.getSceneX() - dragStartX;
        double offsetY = e.getSceneY() - dragStartY;

        draggedCards.clear();

        List<Card> consecutiveCards = activePile
                                      .getCards()
                                      .subList(activePile.getCards().indexOf(card), activePile.numOfCards());

        for (Card currentCard: consecutiveCards) {
            draggedCards.add(currentCard);

            currentCard.getDropShadow().setRadius(20);
            currentCard.getDropShadow().setOffsetX(10);
            currentCard.getDropShadow().setOffsetY(10);

            currentCard.toFront();
            currentCard.setTranslateX(offsetX);
            currentCard.setTranslateY(offsetY);
        }
    };

    private EventHandler<MouseEvent> onMouseReleasedHandler = e -> {
        if (draggedCards.isEmpty())
            return;
        Card card = (Card) e.getSource();
        Pile pile = getValidIntersectingPile(card, tableauPiles);
        if(pile == null && draggedCards.size() == 1){
            pile = getValidIntersectingPile(card, foundationPiles);
        }
        //TODO
        if (pile != null) {
            handleValidMove(card, pile);
            flipTopCard(card);
        } else {
            draggedCards.forEach(MouseUtil::slideBack);
            draggedCards.clear();
        }
    };

    public void flipTopCard(Card card) {
        List<Card> contPile = card.getContainingPile().getCards();
        Card topCard = contPile.indexOf(card) > 0 ? contPile.get(contPile.indexOf(card) -1) : null;
        if (topCard != null)
            if (topCard.isFaceDown())
                topCard.flip();
    }

    public boolean isGameWon() {
        //TODO
        return false;
    }

    public void restart() {
        getChildren().clear();
        deck.clear();
        discardPile.clear();
        foundationPiles.clear();
        tableauPiles.clear();
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        addButtons();
    }

    public Game() {
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        addButtons();
    }

    public void addMouseEventHandlers(Card card) {
        card.setOnMousePressed(onMousePressedHandler);
        card.setOnMouseDragged(onMouseDraggedHandler);
        card.setOnMouseReleased(onMouseReleasedHandler);
        card.setOnMouseClicked(onMouseClickedHandler);
    }

    public void refillStockFromDiscard() {
        //TODO
        if (stockPile.isEmpty()){
            ObservableList<Card> reverseCards = discardPile.getCards();
            FXCollections.reverse(reverseCards);
            for(Card card : reverseCards){
                card.flip();
                stockPile.addCard(card);
            }
            discardPile.clear();
        }
        System.out.println("Stock refilled from discard pile.");
    }

    public boolean isMoveValid(Card card, Pile destPile) {
        //TODO done
        Card topCard = destPile.getTopCard();
        if (destPile.getPileType() == Pile.PileType.TABLEAU)
            return topCard == null ? card.getRank() == Card.Rank.KING :
                Card.isOppositeColor(card, topCard)
                && topCard.getRank().getValue() - 1 == card.getRank().getValue();
        return topCard == null ? card.getRank() == Card.Rank.ACE :
                Card.isSameSuit(card, topCard)
                && topCard.getRank().getValue() + 1 == card.getRank().getValue();
    }

    private Pile getValidIntersectingPile(Card card, List<Pile> piles) {
        Pile result = null;
        for (Pile pile : piles) {
            if (!pile.equals(card.getContainingPile()) &&
                    isOverPile(card, pile) &&
                    isMoveValid(card, pile))
                result = pile;
        }
        return result;
    }

    private boolean isOverPile(Card card, Pile pile) {
        if (pile.isEmpty())
            return card.getBoundsInParent().intersects(pile.getBoundsInParent());
        else
            return card.getBoundsInParent().intersects(pile.getTopCard().getBoundsInParent());
    }

    private void handleValidMove(Card card, Pile destPile) {
        String msg = null;
        if (destPile.isEmpty()) {
            if (destPile.getPileType().equals(Pile.PileType.FOUNDATION))
                msg = String.format("Placed %s to the foundation.", card);
            if (destPile.getPileType().equals(Pile.PileType.TABLEAU))
                msg = String.format("Placed %s to a new pile.", card);
        } else {
            msg = String.format("Placed %s to %s.", card, destPile.getTopCard());
        }
        System.out.println(msg);

        undoPile = card.getContainingPile();
        undoCards = FXCollections.observableArrayList(draggedCards);
        MouseUtil.slideToDest(draggedCards, destPile);
        draggedCards.clear();
    }

    private void addButtons() {
        Button undoBtn = new Button("Undo");
        undoBtn.setStyle("-fx-background-color: #e4e6e8");
        undoBtn.setLayoutX(1330);
        undoBtn.setLayoutY(20);
        getChildren().add(undoBtn);
        undoBtn.setOnMouseClicked(undoBtnClickedHandler);


        Button restartButton = new Button("Restart");
        restartButton.setStyle("-fx-background-color: #e7af10");
        restartButton.setLayoutX(475);
        restartButton.setLayoutY(20);
        restartButton.setOnMouseClicked(restartButtonClickedHandler);
        getChildren().add(restartButton);
    }

    private void initPiles() {
        stockPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
        stockPile.setBlurredBackground();
        stockPile.setLayoutX(95);
        stockPile.setLayoutY(20);
        stockPile.setOnMouseClicked(stockReverseCardsHandler);
        getChildren().add(stockPile);

        discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
        discardPile.setBlurredBackground();
        discardPile.setLayoutX(285);
        discardPile.setLayoutY(20);
        getChildren().add(discardPile);

        for (int i = 0; i < 4; i++) {
            Pile foundationPile = new Pile(Pile.PileType.FOUNDATION, "Foundation " + i, FOUNDATION_GAP);
            foundationPile.setBlurredBackground();
            foundationPile.setLayoutX(610 + i * 180);
            foundationPile.setLayoutY(20);
            foundationPiles.add(foundationPile);
            getChildren().add(foundationPile);
        }
        for (int i = 0; i < 7; i++) {
            Pile tableauPile = new Pile(Pile.PileType.TABLEAU, "Tableau " + i, TABLEAU_GAP);
            tableauPile.setBlurredBackground();
            tableauPile.setLayoutX(95 + i * 180);
            tableauPile.setLayoutY(275);
            tableauPiles.add(tableauPile);
            getChildren().add(tableauPile);
        }
    }

    public void dealCards() {
        Iterator<Card> deckIterator = deck.iterator();
        for (int i = 0; i < tableauPiles.size(); i++) {
            for (int j = 0; j <= i; j++) {
                Card card = deckIterator.next();
                tableauPiles.get(i).addCard(card);
                addMouseEventHandlers(card);
                getChildren().add(card);
            }
            tableauPiles.get(i).getTopCard().flip();
        }
        deckIterator.forEachRemaining(card -> {
            stockPile.addCard(card);
            addMouseEventHandlers(card);
            getChildren().add(card);
        });
    }

    public void setTableBackground(Image tableBackground) {
        setBackground(new Background(new BackgroundImage(tableBackground,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));
    }

}
