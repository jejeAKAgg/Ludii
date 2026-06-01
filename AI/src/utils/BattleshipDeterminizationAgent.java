package utils;

import game.Game;
import game.types.board.SiteType;
import main.collections.FastArrayList;
import other.AI;
import other.context.Context;
import other.move.Move;
import other.state.container.ContainerState;
import other.trial.Trial;
import java.util.List;
import java.util.Random;

public class BattleshipDeterminizationAgent extends AI {

    private DeterminizationEngine engine;
    private ContextDeterminiser.HiddenType hiddenType;
    private Random rnd = new Random();

    protected int player = -1;
    private int opponentZoneStart = 100;

    private boolean[][] currentHitMap = new boolean[GRID_SIZE][GRID_SIZE];

    private static final int GRID_SIZE = 10;

    private final boolean useDeterminization;

    public BattleshipDeterminizationAgent()
    {
        this(true);
    }

    /**
     * Configurable constructor.
     *
     * @param useDeterminization true  -> OSLA+Det (smart determinization)
     *                           false -> OSLA (random determinization, equivalent to TAG BASIC)
     */
    public BattleshipDeterminizationAgent(final boolean useDeterminization)
    {
        this.useDeterminization = useDeterminization;
        setFriendlyName(useDeterminization ? "OSLA+Det" : "OSLA");
    }

    @Override
    public Move selectAction(final Game game, final Context context, final double maxSeconds, final int maxIterations, final int maxDepth) {
        FastArrayList<Move> legalMoves = game.moves(context).moves();
        if (context.trial().moveNumber() < 10) return legalMoves.get(rnd.nextInt(legalMoves.size()));

        updateEngine(context);

        Move bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (final Move m : legalMoves) {
            final int site = m.to();

            final Context copy = copyContext(context);
            final ContainerState cs = copy.state().containerStates()[0];

            final int whoInitial = cs.who(site, 0, SiteType.Cell);

            double score = rnd.nextDouble() * 0.1;

            if (whoInitial != 0) {
                score += 100.0;
            }

            int relativeIndex = site - opponentZoneStart;
            if (relativeIndex >= 0 && relativeIndex < 100) {
                int col = relativeIndex % GRID_SIZE;
                int row = relativeIndex / GRID_SIZE;
                score += adjacencyBonus(col, row);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = m;
            }
        }
        return (bestMove != null) ? bestMove : legalMoves.get(rnd.nextInt(legalMoves.size()));
    }

    private void updateEngine(final Context context) {
        final boolean[][] hitMap = new boolean[GRID_SIZE][GRID_SIZE];
        final boolean[][] missMap = new boolean[GRID_SIZE][GRID_SIZE];

        final Context replay = new Context(context.game(), new Trial(context.game()));
        context.game().start(replay);
        final List<Move> allMoves = context.trial().generateRealMovesList();

        for (final Move mv : allMoves) {
            if (mv.mover() == player) {
                final int site = mv.to();
                int relativeIndex = site - opponentZoneStart;

                if (relativeIndex >= 0 && relativeIndex < 100) {
                    final ContainerState cs = replay.state().containerStates()[0];
                    int col = relativeIndex % GRID_SIZE;
                    int row = relativeIndex / GRID_SIZE;
                    int whoVal = cs.who(site, 0, SiteType.Cell);

                    // DEBUG
                    //System.out.println("Tir site=" + site + " who=" + whoVal + " → " + (whoVal != 0 ? "HIT" : "MISS"));

                    if (whoVal != 0) hitMap[col][row] = true;
                    else missMap[col][row] = true;
                }
            }
            context.game().apply(replay, mv);
        }

        this.currentHitMap = hitMap;

        this.engine = useDeterminization
            ? new DeterminizationEngine(hitMap, missMap, rnd)
            : new DeterminizationEngine(new boolean[GRID_SIZE][GRID_SIZE], new boolean[GRID_SIZE][GRID_SIZE], rnd);

        //this.engine = new DeterminizationEngine(hitMap, missMap, rnd);
        final ContextDeterminiser.HiddenType type = this.hiddenType;
        this.contextCopyer = (ctx) -> ContextDeterminiser.getDeterminisedContext(ctx, player, engine, type);
    }

    private double adjacencyBonus(int col, int row) {
        double bonus = 0.0;
        if (col > 0 && currentHitMap[col-1][row]) bonus += 15.0;
        if (col < GRID_SIZE-1 && currentHitMap[col+1][row]) bonus += 15.0;
        if (row > 0 && currentHitMap[col][row-1]) bonus += 15.0;
        if (row < GRID_SIZE-1 && currentHitMap[col][row+1]) bonus += 15.0;
        return bonus;
    }

    @Override
    public void initAI(final Game game, final int playerID) {
        this.player = playerID;
        this.opponentZoneStart = (playerID == 1) ? 100 : 0;
        this.hiddenType = ContextDeterminiser.detect(game);
        this.contextCopyer = (ctx) -> new Context(ctx);
    }
}
