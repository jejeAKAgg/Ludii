package utils;

import game.Game;
import other.AI;
import other.GameLoader;
import other.context.Context;
import other.move.Move;
import other.trial.Trial;
import search.flat.FlatMonteCarlo;
import search.mcts.MCTS;
import search.mcts.backpropagation.MonteCarloBackprop;
import search.mcts.finalmoveselection.RobustChild;
import search.mcts.playout.MAST;
import search.mcts.selection.UCB1;

/**
 * BattleshipBenchmark
 * -------------------
 *
 * Two exhaustive round-robin tournaments for Battleship in Ludii.
 *
 * TOURNAMENT 1: Native Ludii agents (no determinization)
 *   Random, UCT, MAST, FlatMC, OSLA
 *   Expected: UCT/MAST/FlatMC crush Random (~100%) because they
 *   involuntarily access hidden information via who(). This proves
 *   that without determinization, results are not representative.
 *
 * TOURNAMENT 2: Determinized agents (our contribution)
 *   Random, UCT+Det, MAST+Det, FlatMC+Det, OSLA+Det
 *   Expected: win rates comparable to TAG SMART results, proving
 *   that the smart determinization layer works correctly.
 *
 * @author LECHAT Jérôme
 */
public class BattleshipBenchmark
{
    private static final int GAMES_PER_DIRECTION = 100;
    private static final double THINK_TIME = 0.1;
    private static final String GAME_NAME = "Battleships.lud";
    private static final Game GAME = GameLoader.loadGameFromName(GAME_NAME);

    public static void main(final String[] args) throws Exception
    {
        MCTS.NULL_UNDO_DATA = false;

        System.out.println("========================================");
        System.out.println("BATTLESHIP BENCHMARK - TFE26-093 - by LECHAT Jérôme");
        System.out.println("Game: " + GAME_NAME);
        System.out.println("--------------------");
        System.out.println("Games per direction: " + GAMES_PER_DIRECTION);
        System.out.println("Think time: " + THINK_TIME + "s");
        System.out.println("========================================\n");

        runTournament1();
        runTournament2();
    }

    // =========================================================================
    // TOURNAMENT 1 — Native Ludii agents (no determinization)
    // =========================================================================

    private static void runTournament1() throws Exception
    {
        System.out.println("========================================");
        System.out.println("TOURNAMENT 1 — Native agents (no determinization)");
        System.out.println("Expected: all agents crush Random because they");
        System.out.println("involuntarily access hidden information (cheating).");
        System.out.println("========================================\n");

        final String[] names = {"Random", "UCT", "MAST", "FlatMC"};
        final int n = names.length;
        final int[][] wins = new int[n][2]; // [wins, total]

        // Round-robin
        for (int i = 0; i < n; i++)
        {
            for (int j = i + 1; j < n; j++)
            {
                final AI ai1 = createAgent1(i);
                final AI ai2 = createAgent1(j);
                final AI ai3 = createAgent1(j);
                final AI ai4 = createAgent1(i);

                int[] fwd = run(GAME, names[i], ai1, names[j], ai2);
                int[] rev = run(GAME, names[j], ai3, names[i], ai4);

                wins[i][0] += fwd[0] + rev[1];
                wins[j][0] += fwd[1] + rev[0];
                wins[i][1] += fwd[2] + rev[2];
                wins[j][1] += fwd[2] + rev[2];

                printPairSummary(names[i], names[j], fwd, rev);
            }
        }

        printRanking("TOURNAMENT 1 RANKING", names, wins);
    }

    private static AI createAgent1(final int index)
    {
        switch (index)
        {
            case 0: return new RandomAI();
            case 1: return MCTS.createUCT();
            case 2: return createMAST();
            case 3: return new FlatMonteCarlo();
            default: return new RandomAI();
        }
    }

    // =========================================================================
    // TOURNAMENT 2 — Determinized agents
    // =========================================================================

