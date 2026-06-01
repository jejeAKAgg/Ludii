package utils;

import game.Game;
import game.types.board.SiteType;
import other.AI;
import other.context.Context;
import other.move.Move;
import other.state.container.ContainerState;
import other.trial.Trial;
import utils.ContextDeterminiser.HiddenType;

import java.util.List;
import java.util.Random;

/**
 * DeterminizedAgent
 * -----------------
 *
 * A generic wrapper that adds smart determinization to any existing Ludii agent.
 *
 * Instead of modifying the code of existing agents (UCT, MAST, Alpha-Beta...),
 * this class wraps them and plugs in the determinization layer via the
 * contextCopyer hook exposed by Ludii's AI base class.
 *
 * Usage:
 *   new DeterminizedAgent(MCTS.createUCT())      // UCT + determinization
 *   new DeterminizedAgent(new FlatMonteCarlo())  // Flat MC + determinization
 *
 * NOTE: the DeterminizationEngine is currently specific to Battleship
 * (fixed ship sizes, 10x10 grid). Generalising it to other games is
 * left as future work.
 *
 * @author LECHAT Jérôme
 */
public class DeterminizedAgent extends AI
{

    private final AI wrappedAgent; // The basic AI we want to make 'smart'
    private DeterminizationEngine engine; // The engine
    private HiddenType hiddenType; // The type of hidden information game
    private final Random rnd = new Random();

    private int playerID;
    private int opponentZoneStart;

    private boolean[][] hitMap = new boolean[GRID_SIZE][GRID_SIZE]; // HITs history, made for Battleship game [THESIS ONLY]
    private boolean[][] missMap = new boolean[GRID_SIZE][GRID_SIZE]; // MISSes history, made for Battleship game [THESIS ONLY]
    private int lastProcessedMove = 0; // Number of moves made since the start of the game

    /* Hardcoded settings, made for Battleship game [THESIS ONLY] */
    private static final int GRID_SIZE = 10;



    /**
     * Wraps any Ludii AI with smart determinization.
     *
     * @param agentToWrap the agent we want to make 'smarter'
     */
    public DeterminizedAgent(final AI agentToWrap)
    {
        this.wrappedAgent = agentToWrap;
        setFriendlyName(agentToWrap.friendlyName() + "+Det");
    }

    @Override
    public void initAI(final Game game, final int playerID)
    {
        this.playerID = playerID;
        this.opponentZoneStart = (playerID == 1) ? 100 : 0;
        this.hiddenType = ContextDeterminiser.detect(game);

        // Reset the incremental state
        this.hitMap = new boolean[GRID_SIZE][GRID_SIZE];
        this.missMap = new boolean[GRID_SIZE][GRID_SIZE];
        this.lastProcessedMove = 0;

        // Start with an empty engine — will be updated on first move
        this.engine = new DeterminizationEngine(new boolean[GRID_SIZE][GRID_SIZE], new boolean[GRID_SIZE][GRID_SIZE], rnd);

        // Initialise the wrapped agent normally
        wrappedAgent.initAI(game, playerID);

        // Plug the determinization into the wrapped agent's copy mechanism
        final int player = playerID;
        final DeterminizationEngine engine = this.engine;
        final ContextDeterminiser.HiddenType type = this.hiddenType;

        wrappedAgent.setContextCopyer(contextCopyer = (ctx) -> ContextDeterminiser.getDeterminisedContext(ctx, player, engine, type));
    }

    @Override
    public Move selectAction(final Game game, final Context context, final double maxSeconds, final int maxIterations, final int maxDepth)
    {
        // Placement phase in the Battleship game [THESIS ONLY]
        if (context.trial().moveNumber() < 10) {
            return wrappedAgent.selectAction(game, context, maxSeconds, maxIterations, maxDepth);
        }

        // Rebuild HITs/MISSes history and update the engine
        updateEngine(context);

        // Rebind contextCopyer with the fresh engine
        final int player = this.playerID;
        final DeterminizationEngine engine = this.engine;
        final ContextDeterminiser.HiddenType type = this.hiddenType;

        wrappedAgent.setContextCopyer((ctx) -> ContextDeterminiser.getDeterminisedContext(ctx, player, engine, type));

        // Delegate the actual decision to the wrapped agent
        return wrappedAgent.selectAction(game, context, maxSeconds, maxIterations, maxDepth);
    }

    /**
     * Replays the full game history to reconstruct hit and miss maps then rebuilds the DeterminizationEngine with updated constraints
     */
    private void updateEngine(final Context context)
    {
        final List<Move> allMoves = context.trial().generateRealMovesList();

        // If nothing new, then nothing to do
        if (allMoves.size() == lastProcessedMove)
        {
            this.engine = new DeterminizationEngine(hitMap, missMap, rnd);
            return;
        }

        final Context replay = new Context(context.game(), new Trial(context.game()));
        context.game().start(replay);

        for (int i = 0; i < allMoves.size(); i++)
        {
            final Move mv = allMoves.get(i);

            // We only replay the new moves
            if (i >= lastProcessedMove && mv.mover() == playerID)
            {
                final int site = mv.to();
                final int relativeIndex = site - opponentZoneStart;

                if (relativeIndex >= 0 && relativeIndex < 100)
                {
                    final ContainerState cs = replay.state().containerStates()[0];
                    final int col = relativeIndex % GRID_SIZE;
                    final int row = relativeIndex / GRID_SIZE;

                    if (cs.who(site, 0, SiteType.Cell) != 0)
                    {
                        hitMap[col][row] = true;
                    } else {
                        missMap[col][row] = true;
                    }
                }
            }
            context.game().apply(replay, mv);
        }

        lastProcessedMove = allMoves.size();
        this.engine = new DeterminizationEngine(hitMap, missMap, rnd);
    }
}
