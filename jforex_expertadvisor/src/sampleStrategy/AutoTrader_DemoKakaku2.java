package sampleStrategy;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

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

public class AutoTrader_DemoKakaku2 implements IStrategy {
    @Configurable("通貨ペア") public Instrument pInstrument = Instrument.USDJPY;
    @Configurable("期間") public Period pPeriod = Period.FOUR_HOURS;
    @Configurable("スリッページ") public double pSlippage = 4;
    @Configurable("注文数量") public double pAmount = 0.001;

    private IEngine engine;
    private IConsole console;
    private IIndicators indicators;
    private IAccount account;

    private double riskpct = 0.33;
    private double TP = 38;
    private double SL = 105;
    private int trailingStop = 10;
    private int lockPip = 3;
    private boolean moveBE = true;

    private int _UseHourTrade = 0;
    private int _FromHourTrade = 18;
    private int _ToHourTrade = 24;

    private SimpleDateFormat df = new SimpleDateFormat("MMdd_HHmmss");

    public void onStart(IContext context) throws JFException {
        engine = context.getEngine();
        console = context.getConsole();
        indicators = context.getIndicators();
        console.getOut().println("---Auto Trader ON---");
    }

    public void onAccount(IAccount account) throws JFException {
        this.account = account;
    }

    public void onMessage(IMessage message) throws JFException {}

    public void onStop() throws JFException { console.getOut().println("---Auto Trader OFF---"); }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != pInstrument) return; // 対象通貨ペアではない

        boolean tradingAllowed = true;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(tick.getTime());

        int _day = calendar.get(Calendar.DAY_OF_WEEK) - 1; // 0:Sun、1:Mon ... 6:Sat
        int _hours = calendar.get(Calendar.HOUR_OF_DAY);

        if (_day == 5 && _hours > 20) return; // (UTCで)金曜日の21時以降なら...
        if (_day > 5 || (_day == 0 && _hours < 21)) return; // (UTCで)「土曜日」か「日曜日の21時前」なら...

        if (_UseHourTrade == 1) { // 取引時間を制限するか...
            if (!(_hours >= _FromHourTrade && _hours <= _ToHourTrade)) {
                return;
            }
        }

        int pos = 0;

        for (IOrder order : engine.getOrders(instrument)) { // 既存の注文を取得
            if (order.getState() == IOrder.State.CREATED) tradingAllowed = false;

            if (order.getState() == IOrder.State.FILLED) {
                tradingAllowed = false;

                if (order.isLong()) pos = 1;
                else pos = -1;

                if (SL > 0 && order.getStopLossPrice() == 0) // 損切を初期設定
                    order.setStopLossPrice(order.getOpenPrice() - pos * instrument.getPipValue() * SL, OfferSide.BID, trailingStop);

                if (TP > 0 && order.getTakeProfitPrice() == 0) // 利食を初期設定
                    order.setTakeProfitPrice(order.getOpenPrice() + pos * instrument.getPipValue() * TP);

                double open = order.getOpenPrice();
                double stop = order.getStopLossPrice();
                double diff = open - stop; // stop loss distance

                if (order.isLong()) { // 買注文なら
                    if (moveBE && diff > 0 && tick.getBid() > (open + diff)) {
                        order.setStopLossPrice(open + instrument.getPipValue() * lockPip);
                    }
                } else { // 売注文なら
                    if (moveBE && diff < 0 && tick.getAsk() < (open + diff)) {
                        order.setStopLossPrice(open - (instrument.getPipValue() * lockPip));
                    }
                }
            }
        }

        if (pos == 0) {
            double rsi = indicators.rsi(instrument, pPeriod, OfferSide.BID, IIndicators.AppliedPrice.OPEN, 5, 0);

            if (rsi <= 0) return;

            if (rsi < 30 && tradingAllowed) {
                engine.submitOrder(getLabel(instrument,calendar) + "_L", instrument, IEngine.OrderCommand.BUY, pAmount, 0, pSlippage);
                console.getOut().println(instrument.name() + ": Long entry, lot = " + pAmount);
            }

            if (rsi > 70 && tradingAllowed) {
                engine.submitOrder(getLabel(instrument,calendar) + "_S", instrument, IEngine.OrderCommand.SELL, pAmount, 0, pSlippage);
                console.getOut().println(instrument.name() + ": Short entry, lot = " + pAmount);
            }
        }
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {}

    // ラベルを生成する
    private String getLabel(Instrument instrument, Calendar calendar) {
        String label = instrument.name();
        label = label.substring(0, 3) + label.substring(3, 6);
        label = label + "_" + df.format(calendar.getTime());
        return label;
    }
}
