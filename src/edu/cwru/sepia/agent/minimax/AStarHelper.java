package edu.cwru.sepia.agent.minimax;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;

public class AStarHelper {

	static class MapLocation {

		public int x, y;
		public MapLocation parent;
		public float cost;

		public MapLocation(int x, int y, MapLocation cameFrom, float cost) {
			this.x = x;
			this.y = y;
			this.parent = cameFrom;
			this.cost = cost;
		}

		@Override
		public String toString() {
			return "(" + x + "," + y + ")";
		};

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + x;
			result = prime * result + y;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MapLocation other = (MapLocation) obj;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}

	}

	/**
	 * Takes a unit and its target and computes an optimal path avoiding any
	 * obstacles.
	 * 
	 * @return A Stack representing the path.
	 */
	public Stack<MapLocation> aStarSearch(UnitState unit, UnitState target,
			int xExtent, int yExtent, Collection<ResourceView> resources) {
		MapLocation start = new MapLocation(unit.getXPos(), unit.getYPos(),
				null, 0);
		MapLocation goal = new MapLocation(target.getXPos(), target.getYPos(),
				null, 0);
		Set<MapLocation> resourceLocations = new HashSet<AStarHelper.MapLocation>();
		for (ResourceView resource : resources) {
			resourceLocations.add(new MapLocation(resource.getXPosition(),
					resource.getYPosition(), null, 0));
		}
		// Create the open and closed list
		AStarList openList = newAStarList(goal);
		Set<MapLocation> closedList = new HashSet<AStarHelper.MapLocation>();
		// Add the current location to the open list
		openList.add(start);
		MapLocation currentLoc = openList.pop();

		while (currentLoc != null) {
			// If the current location is the goal, build the path
			if (currentLoc.equals(goal)) {
				return buildPath(currentLoc, start);
			}
			// Get the neighbors that are on the map and not obstacles
			Collection<MapLocation> neighbors = getValidNeighbors(currentLoc,
					xExtent, yExtent, resourceLocations);
			// Check each neighbor
			for (MapLocation loc : neighbors) {
				// If a better path to this node is in the open list, skip it
				if (openList.alreadyContainsWithLowerCost(loc)) {
					continue;
				}
				// If already in closed set
				if (closedList.contains(loc)) {
					continue;
				}
				// Remove this node from any list that may contain it
				closedList.remove(loc);
				openList.remove(loc);
				// Add it to the open list
				openList.add(loc);
			}
			// Mark currentLoc as having been searched
			closedList.add(currentLoc);
			// Move to the next best node
			currentLoc = openList.pop();
		}
		// If the closed list is empty, then there is no path
		System.out.println("No available path.");
		System.exit(0);
		return new Stack<MapLocation>();
	}

	private Stack<MapLocation> buildPath(MapLocation dest, MapLocation start) {
		Stack<MapLocation> path = new Stack<MapLocation>();
		MapLocation end = dest.parent;
		while (!end.equals(start)) {
			path.push(end);
			end = end.parent;
		}
		return path;
	}

	private Collection<MapLocation> getValidNeighbors(MapLocation currentLoc,
			int xExtent, int yExtent, Set<MapLocation> resourceLocations) {
		Collection<MapLocation> neighbors = new HashSet<MapLocation>();
		Collection<MapLocation> candidates = new HashSet<MapLocation>();
		int x = currentLoc.x;
		int y = currentLoc.y;
		float cost = currentLoc.cost + 1;
		// N
		candidates.add(new MapLocation(x, y - 1, currentLoc, cost));
		// W
		candidates.add(new MapLocation(x + 1, y, currentLoc, cost));
		// S
		candidates.add(new MapLocation(x, y + 1, currentLoc, cost));
		// E
		candidates.add(new MapLocation(x - 1, y, currentLoc, cost));
		for (MapLocation loc : candidates) {
			if (!resourceLocations.contains(loc) && 0 <= loc.x
					&& loc.x < xExtent && 0 <= loc.y && loc.y < yExtent) {
				neighbors.add(loc);
			}
		}
		return neighbors;
	}

	private AStarList newAStarList(MapLocation goal) {
		return new AStarList((o1, o2) -> {
			float c1 = totalCost(o1, goal);
			float c2 = totalCost(o2, goal);
			if (c1 < c2) {
				return -1;
			} else if (c1 > c2) {
				return 1;
			} else {
				return 0;
			}
		});
	}

	private float totalCost(MapLocation loc, MapLocation goal) {
		float g = loc.cost;
		float h = Math.max(Math.abs(goal.x - loc.x), Math.abs(goal.y - loc.y));
		return g + h;
	}

}
