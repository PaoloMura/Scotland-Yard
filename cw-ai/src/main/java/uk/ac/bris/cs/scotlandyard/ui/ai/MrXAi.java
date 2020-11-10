package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableValueGraph;
import org.glassfish.grizzly.Transport;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MrXAi implements Ai {

	@Nonnull @Override public String name() { return "Mr X Ai"; }

	/**
	 * @param location a location on the board
	 * @param board the game board
	 * @return true if there is a detective on the given location
	 */
	private boolean detectiveOn(Integer location, Board board) {
		for (Piece piece : board.getPlayers()) {
			if (piece.isDetective()) {
				Piece.Detective detective = (Piece.Detective) piece;
				if (board.getDetectiveLocation(detective).isPresent()) {
					if (board.getDetectiveLocation(detective).get().equals(location)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * @param setA the first set
	 * @param setB the second set
	 * @return setA \ setB
	 */
	private Set<Integer> setMinus(Set<Integer> setA, Set<Integer> setB) {
		return setA.stream()
				   .filter(setB::contains)
				   .collect(Collectors.toSet());
	}

	/**
	 * @param start the start position from which to calculate distances
	 * @param board the game board
	 * @return
	 */
	private Integer shortestPath(Integer start, Board board) {
		ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;
		// dijkstra's algorithm
		// initialise a set of visited nodes
		Set<Integer> visited = new HashSet<>();
		visited.add(start);
		// initialise a set of detective positions
		Set<Integer> detectivePositions = new HashSet<>();
		// Initialise and fill a map of nodes to distances from start
		Map<Integer, Integer> dist = new HashMap<Integer, Integer>();
		for (Integer node : graph.nodes()) {
			if (node.equals(start)) dist.put(node, 0);
			else if (graph.hasEdgeConnecting(start, node)) dist.put(node, 1);
			else dist.put(node, Integer.MAX_VALUE);
		};
		// repeat until all nodes have been visited
		while (!graph.nodes().equals(visited)) {
			// find the nearest node to the current node
			Integer nearestDist = Integer.MAX_VALUE;
			Integer nearestNode = null;
			for (Integer node : setMinus(graph.nodes(), visited)) {
				if (dist.get(node) <= nearestDist) {
					nearestDist = dist.get(node);
					nearestNode = node;
				}
			}
			// set a new current node and add it to visited
			Integer current = nearestNode;
			visited.add(nearestNode);
			if (detectiveOn(nearestNode, board)) {
				detectivePositions.add(nearestNode);
			}
			assert current != null;
			// update the distances for all unvisited nodes
			for (Integer node : setMinus(graph.nodes(), visited)) {
				Integer edge = Integer.MAX_VALUE;
				if (graph.hasEdgeConnecting(current, node)) edge = 1;
				if (dist.get(current) + edge < dist.get(node)) dist.replace(node, dist.get(current) + edge);
			}
		}
		if (detectivePositions.stream().min(Integer::compare).isPresent()) {
			return dist.get(detectivePositions.stream().min(Integer::compare).get());
		}
		else throw new RuntimeException("No detective minimum distance");
	}

	/**
	 * @param board the game board
	 * @return mrX
	 */
	private Piece getMrX(Board board) {
		for (Piece piece : board.getPlayers()) {
			if (piece.isMrX()) return piece;
		}
		throw new RuntimeException("MrX not found");
	}

	/**
	 * @param location the location of interest
	 * @param board the game board
	 * @return the number of possible moves from the location
	 */
	private Integer getPossibleMoves(Integer location, Board board) {
		Set<EndpointPair<Integer>> edges = board.getSetup().graph.incidentEdges(location);
		Integer result = 0;
		// get MrX's tickets or return 0 if he has none
		if (board.getPlayerTickets(getMrX(board)).isPresent()) {
			Board.TicketBoard tickets = board.getPlayerTickets(getMrX(board)).get();
			// count the number of reachable nodes, given mrX's tickets
			if (edges.isEmpty()) return 0;
			else {
				for (EndpointPair<Integer> edge : edges) {
					if (board.getSetup().graph.edgeValue(edge).isPresent()) {
						ImmutableSet<ScotlandYard.Transport> transports = board.getSetup().graph.edgeValue(edge).get();
						boolean match = false;
						for (ScotlandYard.Transport transport : transports) {
							if (tickets.getCount(transport.requiredTicket()) > 0) {
								match = true;
							}
						}
						if (match) result++;
					}
				}
				return result;
			}
		}
		else return 0;
	}

	/**
	 * @param location the location to move to
	 * @param board the game board
	 * @return the calculated 'score' of moving to that location
	 */
	private float score(Integer location, Board board) {
		float result;
		if (detectiveOn(location, board)) return 0.0f;
		Integer nearestDetective = shortestPath(location, board);
		Integer possibleMoves = getPossibleMoves(location, board);
		return possibleMoves - nearestDetective;
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			@Nonnull AtomicBoolean terminate) {
		// returns a random move, replace with your own implementation
		var moves = board.getAvailableMoves().asList();
		return moves.get(new Random().nextInt(moves.size()));
	}
}
