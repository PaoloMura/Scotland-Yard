package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	@Nonnull @Override public Model build(GameSetup setup,
	                                      Player mrX,
	                                      ImmutableList<Player> detectives) {
		return new Model() {

			private MyGameStateFactory factory = new MyGameStateFactory<GameState>();
			private GameState modelState = factory.build(setup, mrX, detectives);
			private ImmutableSet<Observer> observers = ImmutableSet.of();

			@Nonnull
			@Override
			public Board getCurrentBoard() {
				return modelState;
			}

			@Override
			public void registerObserver(@Nonnull Observer observer) {
				if (observer == null) throw new NullPointerException();
				if (observers.contains(observer)) throw new IllegalArgumentException();
				Set<Observer> set = new HashSet<>(this.observers);
				set.add(observer);
				this.observers = ImmutableSet.copyOf(set);
			}

			@Override
			public void unregisterObserver(@Nonnull Observer observer) {
				if (observer == null) throw new NullPointerException();
				if (!observers.contains(observer)) throw new IllegalArgumentException();
				Set<Observer> set = new HashSet<>(this.observers);
				set.remove(observer);
				this.observers = ImmutableSet.copyOf(set);
			}

			@Nonnull
			@Override
			public ImmutableSet<Observer> getObservers() {
				return ImmutableSet.copyOf(observers);
			}

			@Override
			public void chooseMove(@Nonnull Move move) {
				modelState = modelState.advance(move);
				Board state = this.getCurrentBoard();
				var event = state.getWinner().isEmpty() ? Observer.Event.MOVE_MADE : Observer.Event.GAME_OVER;
				for (Observer o : observers) o.onModelChanged(state, event);
			}
		};
	}
}
