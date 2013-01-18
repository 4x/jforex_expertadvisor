package sampleStrategy;

import java.util.concurrent.TimeUnit;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class RSI implements IStrategy {

	@Configurable("通貨ペア")
	public Instrument instrument = Instrument.USDJPY;

	@Configurable("金額")
	public double amount = 0.25;

	@Configurable("時間枠")
	public Period period = Period.FIFTEEN_MINS;

	@Configurable("ビッド or アスク")
	public OfferSide offerSide = OfferSide.BID;

	@Configurable("使用レート")
	public AppliedPrice appliedPrice = AppliedPrice.CLOSE;

	@Configurable("期間")
	public int rsiTimePeriod = 14;

	@Configurable("買われ過ぎ")
	public int rsiUpperLine = 70;

	@Configurable("売られ過ぎ")
	public int rsiLowerLine = 30;

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IOrder order = null;

    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (instrument != this.instrument || period != this.period) return;

        double rsi = indicators.rsi(instrument, period, this.offerSide, this.appliedPrice, this.rsiTimePeriod, 1);
        double rsiPrev = indicators.rsi(instrument, period, this.offerSide, this.appliedPrice, this.rsiTimePeriod, 2);

        if (rsiPrev < rsiLowerLine && rsi > rsiLowerLine) {
            if (this.order == null) submitOrder("Buy", OrderCommand.BUY);
            this.console.getOut().println("Buy: rsiPrev=" + rsiPrev + ",rsi=" + rsi);
        } else if (rsiPrev > rsiUpperLine && rsi < rsiUpperLine) {
            if (this.order == null) submitOrder("Sell", OrderCommand.SELL);
            this.console.getOut().println("Sell: rsiPrev=" + rsiPrev + ",rsi=" + rsi);
        } else if (this.order != null) {
            if (rsiPrev > rsiLowerLine && rsi < rsiLowerLine) closeOrder();
            if (rsiPrev < rsiUpperLine && rsi > rsiUpperLine) closeOrder();
        }
    }

    private void submitOrder(String label, OrderCommand orderCmd) throws JFException {
        this.order = engine.submitOrder(label, this.instrument, orderCmd, this.amount);

        while (order.getState() == IOrder.State.CREATED || order.getState() == IOrder.State.OPENED) {
            order.waitForUpdate(2, TimeUnit.SECONDS);
        }

        if (order.getState() == IOrder.State.CANCELED) {
            this.order = null;
        }
    }

    private void closeOrder() throws JFException {
        if (this.order.getState() == IOrder.State.FILLED) {
            this.order.close();
            this.order = null;
        }
    }
}