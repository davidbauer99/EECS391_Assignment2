package edu.cwru.sepia.agent.minimax;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.agent.minimax.AStarHelper.MapLocation;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.environment.model.state.UnitTemplate.UnitTemplateView;
import edu.cwru.sepia.util.Direction;

/**
 * This class stores all of the information the agent needs to know about the
 * state of the game. For example this might include things like footmen HP and
 * positions.
 *
 * Add any information or methods you would like to this class, but do not
 * delete or change the signatures of the provided methods.
 */
public class GameState {

	private static final int DISTANCE_WEIGHT = -1;
	private static final int FOOTMAN_HP_WEIGHT = 4;
	private static final int FOOTMAN_WIEGHT = 20;
	private static final int ARCHER_HP_WEIGHT = -10;
	private static final int ARCHER_WEIGHT = -50;
	private static final int A_STAR_PATH_BONUS = 15;
	// Create a set of just N,S,E,W for iteration.
	private static final Set<Direction> VALID_DIRECTIONS = new HashSet<Direction>();
	{
		VALID_DIRECTIONS.add(Direction.NORTH);
		VALID_DIRECTIONS.add(Direction.WEST);
		VALID_DIRECTIONS.add(Direction.EAST);
		VALID_DIRECTIONS.add(Direction.SOUTH);
	}
	private static final AStarHelper aStarHelper = new AStarHelper();

	private final int xExtent;
	private final int yExtent;
	private final List<ResourceView> resources;
	private final List<UnitState> footmen;
	private final List<UnitState> archers;
	private final boolean isFootmanTurn;
	private final Map<Integer, Stack<MapLocation>> aStarResult;

	/**
	 * You will implement this constructor. It will extract all of the needed
	 * state information from the built in SEPIA state view.
	 *
	 * You may find the following state methods useful:
	 *
	 * state.getXExtent() and state.getYExtent(): get the map dimensions
	 * state.getAllResourceIDs(): returns all of the obstacles in the map
	 * state.getResourceNode(Integer resourceID): Return a ResourceView for the
	 * given ID
	 *
	 * For a given ResourceView you can query the position using
	 * resource.getXPosition() and resource.getYPosition()
	 *
	 * For a given unit you will need to find the attack damage, range and max
	 * HP unitView.getTemplateView().getRange(): This gives you the attack range
	 * unitView.getTemplateView().getBasicAttack(): The amount of damage this
	 * unit deals unitView.getTemplateView().getBaseHealth(): The maximum amount
	 * of health of this unit
	 *
	 * @param state
	 *            Current state of the episode
	 */
	public GameState(State.StateView state) {
		this.xExtent = state.getXExtent();
		this.yExtent = state.getYExtent();
		this.resources = state.getAllResourceNodes();
		this.footmen = createUnitStates(state.getUnits(0));
		this.archers = createUnitStates(state.getUnits(1));
		this.isFootmanTurn = true;
		this.aStarResult = null;
	}

	public GameState(int xBound, int yBound, List<UnitState> foots,
			List<UnitState> archs, List<ResourceView> resourceList,
			boolean isFootmanTurn, Map<Integer, Stack<MapLocation>> aStarResult) {
		this.xExtent = xBound;
		this.yExtent = yBound;
		this.footmen = foots;
		this.archers = archs;
		this.resources = resourceList;
		this.isFootmanTurn = isFootmanTurn;
		this.aStarResult = aStarResult;
	}

	public boolean isTerminal() {
		return archers.isEmpty() || footmen.isEmpty();
	}

	private List<UnitState> createUnitStates(List<UnitView> units) {
		List<UnitState> unitStates = new ArrayList<UnitState>();
		for (UnitView view : units) {
			UnitTemplateView unitTemplate = view.getTemplateView();
			unitStates.add(new UnitState(view.getXPosition(), view
					.getYPosition(), view.getHP(), unitTemplate
					.getBasicAttack() + unitTemplate.getPiercingAttack(),
					unitTemplate.getRange(), view.getID()));
		}
		return unitStates;
	}

