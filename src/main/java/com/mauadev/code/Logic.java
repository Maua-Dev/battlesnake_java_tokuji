package com.mauadev.code;

// Bem-vindo ao
// __________         __    __  .__                               __
// \______   \_____ _/  |__/  |_|  |   ____   ______ ____ _____  |  | __ ____
//  |    |  _/\__  \   __\   __\  | _/ __ \ /  ___//    \__  \ |  |/ // __ \
//  |    |   \ / __ \|  |  |  | |  |_\  ___/ \___ \|   |  \/ __ \|    <\  ___/
//  |________/(______/__|  |__| |____/\_____>______>___|__(______/__|__\_____>
//
// ESTE E O ARQUIVO QUE VOCE VAI EDITAR. Todo o resto do projeto existe
// so para levar o estado do jogo ate as quatro funcoes abaixo.
//
// Estrategia (pensada para partidas com 4+ cobras): descarta o que mata na
// hora, evita becos (flood fill) e head-to-head com cobras maiores, e so busca
// comida quando esta com fome ou nao e a maior da mesa.
// Documentacao: https://docs.battlesnake.com

import com.mauadev.code.entities.Board;
import com.mauadev.code.entities.Coordinate;
import com.mauadev.code.entities.GameState;
import com.mauadev.code.entities.Snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Logica da cobra. E AQUI que voce programa a inteligencia da snake.
 * <p>
 * GET  /      -> {@link #info()}<br>
 * POST /start -> {@link #start(GameState)}<br>
 * POST /move  -> {@link #getMove(GameState)}  foco principal<br>
 * POST /end   -> {@link #end(GameState)}
 */
public class Logic {

    /**
     * GET / - chamado quando voce cadastra a cobra e a cada partida.
     * Controla a aparencia dela. Opcoes de cabeca, cauda e cor:
     * https://docs.battlesnake.com/guides/customizations
     */
    public static java.util.Map<String, String> info() {
        java.util.Map<String, String> info = new java.util.HashMap<>();
        info.put("apiversion", "1");
        info.put("author", "Tokuji");
        info.put("color", "#0077B6"); // ciano escuro / azul petroleo
        info.put("head", "sand-worm");
        info.put("tail", "round-bum");
        return info;
    }

    /**
     * POST /start - chamado uma vez, quando a partida comeca.
     * Bom lugar para preparar qualquer estado inicial.
     */
    public static void start(GameState state) {
        // state.getTurn(), state.getBoard(), state.getYou() estao disponiveis
    }

    /**
     * POST /end - chamado uma vez, quando a partida termina.
     */
    public static void end(GameState state) {
        // Voce pode analisar o estado final para saber se venceu ou perdeu.
    }

    /**
     * POST /move - chamado a cada turno. Aqui mora a inteligencia da sua cobra.
     * Deve retornar "up", "down", "left" ou "right".
     * Exemplo do JSON recebido: https://docs.battlesnake.com/api/example-move
     *
     * @param state estado atual do jogo
     * @return direcao escolhida
     */
    public static String getMove(GameState state) {
        Snake me = state.getYou();
        Coordinate head = me.getBody().get(0);
        Board board = state.getBoard();
        List<Coordinate> food = orEmpty(board.getFood());
        List<Coordinate> hazards = orEmpty(board.getHazards());
        List<Snake> opponents = new ArrayList<>();
        for (Snake s : orEmpty(board.getSnakes())) {
            if (!me.getId().equals(s.getId())) opponents.add(s);
        }
        int myLength = me.getBody().size();
        int biggestOpponent = 0;
        for (Snake o : opponents) biggestOpponent = Math.max(biggestOpponent, o.getBody().size());

        Grid grid = new Grid(board.getWidth(), board.getHeight());
        grid.block(me);
        for (Snake o : opponents) grid.block(o);

        String bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < MOVES.length; i++) {
            int[] target = {head.getX() + MOVES[i][0], head.getY() + MOVES[i][1]};
            if (!grid.free(target[0], target[1])) continue; // parede, pescoco ou corpo: morte certa

            boolean eats = contains(food, target[0], target[1]);
            boolean inHazard = contains(hazards, target[0], target[1]);
            if (inHazard && !eats && me.getHealth() <= HAZARD_DAMAGE + 1) continue; // o hazard zera a vida

            int score = 0;

            for (Snake o : opponents) {
                if (distance(o.getBody().get(0), target[0], target[1]) == 1) {
                    int theirs = o.getBody().size();
                    score += theirs > myLength ? H2H_BIGGER : theirs == myLength ? H2H_EQUAL : H2H_SMALLER;
                }
            }

            int[] space = {0};
            grid.bfs(target[0], target[1], (x, y, d) -> {
                space[0]++;
                return false;
            });
            if (space[0] < myLength) score += TRAP;
            // Espaco acima de 2x o corpo nao faz diferenca: ai quem decide e a comida.
            score += Math.min(space[0], 2 * myLength);

            if (inHazard) score += HAZARD;

            // Comida mais proxima que nenhum adversario maior/igual alcanca antes.
            int[] foodDist = {-1};
            grid.bfs(target[0], target[1], (x, y, d) -> {
                if (!contains(food, x, y)) return false;
                for (Snake o : opponents) {
                    if (o.getBody().size() >= myLength && distance(o.getBody().get(0), x, y) <= d + 1) return false;
                }
                foodDist[0] = d;
                return true;
            });
            if (foodDist[0] >= 0) {
                int d = foodDist[0];
                if (me.getHealth() - 1 - d < HUNGER_MARGIN) {
                    score += Math.max(0, 200 - 5 * d); // fome: comida vira prioridade
                } else if (myLength <= biggestOpponent) {
                    score += Math.max(0, 40 - 2 * d); // crescer para ganhar os head-to-heads
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = MOVE_NAMES[i];
            }
        }

        return bestMove != null ? bestMove : "up"; // sem saida
    }

    private static final int[][] MOVES = {{0, 1}, {0, -1}, {-1, 0}, {1, 0}};
    private static final String[] MOVE_NAMES = {"up", "down", "left", "right"};

    // Pesos da pontuacao. Ordem de gravidade: beco > head-to-head > o resto.
    private static final int TRAP = -1000;        // espaco alcancavel menor que o nosso corpo
    private static final int H2H_BIGGER = -500;   // casa que um adversario maior alcanca junto com a gente
    private static final int H2H_EQUAL = -300;    // empate de tamanho: morrem os dois
    private static final int H2H_SMALLER = 10;    // adversario menor: o head-to-head e nosso
    private static final int HAZARD = -20;
    private static final int HUNGER_MARGIN = 15;  // vida que queremos sobrando ao chegar na comida
    // A entidade Game nao traz o ruleset, entao usamos o dano padrao do royale.
    private static final int HAZARD_DAMAGE = 14;

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    private static boolean contains(List<Coordinate> cells, int x, int y) {
        for (Coordinate c : cells) {
            if (c.getX() == x && c.getY() == y) return true;
        }
        return false;
    }

    private static int distance(Coordinate a, int x, int y) {
        return Math.abs(a.getX() - x) + Math.abs(a.getY() - y);
    }

    private interface Visit {
        /** Devolve true para parar o BFS. */
        boolean at(int x, int y, int distance);
    }

    /** Casas ocupadas do tabuleiro neste turno. */
    private static class Grid {
        final int w, h;
        final boolean[] blocked;

        Grid(int w, int h) {
            this.w = w;
            this.h = h;
            this.blocked = new boolean[w * h];
        }

        void block(Snake snake) {
            // A cauda sai do lugar neste turno, a nao ser que a cobra tenha
            // acabado de comer (ai os dois ultimos segmentos ficam empilhados).
            List<Coordinate> body = snake.getBody();
            int n = body.size();
            boolean tailMoves = n >= 2 && (body.get(n - 1).getX() != body.get(n - 2).getX()
                    || body.get(n - 1).getY() != body.get(n - 2).getY());
            for (int i = 0; i < (tailMoves ? n - 1 : n); i++) {
                Coordinate c = body.get(i);
                if (inside(c.getX(), c.getY())) blocked[c.getY() * w + c.getX()] = true;
            }
        }

        boolean inside(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h;
        }

        boolean free(int x, int y) {
            return inside(x, y) && !blocked[y * w + x];
        }

        // ponytail: os corpos ficam parados no BFS; casas que liberam com o tempo
        // (caudas andando) nao contam. Pessimista em espaco apertado.
        void bfs(int sx, int sy, Visit visit) {
            boolean[] seen = new boolean[w * h];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{sx, sy, 0});
            seen[sy * w + sx] = true;
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                if (visit.at(c[0], c[1], c[2])) return;
                for (int[] m : MOVES) {
                    int x = c[0] + m[0], y = c[1] + m[1];
                    if (free(x, y) && !seen[y * w + x]) {
                        seen[y * w + x] = true;
                        queue.add(new int[]{x, y, c[2] + 1});
                    }
                }
            }
        }
    }
}