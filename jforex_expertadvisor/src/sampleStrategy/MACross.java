package sampleStrategy;

import java.util.Date;
import java.util.List;
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
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class MACross implements IStrategy {
    @Configurable("通貨ペア") public Instrument instrument = Instrument.EURUSD;
    @Configurable("金額") public double amount = 0.25;
    @Configurable("時間枠") public Period period = Period.TEN_MINS;
    @Configurable("ビッド or アスク") public OfferSide offerSide = OfferSide.BID;
    @Configurable("使用レート") public AppliedPrice appliedPrice = AppliedPrice.CLOSE;
    @Configurable("移動平均線１ 期間") public int fastMaTimePeriod = 7;
    @Configurable("移動平均線１ 種類") public MaType fastMaType = MaType.SMA;
    @Configurable("移動平均線２ 期間") public int slowMaTimePeriod = 21;
    @Configurable("移動平均線２ 種類") public MaType slowMaType = MaType.SMA;
    @Configurable("過去n期間") public int minMaxTimePeriod = 10;
    @Configurable("仕切りルール") public boolean exitRule = true;

	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IContext context;
	private IIndicators indicators;

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

        List orders = this.engine.getOrders(instrument);
        IOrder order = orders.isEmpty() ? null : (IOrder)orders.get(0);

        double fastMa = indicators.ma(instrument, period, this.offerSide, this.appliedPrice, this.fastMaTimePeriod, this.fastMaType, 1);
        double fastMaPrev = indicators.ma(instrument, period, this.offerSide, this.appliedPrice, this.fastMaTimePeriod, this.fastMaType, 2);
        double slowMa = indicators.ma(instrument, period, this.offerSide, this.appliedPrice, this.slowMaTimePeriod, this.slowMaType, 1);
        double slowMaPrev = indicators.ma(instrument, period, this.offerSide, this.appliedPrice, this.slowMaTimePeriod, this.slowMaType, 2);

        if (fastMaPrev < slowMaPrev && fastMa > slowMa) {
            if (order != null) order = closeOrder(order);
            if (order == null) order = submitOrder(OrderCommand.BUY);
        } else if (fastMaPrev > slowMaPrev && fastMa < slowMa) {
            if (order != null) order = closeOrder(order);
            if (order == null) order = submitOrder(OrderCommand.SELL);
        }

        if (this.exitRule &&  order != null) {
            if (order.isLong()) {
                double min = indicators.min(instrument, period, this.offerSide, this.appliedPrice, this.minMaxTimePeriod, 2);
                double stopLossPrice = order.getStopLossPrice();

                if (stopLossPrice == 0 || stopLossPrice < min) {
                    order.setStopLossPrice(min);
                }
            } else {
                double max = indicators.max(instrument, period, this.offerSide, this.appliedPrice, this.minMaxTimePeriod, 2);
                double stopLossPrice = order.getStopLossPrice();

                if (stopLossPrice == 0 || stopLossPrice > max) {
                    order.setStopLossPrice(max);
                }
            }
        }
    }

    private IOrder submitOrder(OrderCommand orderCmd) throws JFException {
        Date date = new Date();
        String label = orderCmd.toString() + "_" + date.getTime();

        IOrder order = engine.submitOrder(label, this.instrument, orderCmd, this.amount);

        while (order.getState() == IOrder.State.CREATED || order.getState() == IOrder.State.OPENED) {
            order.waitForUpdate(2, TimeUnit.SECONDS);
        }

        if (order.getState() == IOrder.State.CANCELED) {
            this.console.getOut().println("注文がキャンセルされました。");
            order = null;
        }

        return order;
    }

    private IOrder closeOrder(IOrder order) throws JFException {
        if (order.getState() == IOrder.State.FILLED) {
            order.close();
        }

        return null;
    }
}