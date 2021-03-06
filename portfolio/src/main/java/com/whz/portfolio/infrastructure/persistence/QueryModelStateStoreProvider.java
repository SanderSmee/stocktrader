/*
 * Copyright © 2020-2021, Fachgruppe Informatik WHZ <help.flaxel@gmail.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.whz.portfolio.infrastructure.persistence;

import com.whz.portfolio.infrastructure.PortfolioData;
import io.vlingo.actors.Stage;
import io.vlingo.lattice.model.stateful.StatefulTypeRegistry;
import io.vlingo.lattice.model.stateful.StatefulTypeRegistry.Info;
import io.vlingo.symbio.EntryAdapterProvider;
import io.vlingo.symbio.StateAdapterProvider;
import io.vlingo.symbio.store.dispatch.Dispatchable;
import io.vlingo.symbio.store.dispatch.Dispatcher;
import io.vlingo.symbio.store.dispatch.DispatcherControl;
import io.vlingo.symbio.store.state.StateStore;
import io.vlingo.symbio.store.state.StateTypeStateStoreMap;
import io.vlingo.xoom.actors.Settings;
import io.vlingo.xoom.annotation.persistence.Persistence.StorageType;
import io.vlingo.xoom.storage.Model;
import io.vlingo.xoom.storage.StoreActorBuilder;

/**
 * Generated class by 'VLINGO/XOOM Starter'.
 *
 * @since 1.0.0
 */
public class QueryModelStateStoreProvider {
  private static QueryModelStateStoreProvider instance;

  public final StateStore store;
  public final PortfolioQueries portfolioQueries;

  public static QueryModelStateStoreProvider instance() {
    return instance;
  }

  public static QueryModelStateStoreProvider using(
      final Stage stage, final StatefulTypeRegistry registry) {
    final Dispatcher noop =
        new Dispatcher() {
          public void controlWith(final DispatcherControl control) {}

          public void dispatch(Dispatchable d) {}
        };

    return using(stage, registry, noop);
  }

  @SuppressWarnings("rawtypes")
  public static QueryModelStateStoreProvider using(
      final Stage stage, final StatefulTypeRegistry registry, final Dispatcher dispatcher) {
    if (instance != null) {
      return instance;
    }

    new EntryAdapterProvider(stage.world()); // future use

    StateTypeStateStoreMap.stateTypeToStoreName(
        PortfolioData.class, PortfolioData.class.getSimpleName());

    final StateAdapterProvider stateAdapterProvider = new StateAdapterProvider(stage.world());
    stateAdapterProvider.registerAdapter(PortfolioData.class, new PortfolioDataStateAdapter());

    final StateStore store =
        StoreActorBuilder.from(
            stage, Model.QUERY, dispatcher, StorageType.STATE_STORE, Settings.properties(), true);

    registry.register(new Info(store, PortfolioData.class, PortfolioData.class.getSimpleName()));

    instance = new QueryModelStateStoreProvider(stage, store);

    return instance;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private QueryModelStateStoreProvider(final Stage stage, final StateStore store) {
    this.store = store;
    this.portfolioQueries =
        stage.actorFor(PortfolioQueries.class, PortfolioQueriesActor.class, store);
  }

  public static void reset() {
    instance = null;
  }
}