	/**
	 * You will implement this function.
	 *
	 * You should use weighted linear combination of features. The features may
	 * be primitives from the state (such as hp of a unit) or they may be higher
	 * level summaries of information from the state such as distance to a
	 * specific location. Come up with whatever features you think are useful
	 * and weight them appropriately.
	 *
	 * It is recommended that you start simple until you have your algorithm
	 * working. Then watch your agent play and try to add features that correct
	 * mistakes it makes. However, remember that your features should be as fast
	 * as possible to compute. If the features are slow then you will be able to
	 * do less plys in a turn.
	 *
	 * Add a good comment about what is in your utility and why you chose those
	 * features.
	 *
	 * @return The weighted linear combination of the features
	 */
	public double getUtility() {
		if (isTerminal()) {
			return footmen.isEmpty() ? Integer.MAX_VALUE : Integer.MIN_VALUE;
		}

		int totalUtility = 0;
		// negative Distance to archers
		int distanceToArchers = 0;
		for (UnitState footman : footmen) {
			distanceToArchers += DISTANCE_WEIGHT
					* distToClosestEnemy(footman, archers);
		}
		totalUtility += distanceToArchers;
		// positive footman health remaining
		int footmanHealth = 0;
		for (UnitState footman : footmen) {
			footmanHealth += footman.getHealth() * FOOTMAN_HP_WEIGHT;
		}
		totalUtility += footmanHealth;
		// positive footmen alive (medium)
		int footmenAlive = FOOTMAN_WIEGHT * footmen.size();
		totalUtility += footmenAlive;
		// negative archer health remaining (high)
		int archerHealth = 0;
		for (UnitState archer : archers) {
			archerHealth = archer.getHealth() * ARCHER_HP_WEIGHT;
		}
		totalUtility += archerHealth;
		// negative archer alive (high)
		int archersAlive = ARCHER_WEIGHT * archers.size();
		totalUtility += archersAlive;
		// If this state corresponds to a move on the best path, add a bonus
		int bonusPathUtility = 0;
		// !footmanturn indicating that they just moved
		if (!isFootmanTurn && aStarResult != null) {
			System.out.println("Checking astar");
			for (UnitState footman : footmen) {
				Stack<MapLocation> aStarPath = aStarResult.get(footman.getId());
				if (aStarPath != null) {
					MapLocation loc = aStarPath.peek();
					if (loc.x == footman.getXPos()
							&& loc.y == footman.getYPos()) {
						System.out.println("Adding bonus");
						bonusPathUtility += A_STAR_PATH_BONUS;
					}
				}
			}
		}
		totalUtility += bonusPathUtility;
		return totalUtility;
	}

	// Finds the distance to the closest enemy, encouraging a footman to move
	// towards units they are already close to.
	private int distToClosestEnemy(UnitState footman, List<UnitState> archs) {
		int min = Integer.MAX_VALUE;
		Point2D footmanLoc = new Point2D.Double(footman.getXPos(),
				footman.getYPos());
		for (UnitState archer : archs) {
			Point2D archerLoc = new Point2D.Double(archer.getXPos(),
					archer.getYPos());
			min = (int) Math.min(min, Math.abs(footmanLoc.distance(archerLoc)));
		}
		return min;
	}

	/**
	 * You will implement this function.
	 *
	 * This will return a list of GameStateChild objects. You will generate all
	 * of the possible actions in a step and then determine the resulting game
	 * state from that action. These are your GameStateChildren.
	 *
	 * You may find it useful to iterate over all the different directions in
	 * SEPIA.
	 *
	 * for(Direction direction : Directions.values())
	 *
	 * To get the resulting position from a move in that direction you can do
	 * the following x += direction.xComponent() y += direction.yComponent()
	 *
	 * @return All possible actions and their associated resulting game state
	 */
	public List<GameStateChild> getChildren() {
		Map<Integer, List<Action>> actions = new HashMap<Integer, List<Action>>();
		if (isFootmanTurn) {
			for (UnitState footman : footmen) {
				List<Action> footmanActions = new ArrayList<Action>();
				// Add move actions
				footmanActions.addAll(moveActions(footman));
				// Add attack options
				footmanActions.addAll(attackActions(footman, archers,
						footman.getRange()));
				actions.put(footman.getId(), footmanActions);
			}
		} else {
			for (UnitState archer : archers) {
				List<Action> archerActions = new ArrayList<Action>();
				// Add move actions
				archerActions.addAll(moveActions(archer));
				// Add attack actions
				archerActions.addAll(attackActions(archer, footmen,
						archer.getRange()));
				actions.put(archer.getId(), archerActions);
			}
		}
		List<GameStateChild> childrenStates = generateChildren(actions);
		return childrenStates;
	}

