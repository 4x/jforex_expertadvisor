package sampleStrategy;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class NovemberStrategyKakaku implements IStrategy {
    @Configurable("通貨ペア") public Instrument pInstrument = Instrument.EURUSD;
    @Configurable("期間") public Period pPeriod = Period.ONE_HOUR;
    @Configurable("注文数量") public double pAmount = 0.001;
    @Configurable("損切(pips)") public int pStopLoss = 35;
    @Configurable("利食(pips)") public int pTakeProfit = 40;

    private IEngine engine = null;
    private IIndicators indicators = null;
    private IConsole console = null;

    public void onStart(IContext context) throws JFException {
        engine = context.getEngine();
        indicators = context.getIndicators();
        console = context.getConsole();
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        if (message != null && message.getType() == IMessage.Type.ORDER_CLOSE_OK) {
            console.getOut().println("Order : " + message.getOrder().getLabel() +
                                     " " + message.getOrder().getOrderCommand() +
                                     " Pips: " + message.getOrder().getProfitLossInPips());
        }
    }

    public void onStop() throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }

    public void onBar(Instrument instrument, Period period, IBar askbar, IBar bidbar) throws JFException {
        if (period != pPeriod || instrument != pInstrument) return;
        if (askbar.getVolume() == 0) return;

        double bidOpen = bidbar.getOpen();
        double askClose = askbar.getClose();
        double bidClose = bidbar.getClose();

        double sma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, 0);
        double sma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, 0);
        double sma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, 0);

        double[] bands = indicators.bbands(
            instrument,
            period,
            OfferSide.BID,
            IIndicators.AppliedPrice.CLOSE,
            30,
            0.8,
            0.8,
            IIndicators.MaType.DEMA,
            0);

        if (positionsTotal(instrument) == 0) {
            if (bidClose > bands[0] && bidOpen < bands[0] && bidClose > sma50 && bidClose > sma100 && bidClose > sma200) {
                engine.submitOrder(getLabel(instrument), instrument, IEngine.OrderCommand.BUY, pAmount, 0, 3,
                    askClose - instrument.getPipValue() * pStopLoss, askClose + instrument.getPipValue() * pTakeProfit);
            } else if (bidClose < bands[2] && bidOpen > bands[2] && bidClose < sma50 && bidClose < sma100 && bidClose < sma200) {
                engine.submitOrder(getLabel(instrument), instrument, IEngine.OrderCommand.SELL, pAmount, 0, 3,
                    bidClose + instrument.getPipValue() * pStopLoss, bidClose - instrument.getPipValue() * pTakeProfit);
            }
        }
    }

    protected int positionsTotal(Instrument instrument) throws JFException {
        int counter = 0;

        for (IOrder order : engine.getOrders(instrument))
            if (order.getState() == IOrder.State.FILLED) counter++;

        return counter;
    }

    protected String getLabel(Instrument instrument) {
        String label = instrument.name();

        long time = new java.util.Date().getTime();

        label = label.substring(0, 2) + label.substring(3, 5);
        label = label + time;
        label = label.toLowerCase();

        return label;
    }
}