    private static void runTournament2() throws Exception
    {
        System.out.println("========================================");
        System.out.println("TOURNAMENT 2 — Determinized agents");
        System.out.println("Expected: win rates comparable to TAG SMART results.");
        System.out.println("========================================\n");

        final String[] names = {"Random", "UCT+Det", "MAST+Det", "FlatMC+Det", "OSLA+Det"};
        final int n = names.length;
        final int[][] wins = new int[n][2];

        // Round-robin
        for (int i = 0; i < n; i++)
        {
            for (int j = i + 1; j < n; j++)
            {
                final AI ai1 = createAgent2(i);
                final AI ai2 = createAgent2(j);
                final AI ai3 = createAgent2(j);
                final AI ai4 = createAgent2(i);

                int[] fwd = run(GAME, names[i], ai1, names[j], ai2);
                int[] rev = run(GAME, names[j], ai3, names[i], ai4);

                wins[i][0] += fwd[0] + rev[1];
                wins[j][0] += fwd[1] + rev[0];
                wins[i][1] += fwd[2] + rev[2];
                wins[j][1] += fwd[2] + rev[2];

                printPairSummary(names[i], names[j], fwd, rev);
            }
        }

        printRanking("TOURNAMENT 2 RANKING", names, wins);
    }

    private static AI createAgent2(final int index)
    {
        switch (index)
        {
            case 0: return new RandomAI();
            case 1: return new DeterminizedAgent(MCTS.createUCT());
            case 2: return new DeterminizedAgent(createMAST());
            case 3: return new DeterminizedAgent(new FlatMonteCarlo());
            case 4: return new BattleshipDeterminizationAgent(true);
            default: return new RandomAI();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static MCTS createMAST()
    {
        final MCTS mcts = new MCTS(
            new UCB1(), new MAST(),
            new MonteCarloBackprop(), new RobustChild()
        );
        mcts.setFriendlyName("MAST");
        return mcts;
    }

    private static int[] run
    (
        final Game game,
        final String name1, final AI ai1,
        final String name2, final AI ai2
    )
    {
        System.out.println("  > " + name1 + " vs " + name2
            + " — " + GAMES_PER_DIRECTION + " games");

        int wins1 = 0, wins2 = 0, draws = 0;

        for (int g = 0; g < GAMES_PER_DIRECTION; g++)
        {
            ai1.initAI(game, 1);
            ai2.initAI(game, 2);

            final Trial   trial   = new Trial(game);
            final Context context = new Context(game, trial);
            game.start(context);

            while (!trial.over())
            {
                final int  mover = context.state().mover();
                final AI   currentAI = (mover == 1) ? ai1 : ai2;
                final Move move = currentAI.selectAction(
                    game, context, THINK_TIME, -1, -1);
                game.apply(context, move);
            }

            final double[] ranking = trial.ranking();
            if (ranking[1] < ranking[2]) wins1++;
            else if (ranking[2] < ranking[1]) wins2++;
            else                              draws++;
        }

        System.out.println("    " + name1 + ":" + wins1
            + " | " + name2 + ":" + wins2
            + " | Draws:" + draws);

        return new int[]{wins1, wins2, GAMES_PER_DIRECTION};
    }

    private static void printPairSummary
    (
        final String nameA, final String nameB,
        final int[] fwd, final int[] rev
    )
    {
        final int totalA = fwd[0] + rev[1];
        final int totalB = fwd[1] + rev[0];
        final int total = fwd[2] + rev[2];
        System.out.printf("  %s vs %s (%d games) : %s=%.1f%% | %s=%.1f%%%n%n",
            nameA, nameB, total,
            nameA, totalA * 100.0 / total,
            nameB, totalB * 100.0 / total);
    }

    private static void printRanking
    (
        final String title,
        final String[] names,
        final int[][] wins
    )
    {
        System.out.println("========================================");
        System.out.println(title);
        System.out.println("========================================");
        for (int i = 0; i < names.length; i++)
        {
            System.out.printf("  %-20s : %d/%d (%.1f%%)%n",
                names[i], wins[i][0], wins[i][1],
                wins[i][1] > 0 ? wins[i][0] * 100.0 / wins[i][1] : 0.0);
        }
        System.out.println("========================================\n");
    }
}