	// Takes a map of unitID to a List of actions that unit can take and creates
	// all possible
	// children states
	private List<GameStateChild> generateChildren(
			Map<Integer, List<Action>> actions) {
		// Get the possible pairs of actions
		List<Map<Integer, Action>> actionPairings = getCrossProductOfActions(actions);
		List<GameStateChild> children = new ArrayList<GameStateChild>();
		// If it is the footmans turn compute A* so that optimal moves can be
		// weighted higher
		Map<Integer, Stack<MapLocation>> aStarResult = new HashMap<Integer, Stack<MapLocation>>();
		if (isFootmanTurn) {
			for (UnitState footman : footmen) {
				UnitState closestTarget = null;
				double dist = Integer.MAX_VALUE;
				// A* to closest enemy
				for (UnitState archer : archers) {
					if (distance(footman, archer) < dist) {
						closestTarget = archer;
					}
				}
				aStarResult.put(footman.getId(), aStarHelper.aStarSearch(
						footman, closestTarget, xExtent, yExtent, resources));
			}
		}
		// Create the children
		for (Map<Integer, Action> actionPair : actionPairings) {
			children.add(childState(this, actionPair, aStarResult));
		}
		return children;
	}

	private double distance(UnitState footman, UnitState archer) {
		Point2D footPoint = new Point2D.Double(footman.getXPos(),
				footman.getYPos());
		Point2D archPoint = new Point2D.Double(archer.getXPos(),
				archer.getYPos());
		return Math.abs(footPoint.distance(archPoint));
	}

	private GameStateChild childState(GameState gameState,
			Map<Integer, Action> actions,
			Map<Integer, Stack<MapLocation>> aStarResult) {
		// Copies bounds and resources
		int xBound = gameState.xExtent;
		int yBound = gameState.yExtent;
		List<ResourceView> resourceList = gameState.resources;
		// Create new footmen
		List<UnitState> foots = new ArrayList<UnitState>();
		for (UnitState unit : gameState.footmen) {
			foots.add(new UnitState(unit.getXPos(), unit.getYPos(), unit
					.getHealth(), unit.getDamage(), unit.getRange(), unit
					.getId()));
		}
		// Create new archers
		List<UnitState> archs = new ArrayList<UnitState>();
		for (UnitState unit : gameState.archers) {
			archs.add(new UnitState(unit.getXPos(), unit.getYPos(), unit
					.getHealth(), unit.getDamage(), unit.getRange(), unit
					.getId()));
		}
		// Apply moves and attacks
		if (gameState.isFootmanTurn) {
			applyActions(foots, archs, actions);
		} else {
			applyActions(archs, foots, actions);
		}
		// create a new games state
		GameState newState = new GameState(xBound, yBound, foots, archs,
				resourceList, !gameState.isFootmanTurn, aStarResult);
		// Return a new GameStateChild
		return new GameStateChild(actions, newState);
	}

	private void applyActions(List<UnitState> units, List<UnitState> targets,
			Map<Integer, Action> actions) {
		for (Action action : actions.values()) {
			// If it is a move, move the footman
			if (action.getType() == ActionType.PRIMITIVEMOVE) {
				UnitState unit = unitByID(units, action.getUnitId());
				DirectedAction dirAction = (DirectedAction) action;
				unit.setXPos(unit.getXPos()
						+ dirAction.getDirection().xComponent());
				unit.setYPos(unit.getYPos()
						+ dirAction.getDirection().yComponent());
			} else {
				// Change target health
				TargetedAction targAction = (TargetedAction) action;
				UnitState unit = unitByID(units, targAction.getUnitId());
				UnitState target = unitByID(targets, targAction.getTargetId());
				target.setHealth(target.getHealth() - unit.getDamage());
			}
		}
	}

