package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.google.common.collect.Maps;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory<E> implements Factory<GameState> {

	@Nonnull
	@Override
	public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
	}


	private final class MyGameState implements GameState {

		private GameSetup setup;    //access to game graph and round.
		private ImmutableSet<Piece> remaining;    //keeps track of pieces yet to move in current round.
		private ImmutableList<LogEntry> log; //holds the travel log and counts rounds.
		private Player mrX;
		private List<Player> detectives;
		private ImmutableList<Player> everyone; //keeps track of which players are in the game.
		private ImmutableSet<Move> moves; //currently possible/available moves.
		private ImmutableSet<Piece> winner; //holds current winner(s).

		private MyGameState(final GameSetup setup,
							final ImmutableSet<Piece> remaining,
							final ImmutableList<LogEntry> log,
							final Player mrX,
							final List<Player> detectives) {

			// validation on inputs
			if (mrX == null || detectives == null) throw new NullPointerException();

			for (Player detective : detectives) {
				if (detective == null) throw new NullPointerException();
				if (detective.isMrX()
						|| detective.has(Ticket.SECRET)
						|| detective.has(Ticket.DOUBLE)) throw new IllegalArgumentException();
			}

			// Check that detectives have unique colours
			if (detectives.size() != countUnique(detectives, x -> x.piece().webColour())) {
				throw new IllegalArgumentException();
			}

			// Check that detectives have unique locations
			if (detectives.size() != countUnique(detectives, Player::location)) {
				throw new IllegalArgumentException();
			}

			if (!mrX.isMrX()) throw new IllegalArgumentException();
			if (setup.rounds.isEmpty()) throw new IllegalArgumentException();
			if (setup.graph.nodes().isEmpty()) throw new IllegalArgumentException();

			// initialise attributes
			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;
			this.everyone = ImmutableList.<Player>builder()
					.add(mrX)
					.addAll(detectives)
					.build();

			// determine available moves
			final var single = new ArrayList<SingleMove>();
			final var doubles = new ArrayList<DoubleMove>();
			for (Piece piece : remaining) {
				Player player = pieceToPlayer(piece);
				single.addAll(makeSingleMoves(setup, detectives, player, player.location()));
				if (player.isMrX() && player.has(Ticket.DOUBLE) && (setup.rounds.size() > 1)) {
					doubles.addAll(makeDoubleMoves(setup, detectives, player, player.location()));
				}
			}
			this.moves = ImmutableSet.<Move>builder()
					.addAll(single)
					.addAll(doubles)
					.build();


			// determine available moves for detectives only
			final var detectiveMoves = new ArrayList<SingleMove>();
			for (Player player : detectives) {
				detectiveMoves.addAll(makeSingleMoves(setup, detectives, player, player.location()));
			}
			ImmutableSet<Move> detectiveMoveSet = ImmutableSet.copyOf(detectiveMoves);

			// check whether any detective is on the same location as mrX
			boolean locationMatch = false;
			for (Player detective : detectives) {
				if (detective.location() == mrX.location()) {
					locationMatch = true;
					break;
				}
			}

			// check whether the detectives are all stuck
			boolean stuck = true;
			for (Player detective : detectives) {
				if (movesContain(detective.piece(), detectiveMoveSet)) {
					stuck = false;
				}
			}

			// determine whether there is a winner
			if (stuck) {
				this.winner = ImmutableSet.of(mrX.piece());
				this.moves = ImmutableSet.<Move>builder().build();
			}
			else if (remaining.contains(mrX.piece()) && !movesContain(mrX.piece(), moves)) {
				this.winner = ImmutableSet.copyOf(detectivesToPieces());
				this.moves = ImmutableSet.<Move>builder().build();
			}
			else if (locationMatch) {
				this.winner = ImmutableSet.copyOf(detectivesToPieces());
				this.moves = ImmutableSet.<Move>builder().build();
			}
			else if ((setup.rounds.size() == log.size()) && remaining.contains(mrX.piece())) {
				this.winner = ImmutableSet.of(mrX.piece());
				this.moves = ImmutableSet.<Move>builder().build();
			}
			else this.winner = ImmutableSet.<Piece>builder().build();
		}

		/**
		 * @param piece the piece to search for
		 * @param moves all available moves
		 * @return true if the piece has any available moves
		 */
		private boolean movesContain(Piece piece, ImmutableSet<Move> moves) {
			boolean result = false;
			for (Move move : moves) {
				if (move.commencedBy().equals(piece)) result = true;
			}
			return result;
		}

		/**
		 * @param list a list of Players
		 * @param function a function that maps each player to its attribute of interest
		 * @return the number of players in the list that have a unique (particular) attribute
		 */
		private int countUnique(List<Player> list, Function<Player, ?> function) {
			return (int) list.stream()
					.map(function)
					.distinct()
					.count();
		}

		/**
		 * @param piece the piece to be converted
		 * @return the player that corresponds to the piece
		 */
		private Player pieceToPlayer(Piece piece) {
			Player result = null;
			for (Player player : everyone) {
				if (player.piece() == piece) result = player;
			}
			return result;
		}

		@Nonnull
		@Override
		public GameSetup getSetup() {
			return setup;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			Piece[] players = everyone.stream()
										.map(Player::piece)
										.toArray(Piece[]::new);
			return ImmutableSet.copyOf(players);
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Detective detective) {
			for (final var p : detectives) {
				if (p.piece() == detective) return Optional.of(p.location());
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			for (Player player : everyone) {
				if (player.piece() == piece) {
					return Optional.of(player.tickets())
							.map(tickets -> ticket -> tickets.getOrDefault(ticket, 0));
				}
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			return winner;
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			return moves;
		}

		/**
		 * @return the set of detectives' pieces
		 */
		private Set<Piece> detectivesToPieces() {
			return detectives.stream().map(Player::piece).collect(Collectors.toSet());
		}

		/**
		 *@param piece the piece to be removed from remaining
		 *@return a new remaining set of pieces
		 **/
		private ImmutableSet<Piece> updateRemaining(Piece piece) {
			Set<Piece> set = new HashSet<>(remaining);
			set.remove(piece);
			if (set.isEmpty()) {
				if (piece.isMrX()) {
					set = detectivesToPieces();
					set.removeIf(detective -> makeSingleMoves(setup, detectives, pieceToPlayer(detective), pieceToPlayer(detective).location()).isEmpty());
				}
				else set.add(mrX.piece());
			}
			return ImmutableSet.copyOf(set);
		}

		/**
		 * @param ticket the ticket used in the move
		 * @param location the new location of mrX
		 * @return an updated log with the new entry
		 */

		private ImmutableList<LogEntry> updateLog(Ticket ticket, int location, ImmutableList<LogEntry> newLog1) {
			List<LogEntry> list = new ArrayList<>(newLog1);
			int round = newLog1.size();
			if (setup.rounds.get(round)) list.add(LogEntry.reveal(ticket, location));
			else list.add(LogEntry.hidden(ticket));
			return ImmutableList.copyOf(list);
		}

			@Override
		public GameState advance(Move move) {
			if (!moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);
			Function<SingleMove, GameState> smf = move1 -> {
				Player player = pieceToPlayer(move1.commencedBy());
				Player newPlayer = player.at(move1.destination).use(move1.ticket);
				List<Player> newDetectives = new ArrayList<>(detectives);
				ImmutableList<LogEntry> newLog = log;
				if (player.isDetective()) {
					mrX = mrX.give(move1.ticket);
					newDetectives.remove(player);
					newDetectives.add(newPlayer);
				}
				if (player.isMrX()) {
					mrX = newPlayer;
					newLog = updateLog(move1.ticket, move1.destination, log);
				}
				ImmutableSet<Piece> newRemaining = updateRemaining(move1.commencedBy());
				return new MyGameState(setup, newRemaining, newLog, mrX, newDetectives);
			};

			Function<DoubleMove, GameState> dmf = move12 -> {
				Player player = pieceToPlayer(move12.commencedBy());
				if (player.isDetective()) throw new IllegalArgumentException("detectives do not have access to this move.");
				mrX = player.at(move12.destination2).use(move12.tickets());
				ImmutableList<LogEntry> newLog;
				ImmutableList<LogEntry> newerLog;
				newLog = updateLog(move12.ticket1, move12.destination1, log);
				newerLog = updateLog(move12.ticket2, move12.destination2, newLog);
				ImmutableSet<Piece> newRemaining = updateRemaining(move12.commencedBy());

				return new MyGameState(setup, newRemaining, newerLog, mrX, detectives);
			};

			return move.visit(new FunctionalVisitor<>(smf, dmf));
		}
	}

	private static boolean unoccupied(int destination, List<Player> detectives) {
		for (Player detective : detectives) {
			if (destination == detective.location()) return false;
		}
		return true;
	}

	private static ImmutableSet<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
		final var singleMoves = new ArrayList<SingleMove>();
		for (int destination : setup.graph.adjacentNodes(source)) {
			if (unoccupied(destination, detectives)) {
				for (Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
					if (player.has(t.requiredTicket())) {
						singleMoves.add(new SingleMove(player.piece(), source, t.requiredTicket(), destination));
					}
				}
				if (player.has(Ticket.SECRET)) {
					singleMoves.add(new SingleMove(player.piece(), source, Ticket.SECRET, destination));
				}
			}
		}
		return ImmutableSet.copyOf(singleMoves);
	}

	private static ImmutableSet<DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
		final var doubleMoves = new ArrayList<DoubleMove>();
		for (int destination1 : setup.graph.adjacentNodes(source)) {
			if (unoccupied(destination1, detectives)) {
				for (Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination1, ImmutableSet.of()))) {
					if (player.has(t.requiredTicket())) {
						ImmutableSet<SingleMove> singles = makeSingleMoves(setup, detectives, player, destination1);
						for (SingleMove single : singles) {
							if (!single.ticket.equals(t.requiredTicket()) || player.hasAtLeast(t.requiredTicket(), 2)) {
								doubleMoves.add(new DoubleMove(player.piece(), source, t.requiredTicket(), destination1, single.ticket, single.destination));
							}
						}
					}
				}
				if (player.has(Ticket.SECRET)) {
					ImmutableSet<SingleMove> singles = makeSingleMoves(setup, detectives, player, destination1);
					for (SingleMove single : singles) {
						if (!single.ticket.equals(Ticket.SECRET) || player.hasAtLeast(Ticket.SECRET, 2)) {
							doubleMoves.add(new DoubleMove(player.piece(), source, Ticket.SECRET, destination1, single.ticket, single.destination));
						}
					}
				}
			}
		}
		return ImmutableSet.copyOf(doubleMoves);
	}
}
