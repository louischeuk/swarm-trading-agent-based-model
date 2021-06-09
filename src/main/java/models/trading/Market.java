package models.trading;

import java.util.HashMap;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;
import simudyne.core.graph.Message.Double;

public class Market extends Agent<TradingModel.Globals> {

  @Variable
  public double price;

  @Variable
  public double trueValue;

  int numTraders;
  long tick = 0L;
//  int marketShockStep = 50;

  HashMap<Long, java.lang.Double> historicalPrices;

  /* --------- function definitions --------- */

  private static Action<Market> action(SerializableConsumer<Market> consumer) {
    return Action.create(Market.class, consumer);
  }

  public static Action<Market> sendPriceToTraders =
      action(m -> {
        m.getLinks(Links.TradeLink.class).send(Messages.MarketPrice.class, m.price);
        m.getLinks(Links.TradeLink.class).send(Messages.Tick.class, m.tick);
      });


  public static Action<Market> calcPriceImpact =
      action(
          m -> {

            // get total amount of buys and sells shares for all agents
            double buys = m.getMessagesOfType(Messages.BuyOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();
            double sells = m.getMessagesOfType(Messages.SellOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();
            double shorts = m.getMessagesOfType(Messages.ShortSellOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();
            double covers = m.getMessagesOfType(Messages.CoverShortPosOrderPlaced.class).stream()
                .mapToDouble(Double::getBody).sum();

            System.out.println(
                "Total buys shares: " + buys + " (with cover short Pos: " + covers + ")");
            System.out.println(
                "Total sell shares: " + sells + " (with short-sell shares: " + shorts + ")");
            System.out.println("Total volume: " + (buys + sells));

            double netDemand = buys - sells;
            System.out.println("Net demand: " + netDemand);

            if (netDemand != 0) {
              double lambda = m.getGlobals().lambda;
              double priceChange = netDemand * lambda;
              System.out.println("Price change: " + priceChange);

              m.price += priceChange;
              m.price = m.price < 0 ? 0 : m.price;
            }

            m.getDoubleAccumulator("price").add(m.price);

            // check if marketShock is triggered
//            if (++m.tick == m.marketShockStep) {
////              m.triggerMarketShock();
//            }

            /* for momentum traders to update the moving averages */
            m.historicalPrices.put(++m.tick, m.price);
            System.out.println("Time step: " + m.tick + "\n");

            m.getLinks(Links.TradeLink.class).send(Messages.HistoricalPrices.class,
              (a, t) -> a.historicalPrices = m.historicalPrices);
            m.getLinks(Links.TradeLink.class).send(Messages.Tick.class, m.tick);
            m.getLinks(Links.TradeLink.class).send(Messages.MarketPrice.class, m.price);

          });

//  private void triggerMarketShock() {
//    getGlobals().trueValue = 150; // hard care when testing
//    getLinks(Links.TradeLink.class).send(Messages.MarketShock.class, marketShockStep);
//
//  }

  /* update true value V(t) - random walk: */
  public static Action<Market> updateTrueValue =
      action(m -> {

        /*
           sum of jump size = Y_i * N_t = N(0,s_j) * P(lambda)   Note. s_j may be == s_v
           sd_j = 1, lambda = 2
        */
        double jumpDiffusionProcess =
            m.getPrng().normal(0, 3).sample()
                * m.getPrng().poisson(2).sample();

        System.out.println("jumpDiffusionProcess: " + jumpDiffusionProcess);

        System.out.println("prev True value: " + m.trueValue);

        /*
           V(t) = V(t – 1) + N(0,sd_v) + jump diffusion process
                = V(t – 1) + N(0,sd_v) + summation[i, N_t](Y_i)
        */
        m.trueValue = m.trueValue
            + m.getPrng().normal(0, m.getGlobals().stdDev).sample()
            + jumpDiffusionProcess;

        m.trueValue = m.trueValue < 0 ? 0 : m.trueValue;
        System.out.println("New True value: " + m.trueValue);

        m.getLinks(Links.TradeLink.class).send(Messages.TrueValue.class, m.trueValue);
      });


}
