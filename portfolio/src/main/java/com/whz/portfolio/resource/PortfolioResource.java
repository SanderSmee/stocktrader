/*
 * Copyright © 2020-2021, Fachgruppe Informatik WHZ <help.flaxel@gmail.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.whz.portfolio.resource;

import static io.vlingo.common.serialization.JsonSerialization.serialized;
import static io.vlingo.http.Response.Status.Created;
import static io.vlingo.http.Response.Status.NotFound;
import static io.vlingo.http.Response.Status.Ok;
import static io.vlingo.http.ResponseHeader.ContentType;
import static io.vlingo.http.ResponseHeader.Location;
import static io.vlingo.http.ResponseHeader.headers;
import static io.vlingo.http.ResponseHeader.of;
import static io.vlingo.http.resource.ResourceBuilder.get;
import static io.vlingo.http.resource.ResourceBuilder.post;
import static io.vlingo.http.resource.ResourceBuilder.resource;

import com.whz.portfolio.infrastructure.AcquireStockData;
import com.whz.portfolio.infrastructure.PortfolioData;
import com.whz.portfolio.infrastructure.StockQuoteData;
import com.whz.portfolio.infrastructure.persistence.PortfolioQueries;
import com.whz.portfolio.infrastructure.persistence.QueryModelStateStoreProvider;
import com.whz.portfolio.model.portfolio.Portfolio;
import com.whz.portfolio.model.portfolio.PortfolioEntity;
import io.vlingo.actors.Stage;
import io.vlingo.common.Completes;
import io.vlingo.http.Response;
import io.vlingo.http.resource.Resource;
import io.vlingo.http.resource.ResourceHandler;

/**
 * Class for the REST end points of the portfolio service.
 *
 * @since 1.0.0
 */
public class PortfolioResource extends ResourceHandler {

  private final Stage stage;
  private final PortfolioQueries portfolioQueries;

  /**
   * Initializes the StockQuoteSubscriber and StockAcquiredPublisher singletons.
   *
   * @param stage
   * @since 1.0.0
   */
  public PortfolioResource(final Stage stage) {
    this.stage = stage;
    this.portfolioQueries = QueryModelStateStoreProvider.instance().portfolioQueries;
    // access attempt to initialize the singletons
    StockQuoteSubscriber sub = StockQuoteSubscriber.INSTANCE;
    StockAcquiredPublisher pub = StockAcquiredPublisher.INSTANCE;
  }

  /**
   * GET-Request to test if the service is ready to do something.
   *
   * @return response for an asynchronous call with a potential result
   * @since 1.0.0
   */
  public Completes<Response> ready() {
    return Completes.withSuccess(Response.of(Response.Status.Ok));
  }

  /**
   * Retrieve an existing portfolio.
   *
   * @param id identifier for portfolio
   * @return A JSON representation of PortfolioData
   * @since 1.0.0
   */
  public Completes<Response> handleGetPortfolio(String id) {
    return portfolioQueries
        .portfolioOf(id)
        .andThenTo(
            PortfolioData.empty(),
            data ->
                Completes.withSuccess(
                    Response.of(
                        Ok, headers(of(ContentType, "application/json")), serialized(data))))
        .otherwise(noData -> Response.of(NotFound, "/portfolio/" + id));
  }

  /**
   * Create a new portfolio.
   *
   * @param owner owner of the portfolio
   * @return JSON representation of the created PortfolioData
   * @since 1.0.0
   */
  public Completes<Response> handleCreatePortfolio(String owner) {
    return Portfolio.defineWith(stage, owner)
        .andThenTo(
            state ->
                Completes.withSuccess(
                    Response.of(
                        Created,
                        headers(of(Location, "/portfolio/" + state.id))
                            .and(of(ContentType, "application/json")),
                        serialized(PortfolioData.from(state)))));
  }

  /**
   * Retrieve a specific stock.
   *
   * @param symbol
   * @return JSON representation of StockQuoteData
   * @since 1.0.0
   */
  public Completes<Response> handleGetStock(String symbol) {
    StockQuoteData result = StockQuoteSubscriber.INSTANCE.get(symbol);
    if (result == null) {
      return Completes.withFailure(Response.of(NotFound, "/stocks/" + symbol));
    }
    return Completes.withSuccess(
        Response.of(Ok, headers(of(ContentType, "application/json")), serialized(result)));
  }

  /**
   * Add a stock to an existing portfolio. The value of the purchase gets published to the account.
   *
   * @param id identifier for portfolio
   * @param data complete data about the stock which is acquired
   * @return JSON representation of the associated PortfolioData
   * @since 1.0.0
   */
  public Completes<Response> handleAcquireStock(String id, AcquireStockData data) {
    StockQuoteData stockQuoteData = StockQuoteSubscriber.INSTANCE.get(data.symbol);
    return resolve(id)
        .andThenTo(
            portfolio ->
                portfolio
                    .stockAcquired(
                        data.symbol,
                        stockQuoteData.regularMarketTime,
                        data.amount,
                        stockQuoteData.regularMarketPrice)
                    .andThenTo(
                        state -> {
                          PortfolioData portfolioData = PortfolioData.from(state);
                          StockAcquiredPublisher.INSTANCE.send(
                              portfolioData.owner, data.amount * stockQuoteData.regularMarketPrice);
                          return Completes.withSuccess(
                              Response.of(
                                  Ok,
                                  headers(of(ContentType, "application/json")),
                                  serialized(portfolioData)));
                        })
                    .otherwise(noData -> Response.of(NotFound, "/portfolio/" + id)));
  }

  /**
   * Retrieve all stocks from the cache.
   *
   * @return JSON representation of multiple StockQuoteData
   * @since 1.0.0
   */
  public Completes<Response> handleGetStocks() {
    return Completes.withSuccess(
        Response.of(
            Ok,
            headers(of(ContentType, "application/json")),
            serialized(StockQuoteSubscriber.INSTANCE.get())));
  }

  /**
   * Configures the REST end point routes.
   *
   * @since 1.0.0
   */
  @Override
  public Resource<?> routes() {
    return resource(
        getClass().getSimpleName(),
        get("/ready").handle(this::ready),
        get("/portfolio/{id}").param(String.class).handle(this::handleGetPortfolio),
        post("/portfolio").body(String.class).handle(this::handleCreatePortfolio),
        post("/portfolio/{id}")
            .param(String.class)
            .body(AcquireStockData.class)
            .handle(this::handleAcquireStock),
        get("/stocks/{symbol}").param(String.class).handle(this::handleGetStock),
        get("/stocks").handle(this::handleGetStocks));
  }

  private Completes<Portfolio> resolve(final String id) {
    return stage.actorOf(Portfolio.class, stage.addressFactory().from(id), PortfolioEntity.class);
  }
}