	private UnitState unitByID(List<UnitState> units, int id) {
		for (UnitState u : units) {
			if (u.getId() == id) {
				return u;
			}
		}
		return null;
	}

	// Creates a List of maps that will contain every possible combination of
	// actions from the input. Will work for an arbitrary number of units.
	private List<Map<Integer, Action>> getCrossProductOfActions(
			Map<Integer, List<Action>> actions) {
		Iterator<Entry<Integer, List<Action>>> actionEntries = actions
				.entrySet().iterator();

		List<Map<Integer, Action>> actionPairings = new ArrayList<Map<Integer, Action>>();
		// Add the actions of the first unit
		actionPairings.addAll(firstUnitsActions(actionEntries.next()));
		List<Map<Integer, Action>> existingPairings = actionPairings;

		// For all subsequent units, add their actions each of the existing
		// actions
		Entry<Integer, List<Action>> nextActions;
		while (actionEntries.hasNext()) {
			nextActions = actionEntries.next();
			actionPairings = new ArrayList<Map<Integer, Action>>();
			for (Map<Integer, Action> actionMap : existingPairings) {
				for (Action nextAction : nextActions.getValue()) {
					Map<Integer, Action> newActionMap = new HashMap<Integer, Action>(
							actionMap);
					newActionMap.put(nextActions.getKey(), nextAction);
					actionPairings.add(newActionMap);
				}
			}
			existingPairings = actionPairings;
		}
		return actionPairings;
	}

	private Collection<? extends Map<Integer, Action>> firstUnitsActions(
			Entry<Integer, List<Action>> actionEntry) {
		List<Map<Integer, Action>> actionList = new ArrayList<Map<Integer, Action>>();
		for (Action action : actionEntry.getValue()) {
			Map<Integer, Action> actionMap = new HashMap<Integer, Action>();
			actionMap.put(actionEntry.getKey(), action);
			actionList.add(actionMap);
		}
		return actionList;
	}

	// Creates attack actions for all targets that are in range
	private List<Action> attackActions(UnitState unit, List<UnitState> targets,
			int range) {
		List<Action> attacks = new ArrayList<Action>();
		for (UnitState target : targets) {
			if (targetDistance(unit, target) <= range) {
				attacks.add(Action.createPrimitiveAttack(unit.getId(),
						target.getId()));
			}
		}
		return attacks;
	}

	/**
	 * Returns the distance from one unit to another as it would be calculated
	 * in a range check.
	 *
	 * @param unitA
	 *            the first unit.
	 * @param unitB
	 *            the second unit.
	 * @return an int representing the distance.
	 */
	private double targetDistance(UnitState unitA, UnitState unitB) {
		int xDiff = Math.abs(unitA.getXPos() - unitB.getXPos());
		int yDiff = Math.abs(unitA.getYPos() - unitB.getYPos());
		return Math.max(xDiff, yDiff);
	}

	/**
	 * Generates all valid move actions for the given unit.
	 *
	 * @param unit
	 *            the unit that will be moving.
	 * @return a List containing all actions corresponding to a valid move.
	 */
	private List<Action> moveActions(UnitState unit) {
		List<Action> moveActions = new ArrayList<Action>();
		int x = unit.getXPos();
		int y = unit.getYPos();
		int id = unit.getId();
		for (Direction direction : VALID_DIRECTIONS) {
			int newX = x + direction.xComponent();
			int newY = y + direction.yComponent();
			if (isValidSpace(newX, newY)) {
				moveActions.add(Action.createPrimitiveMove(id, direction));
			}
		}
		return moveActions;
	}

	/**
	 * Returns true if the space is valid.
	 *
	 * @param newX
	 *            the x position the unit will be moving to.
	 * @param newY
	 *            the y position the unit will be moving to.
	 * @return true if the space is valid, false otherwise.
	 */
	private boolean isValidSpace(int newX, int newY) {
		boolean inBounds = 0 <= newX && 0 <= newY && newX < xExtent
				&& newY < yExtent;
		for (ResourceView resource : resources) {
			if (resource.getXPosition() == newX
					&& resource.getYPosition() == newY) {
				return false;
			}
		}
		return inBounds;
	}
}
