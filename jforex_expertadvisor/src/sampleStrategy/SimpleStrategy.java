package sampleStrategy;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IMessage.Type;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class SimpleStrategy implements IStrategy {
	@Configurable("Instrument") public Instrument instrument = Instrument.EURUSD;
	@Configurable("Amount") public double amount = 0.001;
	@Configurable("Stop loss") public int stopLossPips = 40;
	@Configurable("Take profit") public int takeProfitPips = 40;

	private IEngine engine;
	private IHistory history;

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.history = context.getHistory();

		// 日足チャートの一つ前のバー情報を取得
		IBar prevDailyBar = history.getBar(this.instrument, Period.DAILY, OfferSide.ASK, 1);

        // 初期注文の売買サイドを決定
		OrderCommand orderCmd = prevDailyBar.getClose() > prevDailyBar.getOpen() ? OrderCommand.BUY : OrderCommand.SELL;

		// 注文処理を呼び出す
		submitOrder(this.amount, orderCmd, this.takeProfitPips);
	}

    private IOrder submitOrder(double amount, OrderCommand orderCmd, double takeProfitPips) throws JFException {
    	double stopLossPrice, takeProfitPrice;

        // 損切り値と利食い値を算出
    	if (orderCmd == OrderCommand.BUY) {
    		stopLossPrice = history.getLastTick(this.instrument).getAsk() - this.stopLossPips * this.instrument.getPipValue();
    		takeProfitPrice = history.getLastTick(this.instrument).getAsk() + takeProfitPips * this.instrument.getPipValue();
    	} else {
    		stopLossPrice =  history.getLastTick(this.instrument).getBid() + this.stopLossPips * this.instrument.getPipValue();
    		takeProfitPrice = history.getLastTick(this.instrument).getBid() - takeProfitPips * this.instrument.getPipValue();
    	}

        // 注文を送信(スリッページは20pipsに設定)
    	return engine.submitOrder(getLabel(orderCmd), this.instrument, orderCmd, amount, 0, 20, stopLossPrice, takeProfitPrice);
    }

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
		if (message.getType() == Type.ORDER_CLOSE_OK) {
			// クローズされた注文の情報を取得
			IOrder order = message.getOrder();

            // 次の注文の為の利食い値を決定
			if (order.getProfitLossInPips() > 0) submitOrder(amount, order.getOrderCommand(), 10);
			else submitOrder(amount, oppositeOrderCmd(order), this.takeProfitPips);
		}
	}

    private OrderCommand oppositeOrderCmd(IOrder order) {
    	// 反対のOrderCommandを返す
    	return order.isLong() ? OrderCommand.SELL : OrderCommand.BUY;
    }

	public void onStop() throws JFException {
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar)throws JFException {
    }

    private String getLabel(OrderCommand cmd) {
    	return cmd.toString() + System.currentTimeMillis();
    }
}
