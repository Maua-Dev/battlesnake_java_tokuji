package com.mauadev;

import com.mauadev.code.Logic;
import com.mauadev.code.entities.Board;
import com.mauadev.code.entities.Coordinate;
import com.mauadev.code.entities.Game;
import com.mauadev.code.entities.GameState;
import com.mauadev.code.entities.Snake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cenarios da estrategia, chamando a Logic direto. */
public class LogicTest {

    private static Coordinate c(int x, int y) {
        Coordinate coord = new Coordinate();
        coord.setX(x);
        coord.setY(y);
        return coord;
    }

    private static Snake snake(String id, Coordinate... body) {
        Snake s = new Snake();
        s.setId(id);
        s.setName(id);
        s.setHealth(100);
        s.setBody(List.of(body));
        s.setHead(body[0]);
        s.setLength(body.length);
        return s;
    }

    /** Estado com a gente (`me`) e os adversarios, sem comida. */
    private static GameState arena(Snake me, Snake... opponents) {
        List<Snake> snakes = new ArrayList<>(List.of(opponents));
        snakes.add(0, me);

        Board board = new Board();
        board.setWidth(11);
        board.setHeight(11);
        board.setFood(new ArrayList<>());
        board.setHazards(new ArrayList<>());
        board.setSnakes(snakes);

        Game game = new Game();
        game.setId("teste");
        game.setTimeout(500);

        GameState state = new GameState();
        state.setGame(game);
        state.setTurn(4);
        state.setBoard(board);
        state.setYou(me);
        return state;
    }

    @Test
    @DisplayName("evita head-to-head com cobra maior")
    public void evitaHeadToHeadComCobraMaior() {
        // Adversario maior com a cabeca em (7,5): a casa (6,5) e disputada.
        Snake me = snake("eu", c(5, 5), c(4, 5), c(3, 5));
        Snake big = snake("grande", c(7, 5), c(8, 5), c(9, 5), c(10, 5), c(10, 4));
        assertNotEquals("right", Logic.getMove(arena(me, big)));
    }

    @Test
    @DisplayName("evita beco")
    public void evitaBeco() {
        // Esquerda leva a um bolsao de 5 casas, fechado pelo corpo do adversario
        // (que acabou de comer, entao a cauda nao sai): cabem menos casas que o
        // nosso corpo (6). Subir e a unica saida boa.
        Snake me = snake("eu", c(3, 0), c(4, 0), c(5, 0), c(6, 0), c(7, 0), c(8, 0));
        Snake wall = snake("parede", c(0, 4), c(0, 3), c(0, 2), c(1, 2), c(2, 2), c(2, 1), c(2, 1));
        assertEquals("up", Logic.getMove(arena(me, wall)));
    }

    @Test
    @DisplayName("cauda conta como casa livre")
    public void caudaContaComoCasaLivre() {
        // Enrolada no canto: a unica saida e a casa onde esta a propria cauda.
        Snake me = snake("eu", c(0, 0), c(0, 1), c(1, 1), c(1, 0));
        assertEquals("right", Logic.getMove(arena(me)));
    }

    @Test
    @DisplayName("com fome vai para a comida")
    public void comFomeVaiParaAComida() {
        Snake me = snake("eu", c(5, 5), c(5, 4), c(5, 3));
        me.setHealth(10);
        GameState state = arena(me);
        state.getBoard().setFood(List.of(c(7, 5)));
        assertEquals("right", Logic.getMove(state));
    }
}
